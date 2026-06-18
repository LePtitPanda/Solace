package com.solace.mixin;

import com.solace.ElixirumCauldronHolder;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronRenderer;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AlchemistCauldronRenderer.class)
public class AlchemistCauldronRendererMixin {

    @Redirect(
        method = "renderWater",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/extensions/common/IClientFluidTypeExtensions;getTintColor(Lnet/minecraftforge/fluids/FluidStack;)I"
        ),
        remap = false
    )
    private int solace$overrideTintColor(IClientFluidTypeExtensions instance, FluidStack fluidStack, AlchemistCauldronTile cauldronTile) {
        if (cauldronTile instanceof ElixirumCauldronHolder holder) {
            if (holder.solace$hasElixirumRecipe()) {
                return holder.solace$getElixirumColor();
            }
        }
        return instance.getTintColor(fluidStack);
    }

    @Redirect(
        method = "renderWater",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraftforge/client/extensions/common/IClientFluidTypeExtensions;getTintColor(Lnet/minecraft/world/level/material/FluidState;Lnet/minecraft/world/level/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;)I"
        ),
        remap = false
    )
    private int solace$overrideBiomeTintColor(IClientFluidTypeExtensions instance, net.minecraft.world.level.material.FluidState fluidState, net.minecraft.world.level.BlockAndTintGetter blockAndTintGetter, net.minecraft.core.BlockPos blockPos, AlchemistCauldronTile cauldronTile) {
        if (cauldronTile instanceof ElixirumCauldronHolder holder) {
            if (holder.solace$hasElixirumRecipe()) {
                return 0xFFFFFFFF; // No biome color overlay!
            }
        }
        return instance.getTintColor(fluidState, blockAndTintGetter, blockPos);
    }
}
