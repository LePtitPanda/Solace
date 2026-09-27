package com.solace.menu;

import com.solace.Solace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LootEditorRegistry {
    public static final ResourceLocation MENU_ID = new ResourceLocation(Solace.MOD_ID, "loot_editor");
    public static MenuType<LootEditorMenu> MENU;

    private LootEditorRegistry() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.MENU_TYPES, helper -> {
            MENU = IForgeMenuType.create(LootEditorMenu::new);
            helper.register(MENU_ID, MENU);
        });
    }
}
