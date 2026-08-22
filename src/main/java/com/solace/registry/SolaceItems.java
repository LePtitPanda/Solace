package com.solace.registry;

import com.solace.Solace;
import com.solace.item.IdentifiedArtifactItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class SolaceItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, Solace.MOD_ID);

    public static final RegistryObject<Item> IDENTIFIED_ARTIFACT = ITEMS.register(
            "identified_artifact",
            () -> new IdentifiedArtifactItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON))
    );

    private SolaceItems() {
    }
}
