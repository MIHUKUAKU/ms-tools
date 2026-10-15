package myworld.mstrade;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Crafts empty shulker boxes from a fake inventory or transports them between two saved put boxes. */
public final class CraftBoxManager {
    private static final double MAX_ROUTE_DISTANCE = 100.0;
    private static final double MAX_HEIGHT_DIFFERENCE = 0.6;
    private static final double ARRIVAL_DISTANCE = 0.28;
    private static final double INTERACTION_REACH = 5.0;
    private static final int STALL_LIMIT_TICKS = 20;

    private static final Map<UUID, Route> ROUTES = new HashMap<>();

    private CraftBoxManager() {}

    public static void clearOnServerStop() {
        ROUTES.clear();
    }

    public static int start(CommandSourceStack source, String fakeName, Integer sourceBoxId, Integer outputBoxId) {
        MinecraftServer server = source.getServer();
        ServerPlayer fake = server.getPlayerList().getPlayerByName(fakeName);
        if (fake == null) {
            TradeCommand.message(source, "player " + fakeName + " is not online.");
            return 0;
        }
        if (!isCarpetFake(fake)) {
            TradeCommand.message(source, fakeName + " is online but is not a Carpet fake player.");
            return 0;
        }
        if (TradeRouteManager.isRunning(fakeName) || CollectManager.isRunning(fakeName)) {
            TradeCommand.message(source, fakeName + " is already walking another MS route.");
            return 0;
        }

        Route previous = ROUTES.remove(fake.getUUID());
        if (previous != null) stopMoving(server, fakeName);

        if (sourceBoxId == null && outputBoxId == null) return craftFromInventory(source, fake);
        if (sourceBoxId == null || outputBoxId == null) {
            TradeCommand.message(source, "box mode requires both a material box ID and a separate product box ID.");
            return 0;
        }
        if (sourceBoxId.equals(outputBoxId)) {
            TradeCommand.message(source, "material box and product box must use different IDs.");
            return 0;
        }

        PutBoxStore.Entry sourceEntry;
        PutBoxStore.Entry outputEntry;
        try {
            sourceEntry = PutBoxStore.get(server, sourceBoxId);
            outputEntry = PutBoxStore.get(server, outputBoxId);
        } catch (RuntimeException exception) {
            TradeCommand.message(source, "cannot read saved put boxes: " + exception.getMessage());
            return 0;
        }
        if (sourceEntry == null) {
            TradeCommand.message(source, "put box [" + sourceBoxId + "] was not found; register it with /ms put add first.");
            return 0;
        }
        if (outputEntry == null) {
            TradeCommand.message(source, "put box [" + outputBoxId + "] was not found; register it with /ms put add first.");
            return 0;
        }
        if (!sourceEntry.dimension().equals(outputEntry.dimension())) {
            TradeCommand.message(source, "material box [" + sourceBoxId + "] and product box [" + outputBoxId
                + "] must be in the same dimension.");
            return 0;
        }

        ServerLevel level = level(server, sourceEntry.dimension());
        if (level == null || fake.level() != level) {
            TradeCommand.message(source, "material box [" + sourceBoxId + "] is in " + sourceEntry.dimension()
                + "; " + fakeName + " must already be in that dimension.");
            return 0;
        }

        Route route = new Route(source, fake.getUUID(), fakeName, sourceEntry, outputEntry);
        ROUTES.put(fake.getUUID(), route);
        TradeCommand.message(source, fakeName + " started craftbox route: material [" + sourceBoxId
            + "] -> product [" + outputBoxId + "].");
        beginRoute(server, route, fake);
        return ROUTES.containsKey(fake.getUUID()) ? 1 : route.completed ? 1 : 0;
    }

