package com.solace.client;

import com.solace.Solace;
import com.solace.world.DreamDimension;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.ProgressScreen;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Waking from a dream: the screen goes black the moment the player leaves the dream
 * dimension, stays black through the loading screen and a short hold, then slowly fades
 * back in. Drawn above all UI by GameRendererWakeFadeMixin, or by the event fallbacks below.
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class WakeFadeClient {
    private static final long HOLD_MILLIS = 1500L;
    private static final long FADE_MILLIS = 5000L;

    private static ResourceKey<Level> lastDimension;
    private static boolean active;
    private static long fadeStart = -1L;

    private WakeFadeClient() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ResourceKey<Level> dimension = minecraft.level == null ? null : minecraft.level.dimension();
        if (dimension != null && lastDimension == DreamDimension.LEVEL && dimension != DreamDimension.LEVEL) {
            active = true;
            fadeStart = -1L;
        }
        if (dimension != null) {
            lastDimension = dimension;
        }
        // The hold starts once the new world is actually on screen, not while it loads.
        if (active && fadeStart < 0L && minecraft.player != null && !isLoadingScreen(minecraft)) {
            fadeStart = Util.getMillis() + HOLD_MILLIS;
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        lastDimension = null;
        active = false;
        fadeStart = -1L;
    }

    private static boolean isLoadingScreen(Minecraft minecraft) {
        return minecraft.screen instanceof ReceivingLevelScreen || minecraft.screen instanceof LevelLoadingScreen
                || minecraft.screen instanceof ProgressScreen;
    }

    /** Current black overlay opacity, 1 = fully black. */
    static float opacity() {
        if (!active) {
            return 0.0F;
        }
        if (fadeStart < 0L) {
            return 1.0F;
        }
        long elapsed = Util.getMillis() - fadeStart;
        if (elapsed <= 0L) {
            return 1.0F;
        }
        float t = Mth.clamp(elapsed / (float) FADE_MILLIS, 0.0F, 1.0F);
        if (t >= 1.0F) {
            active = false;
            return 0.0F;
        }
        // Ease in-out, so the world surfaces gently and settles.
        float eased = t * t * (3.0F - 2.0F * t);
        return 1.0F - eased;
    }

    /** True once the GameRenderer mixin has run, which means the event fallbacks stay off. */
    private static boolean mixinApplied;

    /** Called after all GUI, screens and toasts are flushed for the frame. */
    public static void renderFromMixin() {
        mixinApplied = true;
        Minecraft minecraft = Minecraft.getInstance();
        drawOverlay(new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource()), true);
    }

    // Fallbacks if the mixin could not apply: over the screen when one is open, else over the HUD.
    // These cover everything except toasts.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        if (!mixinApplied) {
            drawOverlay(event.getGuiGraphics(), false);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onHudRender(RenderGuiEvent.Post event) {
        if (!mixinApplied && Minecraft.getInstance().screen == null) {
            drawOverlay(event.getGuiGraphics(), false);
        }
    }

    private static void drawOverlay(GuiGraphics graphics, boolean flush) {
        float opacity = opacity();
        if (opacity <= 0.0F) {
            return;
        }
        graphics.pose().pushPose();
        // In front of everything drawn this frame; the GUI projection's near plane allows up to z 1000.
        graphics.pose().translate(0.0F, 0.0F, 950.0F);
        graphics.fill(0, 0, graphics.guiWidth(), graphics.guiHeight(), Math.round(opacity * 255.0F) << 24);
        graphics.pose().popPose();
        if (flush) {
            graphics.flush();
        }
    }
}
