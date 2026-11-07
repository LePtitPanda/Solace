package com.solace.performancechecks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.solace.compat.performance.ClientPerformancePatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Arrays;
import java.util.jar.JarFile;

/** Manual controlled-scene measurements; test output is never in a normal build. */
@Mod.EventBusSubscriber(modid = "solace", value = Dist.CLIENT)
public final class BenchmarkSession {
    public static volatile boolean freezeMusic;
    private static final com.sun.management.ThreadMXBean MX = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final long[] FRAMES = new long[300_000];
    private static boolean active;
    private static String label, savedSong;
    private static int count;
    private static long last, started, deadline, cpuStart, allocatedStart, renderThreadId, musicThreadId, musicCpuStart;

    private BenchmarkSession() { }

    @SubscribeEvent
    public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("solacebench")
                .then(Commands.literal("prepare").executes(context -> action(() -> prepare())))
                .then(Commands.literal("status").executes(context -> action(() -> write("status", status()))))
                .then(Commands.literal("musiccheck").executes(context -> action(() -> musicCheck()))
                        .then(Commands.argument("song", StringArgumentType.word()).executes(context -> action(() -> {
                            savedSong = StringArgumentType.getString(context, "song"); musicCheck();
                        }))))
                .then(Commands.literal("capture").then(Commands.argument("label", StringArgumentType.word())
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 120)).executes(context -> action(() ->
                                start(StringArgumentType.getString(context, "label"), IntegerArgumentType.getInteger(context, "seconds")))))))
                .then(Commands.literal("screenshot").then(Commands.argument("name", StringArgumentType.word()).executes(context -> action(() -> {
                    Minecraft mc = Minecraft.getInstance();
                    Screenshot.grab(mc.gameDirectory, StringArgumentType.getString(context, "name") + ".png", mc.getMainRenderTarget(), ignored -> { });
                }))))
                .then(Commands.literal("release").executes(context -> action(() -> {
                    freezeMusic = false;
                    if (savedSong != null) musicThread().getClass().getMethod("play", String.class).invoke(musicThread(), savedSong);
                }))));
    }

    private static int action(CheckedRunnable runnable) {
        try { runnable.run(); return 1; }
        catch (Throwable exception) {
            exception.printStackTrace();
            Minecraft.getInstance().player.displayClientMessage(Component.literal("Benchmark failed: " + exception), false);
            return 0;
        }
    }

    private static Path directory() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("local/embeddium-performance-stage2-2026-10-01");
    }

    private static void write(String name, JsonObject data) throws Exception {
        Files.writeString(directory().resolve(name + ".json"), new GsonBuilder().setPrettyPrinting().create().toJson(data));
    }

    private static Thread musicThread() throws Exception {
        return (Thread) Class.forName("circuitlord.reactivemusic.ReactiveMusic").getField("thread").get(null);
    }

    private static Object get(Object object, String fieldName) throws Exception {
        Field field = object.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(object);
    }

    private static void prepare() throws Exception {
        if (active) throw new IllegalStateException("Capture active");
        freezeMusic = true;
        Class<?> player = Class.forName("circuitlord.reactivemusic.PlayerThread");
        String song = (String) player.getField("currentSong").get(null);
        if (song != null) savedSong = song;
        musicThread().getClass().getMethod("resetPlayer").invoke(musicThread());
        if (!MX.isThreadCpuTimeEnabled()) MX.setThreadCpuTimeEnabled(true);
        if (!MX.isThreadAllocatedMemoryEnabled()) MX.setThreadAllocatedMemoryEnabled(true);
        write("prepared", status());
    }

    private static JsonObject status() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        JsonObject data = new JsonObject();
        data.addProperty("timestamp", Instant.now().toString());
        data.addProperty("bannerIndex", ClientPerformancePatches.bannerIndex());
        data.addProperty("gliderBlockGate", ClientPerformancePatches.gliderBlockGate());
        data.addProperty("sheepVariantRead", ClientPerformancePatches.sheepVariantRead());
        data.addProperty("lightCacheMissPositions", ClientPerformancePatches.lightCacheMissPositions());
        data.addProperty("musicIdleWait", ClientPerformancePatches.musicIdleWait());
        data.addProperty("freezeMusic", freezeMusic);
        data.addProperty("active", active);
        data.addProperty("flywheel", Class.forName("dev.engine_room.flywheel.impl.BackendManagerImpl").getMethod("getBackendString").invoke(null).toString());
        data.addProperty("renderDistance", mc.options.renderDistance().get());
        data.addProperty("simulationDistance", mc.options.simulationDistance().get());
        data.addProperty("width", mc.getWindow().getWidth());
        data.addProperty("height", mc.getWindow().getHeight());
        data.addProperty("vsync", mc.options.enableVsync().get());
        data.addProperty("fpsLimit", mc.options.framerateLimit().get());
        data.addProperty("savedSong", savedSong);
        data.addProperty("musicState", musicThread().getState().toString());
        data.addProperty("x", mc.player.getX()); data.addProperty("y", mc.player.getY()); data.addProperty("z", mc.player.getZ());
        data.addProperty("yaw", mc.player.getYRot()); data.addProperty("pitch", mc.player.getXRot());
        return data;
    }

    private static void start(String name, int seconds) throws Exception {
        if (active || !freezeMusic) throw new IllegalStateException("Prepare first; one capture at a time");
        label = name; count = 0; last = 0;
        renderThreadId = Thread.currentThread().getId();
        musicThreadId = musicThread().getId();
        cpuStart = MX.getThreadCpuTime(renderThreadId);
        allocatedStart = MX.getThreadAllocatedBytes(renderThreadId);
        musicCpuStart = MX.getThreadCpuTime(musicThreadId);
        started = System.nanoTime(); deadline = started + seconds * 1_000_000_000L;
        active = true;
    }

    public static void completedFrame() {
        if (!active) return;
        long now = System.nanoTime();
        if (last != 0 && count < FRAMES.length) FRAMES[count++] = now - last;
        last = now;
        if (now >= deadline || count == FRAMES.length) {
            active = false;
            long cpu = MX.getThreadCpuTime(renderThreadId) - cpuStart;
            long allocated = MX.getThreadAllocatedBytes(renderThreadId) - allocatedStart;
            long musicCpu = MX.getThreadCpuTime(musicThreadId) - musicCpuStart;
            try {
                long[] ordered = Arrays.copyOf(FRAMES, count); Arrays.sort(ordered);
                long total = 0, slowTotal = 0; int over16 = 0, over33 = 0;
                for (long interval : ordered) { total += interval; if (interval > 16_666_667L) over16++; if (interval > 33_333_333L) over33++; }
                int slowCount = Math.max(1, (int) Math.ceil(count * .01));
                for (int i = count - slowCount; i < count; i++) slowTotal += ordered[i];
                JsonObject result = status();
                result.addProperty("label", label); result.addProperty("frames", count);
                result.addProperty("wallSeconds", (now - started) / 1e9);
                result.addProperty("averageFps", count * 1e9 / total);
                result.addProperty("medianMs", quantile(ordered, .50)); result.addProperty("p95Ms", quantile(ordered, .95));
                result.addProperty("p99Ms", quantile(ordered, .99)); result.addProperty("p999Ms", quantile(ordered, .999));
                result.addProperty("maxMs", ordered[count - 1] / 1e6);
                result.addProperty("onePercentLowFps", slowCount * 1e9 / slowTotal);
                result.addProperty("framesOver16_7ms", over16); result.addProperty("framesOver33_3ms", over33);
                result.addProperty("renderCpuCores", (double) cpu / (now - started));
                result.addProperty("renderAllocatedMBPerSecond", allocated * 1000. / (now - started));
                result.addProperty("renderAllocatedBytesPerFrame", (double) allocated / count);
                result.addProperty("musicCpuCores", (double) musicCpu / (now - started));
                StringBuilder csv = new StringBuilder("frame,interval_ns\n");
                for (int i = 0; i < count; i++) csv.append(i).append(',').append(FRAMES[i]).append('\n');
                Files.writeString(directory().resolve(label + ".csv"), csv);
                write(label, result);
            } catch (Throwable exception) { exception.printStackTrace(); }
        }
    }

    private static double quantile(long[] ordered, double fraction) {
        return ordered[Math.min(ordered.length - 1, (int) Math.ceil(ordered.length * fraction) - 1)] / 1e6;
    }

    private static void musicCheck() throws Exception {
        prepare();
        JsonObject result = new JsonObject();
        Thread original = musicThread();
        Class<?> playerClass = original.getClass();
        Method isPlaying = playerClass.getDeclaredMethod("isPlaying");
        isPlaying.setAccessible(true);
        long cpu = MX.getThreadCpuTime(original.getId()), time = System.nanoTime();
        Thread.sleep(2000);
        result.addProperty("idleCpuCores", (double) (MX.getThreadCpuTime(original.getId()) - cpu) / (System.nanoTime() - time));
        result.addProperty("idleState", original.getState().toString());
        // Run the untransformed installed mod's idle loop briefly in an isolated loader.
        // Its static song fields are separate, so this control cannot alter live playback.
        try (JarFile jar = new JarFile(Minecraft.getInstance().gameDirectory.toPath()
                .resolve("mods/reactivemusic-forge-1.3.5+1.20.1.jar").toFile())) {
            byte[] bytes = jar.getInputStream(jar.getJarEntry("circuitlord/reactivemusic/PlayerThread.class")).readAllBytes();
            Class<?> rawClass = new RawPlayerLoader().define(bytes);
            Thread control = (Thread) rawClass.getConstructor().newInstance();
            try {
                long controlCpu = MX.getThreadCpuTime(control.getId()), controlTime = System.nanoTime();
                Thread.sleep(2000);
                result.addProperty("originalIdleCpuCores", (double) (MX.getThreadCpuTime(control.getId()) - controlCpu) / (System.nanoTime() - controlTime));
            } finally {
                rawClass.getMethod("forceKill").invoke(control);
                control.join(1000);
                if (control.isAlive()) throw new AssertionError("Original control thread failed to stop");
            }
        }
        if (savedSong == null) throw new IllegalStateException("No known valid song yet");
        Object previous = get(original, "player");
        long queuedAt = System.nanoTime();
        playerClass.getMethod("play", String.class).invoke(original, savedSong);
        while ((get(original, "player") == previous || !((boolean) isPlaying.invoke(original))) && System.nanoTime() - queuedAt < 4_000_000_000L) Thread.sleep(10);
        boolean playing = (boolean) isPlaying.invoke(original);
        result.addProperty("song", savedSong);
        result.addProperty("validSongStarted", playing);
        result.addProperty("validSongWakeMs", (System.nanoTime() - queuedAt) / 1e6);
        if (!playing) throw new AssertionError("Queued song did not start");
        Thread.sleep(300);
        playerClass.getMethod("resetPlayer").invoke(original);
        Thread.sleep(300);
        boolean stopped = !(boolean) isPlaying.invoke(original);
        result.addProperty("stopDuringPlayback", stopped);
        if (!stopped) throw new AssertionError("Playback did not stop");
        playerClass.getMethod("play", String.class).invoke(original, "__codex_missing_song__");
        Thread.sleep(100);
        cpu = MX.getThreadCpuTime(original.getId()); time = System.nanoTime();
        Thread.sleep(1000);
        result.addProperty("missingResourceCpuCores", (double) (MX.getThreadCpuTime(original.getId()) - cpu) / (System.nanoTime() - time));
        playerClass.getMethod("resetPlayer").invoke(original);
        Thread.sleep(100);
        Thread dummy = (Thread) playerClass.getConstructor().newInstance();
        Thread.sleep(100);
        time = System.nanoTime();
        playerClass.getMethod("forceKill").invoke(dummy);
        dummy.join(1000);
        result.addProperty("forceKillMs", (System.nanoTime() - time) / 1e6);
        result.addProperty("forceKillStopped", !dummy.isAlive());
        if (dummy.isAlive()) throw new AssertionError("Idle player failed to stop");
        result.addProperty("result", "passed");
        write("music-checks", result);
    }

    private interface CheckedRunnable { void run() throws Exception; }

    private static final class RawPlayerLoader extends ClassLoader {
        RawPlayerLoader() { super(BenchmarkSession.class.getClassLoader()); }
        Class<?> define(byte[] bytes) { return defineClass("circuitlord.reactivemusic.PlayerThread", bytes, 0, bytes.length); }
    }
}
