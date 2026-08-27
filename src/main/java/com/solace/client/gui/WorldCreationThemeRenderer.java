package com.solace.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Mth;

public final class WorldCreationThemeRenderer {
    public static final int BACKGROUND = 0xFF07070F;
    public static final int PANEL = 0xFF0C0C1C;
    public static final int GOLD = 0xFFD4A843;
    public static final int GOLD_DIM = 0xFF6A4E18;
    public static final int TEXT = 0xFFDDDDEE;
    public static final int MUTED = 0xFF7777AA;
    public static final int MENU_HEADER_BOTTOM = 58;
    public static final int MENU_CONTENT_TOP = 84;

    private static final Map<Screen, ThemeState> SCREEN_STATES = new WeakHashMap<>();
    private static final Map<AbstractWidget, ButtonState> BUTTON_STATES = new WeakHashMap<>();

    private WorldCreationThemeRenderer() {
    }

    public static void renderWorldCreationBackground(Screen screen, GuiGraphics graphics) {
        renderBackdrop(screen, graphics);

        Font font = Minecraft.getInstance().font;
        String title = "WORLD CREATION";
        int centerX = screen.width / 2;
        int titleY = getMenuTitleY(screen);
        graphics.drawCenteredString(font, Component.literal(title), centerX, titleY, GOLD);

        int lineHalfWidth = Mth.clamp(screen.width / 7, 105, 220);
        int lineY = titleY + font.lineHeight + 5;
        graphics.fill(centerX - lineHalfWidth, lineY, centerX + lineHalfWidth, lineY + 1, GOLD_DIM);

        renderFooter(screen, graphics);
    }

    public static boolean isFrontEndScreen(Screen screen) {
        if (screen == null || Minecraft.getInstance().level != null || screen instanceof TitleScreen
                || screen instanceof GenericDirtMessageScreen) {
            return false;
        }

        String className = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return !className.contains("difficultyselection")
                && !className.contains("classselection")
                && !className.contains("enhancement");
    }

    public static boolean isThemedButtonScreen(Screen screen) {
        return screen instanceof TitleScreen || isFrontEndScreen(screen);
    }

    public static boolean isEmbeddiumVideoScreen(Screen screen) {
        return screen != null
                && screen.getClass().getName().equals("org.embeddedt.embeddium.gui.EmbeddiumVideoOptionsScreen");
    }

    public static boolean isOculusShaderPackScreen(Screen screen) {
        return screen != null
                && screen.getClass().getName().equals("net.irisshaders.iris.gui.screen.ShaderPackScreen");
    }

    public static void renderFrontEndBackground(Screen screen, GuiGraphics graphics) {
        prepareFrontEndLayout(screen);
        renderBackdrop(screen, graphics);
    }

    public static void renderFrontEndChrome(Screen screen, GuiGraphics graphics) {
        if (!isFrontEndScreen(screen) || screen instanceof CreateWorldScreen || isEmbeddiumVideoScreen(screen)) {
            return;
        }
        renderMenuHeader(screen, graphics);
        renderFooter(screen, graphics);
    }

