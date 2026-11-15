package com.solace.client;

import com.solace.Solace;
import com.solace.world.DreamDimension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Dream sky (black, no sun, moon, stars, clouds or fog) and the hidden health and hunger bars. */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DreamClient {

    // Highest so OverflowingBars and AppleSkin, which draw through these overlays, are skipped too.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void hideBars(RenderGuiOverlayEvent.Pre event) {
        ResourceLocation id = event.getOverlay().id();
        Minecraft mc = Minecraft.getInstance();
        if ((id.equals(VanillaGuiOverlay.PLAYER_HEALTH.id()) || id.equals(VanillaGuiOverlay.FOOD_LEVEL.id()))
                && mc.level != null && DreamDimension.isDream(mc.level)) {
            event.setCanceled(true);
        }
    }

    @Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        public static void registerEffects(RegisterDimensionSpecialEffectsEvent event) {
            // No clouds, no sky pass, nether-style flat face shading.
            event.register(DreamDimension.ID, new DimensionSpecialEffects(Float.NaN, true, DimensionSpecialEffects.SkyType.NONE, false, true) {
                @Override
                public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
                    return Vec3.ZERO;
                }

                @Override
                public boolean isFoggyAt(int x, int y) {
                    return false;
                }

                @Override
                public float[] getSunriseColor(float timeOfDay, float partialTicks) {
                    return null;
                }
            });
        }

        private Setup() {
        }
    }

    private DreamClient() {
    }
}
