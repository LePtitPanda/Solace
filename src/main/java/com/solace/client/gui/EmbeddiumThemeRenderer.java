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
        style.bgHovered = 0xE6242424;
        style.bgDefault = 0xD90A0A0A;
        style.bgDisabled = 0x99050505;
        style.textDefault = WorldCreationThemeRenderer.TEXT;
        style.textDisabled = 0x884A4A4A;
        return style;
    }
}
