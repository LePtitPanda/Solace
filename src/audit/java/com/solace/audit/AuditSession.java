package com.solace.audit;

import com.google.gson.GsonBuilder;
import jdk.jfr.Category;
import jdk.jfr.Event;
import jdk.jfr.Label;
import jdk.jfr.Name;
import jdk.jfr.StackTrace;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Temporary, bounded content attribution. Absent from ordinary Solace builds. */
public final class AuditSession {
    private static final com.sun.management.ThreadMXBean MX =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private static final AtomicLong IDS = new AtomicLong();
    private static final ThreadLocal<Buffer> LOCAL = new ThreadLocal<>();
    private static volatile Capture current;
    private static volatile Capture gpuCapture;
    private AuditSession() { }

    public static final class Capture {
        final long serial = IDS.incrementAndGet();
        final String label;
        final Path directory;
        final int oneIn;
        final boolean probes, gpu;
        final long started = System.nanoTime();
        final String timestamp = Instant.now().toString();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger bufferSlots = new AtomicInteger(), rowSlots = new AtomicInteger();
        final ConcurrentLinkedQueue<Buffer> buffers = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Long> gpuNanos = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<Map<String, Object>> scene = new ConcurrentLinkedQueue<>();
        final AtomicInteger gpuSkipped = new AtomicInteger();
        final AtomicInteger dropped = new AtomicInteger();
        volatile boolean accepting = true;
        volatile long ended;
        Capture(String label, Path directory, int oneIn, boolean probes, boolean gpu) {
            this.label = label; this.directory = directory; this.oneIn = oneIn;
            this.probes = probes; this.gpu = gpu;
        }
    }

    private static final class Buffer {
        final long serial;
        final long threadId = Thread.currentThread().getId();
        final String thread = Thread.currentThread().getName();
        final Map<String, Row> rows = new HashMap<>();
        Buffer(long serial) { this.serial = serial; }
    }

    private static final class Row {
        String category, content, implementation, dimension, thread;
        long threadId, samples, wallNanos, cpuNanos, allocatedBytes, maxWallNanos;
        int sampleOneIn;
        String worstLocation;
        Row(String category, String content, String implementation, String dimension, Buffer buffer, int sampleOneIn) {
            this.category = category; this.content = content; this.implementation = implementation;
            this.dimension = dimension; this.thread = buffer.thread; this.threadId = buffer.threadId;
            this.sampleOneIn = sampleOneIn;
        }
        Row copy(Buffer buffer) {
            Row copy = new Row(category, content, implementation, dimension, buffer, sampleOneIn);
            copy.samples = samples; copy.wallNanos = wallNanos; copy.cpuNanos = cpuNanos; copy.allocatedBytes = allocatedBytes;
            copy.maxWallNanos = maxWallNanos; copy.worstLocation = worstLocation; return copy;
        }
    }

    public static Capture start(String label, Path directory, int oneIn, boolean probes, boolean gpu) {
        if (current != null) throw new IllegalStateException("Capture already running");
        if (MX.isThreadCpuTimeSupported() && !MX.isThreadCpuTimeEnabled()) MX.setThreadCpuTimeEnabled(true);
        if (MX.isThreadAllocatedMemorySupported() && !MX.isThreadAllocatedMemoryEnabled()) MX.setThreadAllocatedMemoryEnabled(true);
        Capture capture = new Capture(label, directory, oneIn, probes, gpu);
        current = capture;
        gpuCapture = capture;
        return capture;
    }

    public static Capture detach() {
        Capture capture = current;
        if (capture != null) { capture.accepting = false; capture.ended = System.nanoTime(); current = null; }
        gpuCapture = null;
        return capture;
    }

    public static boolean active() { return current != null; }
    public static Capture gpuCapture() { return gpuCapture; }
    public static void scene(Map<String, Object> scene) {
        Capture capture = current;
        if (capture != null && capture.scene.size() < 120) capture.scene.add(scene);
    }

    /** Random sampling avoids aliasing with periodic ticks. No key lookup/allocation while idle. */
    public static Ticket open(String category) {
        Capture capture = current;
        if (capture == null || !capture.probes || !capture.accepting) return null;
        // Structure generation is rare enough to inspect every call; do not randomly miss a giant structure.
        int oneIn = category.startsWith("structure_") ? 1 : capture.oneIn;
        if (oneIn != 1 && ThreadLocalRandom.current().nextInt(oneIn) != 0) return null;
        capture.inFlight.incrementAndGet();
        if (!capture.accepting) { capture.inFlight.decrementAndGet(); return null; }
        Buffer buffer = LOCAL.get();
        if (buffer == null || buffer.serial != capture.serial) {
            if (capture.bufferSlots.incrementAndGet() > 128) {
                capture.bufferSlots.decrementAndGet(); capture.dropped.incrementAndGet(); capture.inFlight.decrementAndGet(); return null;
            }
            buffer = new Buffer(capture.serial); LOCAL.set(buffer); capture.buffers.add(buffer);
        }
        return new Ticket(capture, buffer, category, oneIn);
    }

