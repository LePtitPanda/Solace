package com.solace.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    @Shadow
    public abstract String getDescriptionId();

    @Shadow
    public abstract int getMaxLevel();

    @Shadow
    public abstract boolean isCurse();

    @Inject(method = "getFullname", at = @At("HEAD"), cancellable = true)
    private void solace$hideBaseLevelSuffix(int level, CallbackInfoReturnable<Component> cir) {
        if (level == 1 && getMaxLevel() > 1) {
            cir.setReturnValue(Component.translatable(getDescriptionId())
                    .withStyle(isCurse() ? ChatFormatting.RED : ChatFormatting.GRAY));
        }
    }
}
