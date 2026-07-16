package com.solace.mixin;

import com.solace.performance.VMinusIngredientFilterCache;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Pseudo
@Mixin(targets = "net.lixir.vminus.vision.util.VisionUtils", remap = false)
public abstract class VMinusVisionUtilsMixin {
    @Inject(
            method = "filterIngredient(Lnet/minecraft/world/item/ItemStack;Ljava/util/List;)V",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void solace$useCachedIngredientFilter(
            ItemStack stack,
            List<ItemStack> output,
            CallbackInfo ci
    ) {
        VMinusIngredientFilterCache.filter(stack, output);
        ci.cancel();
    }
}
