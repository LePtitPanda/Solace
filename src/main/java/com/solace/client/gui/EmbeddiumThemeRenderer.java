package com.solace.client.gui;

import me.jellysquid.mods.sodium.client.gui.widgets.FlatButtonWidget;

public final class EmbeddiumThemeRenderer {
    private static final FlatButtonWidget.Style STYLE = createStyle();

    private EmbeddiumThemeRenderer() {
    }

    public static FlatButtonWidget.Style style() {
        return STYLE;
    }

    private static FlatButtonWidget.Style createStyle() {
        FlatButtonWidget.Style style = new FlatButtonWidget.Style();
        style.bgHovered = 0xE66A4E18;
        style.bgDefault = 0xD90C0C1C;
        style.bgDisabled = 0x9907070F;
        style.textDefault = WorldCreationThemeRenderer.TEXT;
        style.textDisabled = 0x88444455;
        return style;
    }
}
