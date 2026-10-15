package myworld.mstrade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

public final class TradeCommand {
    private static final int DEFAULT_CAPTURE_RADIUS = 32;

    private TradeCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ONLINE_PLAYERS = (context, builder) -> {
        context.getSource().getServer().getPlayerList().getPlayers().forEach(player -> builder.suggest(player.getGameProfile().name()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> CHAIN_IDS = (context, builder) -> {
        TradeChainStore.ids(context.getSource().getServer()).stream()
            .sorted(Comparator.comparingInt(Integer::parseInt))
            .forEach(builder::suggest);
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mstrade")
            .then(Commands.literal("chain")
                .then(Commands.literal("add")
                    .then(Commands.argument("chain", IntegerArgumentType.integer()).suggests(CHAIN_IDS)
                        .executes(TradeCommand::addEntry)))
                .then(Commands.literal("remove")
                    .then(Commands.argument("chain", IntegerArgumentType.integer()).suggests(CHAIN_IDS)
                        .executes(TradeCommand::removeEntry)))
                .then(Commands.literal("delete")
                    .then(Commands.argument("chain", IntegerArgumentType.integer()).suggests(CHAIN_IDS)
                        .executes(TradeCommand::deleteChain)))
                .then(Commands.literal("list")
                    .executes(TradeCommand::listChains)
                    .then(Commands.argument("chain", IntegerArgumentType.integer()).suggests(CHAIN_IDS)
                        .executes(TradeCommand::listChain)))
                .then(Commands.literal("capture")
                    .then(Commands.argument("chain", IntegerArgumentType.integer()).suggests(CHAIN_IDS)
                        .executes(context -> capture(context, DEFAULT_CAPTURE_RADIUS))
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                            .executes(context -> capture(context, IntegerArgumentType.getInteger(context, "radius")))))))
            .then(Commands.argument("fake", StringArgumentType.word()).suggests(ONLINE_PLAYERS)
                .executes(context -> tradeFront(context.getSource(), StringArgumentType.getString(context, "fake")))
                .then(Commands.argument("chain", IntegerArgumentType.integer()).suggests(CHAIN_IDS)
                    .executes(TradeCommand::tradeChain)
                    .then(Commands.literal("keep")
                        .executes(TradeCommand::tradeChainKeep)
                        .then(Commands.literal("stop")
                            .executes(TradeCommand::stopTradeChainKeep))
                        .then(Commands.literal("report")
                            .executes(context -> setTradeChainKeepReport(context, null))
                            .then(Commands.literal("on")
                                .executes(context -> setTradeChainKeepReport(context, true)))
                            .then(Commands.literal("off")
                                .executes(context -> setTradeChainKeepReport(context, false))))))));

    }

    public static void ensureMsKeepAlias(net.minecraft.server.MinecraftServer server) {
        CommandNode<CommandSourceStack> ms = server.getCommands().getDispatcher().getRoot().getChild("ms");
        if (ms == null) return;
        CommandNode<CommandSourceStack> trade = ms.getChild("trade");
        if (trade == null) return;
        CommandNode<CommandSourceStack> fake = trade.getChild("online_fake");
        if (fake == null) return;
        CommandNode<CommandSourceStack> chain = fake.getChild("trade_chain_id_int");
        if (chain == null || chain.getChild("keep") != null) return;

        chain.addChild(Commands.literal("keep")
            .executes(TradeCommand::tradeChainKeepAlias)
            .then(Commands.literal("stop").executes(TradeCommand::stopTradeChainKeepAlias))
            .then(Commands.literal("report")
                .executes(context -> setTradeChainKeepReportAlias(context, null))
                .then(Commands.literal("on")
                    .executes(context -> setTradeChainKeepReportAlias(context, true)))
                .then(Commands.literal("off")
                    .executes(context -> setTradeChainKeepReportAlias(context, false))))
            .build());
        for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
        server.sendSystemMessage(Component.literal("MS: trade keep command attached to /ms."));
    }

    private static int tradeFront(CommandSourceStack source, String fakeName) {
        ServerPlayer player = source.getServer().getPlayerList().getPlayerByName(fakeName);
        TradeService.Result result = TradeService.trade(player);
        message(source, result.message());
        return result.success() ? result.trades() : 0;
    }

    private static int tradeChain(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        String chain = chainNumber.toString();
        String fakeName = StringArgumentType.getString(context, "fake");
        ServerPlayer fake = source.getServer().getPlayerList().getPlayerByName(fakeName);
        if (fake == null) {
            message(source, "target fake player is not online.");
            return 0;
        }
        if (!fake.getClass().getName().equals("carpet.patches.EntityPlayerMPFake")) {
            message(source, fakeName + " is not a Carpet fake player.");
            return 0;
        }
        if (fake.getMainHandItem().isEmpty()) {
            message(source, fakeName + " must hold the item to buy in the main hand.");
            return 0;
        }

        List<TradeChainStore.Entry> entries = TradeChainStore.get(source.getServer(), chain);
        if (entries.isEmpty()) {
            message(source, "trade chain " + chain + " does not exist or is empty.");
            return 0;
        }

        if (chainNumber > 0) {
            return TradeRouteManager.start(source, fake, chainNumber, entries);
        }

        int totalTrades = 0;
        int reached = 0;
        int missing = 0;
        int unmatched = 0;
        String stopReason = null;
        for (TradeChainStore.Entry entry : entries) {
            TradeService.Result result;
            Entity entity = source.getLevel().getEntityInAnyDimension(entry.uuid());
            if (!(entity instanceof Villager villager) || !villager.isAlive()) {
                missing++;
                continue;
            }
            result = TradeService.trade(fake, villager);
            reached++;
            totalTrades += result.trades();
            if (result.trades() == 0) unmatched++;
            if (result.message().contains("insufficient emeralds") || result.message().contains("no safe space")) {
                stopReason = result.message();
                break;
            }
        }

        String unit = "entity";
        String summary = fakeName + " chain " + chain + " (" + unit + "): " + totalTrades + " trade(s), " + reached + "/" + entries.size()
            + " reached, " + unmatched + " without a usable matching offer";
        if (missing > 0) summary += ", " + missing + " unavailable";
        if (stopReason != null) summary += "; stopped: " + stopReason;
        message(source, summary + ".");
        return totalTrades;
    }

    private static int tradeChainKeep(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        if (chainNumber < 0) {
            message(source, "keep mode requires a positive location chain.");
            return 0;
        }
        String fakeName = StringArgumentType.getString(context, "fake");
        List<TradeChainStore.Entry> entries = TradeChainStore.get(source.getServer(), chainNumber.toString());
        if (entries.isEmpty()) {
            message(source, "trade chain " + chainNumber + " does not exist or is empty.");
            return 0;
        }
        return TradeKeepManager.start(source, fakeName, chainNumber, entries);
    }

    private static int stopTradeChainKeep(CommandContext<CommandSourceStack> context) {
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        return TradeKeepManager.stop(context.getSource(), StringArgumentType.getString(context, "fake"), chainNumber);
    }

    private static int setTradeChainKeepReport(CommandContext<CommandSourceStack> context, Boolean enabled) {
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        String fakeName = StringArgumentType.getString(context, "fake");
        return enabled == null
            ? TradeKeepManager.reportStatus(context.getSource(), fakeName, chainNumber)
            : TradeKeepManager.setReporting(context.getSource(), fakeName, chainNumber, enabled);
    }

    private static int tradeChainKeepAlias(CommandContext<CommandSourceStack> context) {
        Integer chain = scarpetChainNumber(context);
        if (chain == null) return 0;
        return startTradeChainKeep(context.getSource(),
            StringArgumentType.getString(context, "online_fake"),
            chain);
    }

    private static int stopTradeChainKeepAlias(CommandContext<CommandSourceStack> context) {
        Integer chain = scarpetChainNumber(context);
        if (chain == null) return 0;
        if (chain == 0) {
            message(context.getSource(), "chain 0 is invalid.");
            return 0;
        }
        return TradeKeepManager.stop(context.getSource(),
            StringArgumentType.getString(context, "online_fake"), chain);
    }

    private static int setTradeChainKeepReportAlias(CommandContext<CommandSourceStack> context, Boolean enabled) {
        Integer chain = scarpetChainNumber(context);
        if (chain == null) return 0;
        String fakeName = StringArgumentType.getString(context, "online_fake");
        return enabled == null
            ? TradeKeepManager.reportStatus(context.getSource(), fakeName, chain)
            : TradeKeepManager.setReporting(context.getSource(), fakeName, chain, enabled);
    }

    /** Carpet's configured `int` arguments are Brigadier longs, not integers. */
    private static Integer scarpetChainNumber(CommandContext<CommandSourceStack> context) {
        long chain = LongArgumentType.getLong(context, "trade_chain_id_int");
        if (chain < Integer.MIN_VALUE || chain > Integer.MAX_VALUE) {
            message(context.getSource(), "chain number is outside the supported range.");
            return null;
        }
        return (int) chain;
    }

    private static int startTradeChainKeep(CommandSourceStack source, String fakeName, int chainNumber) {
        if (chainNumber == 0) {
            message(source, "chain 0 is invalid.");
            return 0;
        }
        if (chainNumber < 0) {
            message(source, "keep mode requires a positive location chain.");
            return 0;
        }
        List<TradeChainStore.Entry> entries = TradeChainStore.get(source.getServer(), Integer.toString(chainNumber));
        if (entries.isEmpty()) {
            message(source, "trade chain " + chainNumber + " does not exist or is empty.");
            return 0;
        }
        return TradeKeepManager.start(source, fakeName, chainNumber, entries);
    }

    private static int addEntry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            message(source, "chain add must be used by a player.");
            return 0;
        }
        String chain = chainNumber.toString();
        try {
            if (chainNumber < 0) {
                Villager villager = TradeService.villagerInFront(player, 8.0);
                if (villager == null) {
                    message(source, "no villager is under the crosshair within 8 blocks.");
                    return 0;
                }
                boolean added = TradeChainStore.addEntity(source.getServer(), chain, villager);
                message(source, added ? "added villager " + shortUuid(villager) + " to entity chain " + chain + "."
                    : "that villager is already in chain " + chain + ".");
                return added ? 1 : 0;
            }
            TradeChainStore.Entry point = playerPoint(player);
            boolean added = TradeChainStore.addLocation(source.getServer(), chain, point);
            message(source, added ? "added current block center and view to location chain " + chain + "."
                : "a location already exists here in chain " + chain + ".");
            return added ? 1 : 0;
        } catch (IOException exception) {
            message(source, "failed to save chain: " + exception.getMessage());
            return 0;
        }
    }

    private static int removeEntry(CommandContext<CommandSourceStack> context) {
        CommandSourceStack source = context.getSource();
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            message(source, "chain remove must be used by a player.");
            return 0;
        }
        String chain = chainNumber.toString();
        try {
            boolean removed;
            if (chainNumber < 0) {
                Villager villager = TradeService.villagerInFront(player, 8.0);
                if (villager == null) {
                    message(source, "no villager is under the crosshair within 8 blocks.");
                    return 0;
                }
                removed = TradeChainStore.removeEntity(source.getServer(), chain, villager.getUUID());
            } else {
                removed = TradeChainStore.removeNearestLocation(source.getServer(), chain, playerPoint(player));
            }
            message(source, removed ? "removed one entry from chain " + chain + "."
                : "no matching entry was found in chain " + chain + ".");
            return removed ? 1 : 0;
        } catch (IOException exception) {
            message(source, "failed to save chain: " + exception.getMessage());
            return 0;
        }
    }

    private static int capture(CommandContext<CommandSourceStack> context, int radius) {
        CommandSourceStack source = context.getSource();
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        Vec3 center = source.getPosition();
        AABB area = new AABB(center.x - radius, center.y - 8, center.z - radius,
            center.x + radius, center.y + 8, center.z + radius);
        List<Villager> masons = source.getLevel().getEntitiesOfClass(Villager.class, area,
            villager -> villager.isAlive() && villager.getVillagerData().profession().is(VillagerProfession.MASON));
        masons.sort(Comparator.comparingDouble(villager -> villager.distanceToSqr(center)));

        List<TradeChainStore.Entry> entries = new ArrayList<>();
        int missingStations = 0;
        if (chainNumber < 0) {
            masons.forEach(villager -> entries.add(TradeChainStore.Entry.entity(villager)));
        } else {
            for (Villager villager : masons) {
                TradeChainStore.Entry point = stationPoint(villager);
                if (point == null) missingStations++;
                else if (entries.stream().noneMatch(entry -> samePoint(entry, point))) entries.add(point);
            }
        }

        try {
            int count = TradeChainStore.replace(source.getServer(), chainNumber.toString(), entries);
            String type = chainNumber < 0 ? "villager UUID" : "stonecutter-front location";
            String suffix = missingStations == 0 ? "" : "; " + missingStations + " mason(s) had no nearby stonecutter";
            message(source, "captured " + count + " " + type + " entry/entries into chain " + chainNumber + suffix + ".");
            return count;
        } catch (IOException exception) {
            message(source, "failed to save chain: " + exception.getMessage());
            return 0;
        }
    }

    private static TradeChainStore.Entry stationPoint(Villager villager) {
        BlockPos origin = villager.blockPosition();
        BlockPos cutter = null;
        double nearest = Double.MAX_VALUE;
        for (int y = -1; y <= 1; y++) {
            for (int x = -2; x <= 2; x++) {
                for (int z = -2; z <= 2; z++) {
                    BlockPos candidate = origin.offset(x, y, z);
                    if (!villager.level().getBlockState(candidate).is(Blocks.STONECUTTER)) continue;
                    double distance = candidate.distToCenterSqr(villager.position());
                    if (distance < nearest) {
                        cutter = candidate.immutable();
                        nearest = distance;
                    }
                }
            }
        }
        if (cutter == null) return null;

        double cutterX = cutter.getX() + 0.5;
        double cutterZ = cutter.getZ() + 0.5;
        double towardCutterX = cutterX - villager.getX();
        double towardCutterZ = cutterZ - villager.getZ();
        int stepX = Math.abs(towardCutterX) >= Math.abs(towardCutterZ) ? (int) Math.signum(towardCutterX) : 0;
        int stepZ = stepX == 0 ? (int) Math.signum(towardCutterZ) : 0;
        if (stepX == 0 && stepZ == 0) return null;

        double standX = cutterX + stepX;
        double standY = villager.getY();
        double standZ = cutterZ + stepZ;
        double targetY = villager.getBoundingBox().getCenter().y;
        double dx = villager.getX() - standX;
        double dz = villager.getZ() - standZ;
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) Math.toDegrees(-Math.atan2(targetY - (standY + 1.62), horizontal));
        return TradeChainStore.Entry.location((ServerLevel) villager.level(), standX, standY, standZ, yaw, pitch);
    }

    private static boolean samePoint(TradeChainStore.Entry first, TradeChainStore.Entry second) {
        return first.dimension().equals(second.dimension())
            && Math.abs(first.x() - second.x()) < 0.1
            && Math.abs(first.y() - second.y()) < 0.1
            && Math.abs(first.z() - second.z()) < 0.1;
    }

    private static TradeChainStore.Entry playerPoint(ServerPlayer player) {
        double x = Math.floor(player.getX()) + 0.5;
        double z = Math.floor(player.getZ()) + 0.5;
        return TradeChainStore.Entry.location((ServerLevel) player.level(), x, player.getY(), z, player.getYRot(), player.getXRot());
    }

    private static ServerLevel findLevel(CommandSourceStack source, String dimension) {
        for (ServerLevel level : source.getServer().getAllLevels()) {
            if (level.dimension().identifier().toString().equals(dimension)) return level;
        }
        return null;
    }

    private static int deleteChain(CommandContext<CommandSourceStack> context) {
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        try {
            boolean deleted = TradeChainStore.delete(context.getSource().getServer(), chainNumber.toString());
            message(context.getSource(), deleted ? "deleted trade chain " + chainNumber + "." : "trade chain " + chainNumber + " does not exist.");
            return deleted ? 1 : 0;
        } catch (IOException exception) {
            message(context.getSource(), "failed to save chain: " + exception.getMessage());
            return 0;
        }
    }

    private static int listChains(CommandContext<CommandSourceStack> context) {
        List<String> ids = TradeChainStore.ids(context.getSource().getServer()).stream()
            .sorted(Comparator.comparingInt(Integer::parseInt)).toList();
        if (ids.isEmpty()) message(context.getSource(), "no trade chains saved.");
        else message(context.getSource(), "trade chains: " + String.join(", ", ids) + " (negative=entity, positive=location).");
        return ids.size();
    }

    private static int listChain(CommandContext<CommandSourceStack> context) {
        Integer chainNumber = chainNumber(context);
        if (chainNumber == null) return 0;
        List<TradeChainStore.Entry> entries = TradeChainStore.get(context.getSource().getServer(), chainNumber.toString());
        if (entries.isEmpty()) {
            message(context.getSource(), "trade chain " + chainNumber + " does not exist or is empty.");
            return 0;
        }
        String type = chainNumber < 0 ? "entity" : "location";
        message(context.getSource(), "trade chain " + chainNumber + " (" + type + ") contains " + entries.size() + " entry/entries:");
        for (int index = 0; index < entries.size(); index++) {
            TradeChainStore.Entry entry = entries.get(index);
            String identity = chainNumber < 0 && entry.uuid() != null ? entry.uuid().toString().substring(0, 8) + " @ " : "";
            String facing = chainNumber > 0 ? String.format(" yaw %.1f pitch %.1f", entry.yaw(), entry.pitch()) : "";
            message(context.getSource(), (index + 1) + ". " + identity
                + String.format("%.1f %.1f %.1f", entry.x(), entry.y(), entry.z()) + facing + " in " + entry.dimension());
        }
        return entries.size();
    }

    private static Integer chainNumber(CommandContext<CommandSourceStack> context) {
        int number = IntegerArgumentType.getInteger(context, "chain");
        if (number == 0) {
            message(context.getSource(), "chain 0 is invalid; use a negative number for entity chains or a positive number for location chains.");
            return null;
        }
        return number;
    }

    private static String shortUuid(Villager villager) {
        return villager.getUUID().toString().substring(0, 8);
    }

    static void message(CommandSourceStack source, String text) {
        Component component = Component.literal("MS: " + text);
        // Scarpet's run() uses a silent command source, so ordinary command
        // feedback is swallowed. Log once and send directly to real players.
        source.getServer().sendSystemMessage(component);
        for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
            if (!player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake")) {
                player.sendSystemMessage(component);
            }
        }
    }
}
