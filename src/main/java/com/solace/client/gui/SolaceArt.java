package com.solace.client.gui;

/**
 * Solace's pixel UI art: a restrained, monochrome style. Frames are a thin line that is lighter
 * at the top than the bottom, with a small sparkle at each corner; panels and primary buttons
 * carry one airy flourish centered on their top edge; rules fade out at their ends.
 *
 * <p>Plain Java with no Minecraft types, so the art can be rendered to a PNG outside the
 * game for previews. Sprites use role letters, recolored per state by {@link Tones}:
 * {@code k} outline, {@code g} line, {@code d} dim line, {@code h} light line,
 * {@code w} highlight; a space is transparent.
 */
public final class SolaceArt {
    /** Same shape as GuiGraphics#fill(x0, y0, x1, y1, argb). */
    @FunctionalInterface
    public interface Sink {
        void fill(int x0, int y0, int x1, int y1, int argb);
    }

    public static final int BLACK = 0xFF000000;
    public static final int SLOT = 0xFF1A1A1A;
    public static final int SLOT_HOVER = 0xFF242424;
    public static final int DIM = 0xFF404040;
    public static final int LINE = 0xFF808080;
    public static final int LIGHT = 0xFFD7D7D7;
    public static final int WHITE = 0xFFFFFFFF;

    /** Colors for each role letter. */
    public record Tones(int outline, int line, int dim, int light, int rivet) {
        public static final Tones NORMAL = new Tones(0xC0000000, LINE, DIM, LIGHT, WHITE);
        public static final Tones HOVER = new Tones(0xC0000000, LIGHT, LINE, WHITE, WHITE);
        public static final Tones DISABLED = new Tones(0xA0000000, DIM, 0xFF2A2A2A, LINE, LINE);

        public Tones withRivet(int rivet) {
            return new Tones(outline, line, dim, light, rivet);
        }

        public Tones withLine(int line) {
            return new Tones(outline, line, dim, light, rivet);
        }

        /** Blends every tone toward {@code to} by {@code t}, for hover fades. */
        public Tones mix(Tones to, float t) {
            return new Tones(lerp(outline, to.outline, t), lerp(line, to.line, t), lerp(dim, to.dim, t),
                    lerp(light, to.light, t), lerp(rivet, to.rivet, t));
        }

        public Tones alpha(float a) {
            return new Tones(fade(outline, a), fade(line, a), fade(dim, a), fade(light, a), fade(rivet, a));
        }

        int of(char role) {
            return switch (role) {
                case 'k' -> outline;
                case 'g' -> line;
                case 'd' -> dim;
                case 'h' -> light;
                case 'w' -> rivet;
                default -> 0;
            };
        }
    }

    // ---- Sprites ----

    /**
     * Flourish centered on a top edge; row 2 is the edge line, which it replaces. The line breaks
     * into dots toward a small lit diamond in the middle.
     */
    static final String[] FLOURISH = {
            "                    h                    ",
            "           h   h   hwh   h   h           ",
            "gggggg hhh  hhhh h  w  h hhhh  hhh gggggg",
            "          h   hh    d    hh   h          ",
            "                h   h   h                ",
    };

    /** Smaller flourish for bottom edges and narrow widgets; row 1 is the edge line. */
    static final String[] FLOURISH_SMALL = {
            "         h         ",
            "gg hh h  w  h hh gg",
            "      h  h  h      ",
    };

    /** Corner sparkle, drawn just outside each corner of a frame; mirrored per corner. */
    static final String[] SPARK = {
            "h  ",
            "   ",
            "  h",
    };

    /** Slider / scrollbar handle: a small rounded bar. */
    static final String[] PILL = {
            " kkk ",
            "khhhk",
            "khwhk",
            "khhhk",
            "kgggk",
            "kgggk",
            "khhhk",
            "kgggk",
            " kkk ",
    };

    static final String[] RIVET = {" h ", "hwh", " h "};

    public static final int PANEL_ORNATE = 0;
    public static final int PANEL_PLAIN = 1;
    /** Plain panel without the corner sparkles. */
    public static final int PANEL_BARE = 2;
    public static final int PATTERN_SIZE = 32;

    private SolaceArt() {
    }

    public static void glyph(Sink sink, String[] rows, int x, int y, boolean mirrorX, boolean mirrorY, Tones tones) {
        int height = rows.length;
        int width = width(rows);
        for (int r = 0; r < height; r++) {
            String row = rows[r];
            for (int c = 0; c < row.length(); c++) {
                int color = tones.of(row.charAt(c));
                if ((color >>> 24) == 0) {
                    continue;
                }
                int px = x + (mirrorX ? width - 1 - c : c);
                int py = y + (mirrorY ? height - 1 - r : r);
                sink.fill(px, py, px + 1, py + 1, color);
            }
        }
    }

