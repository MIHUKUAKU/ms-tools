package myworld.mstrade;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BarrelBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Searches a bounded storage room and walks a Carpet fake player to matching containers. */
public final class CollectManager {
    private static final int HORIZONTAL_SIZE = 20;
    private static final int VERTICAL_SIZE = 4;
    private static final double INTERACTION_REACH = 4.5;
    private static final double STEP_ARRIVAL = 0.24;
    private static final int STEP_TIMEOUT = 50;
    private static final int STALL_LIMIT = 20;
    private static final int POST_CONTAINER_WAIT = 3;

    private static final Map<UUID, Route> ROUTES = new HashMap<>();

    private CollectManager() {}

    public static void clearOnServerStop() {
        ROUTES.clear();
    }

    public static int start(CommandSourceStack source, String fakeName, Integer putBoxId) {
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
        ItemStack selector = fake.getMainHandItem();
        if (selector.isEmpty()) {
            TradeCommand.message(source, fakeName + " must hold the item to collect in the main hand.");
            return 0;
        }
        if (TradeRouteManager.isRunning(fakeName)) {
            TradeCommand.message(source, fakeName + " is already walking a trade chain; stop it before collecting.");
            return 0;
        }

        Route previous = ROUTES.remove(fake.getUUID());
        if (previous != null) stopMoving(server, previous.fakeName);

        BlockPos start = BlockPos.containing(fake.getX(), fake.getY(), fake.getZ());
        Bounds bounds = new Bounds(start.getX() - HORIZONTAL_SIZE / 2, start.getX() + HORIZONTAL_SIZE / 2 - 1,
            start.getY(), start.getY() + VERTICAL_SIZE - 1,
            start.getZ() - HORIZONTAL_SIZE / 2, start.getZ() + HORIZONTAL_SIZE / 2 - 1);
        List<TargetSpec> targetOrder = selectorTargets(selector);
        ServerLevel fakeLevel = (ServerLevel) fake.level();
        List<ContainerTarget> targets = scanTargets(fakeLevel, fake, bounds, targetOrder.getFirst().item);
        if (targets.isEmpty() && targetOrder.size() == 1) {
            TradeCommand.message(source, "no accessible chest, barrel, or shulker box in the 20x4x20 area contains "
                + targetOrder.getFirst().name + ".");
            return 0;
        }

        CommandSourceStack routeSource = source.getEntity() instanceof ServerPlayer
            ? source : fake.createCommandSourceStack();
        Route route = new Route(routeSource, fakeLevel, fake.getUUID(), fakeName, targetOrder,
            bounds, targets, putBoxId);
        ROUTES.put(fake.getUUID(), route);
        String orderedTargets = String.join(" -> ", targetOrder.stream().map(TargetSpec::name).toList());
        TradeCommand.message(source, fakeName + " started "
            + (targetOrder.size() == 1 ? "collecting " : "ordered selector-box collection: ") + orderedTargets
            + " in a 20x4x20 area"
            + (putBoxId == null ? "." : "; collected inventory will be sent to put box [" + putBoxId + "]."));
        route.waitTicks = 1;
        return 1;
    }

    public static void tick(MinecraftServer server) {
        if (ROUTES.isEmpty()) return;
        for (Route route : new ArrayList<>(ROUTES.values())) {
            ServerPlayer fake = server.getPlayerList().getPlayer(route.fakeId);
            if (fake == null || !isCarpetFake(fake)) {
                fail(server, route, route.fakeName + " went offline.");
                continue;
            }
            if (route.waitTicks > 0) {
                route.waitTicks--;
                if (route.waitTicks == 0) advance(server, route, fake);
                continue;
            }
            if (route.moving) tickStep(server, route, fake);
        }
    }

    static boolean isRunning(String fakeName) {
        return ROUTES.values().stream().anyMatch(route -> route.fakeName.equalsIgnoreCase(fakeName));
    }

