package com.solace.compat.terrainslabs;

import com.mojang.logging.LogUtils;
import com.solace.Solace;
import net.countered.terrainslabs.block.ModSlabsMap;
import net.countered.terrainslabs.block.customslabs.specialslabs.CustomSlab;
import net.countered.terrainslabs.block.customslabs.specialslabs.GravityAffectedSlab;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class TerrainSlabsContent {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, Solace.MOD_ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Solace.MOD_ID);
    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        // Ground blocks placed by modded overworld surface rules that have no Terrain Slabs variant.
        // Biomes that surface with vanilla grass/dirt/sand/stone are already covered by the mod itself.
        add("sandy_soil", "natures_spirit:sandy_soil", false, Blocks.COARSE_DIRT);
        add("pink_sand", "natures_spirit:pink_sand", true, Blocks.SAND);
        add("pink_sandstone", "natures_spirit:pink_sandstone", false, Blocks.SANDSTONE);
        add("red_moss", "natures_spirit:red_moss_block", false, Blocks.MOSS_BLOCK);
        add("white_kaolin", "natures_spirit:white_kaolin", false, Blocks.TERRACOTTA);
        add("white_chalk", "natures_spirit:white_chalk", false, Blocks.CALCITE);
        add("travertine", "natures_spirit:travertine", false, Blocks.STONE);
        add("chert", "natures_spirit:chert", false, Blocks.STONE);
        add("algal_sand", "spawn:algal_sand", true, Blocks.SAND);
    }

    private TerrainSlabsContent() {
    }

    static void register(IEventBus modBus) {
        BLOCKS.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener(TerrainSlabsContent::onCommonSetup);
    }

    private static void add(String name, String base, boolean gravity, Block fallback) {
        ResourceLocation baseId = new ResourceLocation(base);
        if (!ModList.get().isLoaded(baseId.getNamespace())) {
            return;
        }
        String id = name + "_terrain_slab";
        RegistryObject<Block> slab = BLOCKS.register(id, () -> {
            BlockBehaviour.Properties properties = BlockBehaviour.Properties.copy(baseOrFallback(baseId, fallback));
            return gravity ? new GravityAffectedSlab(properties) : new CustomSlab(properties);
        });
        ITEMS.register(id, () -> new BlockItem(slab.get(), new Item.Properties()));
        ENTRIES.add(new Entry(baseId, slab));
    }

    private static Block baseOrFallback(ResourceLocation baseId, Block fallback) {
        // mods.toml orders Solace after the biome mods, so their blocks exist by now.
        Block base = ForgeRegistries.BLOCKS.containsKey(baseId) ? ForgeRegistries.BLOCKS.getValue(baseId) : null;
        if (base == null || base == Blocks.AIR) {
            LOGGER.warn("[Solace] Terrain slab base {} was not registered yet; using vanilla block properties.", baseId);
            return fallback;
        }
        return base;
    }

    private static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(TerrainSlabsContent::injectSlabMap);
    }

    @SuppressWarnings("unchecked")
    private static void injectSlabMap() {
        Map<Block, Block> slabMap;
        try {
            Field field = ModSlabsMap.class.getDeclaredField("SLAB_MAP");
            field.setAccessible(true);
            slabMap = (Map<Block, Block>) field.get(null);
        } catch (ReflectiveOperationException | ClassCastException exception) {
            LOGGER.error("[Solace] Could not reach Terrain Slabs' slab map; modded terrain slabs will not generate.", exception);
            return;
        }

        int added = 0;
        for (Entry entry : ENTRIES) {
            Block base = ForgeRegistries.BLOCKS.getValue(entry.base());
            if (base == null || base == Blocks.AIR || !entry.slab().isPresent()) {
                continue;
            }
            if (slabMap.putIfAbsent(base, entry.slab().get()) == null) {
                added++;
            }
        }
        LOGGER.info("[Solace] Added {} modded ground blocks to Terrain Slabs generation.", added);
    }

    private record Entry(ResourceLocation base, RegistryObject<Block> slab) {
    }
}
