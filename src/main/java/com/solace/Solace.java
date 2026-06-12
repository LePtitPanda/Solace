package com.solace;

import com.solace.events.CommonEvents;
import com.solace.config.VillageBuildingSpawnConfig;
import net.minecraftforge.common.MinecraftForge;
import net.kyrptonaught.customportalapi.api.CustomPortalBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.common.brewing.BrewingRecipeRegistry;
import net.minecraftforge.common.brewing.IBrewingRecipe;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLLoadCompleteEvent;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Mod("solace")
public class Solace {

    public static final String MOD_ID = "solace";
    private static final Logger LOGGER = LogManager.getLogger();

    public Solace() {
        LOGGER.info("[Solace] Mod Constructor Called");
        MinecraftForge.EVENT_BUS.register(CommonEvents.class);
    }

    @Mod.EventBusSubscriber(modid = MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static class ModEventSubscriber {

        @SubscribeEvent
        public static void onCommonSetup(FMLCommonSetupEvent event) {
            LOGGER.info("[Solace] FMLCommonSetupEvent Fired!");
            VillageBuildingSpawnConfig.init();

            event.enqueueWork(() -> {
                // Register custom portal
                LOGGER.info("[Solace] Registering Custom Portal...");
                try {
                    Block bleedingObsidian = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("rubinated_nether", "bleeding_obsidian"));
                    if (bleedingObsidian == null) {
                        LOGGER.error("[Solace] ERROR: Bleeding Obsidian block not found! Is Rubinated Nether installed?");
                        return;
                    }

                    Item forgotten_key = ForgeRegistries.ITEMS.getValue(new ResourceLocation("kubejs", "forgotten_key"));
                    if (forgotten_key == null) {
                        LOGGER.error("[Solace] ERROR: Forgotten Key item not found! Is KubeJS installed and configured?");
                        return;
                    }

                    CustomPortalBuilder.beginPortal()
                            .frameBlock(bleedingObsidian)
                            .destDimID(new ResourceLocation("undergarden", "undergarden"))
                            .returnDim(new ResourceLocation("ad_astra", "moon"), true)
                            .lightWithItem(forgotten_key)
                            .tintColor(130, 0, 0)
                            .registerPortal();

                    LOGGER.info("[Solace] Portal Successfully Registered!");
                } catch (Exception e) {
                    LOGGER.error("[Solace] Portal Registration Failed!", e);
                }
            });
        }

        @SubscribeEvent(priority = EventPriority.LOWEST)
        public static void onLoadComplete(FMLLoadCompleteEvent event) {
            event.enqueueWork(Solace::removeUnwantedBrewingRecipes);
        }

    }

    private static void removeUnwantedBrewingRecipes() {
        try {
            Field recipesField = BrewingRecipeRegistry.class.getDeclaredField("recipes");
            recipesField.setAccessible(true);

            @SuppressWarnings("unchecked")
            List<IBrewingRecipe> recipes = (List<IBrewingRecipe>) recipesField.get(null);

            int before = recipes.size();
            Map<String, Integer> removedByClass = new HashMap<>();
            recipes.removeIf(recipe -> {
                if (!shouldRemoveBrewingRecipe(recipe)) {
                    return false;
                }
                String recipeClass = recipe.getClass().getName();
                removedByClass.merge(recipeClass, 1, Integer::sum);
                return true;
            });

            if (before != recipes.size()) {
                LOGGER.info("[Solace] Removed {} custom brewing recipes; {} remain.",
                        before - recipes.size(), recipes.size());
                removedByClass.forEach((recipeClass, count) ->
                        LOGGER.info("[Solace] Removed {} brewing recipes from {}", count, recipeClass));
            } else {
                LOGGER.info("[Solace] No custom brewing recipes needed removal.");
            }
        } catch (Exception e) {
            LOGGER.error("[Solace] Failed to remove custom brewing recipes.", e);
        }
    }

    private static boolean shouldRemoveBrewingRecipe(IBrewingRecipe recipe) {
        String recipeClass = recipe.getClass().getName();
        if (recipeClass.equals("net.minecraftforge.common.brewing.VanillaBrewingRecipe")) {
            return false;
        }
        return !recipeClass.startsWith("dev.obscuria.elixirum.");
    }
}