    public static void tick(MinecraftServer server) {
        if (ROUTES.isEmpty()) return;
        for (Route route : new ArrayList<>(ROUTES.values())) {
            ServerPlayer fake = server.getPlayerList().getPlayer(route.fakeId);
            if (fake == null || !isCarpetFake(fake)) {
                fail(server, route, route.fakeName + " went offline.");
                continue;
            }
            if (!route.moving) continue;
            PutBoxStore.Entry entry = route.currentEntry();
            double distance = horizontalDistance(fake, entry);
            double heightDifference = Math.abs(entry.approachY() - fake.getY());
            if (distance <= ARRIVAL_DISTANCE) {
                stopMoving(server, route.fakeName);
                route.moving = false;
                arrive(server, route, fake);
            } else if (heightDifference > 0.8) {
                fail(server, route, route.fakeName + " left the saved flat level.");
            } else if (distance > route.previousDistance + 0.15) {
                fail(server, route, route.fakeName + " passed or moved away from the saved approach point.");
            } else {
                route.stalledTicks = route.previousDistance - distance < 0.005 ? route.stalledTicks + 1 : 0;
                route.previousDistance = distance;
                route.ticksLeft--;
                if (route.stalledTicks >= STALL_LIMIT_TICKS) {
                    fail(server, route, route.fakeName + " was blocked while walking to " + route.phase.label
                        + " box [" + entry.id() + "].");
                } else if (route.ticksLeft <= 0) {
                    fail(server, route, route.fakeName + " timed out while walking to " + route.phase.label
                        + " box [" + entry.id() + "].");
                }
            }
        }
    }

    static boolean isRunning(String fakeName) {
        return ROUTES.values().stream().anyMatch(route -> route.fakeName.equalsIgnoreCase(fakeName));
    }

    private static int craftFromInventory(CommandSourceStack source, ServerPlayer fake) {
        Inventory inventory = fake.getInventory();
        CraftResult result = craft(copySlots(inventory, 36));
        if (result.crafted == 0) {
            reportNoCraft(source, fake.getGameProfile().name() + " inventory", result);
            return 0;
        }
        apply(inventory, result.slots);
        inventory.setChanged();
        fake.containerMenu.broadcastChanges();
        reportSuccess(source, fake.getGameProfile().name() + " inventory", result);
        return result.crafted;
    }

    private static void beginRoute(MinecraftServer server, Route route, ServerPlayer fake) {
        PutBoxStore.Entry entry = route.currentEntry();
        double distance = horizontalDistance(fake, entry);
        double heightDifference = Math.abs(entry.approachY() - fake.getY());
        if (heightDifference > MAX_HEIGHT_DIFFERENCE) {
            fail(server, route, "saved approach point is not on the same flat level as " + route.fakeName + ".");
            return;
        }
        if (distance <= ARRIVAL_DISTANCE) {
            arrive(server, route, fake);
            return;
        }
        if (distance > MAX_ROUTE_DISTANCE) {
            fail(server, route, route.phase.label + " box [" + entry.id() + "] is " + String.format("%.1f", distance)
                + " blocks away; the route limit is 100.");
            return;
        }
        if (!straightPathClear((ServerLevel) fake.level(), fake, entry, distance)) {
            fail(server, route, "straight flat path to " + route.phase.label + " box [" + entry.id()
                + "] has a gap or obstruction.");
            return;
        }

        stopMoving(server, route.fakeName);
        double dx = entry.approachX() - fake.getX();
        double dz = entry.approachZ() - fake.getZ();
        float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        fake.setYRot(yaw);
        fake.setYHeadRot(yaw);
        fake.setXRot(0.0F);
        runCarpet(server, "player " + route.fakeName + " move forward");
        route.moving = true;
        route.previousDistance = distance;
        route.stalledTicks = 0;
        route.ticksLeft = Math.max(200, (int) Math.ceil(distance * 15.0));
    }

