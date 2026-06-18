package com.solace.compat.jade;

import com.solace.ElixirumCauldronHolder;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import dev.obscuria.elixirum.api.alchemy.AlchemyRecipe;
import dev.obscuria.elixirum.server.alchemy.brewing.IngredientMixer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class AlchemistCauldronJadeProvider implements IBlockComponentProvider {
    public static final ResourceLocation UID = new ResourceLocation("solace", "alchemist_cauldron_ingredients");

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockEntity blockEntity = accessor.getBlockEntity();
        if (blockEntity instanceof AlchemistCauldronTile cauldronTile && blockEntity instanceof ElixirumCauldronHolder holder) {
            // Check if cauldron has water
            boolean hasWater = false;
            for (FluidStack fluid : cauldronTile.fluidInventory.fluids()) {
                if (fluid.getFluid() == Fluids.WATER && fluid.getAmount() > 0) {
                    hasWater = true;
                    break;
                }
            }
            if (!hasWater) {
                return;
            }

            IngredientMixer mixer = (IngredientMixer) holder.solace$getElixirumMixer();
            if (mixer != null && !mixer.isEmpty()) {
                if (mixer.isComplete()) {
                    return;
                } else {
                    tooltip.add(Component.translatable("ui.elixirum.details.ingredients").withStyle(ChatFormatting.GOLD).withStyle(ChatFormatting.UNDERLINE));
                    
                    AlchemyRecipe recipe = mixer.recipe();
                    
                    // Base
                    if (recipe.base().isPresent()) {
                        Component baseName = recipe.base().get().asStack().getHoverName();
                        tooltip.add(Component.translatable("solace.jade.cauldron.base", baseName).withStyle(ChatFormatting.GREEN));
                    } else {
                        tooltip.add(Component.translatable("solace.jade.cauldron.base_missing").withStyle(ChatFormatting.RED));
                    }
                    
                    // Catalyst
                    if (recipe.catalyst().isPresent()) {
                        Component catalystName = recipe.catalyst().get().asStack().getHoverName();
                        tooltip.add(Component.translatable("solace.jade.cauldron.catalyst", catalystName).withStyle(ChatFormatting.GREEN));
                    } else {
                        tooltip.add(Component.translatable("solace.jade.cauldron.catalyst_missing").withStyle(ChatFormatting.RED));
                    }
                    
                    // Inhibitor
                    if (recipe.inhibitor().isPresent()) {
                        Component inhibitorName = recipe.inhibitor().get().asStack().getHoverName();
                        tooltip.add(Component.translatable("solace.jade.cauldron.inhibitor", inhibitorName).withStyle(ChatFormatting.GREEN));
                    } else {
                        tooltip.add(Component.translatable("solace.jade.cauldron.inhibitor_missing").withStyle(ChatFormatting.RED));
                    }
                }
            }
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
