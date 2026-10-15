package myworld.mstrade;

import net.minecraft.core.BlockPos;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Walks a Carpet fake player through a positive (location) trade chain. */
public final class TradeRouteManager {
    private static final double MAX_LEG_DISTANCE = 100.0;
    private static final double MAX_HEIGHT_DIFFERENCE = 0.6;
    private static final double ARRIVAL_DISTANCE = 0.28;
    private static final int STALL_LIMIT_TICKS = 20;
    private static final int POST_TRADE_WAIT_TICKS = 10;

    private static final Map<UUID, Route> ROUTES = new HashMap<>();
    private TradeRouteManager() {}

    public static void clearOnServerStop() {
        ROUTES.clear();
    }

    public static int start(CommandSourceStack source, ServerPlayer fake, int chain,
                            List<TradeChainStore.Entry> entries) {
        return start(source, fake, chain, entries, true);
    }

    static int start(CommandSourceStack source, ServerPlayer fake, int chain,
                     List<TradeChainStore.Entry> entries, boolean reportRoutine) {
        MinecraftServer server = source.getServer();
        Route previous = ROUTES.remove(fake.getUUID());
        if (previous != null) stopMoving(server, fake.getGameProfile().name());

        Route route = new Route(source, fake.getUUID(), fake.getGameProfile().name(), chain,
            new ArrayList<>(entries), reportRoutine);
        ROUTES.put(fake.getUUID(), route);
        if (reportRoutine) {
            TradeCommand.message(source, fake.getGameProfile().name() + " started walking location chain "
                + chain + " (" + entries.size() + " points).");
        }
        beginLeg(server, route, fake);
        return 1;
    }

    static boolean isRunning(String fakeName) {
        return ROUTES.values().stream().anyMatch(route -> route.fakeName.equalsIgnoreCase(fakeName));
    }

    static void cancelForKeep(MinecraftServer server, String fakeName) {
        Route target = ROUTES.values().stream()
            .filter(route -> route.fakeName.equalsIgnoreCase(fakeName)).findFirst().orElse(null);
        if (target != null) {
            ROUTES.remove(target.fakeId);
            stopMoving(server, target.fakeName);
        }
    }

    static void setReporting(String fakeName, boolean enabled) {
        ROUTES.values().stream()
            .filter(route -> route.fakeName.equalsIgnoreCase(fakeName))
            .forEach(route -> route.reportRoutine = enabled);
    }

    public static void tick(MinecraftServer server) {
        if (ROUTES.isEmpty()) return;
        for (Route route : new ArrayList<>(ROUTES.values())) {
            ServerPlayer fake = server.getPlayerList().getPlayer(route.fakeId);
            if (fake == null || !isCarpetFake(fake)) {
                fail(server, route, route.fakeName + " went offline; walking trade chain stopped.");
                continue;
            }
            if (route.waitTicks > 0) {
                route.waitTicks--;
                if (route.waitTicks == 0) {
                    if (route.index >= route.entries.size()) finish(server, route);
                    else beginLeg(server, route, fake);
                }
                continue;
            }
            if (!route.moving) continue;
            TradeChainStore.Entry target = route.entries.get(route.index);
            double distance = horizontalDistance(fake, target);
            double heightDifference = Math.abs(target.y() - fake.getY());
            if (distance <= ARRIVAL_DISTANCE) {
                stopMoving(server, route.fakeName);
                route.moving = false;
                arriveAndTrade(server, route, fake, target);
            } else if (heightDifference > 0.8) {
                fail(server, route, route.fakeName + " left the saved flat level before point " + (route.index + 1) + ".");
            } else if (distance > route.previousDistance + 0.15) {
                fail(server, route, route.fakeName + " passed or moved away from point " + (route.index + 1) + ".");
            } else {
                route.stalledTicks = route.previousDistance - distance < 0.005 ? route.stalledTicks + 1 : 0;
                route.previousDistance = distance;
                route.ticksLeft--;
                if (route.stalledTicks >= STALL_LIMIT_TICKS) {
                    fail(server, route, route.fakeName + " was blocked before point " + (route.index + 1) + ".");
                } else if (route.ticksLeft <= 0) {
                    fail(server, route, route.fakeName + " timed out before point " + (route.index + 1) + ".");
                }
            }
        }
    }

