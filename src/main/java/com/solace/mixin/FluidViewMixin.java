package com.solace.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import snownee.jade.api.view.FluidView;

@Mixin(FluidView.class)
public class FluidViewMixin {

    @Inject(method = "readDefault", at = @At("RETURN"), remap = false)
    private static void solace$readDefault(CompoundTag tag, CallbackInfoReturnable<FluidView> cir) {
        FluidView view = cir.getReturnValue();
        if (view != null && tag.contains("solace$overrideText", 8)) { // 8 is StringTag
            try {
                Component override = Component.Serializer.fromJson(tag.getString("solace$overrideText"));
                if (override != null) {
                    view.overrideText = override;
                }
            } catch (Exception e) {
                // Ignore parsing errors
            }
        }
    }
}
