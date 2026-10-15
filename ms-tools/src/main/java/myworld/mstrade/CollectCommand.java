package myworld.mstrade;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.ArgumentCommandNode;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

public final class CollectCommand {
    private CollectCommand() {}

    private static final SuggestionProvider<CommandSourceStack> ONLINE_FAKES = (context, builder) -> {
        context.getSource().getServer().getPlayerList().getPlayers().stream()
            .filter(CollectCommand::isCarpetFake)
            .forEach(player -> builder.suggest(player.getGameProfile().name()));
        return builder.buildFuture();
    };

    private static final SuggestionProvider<CommandSourceStack> PUT_BOX_IDS = (context, builder) -> {
        CommandNode<CommandSourceStack> ms = context.getSource().getServer().getCommands()
            .getDispatcher().getRoot().getChild("ms");
        if (ms != null) {
            CommandNode<CommandSourceStack> put = ms.getChild("put");
            CommandNode<CommandSourceStack> fake = put == null ? null : put.getChild("online_fake");
            CommandNode<CommandSourceStack> id = fake == null ? null : fake.getChild("put_saved_id_int");
            if (id instanceof ArgumentCommandNode<CommandSourceStack, ?> argument
                && argument.getCustomSuggestions() != null) {
                return argument.getCustomSuggestions().getSuggestions(context, builder);
            }
        }
        return builder.buildFuture();
    };

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("mscollect")
            .then(Commands.argument("collect_fake", StringArgumentType.word()).suggests(ONLINE_FAKES)
                .executes(context -> start(context, null))
                .then(Commands.argument("collect_box", IntegerArgumentType.integer(1)).suggests(PUT_BOX_IDS)
                    .executes(context -> start(context, IntegerArgumentType.getInteger(context, "collect_box"))))));
    }

    public static void ensureMsAlias(MinecraftServer server) {
        CommandNode<CommandSourceStack> ms = server.getCommands().getDispatcher().getRoot().getChild("ms");
        if (ms == null || ms.getChild("collect") != null) return;

        ms.addChild(Commands.literal("collect")
            .then(Commands.argument("collect_fake", StringArgumentType.word()).suggests(ONLINE_FAKES)
                .executes(context -> start(context, null))
                .then(Commands.argument("collect_box", IntegerArgumentType.integer(1)).suggests(PUT_BOX_IDS)
                    .executes(context -> start(context, IntegerArgumentType.getInteger(context, "collect_box")))))
            .build());

        for (ServerPlayer player : server.getPlayerList().getPlayers()) server.getCommands().sendCommands(player);
        server.sendSystemMessage(Component.literal("MS: collect command attached to /ms."));
    }

    private static int start(CommandContext<CommandSourceStack> context, Integer putBoxId) {
        String fakeName = StringArgumentType.getString(context, "collect_fake");
        return CollectManager.start(context.getSource(), fakeName, putBoxId);
    }

    private static boolean isCarpetFake(ServerPlayer player) {
        return player.getClass().getName().equals("carpet.patches.EntityPlayerMPFake");
    }
}
