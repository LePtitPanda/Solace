package com.solace.compat.jei;

import io.redspace.ironsspellbooks.registries.ItemRegistry;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.world.item.crafting.CraftingRecipe;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

@JeiPlugin
public class SolaceJeiPlugin implements IModPlugin {
    private static final ResourceLocation UID = new ResourceLocation("solace", "jei_plugin");
    private static IJeiRuntime runtime;

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
        applyRecipeLocks(java.util.Set.of(), com.solace.client.quests.ClientRecipeLocks.locked);
    }

    @Override
    public void onRuntimeUnavailable() {
        runtime = null;
    }

    /** Recipe locks: hide newly locked crafting recipes, show unlocked ones again. */
    public static void applyRecipeLocks(java.util.Set<ResourceLocation> before, java.util.Set<ResourceLocation> now) {
        net.minecraft.client.multiplayer.ClientLevel level = net.minecraft.client.Minecraft.getInstance().level;
        if (runtime == null || level == null) {
            return;
        }
        java.util.List<CraftingRecipe> hide = new java.util.ArrayList<>(), show = new java.util.ArrayList<>();
        for (ResourceLocation id : now) {
            level.getRecipeManager().byKey(id).ifPresent(recipe -> {
                if (recipe instanceof CraftingRecipe crafting) {
                    hide.add(crafting);
                }
            });
        }
        for (ResourceLocation id : before) {
            if (!now.contains(id)) {
                level.getRecipeManager().byKey(id).ifPresent(recipe -> {
                    if (recipe instanceof CraftingRecipe crafting) {
                        show.add(crafting);
                    }
                });
            }
        }
        if (!show.isEmpty()) {
            runtime.getRecipeManager().unhideRecipes(RecipeTypes.CRAFTING, show);
        }
        if (!hide.isEmpty()) {
            runtime.getRecipeManager().hideRecipes(RecipeTypes.CRAFTING, hide);
        }
    }

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
