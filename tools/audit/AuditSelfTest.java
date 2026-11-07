package com.solace.audit;

import com.google.gson.JsonParser;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.time.Duration;

public class AuditSelfTest {
    private static volatile long sink;
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void work(int count, boolean distinct) {
        for (int i = 0; i < count; i++) try (AuditSession.Ticket ticket = AuditSession.open("test")) {
            if (ticket != null) ticket.begin(distinct ? "test:" + i : "test:shared", AuditSelfTest.class, "test", "0,0,0");
            sink++;
        }
    }
    public static void main(String[] args) throws Exception {
        Path root = Path.of(args[0]); Files.createDirectories(root);
        check(AuditSession.open("idle") == null, "Idle must return null");
        var capture = AuditSession.start("concurrency", root, 1, true, false);
        Thread a = new Thread(() -> work(10000, false), "audit-test-A"), b = new Thread(() -> work(10000, false), "audit-test-B");
        a.start(); b.start(); a.join(); b.join();
        try (var ticket = AuditSession.open("exception")) {
            ticket.begin("test:throw", AuditSelfTest.class, "test", ""); throw new IllegalStateException("expected");
        } catch (IllegalStateException expected) { }
        AuditSession.detach(); check(capture.inFlight.get() == 0, "Exception leaked a span"); AuditSession.export(capture);
        var json = JsonParser.parseString(Files.readString(root.resolve("content.json"))).getAsJsonObject();
        check(json.get("complete").getAsBoolean(), "Incomplete export");
        long samples = 0; for (var row : json.getAsJsonArray("rows")) samples += row.getAsJsonObject().get("samples").getAsLong();
        check(samples == 20001, "Concurrent samples lost: " + samples);
        capture = AuditSession.start("cap", root, 1, true, false); work(5000, true); AuditSession.detach();
        check(capture.dropped.get() == 904, "Row cap failed"); AuditSession.export(capture);
        capture = AuditSession.start("disabled", root, 1, false, false);
        check(AuditSession.open("test") == null, "Probes=false failed"); AuditSession.detach();
        capture = AuditSession.start("rare", root, 32, true, false);
        for (int i = 0; i < 100; i++) try (var ticket = AuditSession.open("structure_generation")) {
            check(ticket != null, "Rare structure call was sampled away"); ticket.begin("test:rare", AuditSelfTest.class, "test", "");
        }
        AuditSession.detach(); AuditSession.export(capture);
        capture = AuditSession.start("drain", root, 1, true, false); work(1, false);
        CountDownLatch opened = new CountDownLatch(1), release = new CountDownLatch(1);
        Thread slow = new Thread(() -> {
            try (var ticket = AuditSession.open("slow")) {
                ticket.begin("test:pending", AuditSelfTest.class, "test", ""); opened.countDown(); release.await();
            } catch (InterruptedException e) { throw new AssertionError(e); }
        });
        slow.start(); opened.await(); AuditSession.detach();
        try {
            AuditSession.export(capture);
            json = JsonParser.parseString(Files.readString(root.resolve("content.json"))).getAsJsonObject();
            check(!json.get("complete").getAsBoolean(), "Incomplete drain flag missing");
            check(json.getAsJsonArray("rows").size() == 1, "Completed samples lost during incomplete drain");
        } finally { release.countDown(); slow.join(); }
        try (Recording recording = new Recording()) {
            recording.enable(AuditSession.Work.class);
            recording.enable("jdk.ExecutionSample").withPeriod(Duration.ofMillis(1));
            recording.enable("jdk.ObjectAllocationSample").with("throttle", "150/s");
            recording.enable("jdk.ThreadPark").withThreshold(Duration.ZERO);
            recording.enable("jdk.JavaMonitorWait").withThreshold(Duration.ZERO);
            recording.start();
            capture = AuditSession.start("jfr", root, 1, true, false);
            try (var ticket = AuditSession.open("slow")) { ticket.begin("test:slow", AuditSelfTest.class, "test", ""); Thread.sleep(5); }
            for (int i = 0; i < 200; i++) { byte[] allocation = new byte[200_000]; allocation[0] = (byte) i; sink += allocation[0]; }
            long busyUntil = System.nanoTime() + 150_000_000L;
            while (System.nanoTime() < busyUntil) sink = sink * 1664525 + 1013904223;
            AuditSession.detach(); AuditSession.export(capture); recording.stop(); recording.dump(root.resolve("selftest.jfr"));
        }
        boolean found = false;
        try (RecordingFile file = new RecordingFile(root.resolve("selftest.jfr"))) {
            while (file.hasMoreEvents()) if (file.readEvent().getEventType().getName().equals("solace.ContentWork")) found = true;
        }
        check(found, "Custom JFR content event missing");
        work(300000, false);
        long start = System.nanoTime(); work(1000000, false); long idle = System.nanoTime() - start;
        capture = AuditSession.start("overhead", root, 32, true, false); work(300000, false);
        start = System.nanoTime(); work(1000000, false); long active = System.nanoTime() - start;
        AuditSession.detach(); AuditSession.export(capture);
        Files.writeString(root.resolve("selftest.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(Map.of(
                "passed", true, "idleCoreNanosPerCall", idle / 1e6, "activeCoreNanosPerCallOneIn32", active / 1e6,
                "limit", "Core-only microbenchmark; excludes transformed call wrappers/JFR/GPU. Runtime comparison is still required.")));
        System.out.println("PASS: concurrent accounting, exception cleanup, caps, disabled probes, rare calls, incomplete drain, JFR, core overhead");
    }
}
