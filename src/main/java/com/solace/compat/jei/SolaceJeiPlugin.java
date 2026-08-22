package com.solace.compat.jei;

import io.redspace.ironsspellbooks.registries.ItemRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class SolaceJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation("solace", "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(
            new ItemStack(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("solace.jei.alchemist_cauldron.info")
        );
        registration.addIngredientInfo(
            new ItemStack(ItemRegistry.ACANE_ANVIL_BLOCK_ITEM.get()),
            VanillaTypes.ITEM_STACK,
            Component.translatable("solace.jei.arcane_anvil.info")
        );
    }
}
