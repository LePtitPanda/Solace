package com.solace.mixin;

import com.solace.world.ArtifactEnchantingSupport;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "io.redspace.ironsspellbooks.gui.arcane_anvil.ArcaneAnvilMenu", remap = false)
public class ArcaneAnvilMenuMixin {
    @Inject(method = "m_6640_", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$replaceArcaneAnvilRecipes(CallbackInfo ci) {
        ItemCombinerMenuAccessor accessor = (ItemCombinerMenuAccessor) this;
        Container input = accessor.solace$getInputSlots();
        ItemStack output = ArtifactEnchantingSupport.arcaneAnvilResult(input.getItem(0), input.getItem(1));
        accessor.solace$getResultSlots().setItem(0, output);
        ci.cancel();
    }
}
