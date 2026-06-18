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

    @Override
    public void register(EmiRegistry registry) {
        List<EmiIngredient> ingredients = List.of(EmiStack.of(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get()));
        List<Component> pages = List.of(Component.translatable("solace.jei.alchemist_cauldron.info"));
        registry.addRecipe(new EmiInfoRecipe(ingredients, pages, UID));
    }
}