    private static List<TargetSpec> selectorTargets(ItemStack selector) {
        List<TargetSpec> targets = new ArrayList<>();
        Set<Item> seen = new LinkedHashSet<>();
        if (TradeService.isShulker(selector)) {
            for (ItemStack listed : TradeService.boxContents(selector)) {
                if (listed.isEmpty() || !seen.add(listed.getItem())) continue;
                targets.add(new TargetSpec(listed.getItem(), listed.getHoverName().getString()));
            }
        }
        if (targets.isEmpty()) targets.add(new TargetSpec(selector.getItem(), selector.getHoverName().getString()));
        return targets;
    }

    private static List<ContainerTarget> scanTargets(ServerLevel level, ServerPlayer fake, Bounds bounds, Item target) {
        List<ContainerTarget> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        for (int y = bounds.minY; y <= bounds.maxY; y++) {
            for (int x = bounds.minX; x <= bounds.maxX; x++) {
                for (int z = bounds.minZ; z <= bounds.maxZ; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!level.hasChunkAt(pos)) continue;
                    BlockState state = level.getBlockState(pos);
                    Block block = state.getBlock();
                    if (!(block instanceof ChestBlock) && !(block instanceof BarrelBlock)
                        && !(block instanceof ShulkerBoxBlock)) continue;

                    BlockPos primary = pos;
                    BlockPos secondary = null;
                    if (block instanceof ChestBlock && state.hasProperty(ChestBlock.TYPE)
                        && state.getValue(ChestBlock.TYPE) != net.minecraft.world.level.block.state.properties.ChestType.SINGLE) {
                        secondary = ChestBlock.getConnectedBlockPos(pos, state);
                        if (secondary.asLong() < primary.asLong()) {
                            BlockPos swap = primary;
                            primary = secondary;
                            secondary = swap;
                        }
                    }
                    if (!seen.add(primary)) continue;
                    Container container = containerAt(level, fake, primary);
                    if (container != null && containsTarget(container, target)) {
                        result.add(new ContainerTarget(primary.immutable(), secondary == null ? null : secondary.immutable()));
                    }
                }
            }
        }
        return result;
    }

    private static void advance(MinecraftServer server, Route route, ServerPlayer fake) {
        if (!ROUTES.containsKey(route.fakeId)) return;
        if (fake.level() != route.level()) {
            fail(server, route, route.fakeName + " changed dimension.");
            return;
        }

        Iterator<ContainerTarget> iterator = route.targets.iterator();
        while (iterator.hasNext()) {
            ContainerTarget target = iterator.next();
            Container container = containerAt(route.level(), fake, target.primary);
            if (container == null || !containsTarget(container, route.targetItem())) iterator.remove();
        }
        if (route.targets.isEmpty()) {
            completeCurrentTarget(server, route, fake, null);
            return;
        }

        BlockPos current = BlockPos.containing(fake.getX(), fake.getY(), fake.getZ());
        Search search = search(route.level(), current, route.bounds);
        Choice best = null;
        for (ContainerTarget target : route.targets) {
            Choice choice = chooseApproach(route.level(), fake, target, search);
            if (choice != null && (best == null || choice.path.size() < best.path.size())) best = choice;
        }
        if (best == null) {
            completeCurrentTarget(server, route, fake, route.targets.size()
                + " matching container(s) were visible in the scan but not reachable from the walkable area");
            return;
        }

        route.currentTarget = best.target;
        route.path.clear();
        route.path.addAll(best.path);
        if (route.path.isEmpty()) collectAtTarget(server, route, fake);
        else beginStep(server, route, fake);
    }

    private static Search search(ServerLevel level, BlockPos start, Bounds bounds) {
        Map<BlockPos, BlockPos> previous = new HashMap<>();
        Map<BlockPos, Integer> distance = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        BlockPos immutableStart = start.immutable();
        previous.put(immutableStart, null);
        distance.put(immutableStart, 0);
        queue.add(immutableStart);

        while (!queue.isEmpty()) {
            BlockPos current = queue.removeFirst();
            int nextDistance = distance.get(current) + 1;
            for (Direction direction : Direction.Plane.HORIZONTAL) {
                for (int dy : new int[] {0, 1, -1}) {
                    BlockPos candidate = current.relative(direction).offset(0, dy, 0).immutable();
                    if (!bounds.contains(candidate) || previous.containsKey(candidate) || !walkable(level, candidate)) continue;
                    previous.put(candidate, current);
                    distance.put(candidate, nextDistance);
                    queue.addLast(candidate);
                }
            }
        }
        return new Search(start, previous, distance);
    }

    private static Choice chooseApproach(ServerLevel level, ServerPlayer fake, ContainerTarget target, Search search) {
        BlockPos bestPos = null;
        int bestDistance = Integer.MAX_VALUE;
        for (Map.Entry<BlockPos, Integer> entry : search.distance.entrySet()) {
            if (entry.getValue() >= bestDistance) continue;
            if (canReachContainer(level, fake, entry.getKey(), target)) {
                bestPos = entry.getKey();
                bestDistance = entry.getValue();
            }
        }
        if (bestPos == null) return null;
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos cursor = bestPos; cursor != null && !cursor.equals(search.start); cursor = search.previous.get(cursor)) {
            path.add(cursor);
        }
        Collections.reverse(path);
        return new Choice(target, path);
    }

    private static boolean canReachContainer(ServerLevel level, ServerPlayer fake, BlockPos feet, ContainerTarget target) {
        Vec3 eye = new Vec3(feet.getX() + 0.5, feet.getY() + 1.62, feet.getZ() + 0.5);
        if (rayHits(level, fake, eye, target.primary)) return true;
        return target.secondary != null && rayHits(level, fake, eye, target.secondary);
    }

    private static boolean rayHits(ServerLevel level, ServerPlayer fake, Vec3 eye, BlockPos target) {
        Vec3 center = Vec3.atCenterOf(target);
        if (eye.distanceToSqr(center) > INTERACTION_REACH * INTERACTION_REACH) return false;
        BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, fake));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(target);
    }

    private static boolean walkable(ServerLevel level, BlockPos feet) {
        if (!level.hasChunkAt(feet)) return false;
        return !level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty()
            && level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
            && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty();
    }

    private static void beginStep(MinecraftServer server, Route route, ServerPlayer fake) {
        BlockPos next = route.path.peekFirst();
        if (next == null) {
            collectAtTarget(server, route, fake);
            return;
        }
        stopMoving(server, route.fakeName);
        double dx = next.getX() + 0.5 - fake.getX();
        double dz = next.getZ() + 0.5 - fake.getZ();
        float yaw = (float) -Math.toDegrees(Math.atan2(dx, dz));
        fake.setYRot(yaw);
        fake.setYHeadRot(yaw);
        fake.setXRot(0.0F);
        if (next.getY() > fake.getY() + 0.4) runCarpet(server, "player " + route.fakeName + " jump once");
        runCarpet(server, "player " + route.fakeName + " move forward");
        route.moving = true;
        route.horizontalStopped = false;
        route.previousDistance = horizontalDistance(fake, next);
        route.stalledTicks = 0;
        route.stepTicksLeft = STEP_TIMEOUT;
    }

    private static void tickStep(MinecraftServer server, Route route, ServerPlayer fake) {
        BlockPos next = route.path.peekFirst();
        if (next == null) {
            route.moving = false;
            collectAtTarget(server, route, fake);
            return;
        }
        double distance = horizontalDistance(fake, next);
        double heightDifference = Math.abs(fake.getY() - next.getY());
        if (distance <= STEP_ARRIVAL) {
            if (!route.horizontalStopped) {
                stopMoving(server, route.fakeName);
                route.horizontalStopped = true;
            }
            if (heightDifference <= 0.62) {
                float yaw = fake.getYRot();
                fake.teleportTo((ServerLevel) fake.level(), next.getX() + 0.5, next.getY(), next.getZ() + 0.5,
                    Set.<Relative>of(), yaw, 0.0F, false);
                route.path.removeFirst();
                route.moving = false;
                if (route.path.isEmpty()) collectAtTarget(server, route, fake);
                else beginStep(server, route, fake);
                return;
            }
        } else if (distance > route.previousDistance + 0.25) {
            fail(server, route, route.fakeName + " passed a path step or was pushed away.");
            return;
        } else {
            route.stalledTicks = route.previousDistance - distance < 0.004 ? route.stalledTicks + 1 : 0;
            route.previousDistance = distance;
        }

        route.stepTicksLeft--;
        if (route.stalledTicks >= STALL_LIMIT) {
            fail(server, route, route.fakeName + " was blocked while walking to a container.");
        } else if (route.stepTicksLeft <= 0) {
            fail(server, route, route.fakeName + " timed out while walking to a container.");
        }
    }

    private static void collectAtTarget(MinecraftServer server, Route route, ServerPlayer fake) {
        stopMoving(server, route.fakeName);
        route.moving = false;
        ContainerTarget target = route.currentTarget;
        Container container = target == null ? null : containerAt(route.level(), fake, target.primary);
        if (container == null) {
            if (target != null) route.targets.remove(target);
            route.waitTicks = 1;
            return;
        }

        face(fake, target.primary);
        McCompat.swing(fake);
        Transfer transfer = transferMatching(fake, container, route);
        route.visited++;
        route.movedItems += transfer.moved;
        route.currentMovedItems += transfer.moved;
        if (transfer.nestedMoved > 0) route.nestedItems += transfer.nestedMoved;
        route.wholeBoxesTaken += transfer.wholeBoxesTaken;
        route.targets.remove(target);
        fake.getInventory().setChanged();
        fake.containerMenu.broadcastChanges();

        if (transfer.blockedByCapacity) {
            finish(server, route, "inventory and carried shulker boxes are full; matching items were left in their source container.");
        } else {
            route.currentTarget = null;
            route.waitTicks = POST_CONTAINER_WAIT;
        }
    }

    private static Transfer transferMatching(ServerPlayer fake, Container container, Route route) {
        Inventory inventory = fake.getInventory();
        int selectedSlot = inventory.getSelectedSlot();
        Item targetItem = route.targetItem();
        int moved = 0;
        int nestedMoved = 0;
        int wholeBoxesTaken = 0;
        boolean blocked = false;

        // Pure source boxes are cargo, not packaging to unpack. Reserve a loose
        // inventory slot, top the box up from loose matching stacks in this
        // source container, then move the complete box intact to the fake.
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack sourceBox = container.getItem(slot);
            if (!isPureTargetBox(sourceBox, targetItem)) continue;
            int destinationSlot = firstEmptyInventorySlot(inventory, selectedSlot);
            if (destinationSlot < 0) break;

            fillSourceBoxFromLooseStacks(container, slot, sourceBox, targetItem);
            int boxedItems = countTargetContents(sourceBox, targetItem);
            inventory.setItem(destinationSlot, sourceBox.copy());
            container.setItem(slot, ItemStack.EMPTY);
            route.preferredBoxSlots.remove(destinationSlot);
            route.preferredBoxSlots.addFirst(destinationSlot);
            moved += boxedItems;
            nestedMoved += boxedItems;
            wholeBoxesTaken++;
        }

        for (int slot = 0; slot < container.getContainerSize() && !blocked; slot++) {
            ItemStack source = container.getItem(slot);
            if (source.isEmpty()) continue;
            if (source.is(targetItem)) {
                int amount = moveToFake(route, inventory, selectedSlot, source);
                moved += amount;
                container.setItem(slot, source);
                if (!source.isEmpty() && source.is(targetItem)) blocked = true;
                continue;
            }
            if (!TradeService.isShulker(source)) continue;
            NonNullList<ItemStack> contents = TradeService.boxContents(source);
            boolean changed = false;
            for (int index = 0; index < contents.size(); index++) {
                ItemStack nested = contents.get(index);
                if (nested.isEmpty() || !nested.is(targetItem)) continue;
                int amount = moveToFake(route, inventory, selectedSlot, nested);
                moved += amount;
                nestedMoved += amount;
                changed |= amount > 0;
                if (!nested.isEmpty() && nested.is(targetItem)) {
                    blocked = true;
                    break;
                }
            }
            if (changed) {
                TradeService.saveBoxContents(source, contents);
                container.setItem(slot, source);
            }
        }
        container.setChanged();
        return new Transfer(moved, nestedMoved, wholeBoxesTaken, blocked);
    }

    private static int moveToFake(Route route, Inventory inventory, int selectedSlot, ItemStack source) {
        int before = source.getCount();
        ItemStack incoming = source.copy();
        storeWithoutMixing(route, inventory, incoming, selectedSlot);
        int moved = before - incoming.getCount();
        if (moved > 0) source.shrink(moved);
        return moved;
    }

    private static void storeWithoutMixing(Route route, Inventory inventory, ItemStack incoming, int selectedSlot) {
        if (incoming.isEmpty()) return;
        Set<Integer> handledBoxes = new HashSet<>();

        if (!TradeService.isShulker(incoming)) {
            for (int slot : route.preferredBoxSlots) {
                if (slot == selectedSlot || slot < 0 || slot >= 36 || !handledBoxes.add(slot)) continue;
                ItemStack box = inventory.getItem(slot);
                if (isPureTargetBox(box, route.targetItem())) insertIntoCarriedBox(inventory, slot, box, incoming);
                if (incoming.isEmpty()) return;
            }
            for (int slot = 0; slot < 36 && !incoming.isEmpty(); slot++) {
                if (slot == selectedSlot || handledBoxes.contains(slot)) continue;
                ItemStack box = inventory.getItem(slot);
                if (isPureTargetBox(box, route.targetItem())) {
                    handledBoxes.add(slot);
                    insertIntoCarriedBox(inventory, slot, box, incoming);
                }
            }
            for (int slot = 0; slot < 36 && !incoming.isEmpty(); slot++) {
                if (slot == selectedSlot || handledBoxes.contains(slot)) continue;
                ItemStack box = inventory.getItem(slot);
                if (isEmptyShulker(box)) {
                    handledBoxes.add(slot);
                    insertIntoCarriedBox(inventory, slot, box, incoming);
                    route.preferredBoxSlots.addLast(slot);
                }
            }
        }

        for (int slot = 0; slot < 36 && !incoming.isEmpty(); slot++) {
            if (slot == selectedSlot) continue;
            ItemStack current = inventory.getItem(slot);
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, incoming)) {
                int amount = Math.min(incoming.getCount(), current.getMaxStackSize() - current.getCount());
                if (amount > 0) {
                    current.grow(amount);
                    incoming.shrink(amount);
                }
            }
        }
        for (int slot = 0; slot < 36 && !incoming.isEmpty(); slot++) {
            if (slot == selectedSlot || !inventory.getItem(slot).isEmpty()) continue;
            inventory.setItem(slot, incoming.split(Math.min(incoming.getCount(), incoming.getMaxStackSize())));
        }
    }

    private static void insertIntoCarriedBox(Inventory inventory, int slot, ItemStack box, ItemStack incoming) {
        NonNullList<ItemStack> contents = TradeService.boxContents(box);
        insertIntoContents(contents, incoming);
        TradeService.saveBoxContents(box, contents);
        inventory.setItem(slot, box);
    }

    private static void fillSourceBoxFromLooseStacks(Container container, int boxSlot, ItemStack box, Item targetItem) {
        NonNullList<ItemStack> contents = TradeService.boxContents(box);
        boolean changed = false;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            if (slot == boxSlot) continue;
            ItemStack loose = container.getItem(slot);
            if (loose.isEmpty() || !loose.is(targetItem) || TradeService.isShulker(loose)) continue;
            int before = loose.getCount();
            ItemStack incoming = loose.copy();
            insertIntoContents(contents, incoming);
            int inserted = before - incoming.getCount();
            if (inserted > 0) {
                loose.shrink(inserted);
                container.setItem(slot, loose);
                changed = true;
            }
        }
        if (changed) TradeService.saveBoxContents(box, contents);
    }

    private static void insertIntoContents(NonNullList<ItemStack> contents, ItemStack incoming) {
        for (int slot = 0; slot < contents.size() && !incoming.isEmpty(); slot++) {
            ItemStack current = contents.get(slot);
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, incoming)) {
                int amount = Math.min(incoming.getCount(), current.getMaxStackSize() - current.getCount());
                if (amount > 0) {
                    current.grow(amount);
                    incoming.shrink(amount);
                }
            }
        }
        for (int slot = 0; slot < contents.size() && !incoming.isEmpty(); slot++) {
            if (contents.get(slot).isEmpty()) {
                contents.set(slot, incoming.split(Math.min(incoming.getCount(), incoming.getMaxStackSize())));
            }
        }
    }

    private static boolean isPureTargetBox(ItemStack box, Item targetItem) {
        if (!TradeService.isShulker(box)) return false;
        boolean found = false;
        for (ItemStack stack : TradeService.boxContents(box)) {
            if (stack.isEmpty()) continue;
            if (!stack.is(targetItem)) return false;
            found = true;
        }
        return found;
    }

    private static boolean isEmptyShulker(ItemStack box) {
        if (!TradeService.isShulker(box)) return false;
        for (ItemStack stack : TradeService.boxContents(box)) if (!stack.isEmpty()) return false;
        return true;
    }

    private static int firstEmptyInventorySlot(Inventory inventory, int selectedSlot) {
        for (int slot = 0; slot < 36; slot++) {
            if (slot != selectedSlot && inventory.getItem(slot).isEmpty()) return slot;
        }
        return -1;
    }

    private static int countTargetContents(ItemStack box, Item targetItem) {
        int count = 0;
        for (ItemStack stack : TradeService.boxContents(box)) if (stack.is(targetItem)) count += stack.getCount();
        return count;
    }

    private static boolean containsTarget(Container container, Item targetItem) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.is(targetItem)) return true;
            if (TradeService.isShulker(stack)) {
                for (ItemStack nested : TradeService.boxContents(stack)) if (nested.is(targetItem)) return true;
            }
        }
        return false;
    }

    private static Container containerAt(ServerLevel level, ServerPlayer fake, BlockPos pos) {
        if (!level.hasChunkAt(pos)) return null;
        BlockState state = level.getBlockState(pos);
        Block block = state.getBlock();
        if (block instanceof ChestBlock chest) return ChestBlock.getContainer(chest, state, level, pos, false);
        if (!(block instanceof BarrelBlock) && !(block instanceof ShulkerBoxBlock)) return null;
        return level.getBlockEntity(pos) instanceof Container container ? container : null;
    }

    private static void face(ServerPlayer fake, BlockPos pos) {
        Vec3 eye = fake.getEyePosition();
        Vec3 offset = Vec3.atCenterOf(pos).subtract(eye);
        double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
        float yaw = (float) -Math.toDegrees(Math.atan2(offset.x, offset.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(offset.y, horizontal));
        fake.setYRot(yaw);
        fake.setYHeadRot(yaw);
        fake.setXRot(pitch);
    }

    private static double horizontalDistance(ServerPlayer fake, BlockPos pos) {
        double dx = pos.getX() + 0.5 - fake.getX();
        double dz = pos.getZ() + 0.5 - fake.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static void completeCurrentTarget(MinecraftServer server, Route route, ServerPlayer fake, String note) {
        String result = route.targetName() + ": " + route.currentMovedItems + " item(s) collected";
        if (note != null) {
            result += "; " + note;
            route.notes.add(route.targetName() + ": " + note);
        }
        if (route.targetOrder.size() > 1) TradeCommand.message(route.source, result + ".");

        route.targetIndex++;
        route.currentMovedItems = 0;
        route.currentTarget = null;
        route.path.clear();
        route.preferredBoxSlots.clear();
        if (route.targetIndex >= route.targetOrder.size()) {
            finish(server, route, null);
            return;
        }
        route.targets = scanTargets(route.level(), fake, route.bounds, route.targetItem());
        route.waitTicks = 1;
    }

    private static void finish(MinecraftServer server, Route route, String reason) {
        ROUTES.remove(route.fakeId);
        stopMoving(server, route.fakeName);
        String summary;
        if (route.targetOrder.size() == 1) {
            summary = route.fakeName + " collected " + route.movedItems + " "
                + route.targetOrder.getFirst().name + " item(s) from " + route.visited + " container(s)";
        } else {
            summary = route.fakeName + " completed ordered collection of " + route.targetOrder.size()
                + " item type(s): " + route.movedItems + " item(s) from " + route.visited + " container(s)";
        }
        if (route.nestedItems > 0) summary += ", including " + route.nestedItems + " from shulker boxes inside containers";
        if (route.wholeBoxesTaken > 0) summary += ", " + route.wholeBoxesTaken + " pure shulker box(es) taken intact";
        if (!route.notes.isEmpty()) summary += "; notes: " + String.join(" | ", route.notes);
        if (reason != null) summary += "; " + reason;
        TradeCommand.message(route.source, summary + ".");
        if (route.putBoxId != null && route.movedItems > 0) {
            TradeCommand.message(route.source, "sending " + route.fakeName + " to put box [" + route.putBoxId + "].");
            server.getCommands().performPrefixedCommand(route.source, "put " + route.fakeName + " " + route.putBoxId);
        }
    }

    private static void fail(MinecraftServer server, Route route, String reason) {
        finish(server, route, "collection stopped: " + reason);
    }

    private static void stopMoving(MinecraftServer server, String fakeName) {
        runCarpet(server, "player " + fakeName + " move");
    }

    private static void runCarpet(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static boolean isCarpetFake(ServerPlayer player) {
        return player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake");
    }

    private record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
        boolean contains(BlockPos pos) {
            return pos.getX() >= minX && pos.getX() <= maxX
                && pos.getY() >= minY && pos.getY() <= maxY
                && pos.getZ() >= minZ && pos.getZ() <= maxZ;
        }
    }

    private record ContainerTarget(BlockPos primary, BlockPos secondary) {}
    private record TargetSpec(Item item, String name) {}
    private record Search(BlockPos start, Map<BlockPos, BlockPos> previous, Map<BlockPos, Integer> distance) {}
    private record Choice(ContainerTarget target, List<BlockPos> path) {}
    private record Transfer(int moved, int nestedMoved, int wholeBoxesTaken, boolean blockedByCapacity) {}

    private static final class Route {
        private final CommandSourceStack source;
        private final ServerLevel sourceLevel;
        private final UUID fakeId;
        private final String fakeName;
        private final List<TargetSpec> targetOrder;
        private final Bounds bounds;
        private final Integer putBoxId;
        private final Deque<BlockPos> path = new ArrayDeque<>();
        private final Deque<Integer> preferredBoxSlots = new ArrayDeque<>();
        private final List<String> notes = new ArrayList<>();
        private List<ContainerTarget> targets;
        private ContainerTarget currentTarget;
        private int targetIndex;
        private int waitTicks;
        private int stepTicksLeft;
        private int stalledTicks;
        private int visited;
        private int movedItems;
        private int currentMovedItems;
        private int nestedItems;
        private int wholeBoxesTaken;
        private double previousDistance;
        private boolean moving;
        private boolean horizontalStopped;

        private Route(CommandSourceStack source, ServerLevel sourceLevel, UUID fakeId, String fakeName,
                      List<TargetSpec> targetOrder, Bounds bounds, List<ContainerTarget> targets, Integer putBoxId) {
            this.source = source;
            this.sourceLevel = sourceLevel;
            this.fakeId = fakeId;
            this.fakeName = fakeName;
            this.targetOrder = targetOrder;
            this.bounds = bounds;
            this.targets = targets;
            this.putBoxId = putBoxId;
        }

        private Item targetItem() {
            return targetOrder.get(targetIndex).item;
        }

        private String targetName() {
            return targetOrder.get(targetIndex).name;
        }

        private ServerLevel level() {
            return sourceLevel;
        }
    }
}
