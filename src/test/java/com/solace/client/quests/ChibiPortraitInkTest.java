package com.solace.client.quests;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChibiPortraitInkTest {
    private static final int N = 160;

    /** Sets a GL-order (bottom row first) pixel from top-left coordinates. */
    private static void put(ByteBuffer color, FloatBuffer depth, int x, int y, int rgb, float d) {
        int i = ((N - 1 - y) * N + x);
        color.put(i * 4, (byte) (rgb >> 16)).put(i * 4 + 1, (byte) (rgb >> 8)).put(i * 4 + 2, (byte) rgb).put(i * 4 + 3, (byte) 255);
        depth.put(i, d);
    }

    @Test
    void outlinesSilhouetteAndNearerPartOnly() {
        ByteBuffer color = ByteBuffer.allocate(N * N * 4);
        FloatBuffer depth = FloatBuffer.allocate(N * N);
        for (int i = 0; i < N * N; i++) {
            depth.put(i, 1.0F);
        }
        float unit = 1.0F / 20000.0F;
        put(color, depth, 10, 10, 0xFF0000, 0.5F);           // head pixel, near
        put(color, depth, 10, 11, 0x00FF00, 0.5F + 5 * unit); // body pixel just below, 5 px farther
        put(color, depth, 11, 11, 0x00FF00, 0.5F + 7 * unit); // side face: 2 px deeper per pixel, same part

        int[] out = ChibiPortrait.ink(color, depth);

        int darkRed = 0xFF000000 | (255 * 3 / 10);
        assertEquals(darkRed, out[9 * N + 10], "empty pixel above the head gets the outline");
        assertEquals(darkRed, out[11 * N + 10], "farther body pixel under the head becomes the part line");
        assertEquals(0xFF00FF00, out[11 * N + 11], "receding side face is not split into parts");
        assertEquals(0xFF0000FF, out[10 * N + 10], "the nearer pixel keeps its colour (ABGR)");
    }
}
