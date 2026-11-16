package com.solace.mixin;

import net.minecraft.world.level.chunk.CarvingMask;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CarvingMask.class)
public interface CarvingMaskAccessor {
    @Accessor("additionalMask")
    CarvingMask.Mask solace$getAdditionalMask();
}
