package myworld.mstrade;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class TradeService {
    private static final int MAIN_SLOTS = 36;
    private static final int SHULKER_SLOTS = 27;
    private static final int MAX_TRADES_PER_CALL = 512;

    private TradeService() {}

    public record Result(boolean success, int trades, String message) {}

    public enum MatchingOfferState {
        NONE,
        AVAILABLE,
        OUT_OF_STOCK
    }

    public static Result trade(ServerPlayer fake) {
        if (fake == null) return new Result(false, 0, "target player is not online.");
        if (!isCarpetFake(fake)) return new Result(false, 0, fake.getGameProfile().name()+" is online but is not a Carpet fake player.");

        ItemStack selector = fake.getMainHandItem();
        if (selector.isEmpty()) return new Result(false, 0, fake.getGameProfile().name()+" must hold the item to buy in the main hand.");

        Villager villager = villagerInFront(fake, 5.0);
        if (villager == null) return new Result(false, 0, fake.getGameProfile().name()+" is not facing a villager within 5 blocks.");

        return trade(fake, villager);
    }

    public static Result trade(ServerPlayer fake, Villager villager) {
        if (fake == null) return new Result(false, 0, "target player is not online.");
        if (!isCarpetFake(fake)) return new Result(false, 0, fake.getGameProfile().name()+" is online but is not a Carpet fake player.");
        if (villager == null || !villager.isAlive()) return new Result(false, 0, "saved villager is not currently loaded.");

        ItemStack selector = fake.getMainHandItem();
        if (selector.isEmpty()) return new Result(false, 0, fake.getGameProfile().name()+" must hold the item to buy in the main hand.");

        int selectedSlot = fake.getInventory().getSelectedSlot();
        int trades = 0;
        boolean noCurrency = false;
        boolean noSpace = false;
        boolean outOfStock = false;

        Player previousTrader = villager.getTradingPlayer();
        if (previousTrader != null && previousTrader != fake) {
            return new Result(false, 0, "villager is already trading with another player.");
        }
        villager.setTradingPlayer(fake);
        try {
            for (MerchantOffer offer : villager.getOffers()) {
                if (!offer.getResult().is(selector.getItem())) continue;
                if (!emeraldOnly(offer)) continue;
                while (!offer.isOutOfStock() && trades < MAX_TRADES_PER_CALL) {
                    // Simulate the complete payment, change and result insertion on
                    // copies. Separate capacity checks can claim the same free slot.
                    List<ItemStack> working = copyMainInventory(fake.getInventory());
                    ItemStack result = offer.assemble();
                    if (!canStore(working, result, selectedSlot)) {
                        noSpace = true;
                        break;
                    }
                    int cost = emeraldCost(offer);
                    if (!hasEmeraldValue(working, cost, selectedSlot)) {
                        noCurrency = true;
                        break;
                    }
                    if (!canSafelyUncraftChange(working, cost, selectedSlot)) {
                        noSpace = true;
                        break;
                    }
                    if (!withdrawEmeralds(working, cost, selectedSlot)) {
                        noCurrency = true;
                        break;
                    }
                    if (!store(working, result.copy(), selectedSlot)) {
                        // Change and the result may compete for the same space.
                        // Discard the simulated inventory without consuming payment.
                        noSpace = true;
                        break;
                    }
                    applyMainInventory(fake.getInventory(), working);
                    offer.take(offer.getCostA().copy(), offer.getCostB().copy());
                    villager.notifyTrade(offer);
                    fake.awardStat(Stats.TRADED_WITH_VILLAGER);
                    trades++;
                }
                if (offer.isOutOfStock()) outOfStock = true;
                if (noCurrency || noSpace || trades >= MAX_TRADES_PER_CALL) break;
            }
        } finally {
            villager.setTradingPlayer(previousTrader);
        }

        String name = fake.getGameProfile().name();
        if (trades > 0) {
            String suffix = noSpace ? "; stopped because inventory and shulker boxes are full." :
                noCurrency ? "; stopped because emeralds are insufficient." :
                outOfStock ? "; matching villager trade is now locked." : ".";
            return new Result(true, trades, name+" completed "+trades+" trade(s)"+suffix);
        }
        if (noSpace) return new Result(false, 0, name+" has no safe space for the trade result.");
        if (noCurrency) return new Result(false, 0, name+" has insufficient emeralds (emerald blocks and shulker contents are checked).");
        if (outOfStock) return new Result(false, 0, "the matching villager trade is locked.");
        return new Result(false, 0, "no unlocked emerald-only villager trade produces "+selector.getHoverName().getString()+".");
    }

    private static boolean isCarpetFake(ServerPlayer player) {
        return player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake");
    }

    public static Villager villagerInFront(ServerPlayer player, double range) {
        Vec3 eye = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F).normalize();
        return villagerAlongView(player.level(), eye, direction, range);
    }

    public static Villager villagerAtSavedView(ServerLevel level, TradeChainStore.Entry entry, double range) {
        double yaw = Math.toRadians(entry.yaw());
        double pitch = Math.toRadians(entry.pitch());
        double horizontal = Math.cos(pitch);
        Vec3 eye = new Vec3(entry.x(), entry.y() + 1.62, entry.z());
        Vec3 direction = new Vec3(-Math.sin(yaw) * horizontal, -Math.sin(pitch), Math.cos(yaw) * horizontal).normalize();
        return villagerAlongView(level, eye, direction, range);
    }

    public static boolean hasUnlockedMatchingOffer(ServerPlayer fake, Villager villager) {
        return matchingOfferState(fake, villager) == MatchingOfferState.AVAILABLE;
    }

    public static MatchingOfferState matchingOfferState(ServerPlayer fake, Villager villager) {
        ItemStack selector = fake.getMainHandItem();
        if (selector.isEmpty() || villager == null || !villager.isAlive()) return MatchingOfferState.NONE;
        boolean found = false;
        for (MerchantOffer offer : villager.getOffers()) {
            if (!offer.getResult().is(selector.getItem()) || !emeraldOnly(offer)) continue;
            found = true;
            if (!offer.isOutOfStock()) return MatchingOfferState.AVAILABLE;
        }
        return found ? MatchingOfferState.OUT_OF_STOCK : MatchingOfferState.NONE;
    }

    private static Villager villagerAlongView(net.minecraft.world.level.Level level, Vec3 eye, Vec3 direction, double range) {
        Vec3 end = eye.add(direction.scale(range));
        AABB search = new AABB(eye, end).inflate(1.0);
        Villager nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (Villager villager : level.getEntitiesOfClass(Villager.class, search, Villager::isAlive)) {
            Vec3 offset = villager.getBoundingBox().getCenter().subtract(eye);
            double distance = offset.length();
            if (distance > range || distance < 0.01) continue;
            if (direction.dot(offset.normalize()) < 0.70) continue;
            if (distance < nearestDistance) {
                nearest = villager;
                nearestDistance = distance;
            }
        }
        return nearest;
    }

    private static boolean emeraldOnly(MerchantOffer offer) {
        return isEmerald(offer.getCostA()) && (offer.getCostB().isEmpty() || isEmerald(offer.getCostB()));
    }

    private static int emeraldCost(MerchantOffer offer) {
        return offer.getCostA().getCount() + (offer.getCostB().isEmpty() ? 0 : offer.getCostB().getCount());
    }

    private static boolean isEmerald(ItemStack stack) {
        return stack.is(Items.EMERALD);
    }

    private static boolean isEmeraldBlock(ItemStack stack) {
        return stack.is(Items.EMERALD_BLOCK);
    }

    static boolean isShulker(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
    }

    private static List<ItemStack> copyMainInventory(Inventory inventory) {
        List<ItemStack> copy = new ArrayList<>(MAIN_SLOTS);
        for (int slot = 0; slot < MAIN_SLOTS; slot++) copy.add(inventory.getItem(slot).copy());
        return copy;
    }

    private static void applyMainInventory(Inventory inventory, List<ItemStack> items) {
        for (int slot = 0; slot < MAIN_SLOTS; slot++) inventory.setItem(slot, items.get(slot));
        inventory.setChanged();
    }

    private static boolean hasEmeraldValue(List<ItemStack> inventory, int required, int selectedSlot) {
        int value = 0;
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            if (slot == selectedSlot) continue;
            ItemStack stack = inventory.get(slot);
            if (isEmerald(stack)) value += stack.getCount();
            else if (isEmeraldBlock(stack)) value += stack.getCount() * 9;
            else if (isShulker(stack)) value += emeraldValueInBox(stack);
            if (value >= required) return true;
        }
        return false;
    }

    private static int emeraldValueInBox(ItemStack box) {
        int value = 0;
        for (ItemStack stack : boxContents(box)) {
            if (isEmerald(stack)) value += stack.getCount();
            else if (isEmeraldBlock(stack)) value += stack.getCount() * 9;
        }
        return value;
    }

    private static boolean withdrawEmeralds(List<ItemStack> inventory, int required, int selectedSlot) {
        int remaining = takeItem(inventory, Items.EMERALD, required, selectedSlot);
        if (remaining == 0) return true;
        remaining = takeEmeraldsFromBoxes(inventory, remaining, selectedSlot);
        if (remaining == 0) return true;
        return uncraftEmeraldBlocks(inventory, remaining, selectedSlot);
    }

    // A block of emeralds can leave at most eight emeralds as change. Check for
    // that change before removing any currency, so a completely full inventory
    // cannot lose payment without receiving a result.
    private static boolean canSafelyUncraftChange(List<ItemStack> inventory, int required, int selectedSlot) {
        int looseEmeralds = 0;
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            if (slot == selectedSlot) continue;
            ItemStack stack = inventory.get(slot);
            if (isEmerald(stack)) looseEmeralds += stack.getCount();
            else if (isShulker(stack)) {
                for (ItemStack boxed : boxContents(stack)) if (isEmerald(boxed)) looseEmeralds += boxed.getCount();
            }
            if (looseEmeralds >= required) return true;
        }
        int shortage = required - looseEmeralds;
        int change = (9 - (shortage % 9)) % 9;
        return change == 0 || canStore(inventory, new ItemStack(Items.EMERALD, change), selectedSlot);
    }

    private static int takeItem(List<ItemStack> inventory, net.minecraft.world.item.Item item, int required, int selectedSlot) {
        int remaining = required;
        for (int slot = 0; slot < MAIN_SLOTS && remaining > 0; slot++) {
            if (slot == selectedSlot) continue;
            ItemStack stack = inventory.get(slot);
            if (!stack.is(item)) continue;
            int moved = Math.min(remaining, stack.getCount());
            stack.shrink(moved);
            remaining -= moved;
        }
        return remaining;
    }

    private static int takeEmeraldsFromBoxes(List<ItemStack> inventory, int required, int selectedSlot) {
        int remaining = required;
        for (int slot = 0; slot < MAIN_SLOTS && remaining > 0; slot++) {
            if (slot == selectedSlot) continue;
            ItemStack box = inventory.get(slot);
            if (!isShulker(box)) continue;
            NonNullList<ItemStack> contents = boxContents(box);
            for (int index = 0; index < contents.size() && remaining > 0; index++) {
                ItemStack stack = contents.get(index);
                if (!isEmerald(stack)) continue;
                int moved = Math.min(remaining, stack.getCount());
                stack.shrink(moved);
                remaining -= moved;
            }
            saveBoxContents(box, contents);
            inventory.set(slot, box);
        }
        return remaining;
    }

    // Converts as few emerald blocks as necessary. Any change is inserted using
    // the same box-first storage policy. The caller only reaches this after
    // proving that the total emerald value is sufficient.
    private static boolean uncraftEmeraldBlocks(List<ItemStack> inventory, int required, int selectedSlot) {
        int remaining = required;
        for (int slot = 0; slot < MAIN_SLOTS && remaining > 0; slot++) {
            if (slot == selectedSlot) continue;
            ItemStack stack = inventory.get(slot);
            if (!isEmeraldBlock(stack)) continue;
            while (remaining > 0 && isEmeraldBlock(stack)) {
                stack.shrink(1);
                int provided = Math.min(9, remaining);
                remaining -= provided;
                int change = 9 - provided;
                if (change > 0 && !store(inventory, new ItemStack(Items.EMERALD, change), selectedSlot)) return false;
            }
        }
        for (int slot = 0; slot < MAIN_SLOTS && remaining > 0; slot++) {
            if (slot == selectedSlot) continue;
            ItemStack box = inventory.get(slot);
            if (!isShulker(box)) continue;
            for (int index = 0; index < SHULKER_SLOTS && remaining > 0; index++) {
                // Reload after each write: storing change may have updated this box.
                NonNullList<ItemStack> contents = boxContents(box);
                ItemStack stack = contents.get(index);
                while (remaining > 0 && isEmeraldBlock(stack)) {
                    stack.shrink(1);
                    int provided = Math.min(9, remaining);
                    remaining -= provided;
                    int change = 9 - provided;
                    saveBoxContents(box, contents);
                    inventory.set(slot, box);
                    if (change > 0 && !store(inventory, new ItemStack(Items.EMERALD, change), selectedSlot)) return false;
                    contents = boxContents(box);
                    stack = contents.get(index);
                }
            }
        }
        return remaining == 0;
    }

    private static boolean canStore(List<ItemStack> inventory, ItemStack incoming, int selectedSlot) {
        if (incoming.isEmpty()) return true;
        int capacity = 0;
        if (!isShulker(incoming)) {
            for (int slot = 0; slot < MAIN_SLOTS; slot++) {
                ItemStack box = inventory.get(slot);
                if (isShulker(box)) capacity += boxCapacity(box, incoming);
                if (capacity >= incoming.getCount()) return true;
            }
        }
        for (int slot = 0; slot < MAIN_SLOTS; slot++) {
            ItemStack stack = inventory.get(slot);
            if (stack.isEmpty()) capacity += incoming.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, incoming)) capacity += stack.getMaxStackSize() - stack.getCount();
            if (capacity >= incoming.getCount()) return true;
        }
        return false;
    }

    private static int boxCapacity(ItemStack box, ItemStack incoming) {
        int capacity = 0;
        for (ItemStack stack : boxContents(box)) {
            if (stack.isEmpty()) capacity += incoming.getMaxStackSize();
            else if (ItemStack.isSameItemSameComponents(stack, incoming)) capacity += stack.getMaxStackSize() - stack.getCount();
        }
        return capacity;
    }

    // Box-first storage is intentional: trading results fill existing shulker
    // stacks and empty shulker slots before taking loose backpack space.
    private static boolean store(List<ItemStack> inventory, ItemStack incoming, int selectedSlot) {
        if (!isShulker(incoming)) {
            for (int slot = 0; slot < MAIN_SLOTS && !incoming.isEmpty(); slot++) {
                ItemStack box = inventory.get(slot);
                if (!isShulker(box)) continue;
                NonNullList<ItemStack> contents = boxContents(box);
                insert(contents, incoming);
                saveBoxContents(box, contents);
                inventory.set(slot, box);
            }
        }
        for (int slot = 0; slot < MAIN_SLOTS && !incoming.isEmpty(); slot++) {
            ItemStack stack = inventory.get(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, incoming)) {
                int moved = Math.min(incoming.getCount(), stack.getMaxStackSize() - stack.getCount());
                if (moved > 0) {
                    stack.grow(moved);
                    incoming.shrink(moved);
                }
            }
        }
        for (int slot = 0; slot < MAIN_SLOTS && !incoming.isEmpty(); slot++) {
            if (inventory.get(slot).isEmpty()) inventory.set(slot, incoming.split(Math.min(incoming.getCount(), incoming.getMaxStackSize())));
        }
        return incoming.isEmpty();
    }

    private static void insert(NonNullList<ItemStack> contents, ItemStack incoming) {
        for (int index = 0; index < contents.size() && !incoming.isEmpty(); index++) {
            ItemStack current = contents.get(index);
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, incoming)) {
                int moved = Math.min(incoming.getCount(), current.getMaxStackSize() - current.getCount());
                if (moved > 0) {
                    current.grow(moved);
                    incoming.shrink(moved);
                }
            }
        }
        for (int index = 0; index < contents.size() && !incoming.isEmpty(); index++) {
            if (contents.get(index).isEmpty()) contents.set(index, incoming.split(Math.min(incoming.getCount(), incoming.getMaxStackSize())));
        }
    }

    static NonNullList<ItemStack> boxContents(ItemStack box) {
        NonNullList<ItemStack> contents = NonNullList.withSize(SHULKER_SLOTS, ItemStack.EMPTY);
        box.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).copyInto(contents);
        return contents;
    }

    static void saveBoxContents(ItemStack box, List<ItemStack> contents) {
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
    }
}
