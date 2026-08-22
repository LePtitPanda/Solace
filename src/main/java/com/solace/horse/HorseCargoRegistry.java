package com.solace.horse;

import com.solace.Solace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegisterEvent;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HorseCargoRegistry {
    public static final ResourceLocation SACK_CAPACITY_ID = new ResourceLocation(Solace.MOD_ID, "sack_capacity");
    public static final ResourceLocation MENU_ID = new ResourceLocation(Solace.MOD_ID, "horse_cargo");

    public static Attribute SACK_CAPACITY;
    public static MenuType<HorseCargoMenu> HORSE_CARGO_MENU;

    private HorseCargoRegistry() {
    }

    @SubscribeEvent
    public static void register(RegisterEvent event) {
        event.register(ForgeRegistries.Keys.ATTRIBUTES, helper -> {
            SACK_CAPACITY = new RangedAttribute("attribute.name.solace.sack_capacity", 0.0D, 0.0D, 6.0D)
                    .setSyncable(true);
            helper.register(SACK_CAPACITY_ID, SACK_CAPACITY);
        });
        event.register(ForgeRegistries.Keys.MENU_TYPES, helper -> {
            HORSE_CARGO_MENU = IForgeMenuType.create(HorseCargoMenu::new);
            helper.register(MENU_ID, HORSE_CARGO_MENU);
        });
    }

    @SubscribeEvent
    public static void addHorseAttribute(EntityAttributeModificationEvent event) {
        event.add(EntityType.HORSE, SACK_CAPACITY);
    }
}
