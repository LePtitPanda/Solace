package com.solace.audit;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraft.client.Minecraft;
import org.lwjgl.opengl.GL11;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Mod.EventBusSubscriber(modid = "solace", value = Dist.CLIENT)
public final class AuditClient {
    private static long nextSnapshot;
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        AuditControl.initialize();
        if (!AuditSession.active() || System.nanoTime() < nextSnapshot) return;
        nextSnapshot = System.nanoTime() + 5_000_000_000L;
        Minecraft mc = Minecraft.getInstance();
        Map<String, Object> scene = new LinkedHashMap<>();
        scene.put("utc", Instant.now().toString()); scene.put("paused", mc.isPaused());
        scene.put("noRender", mc.noRender); scene.put("windowActive", mc.isWindowActive());
        scene.put("screen", mc.screen == null ? "game" : mc.screen.getClass().getName());
        scene.put("renderDistance", mc.options.renderDistance().get()); scene.put("simulationDistance", mc.options.simulationDistance().get());
        scene.put("entityDistance", mc.options.entityDistanceScaling().get()); scene.put("particles", mc.options.particles().get().toString());
        scene.put("resolution", mc.getWindow().getWidth() + "x" + mc.getWindow().getHeight());
        scene.put("vsync", mc.options.enableVsync().get()); scene.put("frameLimit", mc.options.framerateLimit().get());
        scene.put("gpu", GL11.glGetString(GL11.GL_RENDERER));
        if (mc.level != null) { scene.put("dimension", mc.level.dimension().location().toString()); scene.put("particlesLoaded", mc.particleEngine.countParticles()); }
        if (mc.player != null) { scene.put("position", mc.player.blockPosition().toString()); scene.put("yaw", mc.player.getYRot()); scene.put("pitch", mc.player.getXRot()); }
        AuditSession.scene(scene);
    }
}
