package com.solace.mixin;

import com.solace.access.BookChoiceLevelsAccess;
import net.backupcup.mcde.util.EnchantmentSlot;
import net.backupcup.mcde.util.SlotPosition;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.backupcup.mcde.util.Choice", remap = false)
public class ChoiceMixin {
    @Shadow(remap = false)
    @Final
    private SlotPosition choicePos;

    @Shadow(remap = false)
    @Final
    private EnchantmentSlot enchantmentSlot;

    @Inject(method = "getLevel", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$showBookshelfBookLevel(CallbackInfoReturnable<Integer> cir) {
        int level = ((BookChoiceLevelsAccess) enchantmentSlot).solace$getChoiceLevel(choicePos);
        if (level > 0) {
            cir.setReturnValue(level);
        }
    }
}
