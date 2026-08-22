package com.solace.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "io.github.rcneg.compositematerial.common.tier.ArmorTier", remap = false)
public class CompositeMaterialArmorTierMixin {
    private static final ResourceLocation TIN_INGOT = new ResourceLocation("unify", "tin_ingot");

    @Inject(method = "m_6230_", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$useTinToRepairCopperArmor(CallbackInfoReturnable<Ingredient> cir) {
        if (!((Object) this instanceof Enum<?> tier) || !"COPPER".equals(tier.name())) {
            return;
        }

        Item tinIngot = ForgeRegistries.ITEMS.getValue(TIN_INGOT);
        if (tinIngot != null && tinIngot != Items.AIR) {
            cir.setReturnValue(Ingredient.of(tinIngot));
        }
    }
}