    private static void arrive(MinecraftServer server, Route route, ServerPlayer fake) {
        PutBoxStore.Entry entry = route.currentEntry();
        fake.setYRot(entry.yaw());
        fake.setYHeadRot(entry.yaw());
        fake.setXRot(entry.pitch());
        BlockPos pos = new BlockPos(entry.x(), entry.y(), entry.z());
        ServerLevel level = (ServerLevel) fake.level();
        BlockState state = level.getBlockState(pos);
        if (route.phase == Phase.OUTPUT && state.getBlock() instanceof ShulkerBoxBlock) {
            fail(server, route, "product box [" + entry.id()
                + "] is itself a shulker box, so empty shulker-box products cannot be placed inside it.");
            return;
        }
        Container container = containerAt(level, fake, pos);
        if (container == null) {
            fail(server, route, "saved block for " + route.phase.label + " box [" + entry.id()
                + "] is no longer a container.");
            return;
        }
        if (!canReach(level, fake, pos)) {
            fail(server, route, route.phase.label + " box [" + entry.id()
                + "] is no longer visible and within reach.");
            return;
        }

        McCompat.swing(fake);
        if (route.phase == Phase.SOURCE) craftAtSource(server, route, fake, container);
        else depositAtOutput(server, route, fake, container);
    }

    private static void craftAtSource(MinecraftServer server, Route route, ServerPlayer fake, Container container) {
        Inventory inventory = fake.getInventory();
        List<Integer> freeSlots = new ArrayList<>();
        for (int slot = 0; slot < 36; slot++) if (inventory.getItem(slot).isEmpty()) freeSlots.add(slot);
        if (freeSlots.isEmpty()) {
            finishWithoutCraft(server, route, "fake inventory has no empty top-level slot for transporting products; no materials were consumed.");
            return;
        }

        TransportCraft result = craftForTransport(copySlots(container, container.getContainerSize()), freeSlots.size());
        if (result.crafted == 0) {
            finishWithoutCraft(server, route, "material box [" + route.sourceEntry.id()
                + "] needs at least 1 chest and 2 shulker shells; found " + result.chests
                + " chest(s) and " + result.shells + " shell(s). No materials were consumed.");
            return;
        }
        apply(container, result.slots);
        container.setChanged();

        for (int index = 0; index < result.crafted; index++) {
            int slot = freeSlots.get(index);
            inventory.setItem(slot, new ItemStack(Items.SHULKER_BOX));
            route.productSlots.add(slot);
        }
        inventory.setChanged();
        fake.containerMenu.broadcastChanges();
        route.crafted = result.crafted;
        route.phase = Phase.OUTPUT;
        TradeCommand.message(route.source, route.fakeName + " crafted " + result.crafted
            + " empty shulker box(es) from material box [" + route.sourceEntry.id()
            + "]; carrying them to product box [" + route.outputEntry.id() + "].");
        beginRoute(server, route, fake);
    }

    private static void depositAtOutput(MinecraftServer server, Route route, ServerPlayer fake, Container container) {
        Inventory inventory = fake.getInventory();
        int delivered = 0;
        for (int productSlot : route.productSlots) {
            ItemStack product = inventory.getItem(productSlot);
            if (!product.is(Items.SHULKER_BOX)) continue;
            int outputSlot = firstEmptySlot(container);
            if (outputSlot < 0) break;
            container.setItem(outputSlot, product.copy());
            inventory.setItem(productSlot, ItemStack.EMPTY);
            delivered++;
        }
        container.setChanged();
        inventory.setChanged();
        fake.containerMenu.broadcastChanges();
        ROUTES.remove(route.fakeId);
        route.completed = true;
        int carried = route.crafted - delivered;
        String summary = route.fakeName + " craftbox route material [" + route.sourceEntry.id()
            + "] -> product [" + route.outputEntry.id() + "]: crafted " + route.crafted
            + ", delivered " + delivered;
        if (carried > 0) summary += "; product box became full, so " + carried + " product(s) remain on the fake";
        TradeCommand.message(route.source, summary + ".");
    }

    private static void finishWithoutCraft(MinecraftServer server, Route route, String message) {
        ROUTES.remove(route.fakeId);
        stopMoving(server, route.fakeName);
        route.completed = true;
        TradeCommand.message(route.source, message);
    }

