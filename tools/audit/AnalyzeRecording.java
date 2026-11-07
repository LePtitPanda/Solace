import jdk.jfr.consumer.*;
import java.nio.file.*;
import java.util.*;
import java.io.*;

/** Offline streaming JFR reduction. Run with Java 17; no Minecraft classes or external libraries. */
public class AnalyzeRecording {
    private static final Map<String, String> OWNERS = new HashMap<>();
    private static final Map<String, long[]> CPU = new HashMap<>(), ALLOC = new HashMap<>(), WAITS = new HashMap<>();
    private static final Map<String, Long> EVENTS = new TreeMap<>();
    private static final List<Map<String, Object>> GC = new ArrayList<>(), LOAD = new ArrayList<>();
    private static final List<Map<String, Object>> HEAP = new ArrayList<>();
    private static final Map<String, long[]> THREAD_BYTES = new HashMap<>();
    private static String owner(String name) {
        if (name.startsWith("com.solace.audit.")) return "audit";
        String clean = name.split("/", 2)[0].split("\\$\\$Lambda", 2)[0];
        String result = OWNERS.get(clean);
        if (result != null) return result;
        if (clean.startsWith("net.minecraft.")) return "minecraft/shared";
        if (clean.startsWith("net.minecraftforge.")) return "forge/shared";
        if (clean.startsWith("java.") || clean.startsWith("jdk.") || clean.startsWith("sun.")) return "jvm";
        return "unmapped";
    }
    private static String[] stack(RecordedEvent event) {
        RecordedStackTrace trace = event.getStackTrace();
        String leaf = "no-stack", attribution = "unmapped", anchor = "no-stack";
        if (trace != null && !trace.getFrames().isEmpty()) {
            var frames = trace.getFrames();
            RecordedMethod first = frames.get(0).getMethod();
            leaf = first.getType().getName() + "." + first.getName();
            attribution = owner(first.getType().getName()); anchor = leaf;
            // Closest known mod/library frame. Shared vanilla/Forge work remains visible in the leaf.
            for (RecordedFrame frame : frames) {
                RecordedMethod method = frame.getMethod(); String candidate = owner(method.getType().getName());
                if (!Set.of("minecraft/shared", "forge/shared", "jvm", "unmapped").contains(candidate)) {
                    attribution = candidate; anchor = method.getType().getName() + "." + method.getName(); break;
                }
            }
        }
        return new String[]{attribution, leaf, anchor};
    }
    private static String thread(RecordedEvent event) {
        RecordedThread thread = event.hasField("sampledThread") ? event.getThread("sampledThread") : event.getThread();
        return thread == null ? "unknown" : thread.getJavaName() + "#" + thread.getJavaThreadId();
    }
    private static void add(Map<String, long[]> map, String key, long weight) {
        long[] value = map.computeIfAbsent(key, ignored -> new long[2]); value[0]++; value[1] += weight;
    }
    private static List<Map<String, Object>> rows(Map<String, long[]> map, String weightName, String... keys) {
        List<Map<String, Object>> result = new ArrayList<>();
        map.entrySet().stream().sorted((a,b) -> Long.compare(b.getValue()[1], a.getValue()[1])).limit(4000).forEach(entry -> {
            Map<String, Object> row = new LinkedHashMap<>(); String[] parts = entry.getKey().split("\t", -1);
            for (int i = 0; i < keys.length; i++) row.put(keys[i], parts[i]);
            row.put("events", entry.getValue()[0]); row.put(weightName, entry.getValue()[1]); result.add(row);
        });
        return result;
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 3) throw new IllegalArgumentException("profile.jfr class-owners.tsv output.json");
        for (String line : Files.readAllLines(Path.of(args[1]))) {
            int tab = line.indexOf('\t'); if (tab > 0) OWNERS.put(line.substring(0, tab), line.substring(tab + 1));
        }
        long samples = 0, executionSamples = 0, nativeSamples = 0, allocationWeight = 0;
        try (RecordingFile file = new RecordingFile(Path.of(args[0]))) {
            while (file.hasMoreEvents()) {
                RecordedEvent event = file.readEvent(); String type = event.getEventType().getName();
                EVENTS.merge(type, 1L, Long::sum);
                if (type.equals("jdk.ExecutionSample") || type.equals("jdk.NativeMethodSample")) {
                    String[] stack = stack(event); samples++;
                    if (type.equals("jdk.ExecutionSample")) executionSamples++; else nativeSamples++;
                    add(CPU, thread(event) + "\t" + type + "\t" + String.join("\t", stack), 1);
                } else if (type.equals("jdk.ObjectAllocationSample")) {
                    long weight = event.getLong("weight"); allocationWeight += weight;
                    String[] stack = stack(event);
                    String object = event.getClass("objectClass").getName();
                    add(ALLOC, thread(event) + "\t" + object + "\t" + owner(object) + "\t" + String.join("\t", stack), weight);
                } else if (Set.of("jdk.ThreadPark", "jdk.JavaMonitorEnter", "jdk.JavaMonitorWait", "jdk.FileRead", "jdk.FileWrite", "jdk.SocketRead", "jdk.SocketWrite").contains(type)) {
                    add(WAITS, thread(event) + "\t" + type + "\t" + String.join("\t", stack(event)), event.getDuration().toNanos());
                } else if (type.equals("jdk.GCPhasePause") && GC.size() < 4000) {
                    GC.add(Map.of("utc", event.getStartTime().toString(), "pauseNanos", event.getDuration().toNanos(), "name", event.getString("name")));
                } else if (type.equals("jdk.CPULoad") && LOAD.size() < 1000) {
                    LOAD.add(Map.of("utc", event.getStartTime().toString(), "jvmUser", event.getFloat("jvmUser"), "jvmSystem", event.getFloat("jvmSystem"), "machineTotal", event.getFloat("machineTotal")));
                } else if (type.equals("jdk.GCHeapSummary") && HEAP.size() < 4000) {
                    HEAP.add(Map.of("utc", event.getStartTime().toString(), "gcId", event.getInt("gcId"), "when", event.getString("when"), "usedBytes", event.getLong("heapUsed")));
                } else if (type.equals("jdk.ThreadAllocationStatistics")) {
                    RecordedThread thread = event.getThread("thread");
                    if (thread != null) {
                        String key = thread.getJavaName() + "#" + thread.getJavaThreadId(); long allocated = event.getLong("allocated");
                        long[] bytes = THREAD_BYTES.computeIfAbsent(key, ignored -> new long[]{allocated, allocated, 0});
                        bytes[1] = allocated; bytes[2]++;
                    }
                }
            }
        }
        Map<String, Object> output = new LinkedHashMap<>();
        output.put("schema", 1); output.put("cpuSamplesTotal", samples); output.put("allocationWeightedBytesTotal", allocationWeight);
        output.put("executionSamplesTotal", executionSamples); output.put("nativeSamplesTotal", nativeSamples);
        output.put("eventCounts", EVENTS);
        output.put("cpu", rows(CPU, "samples", "thread", "event", "nearestOwner", "leaf", "anchor"));
        output.put("allocations", rows(ALLOC, "weightedBytes", "thread", "objectClass", "objectOwner", "nearestOwner", "leaf", "anchor"));
        output.put("waitsAndIO", rows(WAITS, "wallNanos", "thread", "event", "nearestOwner", "leaf", "anchor"));
        output.put("gcPauses", GC); output.put("cpuLoad", LOAD);
        output.put("gcHeap", HEAP);
        List<Map<String, Object>> threadBytes = new ArrayList<>();
        THREAD_BYTES.forEach((thread, bytes) -> threadBytes.add(Map.of("thread", thread, "allocatedDeltaBytes", Math.max(0, bytes[1] - bytes[0]), "observations", bytes[2])));
        threadBytes.sort((a,b) -> Long.compare((Long)b.get("allocatedDeltaBytes"), (Long)a.get("allocatedDeltaBytes")));
        output.put("threadAllocationDeltas", threadBytes);
        output.put("rowLimit", 4000); output.put("cpuUniqueRows", CPU.size()); output.put("allocationUniqueRows", ALLOC.size()); output.put("waitUniqueRows", WAITS.size());
        output.put("interpretation", List.of("CPU samples are statistical observations, not milliseconds or per-mod removal gains.",
                "nearestOwner is the closest known mod/library stack frame; it is attribution evidence, not proof of causality.",
                "Mixed-in methods may retain a vanilla owner; inspect leaf/anchor method names and content probes together.",
                "Allocation weights are JFR estimates of bytes allocated, not retained heap or exact object counts.",
                "Wait/IO durations overlap and can include idle sleeping/vsync. Do not sum as CPU time.",
                "Native method samples can capture blocked IO watcher/event-loop threads; inspect them separately from Java execution samples.",
                "Rows are capped after ranking. Raw JFR retains the detailed stacks/timeline for further inspection."));
        Files.writeString(Path.of(args[2]), json(output));
        System.out.println("Reduced " + samples + " CPU samples, " + allocationWeight + " weighted allocation bytes");
    }
    private static String json(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map<?, ?> map) {
            List<String> pairs = new ArrayList<>(); map.forEach((key, item) -> pairs.add(json(key.toString()) + ":" + json(item)));
            return "{" + String.join(",", pairs) + "}";
        }
        if (value instanceof Iterable<?> list) {
            List<String> items = new ArrayList<>(); list.forEach(item -> items.add(json(item))); return "[" + String.join(",", items) + "]";
        }
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) switch (c) {
            case '"' -> out.append("\\\""); case '\\' -> out.append("\\\\"); case '\n' -> out.append("\\n");
            case '\r' -> out.append("\\r"); case '\t' -> out.append("\\t");
            default -> { if (c < 32) out.append(String.format("\\u%04x", (int)c)); else out.append(c); }
        }
        return out.append('"').toString();
    }
}
