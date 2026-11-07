package com.solace.audit;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.logging.LogUtils;
import jdk.jfr.Configuration;
import jdk.jfr.Recording;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Mod.EventBusSubscriber(modid = "solace")
public final class AuditControl {
    private static final Logger LOG = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path ROOT = FMLPaths.GAMEDIR.get().resolve("local/solace-audit");
    private static final ScheduledExecutorService WORKER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "Solace audit IO"); t.setDaemon(true); return t;
    });
    private static final AtomicBoolean INITIALIZED = new AtomicBoolean();
    private static String lastRequest = "";
    private static Recording recording;
    private static AuditSession.Capture capture;
    private static long deadline;
    private static String lastError = "", lastDirectory = "";
    private AuditControl() { }

    public static void initialize() {
        if (!INITIALIZED.compareAndSet(false, true)) return;
        try {
            Path control = ROOT.resolve("control.json");
            if (Files.isRegularFile(control)) lastRequest = JsonParser.parseString(Files.readString(control)).getAsJsonObject().get("requestId").getAsString();
        } catch (Exception e) { LOG.warn("Ignoring malformed previous audit request", e); }
        WORKER.scheduleWithFixedDelay(AuditControl::poll, 0, 1, TimeUnit.SECONDS);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            synchronized (AuditControl.class) { try { stop(); } catch (Exception e) { LOG.error("Audit shutdown", e); } }
        }, "Solace audit shutdown"));
    }
    private static synchronized void poll() {
        try {
            Files.createDirectories(ROOT);
            Path control = ROOT.resolve("control.json");
            if (Files.isRegularFile(control)) {
                JsonObject request = JsonParser.parseString(Files.readString(control)).getAsJsonObject();
                String id = request.get("requestId").getAsString();
                if (!id.equals(lastRequest)) {
                    lastRequest = id;
                    lastError = "";
                    String action = request.get("action").getAsString();
                    if (action.equals("stop")) stop();
                    else if (action.equals("start")) start(request);
                    else if (!action.equals("status")) throw new IllegalArgumentException("Unknown action: " + action);
                }
            }
            if (capture != null && System.nanoTime() >= deadline) stop();
        } catch (Exception e) { lastError = e.toString(); LOG.error("Solace audit controller", e); }
        try { status(); } catch (Exception e) { LOG.error("Solace audit status", e); }
    }
    private static int integer(JsonObject json, String key, int fallback) {
        return json.has(key) ? json.get(key).getAsInt() : fallback;
    }
    private static boolean flag(JsonObject json, String key, boolean fallback) {
        return json.has(key) ? json.get(key).getAsBoolean() : fallback;
    }
    private static synchronized void start(JsonObject request) throws Exception {
        if (capture != null) throw new IllegalStateException("Capture already running");
        int seconds = integer(request, "seconds", 60), oneIn = integer(request, "sampleOneIn", 32);
        if (seconds < 5 || seconds > 300 || oneIn < 1 || oneIn > 4096) throw new IllegalArgumentException("seconds 5..300; sampleOneIn 1..4096");
        String label = request.has("label") ? request.get("label").getAsString() : "capture";
        if (!label.matches("[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException("Unsafe capture label");
        Path directory = ROOT.resolve(Instant.now().toString().replace(':', '-') + "-" + label);
        Files.createDirectories(directory);
        try {
            if (flag(request, "jfr", true)) {
                recording = new Recording(Configuration.getConfiguration("profile"));
                recording.setName("Solace-" + label); recording.setMaxSize(256L * 1024 * 1024);
                recording.setMaxAge(Duration.ofSeconds(330)); recording.setToDisk(true);
                recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(20));
                recording.enable("jdk.NativeMethodSample").withPeriod(Duration.ofMillis(20));
                recording.enable("jdk.ObjectAllocationSample").with("throttle", "150/s");
                recording.enable(AuditSession.Work.class); recording.start();
            }
            capture = AuditSession.start(label, directory, oneIn, flag(request, "probes", true), flag(request, "gpu", true));
            deadline = System.nanoTime() + seconds * 1_000_000_000L;
            lastDirectory = directory.toString(); lastError = "";
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("request", request); metadata.put("java", System.getProperty("java.runtime.version"));
            metadata.put("vm", System.getProperty("java.vm.name")); metadata.put("os", System.getProperty("os.name"));
            metadata.put("logicalProcessors", Runtime.getRuntime().availableProcessors());
            metadata.put("maxHeapBytes", Runtime.getRuntime().maxMemory());
            metadata.put("jfrMaxBytes", 256L * 1024 * 1024); metadata.put("jfrMaxAgeSeconds", 330);
            metadata.put("mods", ModList.get().getMods().stream().map(m -> Map.of("id", m.getModId(), "version", m.getVersion().toString(),
                    "file", m.getOwningFile().getFile().getFileName())).toList());
            metadata.put("coverage", List.of("server/client entity ticks, block-entity ticks, scheduled/random block/fluid ticks",
                    "entity/block-entity renderer CPU, particle tick/render CPU, placed features, structure generation/placement",
                    "JFR sampled JVM threads/stacks, weighted allocations, GC, file/socket IO, locks; asynchronous whole-render GPU queries"));
            Files.writeString(directory.resolve("metadata.json"), GSON.toJson(metadata));
            LOG.info("Solace audit started: {}", directory);
        } catch (Exception e) {
            AuditSession.detach(); capture = null;
            if (recording != null) { recording.close(); recording = null; }
            throw e;
        }
    }
    private static synchronized void stop() throws Exception {
        if (capture == null) return;
        AuditSession.Capture finished = AuditSession.detach(); capture = null;
        Recording finishedRecording = recording; recording = null;
        // Controller runs off the client/server threads; no JSON/JFR disk writes inside a tick or draw.
        try {
            try { AuditSession.export(finished); }
            finally {
                if (finishedRecording != null) { finishedRecording.stop(); finishedRecording.dump(finished.directory.resolve("profile.jfr")); }
            }
            LOG.info("Solace audit saved: {}", finished.directory);
        } finally { if (finishedRecording != null) finishedRecording.close(); }
    }
    private static void status() throws Exception {
        Map<String, Object> status = Map.of("schema", 1, "active", capture != null, "requestId", lastRequest,
                "directory", lastDirectory, "lastError", lastError, "timestamp", Instant.now().toString());
        Files.writeString(ROOT.resolve("status.json"), GSON.toJson(status));
    }
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("solaceaudit").requires(source -> source.hasPermission(2))
                .then(Commands.literal("stop").executes(context -> { WORKER.execute(() -> commandStop()); return 1; }))
                .then(Commands.literal("start").then(Commands.argument("label", StringArgumentType.word())
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 300)).executes(context -> {
                            JsonObject request = new JsonObject(); request.addProperty("label", StringArgumentType.getString(context, "label"));
                            request.addProperty("seconds", IntegerArgumentType.getInteger(context, "seconds"));
                            WORKER.execute(() -> { try { start(request); } catch (Exception e) { lastError = e.toString(); LOG.error("Audit start", e); } });
                            return 1;
                        })))));
    }
    private static void commandStop() { try { stop(); } catch (Exception e) { lastError = e.toString(); LOG.error("Audit stop", e); } }
    @SubscribeEvent public static void commands(RegisterCommandsEvent event) { initialize(); register(event.getDispatcher()); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { WORKER.execute(AuditControl::commandStop); }
}