    private static void beginLeg(MinecraftServer server, Route route, ServerPlayer fake) {
        if (!ROUTES.containsKey(route.fakeId)) return;
        if (route.index >= route.entries.size()) {
            finish(server, route);
            return;
        }
        TradeChainStore.Entry target = route.entries.get(route.index);
        ServerLevel level = level(server, target.dimension());
        if (level == null || fake.level() != level) {
            fail(server, route, "point " + (route.index + 1) + " is in " + target.dimension()
                + "; " + route.fakeName + " must already be in that dimension.");
            return;
        }
        double distance = horizontalDistance(fake, target);
        double heightDifference = Math.abs(target.y() - fake.getY());
        if (heightDifference > MAX_HEIGHT_DIFFERENCE) {
            fail(server, route, "point " + (route.index + 1) + " is not on the same flat level as " + route.fakeName + ".");
            return;
        }
        if (distance <= ARRIVAL_DISTANCE) {
            arriveAndTrade(server, route, fake, target);
            return;
        }
        if (distance > MAX_LEG_DISTANCE) {
            fail(server, route, "point " + (route.index + 1) + " is " + String.format("%.1f", distance)
                + " blocks away; the per-leg limit is 100.");
            return;
        }
        if (!straightPathClear(level, fake, target, distance)) {
            fail(server, route, "straight flat path to point " + (route.index + 1) + " has a gap or obstruction.");
            return;
        }

        stopMoving(server, route.fakeName);
        double dx = target.x() - fake.getX();
        double dz = target.z() - fake.getZ();
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

    private static void arriveAndTrade(MinecraftServer server, Route route, ServerPlayer fake,
                                       TradeChainStore.Entry target) {
        fake.setYRot(target.yaw());
        fake.setYHeadRot(target.yaw());
        fake.setXRot(target.pitch());
        TradeService.Result result = TradeService.trade(fake);
        absorbNearbyExperience(fake);
        route.reached++;
        route.totalTrades += result.trades();
        if (result.trades() == 0) route.unmatched++;
        route.index++;
        route.waitTicks = POST_TRADE_WAIT_TICKS;

        if (result.message().contains("insufficient emeralds") || result.message().contains("no safe space")) {
            route.stopReason = result.message();
            route.index = route.entries.size();
        }
    }

    private static boolean straightPathClear(ServerLevel level, ServerPlayer fake,
                                             TradeChainStore.Entry target, double distance) {
        int samples = Math.max(1, (int) Math.ceil(distance * 2.0));
        for (int index = 0; index <= samples; index++) {
            double ratio = (double) index / samples;
            double x = fake.getX() + (target.x() - fake.getX()) * ratio;
            double z = fake.getZ() + (target.z() - fake.getZ()) * ratio;
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

    // Trading XP is spawned beside the villager. On compact trading halls the
    // workstation can physically keep those orbs away from a player walking in
    // the aisle. Resolve nearby orbs through vanilla playerTouch(), retaining
    // Mending and normal XP accounting while avoiding a growing orb backlog.
    private static void absorbNearbyExperience(ServerPlayer fake) {
        AABB area = fake.getBoundingBox().inflate(6.0);
        for (ExperienceOrb orb : fake.level().getEntitiesOfClass(ExperienceOrb.class, area, ExperienceOrb::isAlive)) {
            int safety = 1024;
            while (orb.isAlive() && safety-- > 0) {
                fake.takeXpDelay = 0;
                orb.playerTouch(fake);
            }
        }
    }

    private static double horizontalDistance(ServerPlayer fake, TradeChainStore.Entry target) {
        double dx = target.x() - fake.getX();
        double dz = target.z() - fake.getZ();
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

    private static void finish(MinecraftServer server, Route route) {
        ROUTES.remove(route.fakeId);
        stopMoving(server, route.fakeName);
        String summary = route.fakeName + " chain " + route.chain + " (walking location): "
            + route.totalTrades + " trade(s), " + route.reached + "/" + route.entries.size()
            + " reached, " + route.unmatched + " without a usable matching offer";
        if (route.stopReason != null) summary += "; stopped: " + route.stopReason;
        if (route.reportRoutine || route.stopReason != null) TradeCommand.message(route.source, summary + ".");
        TradeKeepManager.routeFinished(server, route.fakeName, route.stopReason);
    }

    private static void fail(MinecraftServer server, Route route, String reason) {
        ROUTES.remove(route.fakeId);
        stopMoving(server, route.fakeName);
        TradeCommand.message(route.source, "walking chain " + route.chain + " stopped: " + reason);
        TradeKeepManager.routeFailed(server, route.fakeName, reason);
    }

    private static final class Route {
        private final CommandSourceStack source;
        private final UUID fakeId;
        private final String fakeName;
        private final int chain;
        private final List<TradeChainStore.Entry> entries;
        private int index;
        private int reached;
        private int totalTrades;
        private int unmatched;
        private int waitTicks;
        private int stalledTicks;
        private int ticksLeft;
        private double previousDistance;
        private boolean moving;
        private boolean reportRoutine;
        private String stopReason;

        private Route(CommandSourceStack source, UUID fakeId, String fakeName, int chain,
                      List<TradeChainStore.Entry> entries, boolean reportRoutine) {
            this.source = source;
            this.fakeId = fakeId;
            this.fakeName = fakeName;
            this.chain = chain;
            this.entries = entries;
            this.reportRoutine = reportRoutine;
        }
    }
}
