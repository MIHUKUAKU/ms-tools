package myworld.mstrade;

import myworld.mstrade.mixin.VillagerAccessor;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.clock.WorldClocks;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.timeline.Timeline;
import net.minecraft.world.timeline.Timelines;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Keeps fake players online only while villagers are able to work and restock. */
public final class TradeKeepManager {
    // Come online shortly after morning so the trading hall remains loaded.
    // Actual offer checks wait for vanilla villager work time at 2000 gt.
    private static final int ONLINE_START = 300;
    private static final int TRADE_START = 2000;
    private static final int WORK_END = 9000;
    private static final int CHECK_INTERVAL_TICKS = 20;
    private static final int RESTOCK_SETTLE_TICKS = 40;
    private static final int SPAWN_RETRY_TICKS = 200;

    private static final Map<String, Keep> KEEPS = new HashMap<>();
    private TradeKeepManager() {}

    public static void clearOnServerStop() {
        KEEPS.clear();
    }

    public static int start(CommandSourceStack source, String fakeName, int chain,
                            List<TradeChainStore.Entry> entries) {
        MinecraftServer server = source.getServer();
        ServerPlayer online = server.getPlayerList().getPlayerByName(fakeName);
        if (online != null && !isCarpetFake(online)) {
            TradeCommand.message(source, fakeName + " is a real player; keep mode only controls Carpet fake players.");
            return 0;
        }
        String key = key(fakeName);
        Keep previous = KEEPS.put(key, new Keep(source, fakeName, chain, new ArrayList<>(entries)));
        if (previous != null) TradeRouteManager.cancelForKeep(server, previous.fakeName);
        TradeCommand.message(source, "trade keep enabled for " + fakeName + " on location chain " + chain
            + "; online from 300 gt, then logs off after every saved villager has used both vanilla restocks "
            + "and the matching offer is sold out. Routine reports are OFF; use /ms trade " + fakeName + " "
            + chain + " keep report on to enable them, or keep stop to disable keep.");
        return 1;
    }

    public static int stop(CommandSourceStack source, String fakeName, int chain) {
        Keep keep = KEEPS.get(key(fakeName));
        if (keep == null || keep.chain != chain) {
            TradeCommand.message(source, "no active trade keep was found for " + fakeName + " on chain " + chain + ".");
            return 0;
        }
        KEEPS.remove(key(fakeName));
        TradeRouteManager.cancelForKeep(source.getServer(), keep.fakeName);
        logOffFake(source.getServer(), keep.fakeName);
        TradeCommand.message(source, "trade keep disabled for " + keep.fakeName + " on chain " + chain + ".");
        return 1;
    }

    public static int reportStatus(CommandSourceStack source, String fakeName, int chain) {
        Keep keep = active(source, fakeName, chain);
        if (keep == null) return 0;
        TradeCommand.message(source, "trade keep routine reports for " + keep.fakeName + " on chain "
            + chain + " are " + (keep.report ? "ON." : "OFF."));
        return keep.report ? 1 : 0;
    }

    public static int setReporting(CommandSourceStack source, String fakeName, int chain, boolean enabled) {
        Keep keep = active(source, fakeName, chain);
        if (keep == null) return 0;
        keep.report = enabled;
        TradeRouteManager.setReporting(keep.fakeName, enabled);
        TradeCommand.message(source, "trade keep routine reports for " + keep.fakeName + " on chain "
            + chain + " turned " + (enabled ? "ON." : "OFF."));
        return 1;
    }

    private static Keep active(CommandSourceStack source, String fakeName, int chain) {
        Keep keep = KEEPS.get(key(fakeName));
        if (keep == null || keep.chain != chain) {
            TradeCommand.message(source, "no active trade keep was found for " + fakeName + " on chain " + chain + ".");
            return null;
        }
        return keep;
    }

    static void routeFinished(MinecraftServer server, String fakeName, String stopReason) {
        Keep keep = KEEPS.get(key(fakeName));
        if (keep == null) return;
        keep.availableSince = -1;
        keep.nextCheckTick = server.getTickCount() + CHECK_INTERVAL_TICKS;
        if (stopReason != null) {
            KEEPS.remove(key(fakeName));
            logOffFake(server, keep.fakeName);
            TradeCommand.message(keep.source, "trade keep stopped because " + stopReason);
        }
    }