    public static int width(String[] rows) {
        int max = 0;
        for (String row : rows) {
            max = Math.max(max, row.length());
        }
        return max;
    }

    /**
     * Frame inside the bounds: a soft outline, then a line that is lighter at the top and
     * dimmer at the bottom, with corner sparkles just outside.
     */
    public static void frame(Sink sink, int x, int y, int w, int h, Tones tones) {
        frame(sink, x, y, w, h, tones, true);
    }

    /** Frame with or without the diagonal corner sparkles. */
    public static void frame(Sink sink, int x, int y, int w, int h, Tones tones, boolean sparks) {
        if (w < 4 || h < 4) {
            return;
        }
        ring(sink, x, y, w, h, tones.outline);
        int top = lerp(tones.line, tones.light, 0.35F);
        int bottom = lerp(tones.line, tones.dim, 0.55F);
        sink.fill(x + 1, y + 1, x + w - 1, y + 2, top);
        sink.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, bottom);
        // Sides blend from the top tone to the bottom tone.
        for (int row = y + 2; row < y + h - 2; row++) {
            int color = lerp(top, bottom, (row - y - 2) / (float) Math.max(1, h - 5));
            sink.fill(x + 1, row, x + 2, row + 1, color);
            sink.fill(x + w - 2, row, x + w - 1, row + 1, color);
        }
        // Lit corner pixels and a sparkle outside each corner.
        int corner = lerp(tones.line, tones.light, 0.7F);
        sink.fill(x + 1, y + 1, x + 2, y + 2, corner);
        sink.fill(x + w - 2, y + 1, x + w - 1, y + 2, corner);
        if (sparks && w >= 16 && h >= 12) {
            glyph(sink, SPARK, x - 2, y - 2, false, false, tones);
            glyph(sink, SPARK, x + w - 1, y - 2, true, false, tones);
            glyph(sink, SPARK, x - 2, y + h - 1, false, true, tones);
            glyph(sink, SPARK, x + w - 1, y + h - 1, true, true, tones);
        }
    }

    /** Standard button: frame only. */
    public static void buttonFrame(Sink sink, int x, int y, int w, int h, Tones tones) {
        frame(sink, x, y, w, h, tones);
    }

    /** Primary button (Play, confirm): a frame with the small flourish on its top and bottom edges. */
    public static void primaryFrame(Sink sink, int x, int y, int w, int h, Tones tones) {
        frame(sink, x, y, w, h, tones);
        int fw = width(FLOURISH_SMALL);
        if (w >= fw + 12) {
            glyph(sink, FLOURISH_SMALL, x + (w - fw) / 2, y, false, false, tones);
            glyph(sink, FLOURISH_SMALL, x + (w - fw) / 2, y + h - 3, false, true, tones);
        }
    }

    /** Hold progress inside a frame: a soft white wash and the top line lighting from the left. */
    public static void progress(Sink sink, int x, int y, int w, int h, float progress) {
        if (progress <= 0.0F) {
            return;
        }
        int end = x + 2 + Math.round((w - 4) * Math.min(1.0F, progress));
        sink.fill(x + 2, y + 2, end, y + h - 2, 0x2CFFFFFF);
        sink.fill(x + 1, y + 1, end, y + 2, WHITE);
    }

    /** Title rule: a line fading out at both ends with the flourish at its center. */
    public static void headerRule(Sink sink, int cx, int y, int half, Tones tones) {
        fadingLine(sink, cx - half, cx + half, y, tones.line);
        int fw = width(FLOURISH);
        if (half * 2 >= fw + 20) {
            glyph(sink, FLOURISH, cx - fw / 2, y - 2, false, false, tones);
        }
    }

    /** Footer rule: a fading line with the small flourish, mirrored to point down. */
    public static void footerRule(Sink sink, int cx, int y, int half, Tones tones) {
        fadingLine(sink, cx - half, cx + half, y, tones.line);
        int fw = width(FLOURISH_SMALL);
        glyph(sink, FLOURISH_SMALL, cx - fw / 2, y - 1, false, true, tones);
    }

    /** Framed panel; {@link #PANEL_ORNATE} adds the flourish on the top edge and the small one below. */
    public static void panel(Sink sink, int x, int y, int w, int h, int fill, Tones tones, int style) {
        sink.fill(x + 2, y + 2, x + w - 2, y + h - 2, fill);
        frame(sink, x, y, w, h, tones, style != PANEL_BARE);
        if (style != PANEL_ORNATE) {
            return;
        }
        int fw = width(FLOURISH);
        if (w >= fw + 24) {
            glyph(sink, FLOURISH, x + (w - fw) / 2, y - 1, false, false, tones);
        }
        int sw = width(FLOURISH_SMALL);
        if (w >= sw + 24 && h >= 40) {
            glyph(sink, FLOURISH_SMALL, x + (w - sw) / 2, y + h - 3, false, true, tones);
        }
    }

    /**
     * Screen-corner bracket: two lines leaving the corner at (x, y) toward (dx, dy) and fading
     * out, with a sparkle on the corner.
     */
    public static void cornerBracket(Sink sink, int x, int y, int length, int dx, int dy, Tones tones) {
        for (int i = 0; i < length; i++) {
            int color = fade(tones.line, 1.0F - i / (float) length);
            sink.fill(x + i * dx, y, x + i * dx + 1, y + 1, color);
            sink.fill(x, y + i * dy, x + 1, y + i * dy + 1, color);
        }
        glyph(sink, RIVET, x - 1, y - 1, false, false, tones);
    }

    /** Recessed slot well, like an inventory slot: dark fill with a black edge. */
    public static void slot(Sink sink, int x, int y, int w, int h, int fill) {
        ring(sink, x, y, w, h, 0xC0000000);
        sink.fill(x + 1, y + 1, x + w - 1, y + h - 1, fill);
    }

    public static void pill(Sink sink, int cx, int cy, Tones tones) {
        glyph(sink, PILL, cx - 2, cy - PILL.length / 2, false, false, tones);
    }

    public static void rivet(Sink sink, int x, int y, Tones tones) {
        glyph(sink, RIVET, x, y, false, false, tones);
    }

    /**
     * One pixel of the background tile: a faint double-lined diamond lattice with small dots
     * where the lines cross. Tiles seamlessly.
     */
    public static int patternPixel(int x, int y) {
        int n = PATTERN_SIZE;
        int a = Math.floorMod(x + y, n);
        int b = Math.floorMod(x - y, n);
        int px = Math.floorMod(x, n);
        int py = Math.floorMod(y, n);
        int[][] crossings = {{0, 0}, {n, 0}, {0, n}, {n, n}, {n / 2, n / 2}};
        for (int[] c : crossings) {
            int dx = px - c[0];
            int dy = py - c[1];
            if (Math.abs(dx) <= 1 && Math.abs(dy) <= 1) {
                return dx == 0 && dy == 0 ? 0xFF1E1E1E : BLACK;
            }
        }
        if (a == 0 || b == 0) {
            return 0xFF141414;
        }
        if (a == 2 || b == 2) {
            return 0xFF111111;
        }
        if (a == 1 || b == 1) {
            return 0xFF080808;
        }
        return 0xFF0C0C0C;
    }

    /** A 1px line whose ends fade to transparent over a quarter of its length (at most 48px). */
    private static void fadingLine(Sink sink, int x0, int x1, int y, int color) {
        int length = x1 - x0;
        int fadeLength = Math.max(1, Math.min(48, length / 4));
        sink.fill(x0 + fadeLength, y, x1 - fadeLength, y + 1, color);
        for (int i = 0; i < fadeLength; i++) {
            int faded = fade(color, (i + 1) / (float) (fadeLength + 1));
            sink.fill(x0 + i, y, x0 + i + 1, y + 1, faded);
            sink.fill(x1 - 1 - i, y, x1 - i, y + 1, faded);
        }
    }

    private static void ring(Sink sink, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        sink.fill(x, y, x + w, y + 1, color);
        sink.fill(x, y + h - 1, x + w, y + h, color);
        sink.fill(x, y + 1, x + 1, y + h - 1, color);
        sink.fill(x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    public static int lerpPublic(int from, int to, float t) {
        return lerp(from, to, t);
    }

    static int lerp(int from, int to, float t) {
        float v = Math.max(0.0F, Math.min(1.0F, t));
        int a = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * v);
        int r = Math.round((from >> 16 & 0xFF) + ((to >> 16 & 0xFF) - (from >> 16 & 0xFF)) * v);
        int g = Math.round((from >> 8 & 0xFF) + ((to >> 8 & 0xFF) - (from >> 8 & 0xFF)) * v);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * v);
        return a << 24 | r << 16 | g << 8 | b;
    }

    static int fade(int color, float alpha) {
        return Math.round(Math.max(0.0F, Math.min(1.0F, alpha)) * (color >>> 24)) << 24 | color & 0xFFFFFF;
    }
}
