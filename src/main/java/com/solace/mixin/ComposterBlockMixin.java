package com.solace.mixin;

import it.unimi.dsi.fastutil.objects.Object2FloatMap;
import it.unimi.dsi.fastutil.objects.Object2FloatMaps;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.ComposterBlock;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ComposterBlock.class)
public abstract class ComposterBlockMixin {
    @Shadow
    @Final
    @Mutable
    public static Object2FloatMap<Item> COMPOSTABLES;

    @Inject(method = "<clinit>", at = @At("TAIL"))
    private static void solace$makeCompostableRegistryThreadSafe(CallbackInfo ci) {
        COMPOSTABLES = Object2FloatMaps.synchronize(COMPOSTABLES);
    }
}
