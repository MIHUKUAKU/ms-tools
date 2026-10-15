package myworld.mstrade.mixin;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import com.mojang.brigadier.tree.RootCommandNode;
import myworld.mstrade.TradeCommand;
import myworld.mstrade.CollectCommand;
import myworld.mstrade.CraftBoxCommand;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(Commands.class)
public abstract class CommandsMixin {
    private static final Set<String> MSTRADE_HIDDEN_ROOT_COMMANDS = Set.of("me", "msg", "mstrade", "mscollect", "mscraftbox");

    @Shadow @Final private CommandDispatcher<CommandSourceStack> dispatcher;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void mstrade$register(Commands.CommandSelection selection, CommandBuildContext context, CallbackInfo ci) {
        TradeCommand.register(this.dispatcher);
        CollectCommand.register(this.dispatcher);
        CraftBoxCommand.register(this.dispatcher);
    }

    @ModifyArg(
        method = "sendCommands",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket;<init>(Lcom/mojang/brigadier/tree/RootCommandNode;Lnet/minecraft/network/protocol/game/ClientboundCommandsPacket$NodeInspector;)V"
        ),
        index = 0
    )
    private RootCommandNode<SharedSuggestionProvider> mstrade$hideRootCommands(
        RootCommandNode<SharedSuggestionProvider> root
    ) {
        CommandNodeAccessor accessor = (CommandNodeAccessor) (CommandNode<?>) root;
        for (String command : MSTRADE_HIDDEN_ROOT_COMMANDS) {
            accessor.mstrade$getChildren().remove(command);
            accessor.mstrade$getLiterals().remove(command);
        }
        return root;
    }
}
