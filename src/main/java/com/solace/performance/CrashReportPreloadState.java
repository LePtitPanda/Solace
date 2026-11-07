package com.solace.performance;

/** Main-thread flag set only while vanilla CrashReport.preload() runs at startup. */
public final class CrashReportPreloadState {
    private static volatile Thread preloading;

    private CrashReportPreloadState() { }

    public static void begin() {
        preloading = Thread.currentThread();
    }

    public static void end() {
        preloading = null;
    }

    public static boolean active() {
        return preloading == Thread.currentThread();
    }
}
