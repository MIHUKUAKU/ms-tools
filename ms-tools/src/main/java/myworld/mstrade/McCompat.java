package myworld.mstrade;

import java.lang.reflect.Method;
import net.minecraft.core.Holder;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/** Small bridge for the two API changes between Minecraft 26.2 and 26.3. */
final class McCompat {
    private McCompat() {}

    private static final Method SWING;
    private static final Object SWING_DEFAULT;
    static {
        try {
            Method method;
            Object animation = null;
            try {
                method = ServerPlayer.class.getMethod("swing", InteractionHand.class);
            } catch (NoSuchMethodException newerApi) {
                Class<?> type = Class.forName("net.minecraft.world.item.component.SwingAnimation");
                animation = type.getField("DEFAULT").get(null);
                method = ServerPlayer.class.getMethod("swing", InteractionHand.class, type, boolean.class);
            }
            SWING = method;
            SWING_DEFAULT = animation;
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported Minecraft swing API (tested: 26.2 / 26.3)", failure);
        }
    }

    static void swing(ServerPlayer fake) {
        try {
            if (SWING_DEFAULT == null) SWING.invoke(fake, InteractionHand.MAIN_HAND);
            else SWING.invoke(fake, InteractionHand.MAIN_HAND, SWING_DEFAULT, false);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Cannot animate fake player", failure);
        }
    }

    private static Method oldTicks;
    private static Method instance;
    private static Method totalTicks;
    private static Class<?> clockType;

    static long overworldTicks(MinecraftServer server, Holder<?> clock) {
        Object manager = server.overworld().clockManager();
        try {
            if (clockType != manager.getClass()) {
                clockType = manager.getClass();
                oldTicks = instance = totalTicks = null;
                try {
                    oldTicks = clockType.getMethod("getTotalTicks", Holder.class);
                } catch (NoSuchMethodException newerApi) {
                    instance = clockType.getMethod("getInstance", Holder.class);
                    totalTicks = instance.getReturnType().getMethod("totalTicks");
                }
            }
            if (oldTicks != null) return ((Number) oldTicks.invoke(manager, clock)).longValue();
            return ((Number) totalTicks.invoke(instance.invoke(manager, clock))).longValue();
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("Unsupported Minecraft clock API (tested: 26.2 / 26.3)", failure);
        }
    }
}
