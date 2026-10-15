package myworld.mstrade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CraftBoxCommand {
    private CraftBoxCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ONLINE_FAKES = (context, builder) -> {
        context.getSource().getServer().getPlayerList().getPlayers().stream()
            .filter(CraftBoxCommand::isCarpetFake)
            .forEach(player -> builder.suggest(player.getGameProfile().name()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PUT_BOX_IDS = (context, builder) -> {
        for (String id : PutBoxStore.ids(context.getSource().getServer())) builder.suggest(id);
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mscraftbox")
            .then(Commands.argument("craftbox_fake", StringArgumentType.word()).suggests(ONLINE_FAKES)
                .executes(context -> start(context, null, null))
                .then(Commands.argument("craftbox_source", IntegerArgumentType.integer(1)).suggests(PUT_BOX_IDS)
                    .then(Commands.argument("craftbox_output", IntegerArgumentType.integer(1)).suggests(PUT_BOX_IDS)
                        .executes(context -> start(context,
                            IntegerArgumentType.getInteger(context, "craftbox_source"),
                            IntegerArgumentType.getInteger(context, "craftbox_output")))))));
    }

    public static void ensureMsAlias(MinecraftServer server) {
        CommandNode<CommandSourceStack> ms = server.getCommands().getDispatcher().getRoot().getChild("ms");
        if (ms == null || ms.getChild("craftbox") != null) return;

        ms.addChild(Commands.literal("craftbox")
            .then(Commands.argument("craftbox_fake", StringArgumentType.word()).suggests(ONLINE_FAKES)
                .executes(context -> start(context, null, null))
                .then(Commands.argument("craftbox_source", IntegerArgumentType.integer(1)).suggests(PUT_BOX_IDS)
                    .then(Commands.argument("craftbox_output", IntegerArgumentType.integer(1)).suggests(PUT_BOX_IDS)
                        .executes(context -> start(context,
                            IntegerArgumentType.getInteger(context, "craftbox_source"),
                            IntegerArgumentType.getInteger(context, "craftbox_output"))))))
            .build());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
        server.sendSystemMessage(Component.literal("MS: craftbox command attached to /ms."));
    }

    private static int start(CommandContext<CommandSourceStack> context, Integer sourceBoxId, Integer outputBoxId) {
        String fakeName = StringArgumentType.getString(context, "craftbox_fake");
        return CraftBoxManager.start(context.getSource(), fakeName, sourceBoxId, outputBoxId);
    }

    private static boolean isCarpetFake(ServerPlayer player) {
        return player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake");
    }
}
