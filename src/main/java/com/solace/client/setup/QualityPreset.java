package com.solace.client.setup;

/**
 * Solace's three Astralis quality presets. Colors follow Difficulty Enhancement:
 * Performance uses Cozy's aqua, Balanced uses Normal's gold, Quality uses Extreme's red.
 * The settings each preset writes live in {@link QualityPresetApplier}.
 */
public enum QualityPreset {
    PERFORMANCE("performance", "Performance", 0x55FFFF, 0x050F0F,
            "For laptops and older PCs. 8 chunks, fast leaves and weather, no shaders, lighter particles,"
                    + " ambience and sound, and only the essential resource packs."),
    BALANCED("balanced", "Balanced", 0xD4A843, 0x0F0C05,
            "For most PCs. 10 chunks, fancy leaves, full particles and every resource pack, with"
                    + " trimmed water, weather and sound effects."),
    QUALITY("quality", "Quality", 0xFF3333, 0x100505,
            "For strong gaming PCs. 12 chunks, the full sound simulation, realtime dynamic lights and"
                    + " every ambient effect at its richest.");

    private final String id;
    private final String displayName;
    private final int accent;
    private final int background;
    private final String description;

    QualityPreset(String id, String displayName, int accent, int background, String description) {
        this.id = id;
        this.displayName = displayName;
        this.accent = accent;
        this.background = background;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** RGB accent color. */
    public int accent() {
        return accent;
    }

    /** RGB tinted background shown while selected. */
    public int background() {
        return background;
    }

    public String description() {
        return description;
    }

    /** Picks the value for this preset from (performance, balanced, quality). */
    <T> T pick(T performance, T balanced, T quality) {
        return switch (this) {
            case PERFORMANCE -> performance;
            case BALANCED -> balanced;
            case QUALITY -> quality;
        };
    }

    public static QualityPreset byId(String id) {
        if (id != null) {
            for (QualityPreset preset : values()) {
                if (preset.id.equals(id)) {
                    return preset;
                }
            }
        }
        return null;
    }

    /**
     * A conservative guess from the JVM heap and CPU threads. The GPU is unknown at
     * this point, so Quality is only suggested for clearly strong machines.
     */
    public static QualityPreset recommended() {
        long heapGiB = Runtime.getRuntime().maxMemory() / (1024L * 1024L * 1024L);
        int threads = Runtime.getRuntime().availableProcessors();
        if (heapGiB < 6 || threads <= 4) {
            return PERFORMANCE;
        }
        if (heapGiB >= 8 && threads >= 12) {
            return QUALITY;
        }
        return BALANCED;
    }
}