    public static final class Ticket implements AutoCloseable {
        private final Capture capture;
        private final Buffer buffer;
        private final String category;
        private final int oneIn;
        private Row row;
        private String location;
        private long wall, cpu, bytes;
        private boolean begun, closed;
        private Ticket(Capture capture, Buffer buffer, String category, int oneIn) {
            this.capture = capture; this.buffer = buffer; this.category = category;
            this.oneIn = oneIn;
        }
        public void begin(Object content, Class<?> implementation, Object dimension, Object location) {
            String id = String.valueOf(content), impl = implementation.getName(), dim = String.valueOf(dimension);
            String key = category + '\0' + id + '\0' + impl + '\0' + dim;
            synchronized (buffer) {
                row = buffer.rows.get(key);
                if (row == null) {
                    if (buffer.rows.size() >= 4096) { capture.dropped.incrementAndGet(); return; }
                    if (capture.rowSlots.incrementAndGet() > 65_536) {
                        capture.rowSlots.decrementAndGet(); capture.dropped.incrementAndGet(); return;
                    }
                    row = new Row(category, id, impl, dim, buffer, oneIn); buffer.rows.put(key, row);
                }
            }
            this.location = String.valueOf(location);
            cpu = cpu(); bytes = bytes(); wall = System.nanoTime(); begun = true;
        }
        public void close() {
            if (closed) return;
            closed = true;
            try {
                if (!begun) return;
                long elapsed = System.nanoTime() - wall;
                long cpuDelta = Math.max(0, cpu() - cpu), allocated = Math.max(0, bytes() - bytes);
                synchronized (buffer) {
                    row.samples++; row.wallNanos += elapsed; row.cpuNanos += cpuDelta; row.allocatedBytes += allocated;
                    if (elapsed > row.maxWallNanos) { row.maxWallNanos = elapsed; row.worstLocation = location; }
                }
                if (elapsed >= 1_000_000L) {
                    Work event = new Work();
                    event.categoryName = category; event.content = row.content; event.implementation = row.implementation;
                    event.wallNanos = elapsed; event.cpuNanos = cpuDelta; event.allocatedBytes = allocated;
                    event.dimension = row.dimension; event.location = location; event.commit();
                }
            } finally { capture.inFlight.decrementAndGet(); }
        }
    }

    private static long cpu() { return MX.isThreadCpuTimeSupported() ? MX.getCurrentThreadCpuTime() : 0; }
    private static long bytes() { return MX.isThreadAllocatedMemorySupported() ? MX.getThreadAllocatedBytes(Thread.currentThread().getId()) : 0; }

    @Name("solace.ContentWork") @Label("Sampled content work") @Category("Solace audit") @StackTrace(false)
    public static final class Work extends Event {
        public String categoryName, content, implementation, dimension, location;
        public long wallNanos, cpuNanos, allocatedBytes;
    }

    public static void export(Capture capture) throws Exception {
        long limit = System.nanoTime() + 5_000_000_000L;
        while (capture.inFlight.get() != 0 && System.nanoTime() < limit) Thread.sleep(10);
        boolean complete = capture.inFlight.get() == 0;
        List<Row> rows = new ArrayList<>();
        // Snapshot under per-buffer locks even if a slow worldgen job outlives the drain window.
        for (Buffer buffer : capture.buffers) synchronized (buffer) {
            for (Row row : buffer.rows.values()) if (row.samples > 0) rows.add(row.copy(buffer));
        }
        rows.sort((a, b) -> Long.compare(b.wallNanos, a.wallNanos));
        Map<String, Object> output = new HashMap<>();
        output.put("schema", 1); output.put("label", capture.label); output.put("startedUtc", capture.timestamp);
        output.put("elapsedSeconds", (capture.ended - capture.started) / 1e9);
        output.put("counterSupport", Map.of("threadCpu", MX.isThreadCpuTimeSupported(), "threadAllocatedBytes", MX.isThreadAllocatedMemorySupported()));
        output.put("sampleOneIn", capture.oneIn); output.put("probes", capture.probes); output.put("complete", complete);
        output.put("inFlightAtExport", capture.inFlight.get()); output.put("droppedKeys", capture.dropped.get());
        output.put("rows", rows); output.put("scene", capture.scene);
        output.put("gpuRenderNanos", capture.gpuNanos); output.put("gpuSkipped", capture.gpuSkipped.get());
        output.put("interpretation", List.of("Rows contain sampled inclusive costs; categories overlap and must not be summed.",
                "Estimated workload = sampled totals * each row's sampleOneIn; structures use 1 (all calls). Uncertainty is high for rare sampled content.",
                "Content namespace is ownership of the registry entry, not proof of the cause of shared/mixin code.",
                "Renderer rows measure CPU submission. GPU queries cover GameRenderer.render including GUI, not individual mods.",
                "Per-probe CPU/allocation counters include counter overhead. JFR also includes audit overhead.",
                "GPU query pool is bounded; unresolved results at stop are omitted. No glFinish or blocking result reads."));
        Files.writeString(capture.directory.resolve("content.json"), new GsonBuilder().setPrettyPrinting().create().toJson(output));
    }
}
