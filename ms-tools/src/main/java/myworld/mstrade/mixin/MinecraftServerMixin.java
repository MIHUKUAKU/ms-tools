package myworld.mstrade.mixin;

import myworld.mstrade.CollectCommand;
import myworld.mstrade.CollectManager;
import myworld.mstrade.CraftBoxCommand;
import myworld.mstrade.CraftBoxManager;
import myworld.mstrade.TradeCommand;
import myworld.mstrade.TradeKeepManager;
import myworld.mstrade.TradeRouteManager;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
public abstract class MinecraftServerMixin {
    @Inject(method = "stopServer", at = @At("HEAD"))
    private void mstrade$clearTasksOnStop(CallbackInfo ci) {
        CollectManager.clearOnServerStop();
        CraftBoxManager.clearOnServerStop();
        TradeRouteManager.clearOnServerStop();
        TradeKeepManager.clearOnServerStop();
    }

    @Inject(method = "tickServer", at = @At("TAIL"))
    private void mstrade$attachMsKeepCommand(BooleanSupplier hasTimeLeft, CallbackInfo ci) {
        MinecraftServer server = (MinecraftServer) (Object) this;
        TradeCommand.ensureMsKeepAlias(server);
        CollectCommand.ensureMsAlias(server);
        CraftBoxCommand.ensureMsAlias(server);
        CollectManager.tick(server);
        CraftBoxManager.tick(server);
        TradeRouteManager.tick(server);
        TradeKeepManager.tick(server);
    }
}
