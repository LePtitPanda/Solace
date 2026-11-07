package com.solace.audit;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL33;
import java.util.ArrayDeque;

/** Render-thread only. Never reads a query result before availability or waits for the GPU. */
public final class GpuQueries {
    private record Pending(int start, int end, AuditSession.Capture capture) { }
    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static int renders;
    private GpuQueries() { }
    public static int begin(boolean renderEnabled) {
        while (!PENDING.isEmpty()) {
            Pending first = PENDING.peek();
            if (GL15.glGetQueryObjecti(first.end, GL15.GL_QUERY_RESULT_AVAILABLE) == 0) break;
            PENDING.remove();
            long nanos = GL33.glGetQueryObjectui64(first.end, GL15.GL_QUERY_RESULT) - GL33.glGetQueryObjectui64(first.start, GL15.GL_QUERY_RESULT);
            if (nanos >= 0 && first.capture.accepting && first.capture.gpuNanos.size() < 20_000) first.capture.gpuNanos.add(nanos);
            GL15.glDeleteQueries(first.start); GL15.glDeleteQueries(first.end);
        }
        AuditSession.Capture capture = AuditSession.gpuCapture();
        if (!renderEnabled) return 0;
        if (capture == null || !capture.gpu || ++renders % 16 != 0) return 0;
        if (!GL.getCapabilities().OpenGL33 || PENDING.size() >= 8) {
            capture.gpuSkipped.incrementAndGet(); return 0;
        }
        int start = GL15.glGenQueries(), end = GL15.glGenQueries();
        GL33.glQueryCounter(start, GL33.GL_TIMESTAMP);
        PENDING.add(new Pending(start, end, capture));
        return end;
    }
    public static void end(int query) { if (query != 0) GL33.glQueryCounter(query, GL33.GL_TIMESTAMP); }
}
