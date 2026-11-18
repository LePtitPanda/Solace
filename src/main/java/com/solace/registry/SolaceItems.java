package com.solace.registry;

import com.solace.Solace;
import com.solace.item.IdentifiedArtifactItem;
import com.solace.item.WoodenHammerItem;
import com.solace.item.LootTableEditorItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SolaceItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Solace.MOD_ID);

    public static final RegistryObject<Item> IDENTIFIED_ARTIFACT = ITEMS.register(
            "identified_artifact",
            () -> new IdentifiedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final RegistryObject<Item> WOODEN_HAMMER = ITEMS.register(
            "wooden_hammer",
            () -> new WoodenHammerItem(new Item.Properties())
    );

    public static final RegistryObject<Item> LOOT_TABLE_EDITOR = ITEMS.register(
            "loot_table_editor",
            () -> new LootTableEditorItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC))
    );

    public static final RegistryObject<Item> KNOWLEDGE_BOOK = ITEMS.register(
            "knowledge_book",
            () -> new com.solace.item.KnowledgeBookItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    public static final RegistryObject<Item> GUILDMASTER_SPAWN_EGG = ITEMS.register(
            "guildmaster_spawn_egg",
            () -> new ForgeSpawnEggItem(SolaceEntities.GUILDMASTER, 0x5E3B26, 0xC9A227, new Item.Properties())
    );

    @SubscribeEvent
    public static void addCreativeTabItems(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(WOODEN_HAMMER);
            event.accept(LOOT_TABLE_EDITOR);
        }
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(GUILDMASTER_SPAWN_EGG);
        }
    }

    private SolaceItems() {
    }
}