    public static void renderMenuHeader(Screen screen, GuiGraphics graphics) {
        if (screen instanceof CreateWorldScreen || screen instanceof TitleScreen || isEmbeddiumVideoScreen(screen)) {
            return;
        }

        String title = screen.getTitle().getString().trim();
        if (title.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int centerX = screen.width / 2;
        int titleY = getMenuTitleY(screen);
        graphics.fill(0, 0, screen.width, MENU_HEADER_BOTTOM, 0xD907070F);
        graphics.drawCenteredString(font, title.toUpperCase(Locale.ROOT), centerX, titleY, GOLD);

        int lineHalfWidth = Mth.clamp(screen.width / 6, 110, 260);
        int lineY = titleY + font.lineHeight + 5;
        graphics.fill(centerX - lineHalfWidth, lineY, centerX + lineHalfWidth, lineY + 1, GOLD_DIM);
    }

    public static boolean shouldSuppressVanillaTitle(Screen screen, Component text, int y) {
        boolean duplicateHeader = isFrontEndScreen(screen)
                && !(screen instanceof CreateWorldScreen)
                && !isEmbeddiumVideoScreen(screen)
                && y < MENU_CONTENT_TOP
                && text.getString().equals(screen.getTitle().getString());
        if (duplicateHeader) {
            return true;
        }

        if (!isOculusShaderPackScreen(screen) || y > MENU_HEADER_BOTTOM
                || !(text.getContents() instanceof TranslatableContents translatable)) {
            if (screen == null
                    || !screen.getClass().getName().equals("net.minecraft.client.gui.screens.packs.PackSelectionScreen")
                    || y >= MENU_HEADER_BOTTOM
                    || !(text.getContents() instanceof TranslatableContents packText)) {
                return false;
            }
            return packText.getKey().equals("pack.dropInfo");
        }
        String key = translatable.getKey();
        return key.equals("pack.iris.select.title") || key.equals("pack.iris.configure.title");
    }

    public static void prepareFrontEndLayout(Screen screen) {
        if (!isFrontEndScreen(screen) || screen instanceof CreateWorldScreen || isEmbeddiumVideoScreen(screen)) {
            return;
        }

        int targetY = MENU_HEADER_BOTTOM + 2;
        List<AbstractWidget> widgets = new ArrayList<>();
        List<AbstractSelectionList<?>> lists = new ArrayList<>();
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractSelectionList<?> list) {
                lists.add(list);
            }
            if (!(listener instanceof AbstractWidget widget)) {
                continue;
            }
            widgets.add(widget);
            if (widget instanceof StringWidget
                    && widget.getMessage().getString().equals(screen.getTitle().getString())) {
                widget.visible = false;
                continue;
            }
            if (widget.visible
                    && widget.getY() < MENU_HEADER_BOTTOM
                    && widget.getY() + widget.getHeight() > 2) {
                widget.setY(targetY);
            }
        }
        repositionCompactDoneButton(screen, widgets, lists);
    }

    private static int getMenuTitleY(Screen screen) {
        return Mth.clamp(screen.height / 11, 26, 40);
    }

    private static void repositionCompactDoneButton(Screen screen, List<AbstractWidget> widgets,
                                                    List<AbstractSelectionList<?>> lists) {
        int bottomRegionStart = Math.round(screen.height * 0.65F);
        List<AbstractWidget> bottomActions = widgets.stream()
                .filter(widget -> widget.visible && widget.getHeight() > 0
                        && widget.getY() >= bottomRegionStart && widget.getY() < screen.height)
                .toList();
        if (bottomActions.size() != 1) {
            return;
        }

        AbstractWidget done = bottomActions.get(0);
        if (!done.getMessage().getString().equals(CommonComponents.GUI_DONE.getString())) {
            return;
        }

        int targetY = -1;
        int contentBottom = -1;
        AbstractSelectionList<?> compactList = null;
        for (AbstractSelectionList<?> list : lists) {
            if (list.children().size() <= 10) {
                compactList = list;
                break;
            }
        }
        for (AbstractWidget widget : widgets) {
            if (widget == done || !widget.visible) {
                continue;
            }
            if (!(widget instanceof StringWidget)
                    && widget.getY() >= MENU_CONTENT_TOP
                    && widget.getY() < done.getY()) {
                contentBottom = Math.max(contentBottom, widget.getY() + widget.getHeight());
            }
        }

        if (contentBottom >= MENU_CONTENT_TOP) {
            targetY = contentBottom + 8;
        } else if (compactList != null) {
            targetY = MENU_CONTENT_TOP + 8 + compactList.children().size() * 25;
        }

        if (targetY >= MENU_CONTENT_TOP
                && targetY + done.getHeight() <= screen.height - 48) {
            done.setY(targetY);
        }
    }

    public static void renderBackdrop(Screen screen, GuiGraphics graphics) {
        graphics.fillGradient(0, 0, screen.width, screen.height, BACKGROUND, 0xFF030308);

        ThemeState state = SCREEN_STATES.computeIfAbsent(screen,
                ignored -> new ThemeState(screen.width, screen.height, Util.getMillis()));
        state.resize(screen.width, screen.height);
        state.render(graphics, Util.getMillis());

        int vignetteSize = Mth.clamp(Math.min(screen.width, screen.height) / 9, 24, 72);
        for (int i = 0; i < 8; i++) {
            int inset = i * vignetteSize / 8;
            int alpha = 10 + i * 4;
            int color = alpha << 24;
            graphics.fill(inset, inset, screen.width - inset, inset + 1, color);
            graphics.fill(inset, screen.height - inset - 1, screen.width - inset, screen.height - inset, color);
            graphics.fill(inset, inset, inset + 1, screen.height - inset, color);
            graphics.fill(screen.width - inset - 1, inset, screen.width - inset, screen.height - inset, color);
        }
    }

    public static void renderFooter(Screen screen, GuiGraphics graphics) {
        if (Minecraft.getInstance().getOverlay() != null) {
            return;
        }
        int footerY = getFooterY(screen);
        graphics.fill(0, footerY, screen.width, screen.height, 0xC9040409);
        graphics.fill(screen.width / 2 - Math.min(310, screen.width / 3), footerY,
                screen.width / 2 + Math.min(310, screen.width / 3), footerY + 1, GOLD_DIM);
    }

    public static int getFooterY(Screen screen) {
        int footerY = screen.height - 38;
        int bottomRegionStart = Math.round(screen.height * 0.65F);
        int firstBottomControlY = Integer.MAX_VALUE;
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget.visible
                    && widget.getHeight() > 0
                    && widget.getY() >= bottomRegionStart
                    && widget.getY() < screen.height) {
                firstBottomControlY = Math.min(firstBottomControlY, widget.getY());
            }
        }
        if (firstBottomControlY != Integer.MAX_VALUE) {
            footerY = Mth.clamp(firstBottomControlY - 18, screen.height / 2, screen.height - 16);
        }
        return footerY;
    }

    public static int getListBottomY(Screen screen) {
        int bottom = getFooterY(screen);
        for (GuiEventListener listener : screen.children()) {
            if (listener instanceof AbstractWidget widget
                    && widget.visible
                    && widget.getMessage().getString().equals(CommonComponents.GUI_DONE.getString())
                    && widget.getY() >= MENU_CONTENT_TOP
                    && widget.getY() < bottom) {
                // Keep the list out of the relocated button's click area. The layout
                // reserves eight pixels above compact Done buttons, so using y - 1
                // still leaves enough list height to avoid a false four-pixel scroll.
                bottom = Math.max(MENU_CONTENT_TOP + 20, widget.getY() - 1);
            }
        }
        return bottom;
    }

    public static void renderButton(AbstractWidget widget, GuiGraphics graphics, boolean selected) {
        renderButton(widget, graphics, selected, widget.isHoveredOrFocused());
    }

    public static void renderButton(AbstractWidget widget, GuiGraphics graphics, boolean selected, boolean hovered) {
        if (!widget.visible) {
            return;
        }

        long now = Util.getMillis();
        ButtonState state = BUTTON_STATES.computeIfAbsent(widget, ignored -> new ButtonState(now));
        state.update(now, hovered, selected);

        if (Minecraft.getInstance().screen instanceof TitleScreen) {
            renderTitleScreenButton(widget, graphics, selected, state);
            return;
        }

        float emphasis = Math.max(state.hover, state.selected);
        float scale = 1.0F + state.hover * 0.018F;
        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        float centerX = x + width / 2.0F;
        float centerY = y + height / 2.0F;

        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(centerX, centerY, 0.0F);
        pose.scale(scale, scale, 1.0F);
        pose.translate(-centerX, -centerY, 0.0F);

        boolean featureCard = height >= 60;
        int baseColor = selected ? 0xF06A4E18 : featureCard ? 0xA60C0C1C : 0xE60C0C1C;
        if (!widget.active) {
            baseColor = 0xB607070F;
        }
        graphics.fill(x, y, x + width, y + height, baseColor);

        if (emphasis > 0.02F) {
            int borderAlpha = Math.round((selected ? 180.0F : 105.0F) * emphasis);
            renderBorder(graphics, x, y, width, height, (borderAlpha << 24) | (GOLD & 0xFFFFFF));
        }

        int railWidth = 3 + Math.round(5.0F * state.hover + 8.0F * state.selected);
        int railColor = widget.active ? GOLD : 0xFF3A321F;
        graphics.fill(x, y, x + railWidth, y + height, railColor);

        Font font = Minecraft.getInstance().font;
        int textColor;
        if (!widget.active) {
            textColor = 0xFF444455;
        } else {
            textColor = lerpColor(state.hover, MUTED, TEXT);
            if (selected) {
                textColor = TEXT;
            }
        }
        String raw = widget.getMessage().getString();
        String fitted = font.plainSubstrByWidth(raw, Math.max(1, width - railWidth - 12));
        graphics.drawCenteredString(font, fitted, x + width / 2 + railWidth / 2,
                y + (height - font.lineHeight) / 2 + 1, textColor);

        pose.popPose();
    }

    public static void renderSlider(AbstractWidget widget, GuiGraphics graphics, double value) {
        if (!widget.visible) {
            return;
        }

        long now = Util.getMillis();
        ButtonState state = BUTTON_STATES.computeIfAbsent(widget, ignored -> new ButtonState(now));
        state.update(now, widget.isHoveredOrFocused(), false);

        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        int baseColor = widget.active ? 0xE60C0C1C : 0xB607070F;
        graphics.fill(x, y, x + width, y + height, baseColor);

        int borderAlpha = Math.round(70.0F + state.hover * 150.0F);
        renderBorder(graphics, x, y, width, height, borderAlpha << 24 | GOLD & 0xFFFFFF);
        graphics.fill(x, y, x + 3 + Math.round(state.hover * 2.0F), y + height,
                widget.active ? GOLD : 0xFF3A321F);

        int trackLeft = x + 9;
        int trackRight = x + width - 8;
        int trackY = y + height - 7;
        int progressX = Mth.clamp(trackLeft + (int) Math.round((trackRight - trackLeft) * value),
                trackLeft, trackRight);
        graphics.fill(trackLeft, trackY, trackRight, trackY + 1, 0xFF332B22);
        graphics.fill(trackLeft, trackY, progressX, trackY + 2, GOLD_DIM);
        graphics.fill(progressX - 2, trackY - 2, progressX + 2, trackY + 3,
                widget.active ? GOLD : 0xFF55472B);

        Font font = Minecraft.getInstance().font;
        int textColor = widget.active ? lerpColor(state.hover, MUTED, TEXT) : 0xFF444455;
        String fitted = font.plainSubstrByWidth(widget.getMessage().getString(), Math.max(1, width - 20));
        graphics.drawCenteredString(font, fitted, x + width / 2, y + 3, textColor);
    }

    private static void renderTitleScreenButton(AbstractWidget widget, GuiGraphics graphics,
                                                boolean selected, ButtonState state) {
        Screen currentScreen = Minecraft.getInstance().screen;
        boolean compactFooterText = currentScreen != null
                && widget.getHeight() <= 12
                && widget.getWidth() >= 150
                && widget.getY() >= currentScreen.height - 20;
        if (compactFooterText || widget.getMessage().getString().toLowerCase(Locale.ROOT).contains("copyright")) {
            return;
        }

        if (widget.getWidth() >= 300 && widget.getHeight() >= 120) {
            renderTitlePlayRegion(widget, graphics, state);
            return;
        }

        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        if (state.hover > 0.01F) {
            int hoverAlpha = Math.round(10.0F + state.hover * 34.0F);
            graphics.fill(x, y, x + width, y + height, hoverAlpha << 24 | 0x0007070F);
        }

        int animatedWidth = Math.round((width - 12) * state.hover);
        if (animatedWidth > 0) {
            int centerX = x + width / 2;
            graphics.fill(centerX - animatedWidth / 2, y + height - 2,
                    centerX + (animatedWidth + 1) / 2, y + height, GOLD);
        }

        Font font = Minecraft.getInstance().font;
        int textColor = widget.active ? lerpColor(state.hover, MUTED, TEXT) : 0xFF444455;
        graphics.drawCenteredString(font, widget.getMessage(), x + width / 2 + 1,
                y + (height - font.lineHeight) / 2 + 1, textColor);
    }

    private static void renderTitlePlayRegion(AbstractWidget widget, GuiGraphics graphics, ButtonState state) {
        Font font = Minecraft.getInstance().font;
        int centerX = widget.getX() + widget.getWidth() / 2;
        int textY = widget.getY() + widget.getHeight() - 48;
        String label = widget.getMessage().getString().trim();
        if (label.isEmpty()) {
            label = "PLAY";
        }
        label = label.toUpperCase(Locale.ROOT);
        int textColor = widget.active ? lerpColor(state.hover, MUTED, TEXT) : 0xFF444455;
        float textScale = 1.4F;
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.scale(textScale, textScale, 1.0F);
        graphics.drawCenteredString(font, label, Math.round(centerX / textScale),
                Math.round(textY / textScale), textColor);
        pose.popPose();

        int lineWidth = 28 + Math.round(54.0F * state.hover);
        int lineY = textY + Math.round(font.lineHeight * textScale) + 4;
        graphics.fill(centerX - lineWidth / 2, lineY,
                centerX + (lineWidth + 1) / 2, lineY + 1,
                widget.active ? lerpColor(state.hover, GOLD_DIM, GOLD) : 0xFF3A321F);
    }

    public static void renderListRowBackdrop(GuiGraphics graphics, int left, int top, int width, int height,
                                               boolean hovered) {
        int right = left + Math.max(8, width - 4);
        int bottom = top + Math.max(8, height - 2);
        graphics.fill(left + 1, top, right, bottom, hovered ? 0x66352A1A : 0x420C0C1C);
        graphics.fill(left + 34, bottom - 1, right, bottom, 0x596A4E18);
    }

    public static void renderWorldJoinControl(GuiGraphics graphics, int left, int top, boolean hovered) {
        graphics.fill(left, top, left + 32, top + 32, hovered ? 0xFF352A1A : 0xFF07070F);
        renderBorder(graphics, left + 5, top + 5, 22, 22, hovered ? GOLD : GOLD_DIM);
        renderChevron(graphics, left + 16, top + 16, 1, 0, hovered ? GOLD : TEXT);
    }

    public static void renderResourcePackControls(GuiGraphics graphics, int left, int top,
                                                   int mouseX, int mouseY, boolean canSelect,
                                                   boolean canUnselect, boolean canMoveUp, boolean canMoveDown) {
        graphics.fill(left, top, left + 32, top + 32, 0xA807070F);
        if (canSelect) {
            boolean hovered = mouseX >= left && mouseX < left + 32 && mouseY >= top && mouseY < top + 32;
            graphics.fill(left + 3, top + 3, left + 29, top + 29, hovered ? 0xA6352A1A : 0x66101020);
            renderBorder(graphics, left + 5, top + 5, 22, 22, hovered ? GOLD : GOLD_DIM);
            renderChevron(graphics, left + 16, top + 16, 1, 0, hovered ? GOLD : TEXT);
            return;
        }

        graphics.fill(left + 16, top, left + 17, top + 32, 0x806A4E18);
        graphics.fill(left + 16, top + 16, left + 32, top + 17, 0x806A4E18);
        if (canUnselect) {
            boolean hovered = mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 32;
            graphics.fill(left + 1, top + 1, left + 16, top + 31, hovered ? 0xA6352A1A : 0x40101020);
            renderChevron(graphics, left + 8, top + 16, -1, 0, hovered ? GOLD : TEXT);
        }
        if (canMoveUp) {
            boolean hovered = mouseX >= left + 16 && mouseX < left + 32 && mouseY >= top && mouseY < top + 16;
            graphics.fill(left + 17, top + 1, left + 31, top + 16, hovered ? 0xA6352A1A : 0x40101020);
            renderChevron(graphics, left + 24, top + 8, 0, -1, hovered ? GOLD : TEXT);
        }
        if (canMoveDown) {
            boolean hovered = mouseX >= left + 16 && mouseX < left + 32 && mouseY >= top + 16 && mouseY < top + 32;
            graphics.fill(left + 17, top + 17, left + 31, top + 31, hovered ? 0xA6352A1A : 0x40101020);
            renderChevron(graphics, left + 24, top + 24, 0, 1, hovered ? GOLD : TEXT);
        }
    }

    public static void renderFavoriteControl(GuiGraphics graphics, int x, int y,
                                             boolean favorite, boolean hovered) {
        graphics.fill(x - 1, y - 1, x + 10, y + 10, hovered ? 0xE6352A1A : 0xD907070F);
        renderBorder(graphics, x, y, 9, 9, hovered ? GOLD : GOLD_DIM);
        int color = favorite ? GOLD : (hovered ? TEXT : MUTED);
        graphics.fill(x + 4, y + 1, x + 5, y + 8, color);
        graphics.fill(x + 1, y + 4, x + 8, y + 5, color);
        graphics.fill(x + 2, y + 2, x + 3, y + 3, color);
        graphics.fill(x + 6, y + 2, x + 7, y + 3, color);
        graphics.fill(x + 2, y + 6, x + 3, y + 7, color);
        graphics.fill(x + 6, y + 6, x + 7, y + 7, color);
    }

    private static void renderChevron(GuiGraphics graphics, int centerX, int centerY,
                                      int directionX, int directionY, int color) {
        for (int offset = -3; offset <= 3; offset++) {
            int distance = Math.abs(offset);
            int x = directionX == 0 ? centerX + offset : centerX - directionX * distance;
            int y = directionY == 0 ? centerY + offset : centerY - directionY * distance;
            graphics.fill(x, y, x + 2, y + 2, color);
        }
    }

    public static void renderOculusShaderToggle(GuiGraphics graphics, int left, int top, int width, int height,
                                                 Component label, float hover) {
        int x = left + 1;
        int y = top;
        int buttonWidth = Math.max(8, width - 2);
        int buttonHeight = Math.max(14, height - 2);
        int base = lerpColor(hover * 0.55F, 0xFF0C0C1C, 0xFF352A1A);
        graphics.fill(x, y, x + buttonWidth, y + buttonHeight, base);

        int animatedWidth = Math.round((buttonWidth - 16) * hover);
        if (animatedWidth > 0) {
            int centerX = x + buttonWidth / 2;
            graphics.fill(centerX - animatedWidth / 2, y + buttonHeight - 2,
                    centerX + (animatedWidth + 1) / 2, y + buttonHeight, GOLD);
        }

        Font font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, label, x + buttonWidth / 2,
                y + (buttonHeight - font.lineHeight) / 2 + 1, lerpColor(hover, TEXT, GOLD));
    }

    public static void renderArrow(AbstractWidget widget, GuiGraphics graphics, boolean pointsLeft) {
        renderButton(widget, graphics, false);
        if (!widget.visible) {
            return;
        }

        int centerX = widget.getX() + widget.getWidth() / 2 + 2;
        int centerY = widget.getY() + widget.getHeight() / 2;
        int direction = pointsLeft ? -1 : 1;
        int color = widget.active ? GOLD : 0xFF444455;
        for (int i = -3; i <= 3; i++) {
            int x = centerX + direction * Math.abs(i);
            graphics.fill(x - 1, centerY + i - 1, x + 1, centerY + i + 1, color);
        }
    }

    public static void renderModeButtonOverlay(AbstractWidget widget, GuiGraphics graphics, boolean selected) {
        if (!widget.visible) {
            return;
        }

        long now = Util.getMillis();
        ButtonState state = BUTTON_STATES.computeIfAbsent(widget, ignored -> new ButtonState(now));
        state.update(now, widget.isHoveredOrFocused(), selected);
        float emphasis = Math.max(state.hover * 0.72F, state.selected);
        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        if (emphasis > 0.02F) {
            graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1,
                    Math.round(22.0F + emphasis * 35.0F) << 24 | 0x00352A1A);
        }

        int borderAlpha = Math.round(selected ? 255.0F : 62.0F + state.hover * 110.0F);
        renderBorder(graphics, x, y, width, height, borderAlpha << 24 | GOLD & 0xFFFFFF);
        int underlineWidth = Math.round((width - 16) * Math.max(state.hover, state.selected));
        if (underlineWidth > 0) {
            int centerX = x + width / 2;
            graphics.fill(centerX - underlineWidth / 2, y + height - 2,
                    centerX + (underlineWidth + 1) / 2, y + height, selected ? GOLD : GOLD_DIM);
        }
    }

    public static int editBoxFillColor(EditBox editBox, int originalColor) {
        if (originalColor == 0xFF000000) {
            return PANEL;
        }
        return editBox.isFocused() ? GOLD : GOLD_DIM;
    }

    public static void renderBracketPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        int corner = Mth.clamp(Math.min(width, height) / 7, 7, 14);
        graphics.fill(x, y, x + corner, y + 2, color);
        graphics.fill(x + width - corner, y, x + width, y + 2, color);
        graphics.fill(x, y + height - 2, x + corner, y + height, color);
        graphics.fill(x + width - corner, y + height - 2, x + width, y + height, color);
        graphics.fill(x, y, x + 2, y + corner, color);
        graphics.fill(x + width - 2, y, x + width, y + corner, color);
        graphics.fill(x, y + height - corner, x + 2, y + height, color);
        graphics.fill(x + width - 2, y + height - corner, x + width, y + height, color);
    }

    private static void renderBorder(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x - 1, y - 1, x + width + 1, y, color);
        graphics.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
        graphics.fill(x - 1, y, x, y + height, color);
        graphics.fill(x + width, y, x + width + 1, y + height, color);
    }

    private static int lerpColor(float progress, int from, int to) {
        float value = Mth.clamp(progress, 0.0F, 1.0F);
        int red = Math.round(Mth.lerp(value, from >> 16 & 0xFF, to >> 16 & 0xFF));
        int green = Math.round(Mth.lerp(value, from >> 8 & 0xFF, to >> 8 & 0xFF));
        int blue = Math.round(Mth.lerp(value, from & 0xFF, to & 0xFF));
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }

    private static final class ButtonState {
        private float hover;
        private float selected;
        private long lastFrame;

        private ButtonState(long now) {
            this.lastFrame = now;
        }

        private void update(long now, boolean hovered, boolean isSelected) {
            float delta = Mth.clamp((now - this.lastFrame) / 1000.0F, 0.0F, 0.1F);
            this.lastFrame = now;
            this.hover += ((hovered ? 1.0F : 0.0F) - this.hover) * Math.min(1.0F, delta * 13.0F);
            this.selected += ((isSelected ? 1.0F : 0.0F) - this.selected) * Math.min(1.0F, delta * 10.0F);
        }
    }

    private static final class ThemeState {
        private final Particle[] particles = new Particle[38];
        private int width;
        private int height;
        private long lastFrame;

        private ThemeState(int width, int height, long now) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
            this.lastFrame = now;
            for (int i = 0; i < this.particles.length; i++) {
                this.particles[i] = Particle.create(i, this.width, this.height);
            }
        }

        private void resize(int width, int height) {
            this.width = Math.max(1, width);
            this.height = Math.max(1, height);
        }

        private void render(GuiGraphics graphics, long now) {
            float delta = Mth.clamp((now - this.lastFrame) / 1000.0F, 0.0F, 0.1F);
            this.lastFrame = now;
            for (Particle particle : this.particles) {
                particle.y -= particle.speed * delta;
                particle.x += particle.drift * delta;
                int bandTop = Math.round(this.height * 0.76F);
                if (particle.y < bandTop || particle.x < -4.0F || particle.x > this.width + 4.0F) {
                    particle.respawn(this.width, this.height);
                }
                int alpha = Math.round(255.0F * particle.alpha);
                int rgb = particle.aqua ? 0x55FFFF : 0xD4A843;
                int size = particle.large ? 2 : 1;
                graphics.fill(Math.round(particle.x), Math.round(particle.y),
                        Math.round(particle.x) + size, Math.round(particle.y) + size, alpha << 24 | rgb);
            }
        }
    }

    private static final class Particle {
        private float x;
        private float y;
        private float speed;
        private float drift;
        private float alpha;
        private boolean aqua;
        private boolean large;
        private int generation;

        private static Particle create(int index, int width, int height) {
            Particle particle = new Particle();
            particle.generation = index * 37 + 11;
            particle.randomize(width, height, false);
            return particle;
        }

        private void respawn(int width, int height) {
            this.generation += 97;
            this.randomize(width, height, true);
        }

        private void randomize(int width, int height, boolean fromBottom) {
            int seed = this.generation;
            this.x = Math.floorMod(seed * 73, Math.max(1, width));
            int bandTop = Math.round(height * 0.76F);
            int bandHeight = Math.max(1, height - bandTop);
            this.y = fromBottom ? height + Math.floorMod(seed, 18)
                    : bandTop + Math.floorMod(seed * 41, bandHeight);
            this.speed = 34.0F + Math.floorMod(seed * 17, 430) / 10.0F;
            this.drift = (Math.floorMod(seed * 29, 21) - 10) / 2.0F;
            this.alpha = 0.20F + Math.floorMod(seed * 31, 32) / 100.0F;
            this.aqua = Math.floorMod(seed, 9) == 0;
            this.large = Math.floorMod(seed, 7) == 0;
        }
    }
}
