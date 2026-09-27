package com.solace.mixin;

import net.minecraft.util.RandomSource;
import net.minecraft.world.level.storage.loot.LootContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Allows the cached loot-extra probe to exercise probability edges without changing real chest RNG. */
@Mixin(LootContext.class)
public interface LootContextRandomAccessor {
    @Mutable
    @Accessor("random")
    void solace$setRandom(RandomSource random);
}
