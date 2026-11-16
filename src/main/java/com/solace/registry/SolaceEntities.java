package com.solace.registry;

import com.solace.Solace;
import com.solace.entity.Guildmaster;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SolaceEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, Solace.MOD_ID);

    public static final RegistryObject<EntityType<Guildmaster>> GUILDMASTER = ENTITY_TYPES.register("guildmaster",
            () -> EntityType.Builder.<Guildmaster>of(Guildmaster::new, MobCategory.MISC)
                    .sized(0.6F, 1.95F).clientTrackingRange(10).build("guildmaster"));

    @SubscribeEvent
    public static void attributes(EntityAttributeCreationEvent event) {
        event.put(GUILDMASTER.get(), Guildmaster.createAttributes().build());
    }

    private SolaceEntities() {
    }
}