    static void routeFailed(MinecraftServer server, String fakeName, String reason) {
        Keep keep = KEEPS.remove(key(fakeName));
        if (keep == null) return;
        logOffFake(server, keep.fakeName);
        TradeCommand.message(keep.source, "trade keep disabled after route failure: " + reason);
    }

    public static void tick(MinecraftServer server) {
        for (Keep keep : new ArrayList<>(KEEPS.values())) tickOne(server, keep);
    }

    private static void tickOne(MinecraftServer server, Keep keep) {
        if (KEEPS.get(key(keep.fakeName)) != keep) return;
        long totalDayTicks = overworldTicks(server);
        long day = Math.floorDiv(totalDayTicks, 24000L);
        int dayTick = (int) Math.floorMod(totalDayTicks, 24000L);
        if (keep.day != day) {
            keep.day = day;
            Arrays.fill(keep.completed, false);
            keep.dayComplete = false;
            keep.availableSince = -1;
            keep.nextSpawnTick = 0;
        }

        boolean onlineTime = dayTick >= ONLINE_START && dayTick < WORK_END;
        ServerPlayer fake = server.getPlayerList().getPlayerByName(keep.fakeName);

        if (!onlineTime || keep.dayComplete) {
            keep.availableSince = -1;
            keep.nextSpawnTick = 0;
            if (TradeRouteManager.isRunning(keep.fakeName)) TradeRouteManager.cancelForKeep(server, keep.fakeName);
            if (fake != null && isCarpetFake(fake) && !keep.logoffRequested) {
                keep.logoffRequested = true;
                logOffFake(server, keep.fakeName);
                if (!keep.dayComplete) {
                    report(keep, keep.fakeName + " logged off outside the daily keep window.");
                }
            }
            return;
        }

        keep.logoffRequested = false;
        int tick = server.getTickCount();
        if (fake == null) {
            if (tick >= keep.nextSpawnTick) {
                runCarpet(server, spawnCommand(keep));
                keep.nextSpawnTick = tick + SPAWN_RETRY_TICKS;
            }
            return;
        }
        if (!isCarpetFake(fake)) {
            KEEPS.remove(key(keep.fakeName));
            TradeCommand.message(keep.source, "trade keep disabled because " + keep.fakeName + " is online as a real player.");
            return;
        }
        if (fake.getMainHandItem().isEmpty()) {
            KEEPS.remove(key(keep.fakeName));
            logOffFake(server, keep.fakeName);
            TradeCommand.message(keep.source, "trade keep disabled: " + keep.fakeName
                + " has no trade selector item in the main hand.");
            return;
        }
        if (dayTick < TRADE_START) return;
        if (TradeRouteManager.isRunning(keep.fakeName) || tick < keep.nextCheckTick) return;
        keep.nextCheckTick = tick + CHECK_INTERVAL_TICKS;

        OfferScan scan = scanOffers(server, fake, keep);
        if (scan.newlyCompleted > 0) {
            report(keep, "trade keep completed " + completedCount(keep) + "/"
                + keep.entries.size() + " saved villager(s) for today.");
        }
        if (allCompleted(keep)) {
            keep.dayComplete = true;
            keep.availableSince = -1;
            logOffFake(server, keep.fakeName);
            keep.logoffRequested = true;
            report(keep, "trade keep finished today's chain for " + keep.fakeName
                + ": every saved villager used both vanilla restocks and the matching offer is sold out; logged off until tomorrow.");
            return;
        }
        if (scan.availableEntries.isEmpty()) {
            keep.availableSince = -1;
            return;
        }
        if (keep.availableSince < 0) {
            keep.availableSince = tick;
            return;
        }
        if (tick - keep.availableSince < RESTOCK_SETTLE_TICKS) return;

        keep.availableSince = -1;
        report(keep, "trade keep detected " + scan.availableEntries.size()
            + " available unfinished villager(s) for " + keep.fakeName + "; walking chain " + keep.chain + ".");
        TradeRouteManager.start(keep.source, fake, keep.chain, scan.availableEntries, keep.report);
    }

