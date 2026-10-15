package myworld.mstrade.mixin;

import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Read-only access to vanilla's per-day restock counter. */
@Mixin(Villager.class)
public interface VillagerAccessor {
    @Accessor("numberOfRestocksToday")
    int mstrade$getNumberOfRestocksToday();

    @Accessor("lastRestockCheckDay")
    long mstrade$getLastRestockCheckDay();

    @Invoker("resetNumberOfRestocks")
    void mstrade$resetNumberOfRestocks();
}
