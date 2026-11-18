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

    /** Opens EMI's recipes (or uses) for a required item clicked in the quest book. */
    public static void show(net.minecraft.world.item.ItemStack stack, boolean uses) {
        // EMI only opens over a container screen and goes back to it; from anything else it opens the inventory
        // (which Better Inventory swaps for its own, so no recipes). Give it a stand-in that reopens the journal.
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        BackTo back = new BackTo(mc.screen);
        mc.setScreen(back);
        if (uses) dev.emi.emi.api.EmiApi.displayUses(EmiStack.of(stack));
        else dev.emi.emi.api.EmiApi.displayRecipes(EmiStack.of(stack));
        back.armed = true;
        if (mc.screen == back) mc.setScreen(back.screen); // nothing to show
    }

    /** Invisible container screen: when EMI returns to it, it opens {@link #screen} instead. */
    private static final class BackTo extends net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<net.minecraft.world.inventory.InventoryMenu> {
        final net.minecraft.client.gui.screens.Screen screen;
        boolean armed;

        BackTo(net.minecraft.client.gui.screens.Screen screen) {
            super(net.minecraft.client.Minecraft.getInstance().player.inventoryMenu,
                    net.minecraft.client.Minecraft.getInstance().player.getInventory(), Component.empty());
            this.screen = screen;
        }

        @Override
        protected void init() {
            if (armed) minecraft.setScreen(screen);
            else super.init();
        }

        @Override
        protected void renderBg(net.minecraft.client.gui.GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        }
    }

    @Override
    public void register(EmiRegistry registry) {
        List<EmiIngredient> ingredients = List.of(EmiStack.of(ItemRegistry.ALCHEMIST_CAULDRON_BLOCK_ITEM.get()));
        List<Component> pages = List.of(Component.translatable("solace.jei.alchemist_cauldron.info"));
        registry.addRecipe(new EmiInfoRecipe(ingredients, pages, UID));
        // Recipe locks: recipes this player has not learned yet (re-run when the server sends a change).
        registry.removeRecipes(recipe -> recipe.getId() != null
                && com.solace.client.quests.ClientRecipeLocks.locked.contains(recipe.getId())
                || !com.solace.client.quests.ClientRecipeLocks.items.isEmpty() && recipe.getOutputs().stream()
                .anyMatch(output -> com.solace.client.quests.ClientRecipeLocks.items.contains(output.getId())));
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