    private static OfferScan scanOffers(MinecraftServer server, ServerPlayer fake, Keep keep) {
        List<TradeChainStore.Entry> available = new ArrayList<>();
        int newlyCompleted = 0;
        int villagerDay = villagerDay(server);
        for (int index = 0; index < keep.entries.size(); index++) {
            if (keep.completed[index]) continue;
            TradeChainStore.Entry entry = keep.entries.get(index);
            ServerLevel level = level(server, entry.dimension());
            if (level == null) continue;
            Villager villager = TradeService.villagerAtSavedView(level, entry, 5.0);
            if (villager == null) continue;

            VillagerAccessor accessor = (VillagerAccessor) villager;
            // Vanilla deliberately does not reset the count when the saved
            // last-check day is still its initial zero value. Old/imported
            // villagers can therefore carry yesterday's count into their
            // first observed new day. Use vanilla's own reset routine once for
            // that edge, including its normal demand catch-up behavior.
            if (accessor.mstrade$getLastRestockCheckDay() <= 0 && villagerDay > 0
                    && accessor.mstrade$getNumberOfRestocksToday() > 0) {
                accessor.mstrade$resetNumberOfRestocks();
            }
            // Besides answering whether restocking is needed, vanilla uses
            // this call to update lastRestockCheckDay and perform normal later
            // daily resets.
            villager.shouldRestock(level);
            int restocks = accessor.mstrade$getNumberOfRestocksToday();
            TradeService.MatchingOfferState state = TradeService.matchingOfferState(fake, villager);
            if (restocks >= 2 && state == TradeService.MatchingOfferState.OUT_OF_STOCK) {
                keep.completed[index] = true;
                newlyCompleted++;
            } else if (state == TradeService.MatchingOfferState.AVAILABLE) {
                available.add(entry);
            }
        }
        return new OfferScan(available, newlyCompleted);
    }

    private static long overworldTicks(MinecraftServer server) {
        // Read the overworld clock directly. Unlike gameTime, this clock jumps
        // to morning when players sleep, so keep mode cannot remain stuck in
        // the previous night's time window after a skipped night.
        var overworldClock = server.registryAccess().lookupOrThrow(Registries.WORLD_CLOCK)
            .getOrThrow(WorldClocks.OVERWORLD);
        return McCompat.overworldTicks(server, overworldClock);
    }

    private static int villagerDay(MinecraftServer server) {
        Timeline timeline = server.registryAccess().lookupOrThrow(Registries.TIMELINE)
            .getOrThrow(Timelines.OVERWORLD_DAY).value();
        return timeline.getPeriodCount(server.overworld().clockManager());
    }

    private static int completedCount(Keep keep) {
        int completed = 0;
        for (boolean value : keep.completed) if (value) completed++;
        return completed;
    }

    private static boolean allCompleted(Keep keep) {
        return completedCount(keep) == keep.completed.length;
    }

    private static void report(Keep keep, String text) {
        if (keep.report) TradeCommand.message(keep.source, text);
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

    private static void logOffFake(MinecraftServer server, String fakeName) {
        ServerPlayer player = server.getPlayerList().getPlayerByName(fakeName);
        if (player != null && isCarpetFake(player)) runCarpet(server, "player " + fakeName + " kill");
    }

    private static void runCarpet(MinecraftServer server, String command) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(), command);
    }

    private static String spawnCommand(Keep keep) {
        TradeChainStore.Entry entry = keep.entries.get(0);
        return "player " + keep.fakeName + " spawn at " + entry.x() + " " + entry.y() + " " + entry.z()
            + " facing " + entry.yaw() + " " + entry.pitch() + " in " + entry.dimension();
    }

    private static String key(String fakeName) {
        return fakeName.toLowerCase(Locale.ROOT);
    }

    private static final class Keep {
        private final CommandSourceStack source;
        private final String fakeName;
        private final int chain;
        private final List<TradeChainStore.Entry> entries;
        private int nextCheckTick;
        private int nextSpawnTick;
        private int availableSince = -1;
        private boolean logoffRequested;
        private long day = Long.MIN_VALUE;
        private final boolean[] completed;
        private boolean dayComplete;
        private boolean report;

        private Keep(CommandSourceStack source, String fakeName, int chain,
                     List<TradeChainStore.Entry> entries) {
            this.source = source;
            this.fakeName = fakeName;
            this.chain = chain;
            this.entries = entries;
            this.completed = new boolean[entries.size()];
        }
    }

    private record OfferScan(List<TradeChainStore.Entry> availableEntries, int newlyCompleted) {}
}