    private static CraftResult craft(List<ItemStack> original) {
        List<ItemStack> working = copySlots(original);
        int crafted = 0;
        boolean blockedByOutputSpace = false;
        while (count(working, Items.CHEST) >= 1 && count(working, Items.SHULKER_SHELL) >= 2) {
            List<ItemStack> attempt = copySlots(working);
            consume(attempt, Items.CHEST, 1);
            consume(attempt, Items.SHULKER_SHELL, 2);
            if (!insertTopLevel(attempt, new ItemStack(Items.SHULKER_BOX))) {
                blockedByOutputSpace = true;
                break;
            }
            working = attempt;
            crafted++;
        }
        return new CraftResult(working, crafted, count(working, Items.CHEST),
            count(working, Items.SHULKER_SHELL), blockedByOutputSpace);
    }

    private static TransportCraft craftForTransport(List<ItemStack> original, int transportSlots) {
        List<ItemStack> working = copySlots(original);
        int possible = Math.min(count(working, Items.CHEST), count(working, Items.SHULKER_SHELL) / 2);
        int crafted = Math.min(possible, transportSlots);
        for (int index = 0; index < crafted; index++) {
            consume(working, Items.CHEST, 1);
            consume(working, Items.SHULKER_SHELL, 2);
        }
        return new TransportCraft(working, crafted, count(working, Items.CHEST),
            count(working, Items.SHULKER_SHELL));
    }

    private static int count(List<ItemStack> slots, Item item) {
        int total = 0;
        for (ItemStack stack : slots) {
            if (stack.is(item)) total += stack.getCount();
            if (!TradeService.isShulker(stack)) continue;
            for (ItemStack nested : TradeService.boxContents(stack)) if (nested.is(item)) total += nested.getCount();
        }
        return total;
    }

