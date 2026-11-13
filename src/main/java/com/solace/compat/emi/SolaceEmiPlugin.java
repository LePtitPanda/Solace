package com.solace.compat.emi;

import io.redspace.ironsspellbooks.registries.ItemRegistry;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiInfoRecipe;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import java.util.List;

@EmiEntrypoint
public class SolaceEmiPlugin implements EmiPlugin {
    private static final ResourceLocation UID = new ResourceLocation("solace", "alchemist_cauldron_info");
    private static final ResourceLocation ARCANE_ANVIL_INFO = new ResourceLocation("solace", "arcane_anvil_info");

    @Override
    public void register(EmiRegistry registry) {
        List<EmiIngredient> ingredients = List.of(EmiStack.of(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get()));
        List<Component> pages = List.of(Component.translatable("solace.jei.alchemist_cauldron.info"));
        registry.addRecipe(new EmiInfoRecipe(ingredients, pages, UID));
        // Recipe locks: recipes this player has not learned yet (re-run when the server sends a change).
        registry.removeRecipes(recipe -> recipe.getId() != null
                && com.solace.client.quests.ClientRecipeLocks.locked.contains(recipe.getId()));
        registry.removeRecipes(recipe -> {
            ResourceLocation category = recipe.getCategory().getId();
            return category.getNamespace().equals("irons_spellbooks") && category.getPath().contains("arcane_anvil");
        });
        registry.addRecipe(new EmiInfoRecipe(
                List.of(EmiStack.of(ItemRegistry.ACANE_ANVIL_BLOCK_ITEM.get())),
                List.of(Component.translatable("solace.jei.arcane_anvil.info")),
                ARCANE_ANVIL_INFO
        ));
    }
}