    private static void consume(List<ItemStack> slots, Item item, int amount) {
        int remaining = amount;
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            ItemStack stack = slots.get(slot);
            if (!stack.is(item)) continue;
            int taken = Math.min(remaining, stack.getCount());
            stack.shrink(taken);
            remaining -= taken;
            slots.set(slot, stack);
        }
        for (int slot = 0; slot < slots.size() && remaining > 0; slot++) {
            ItemStack box = slots.get(slot);
            if (!TradeService.isShulker(box)) continue;
            NonNullList<ItemStack> contents = TradeService.boxContents(box);
            boolean changed = false;
            for (int nestedSlot = 0; nestedSlot < contents.size() && remaining > 0; nestedSlot++) {
                ItemStack stack = contents.get(nestedSlot);
                if (!stack.is(item)) continue;
                int taken = Math.min(remaining, stack.getCount());
                stack.shrink(taken);
                remaining -= taken;
                contents.set(nestedSlot, stack);
                changed = true;
            }
            if (changed) {
                TradeService.saveBoxContents(box, contents);
                slots.set(slot, box);
            }
        }
        if (remaining != 0) throw new IllegalStateException("craft material count changed during simulation");
    }

    private static boolean insertTopLevel(List<ItemStack> slots, ItemStack output) {
        for (int slot = 0; slot < slots.size(); slot++) {
            if (!slots.get(slot).isEmpty()) continue;
            slots.set(slot, output.copy());
            return true;
        }
        return false;
    }

    private static int firstEmptySlot(Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (container.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static List<ItemStack> copySlots(Container container, int size) {
        List<ItemStack> result = new ArrayList<>(size);
        for (int slot = 0; slot < size; slot++) result.add(container.getItem(slot).copy());
        return result;
    }

    private static List<ItemStack> copySlots(List<ItemStack> slots) {
        List<ItemStack> result = new ArrayList<>(slots.size());
        for (ItemStack stack : slots) result.add(stack.copy());
        return result;
    }

    private static void apply(Container container, List<ItemStack> slots) {
        for (int slot = 0; slot < slots.size(); slot++) container.setItem(slot, slots.get(slot));
    }

    private static void reportSuccess(CommandSourceStack source, String sourceName, CraftResult result) {
        TradeCommand.message(source, sourceName + " crafted " + result.crafted + " empty shulker box(es); remaining "
            + result.chests + " chest(s) and " + result.shells + " shulker shell(s)"
            + (result.blockedByOutputSpace ? "; stopped because no top-level output slot remained." : "."));
    }

    private static void reportNoCraft(CommandSourceStack source, String sourceName, CraftResult result) {
        if (result.blockedByOutputSpace) {
            TradeCommand.message(source, sourceName
                + " has ingredients but no top-level slot for an empty shulker box; no materials were consumed.");
        } else {
            TradeCommand.message(source, sourceName + " needs at least 1 chest and 2 shulker shells; found "
                + result.chests + " chest(s) and " + result.shells + " shell(s). No materials were consumed.");
        }
    }

    private static Container containerAt(ServerLevel level, ServerPlayer fake, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, level, pos, false);
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    private static boolean canReach(ServerLevel level, ServerPlayer fake, BlockPos pos) {
        Vec3 eye = fake.getEyePosition();
        Vec3 center = Vec3.atCenterOf(pos);
        if (eye.distanceToSqr(center) > INTERACTION_REACH * INTERACTION_REACH) return false;
        BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE,
            ClipContext.Fluid.NONE, fake));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(pos);
    }

    private static boolean straightPathClear(ServerLevel level, ServerPlayer fake,
                                             PutBoxStore.Entry target, double distance) {
        int samples = Math.max(1, (int) Math.ceil(distance * 2.0));
        for (int index = 0; index <= samples; index++) {
            double ratio = (double) index / samples;
            double x = fake.getX() + (target.approachX() - fake.getX()) * ratio;
            double z = fake.getZ() + (target.approachZ() - fake.getZ()) * ratio;
            int blockX = Mth.floor(x);
            int blockZ = Mth.floor(z);
            int bodyY = Mth.ceil(fake.getY());
            int groundY = Mth.floor(fake.getY() - 0.1);
            BlockPos ground = new BlockPos(blockX, groundY, blockZ);
            BlockPos body = new BlockPos(blockX, bodyY, blockZ);
            if (level.getBlockState(ground).getCollisionShape(level, ground).isEmpty()
                || !level.getBlockState(body).getCollisionShape(level, body).isEmpty()
                || !level.getBlockState(body.above()).getCollisionShape(level, body.above()).isEmpty()) return false;
        }
        return true;
    }

    private static double horizontalDistance(ServerPlayer fake, PutBoxStore.Entry target) {
        double dx = target.approachX() - fake.getX();
        double dz = target.approachZ() - fake.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static ServerLevel level(MinecraftServer server, String dimension) {
        for (ServerLevel level : server.getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        }
        return null;
    }

    private static boolean isCarpetFake(ServerPlayer player) {
        return player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake");
    }

    private static void stopMoving(MinecraftServer server, String fakeName) {
        runCarpet(server, "player " + fakeName + " move");
    }

    private static void runCarpet(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static void fail(MinecraftServer server, Route route, String reason) {
        ROUTES.remove(route.fakeId);
        stopMoving(server, route.fakeName);
        TradeCommand.message(route.source, "craftbox stopped: " + reason);
    }

    private record CraftResult(List<ItemStack> slots, int crafted, int chests, int shells,
                               boolean blockedByOutputSpace) {}
    private record TransportCraft(List<ItemStack> slots, int crafted, int chests, int shells) {}

    private enum Phase {
        SOURCE("material"), OUTPUT("product");

        private final String label;
        Phase(String label) {
            this.label = label;
        }
    }

    private static final class Route {
        private final CommandSourceStack source;
        private final UUID fakeId;
        private final String fakeName;
        private final PutBoxStore.Entry sourceEntry;
        private final PutBoxStore.Entry outputEntry;
        private final List<Integer> productSlots = new ArrayList<>();
        private int stalledTicks;
        private int ticksLeft;
        private int crafted;
        private double previousDistance;
        private boolean moving;
        private boolean completed;
        private Phase phase = Phase.SOURCE;

        private Route(CommandSourceStack source, UUID fakeId, String fakeName,
                      PutBoxStore.Entry sourceEntry, PutBoxStore.Entry outputEntry) {
            this.source = source;
            this.fakeId = fakeId;
            this.fakeName = fakeName;
            this.sourceEntry = sourceEntry;
            this.outputEntry = outputEntry;
        }

        private PutBoxStore.Entry currentEntry() {
            return phase == Phase.SOURCE ? sourceEntry : outputEntry;
        }
    }
}
