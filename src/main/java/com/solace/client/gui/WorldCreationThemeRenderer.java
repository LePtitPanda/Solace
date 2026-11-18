package com.solace.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
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
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * Solace's front-end theme: monochrome, restrained frames with small flourishes, over a faint
 * lattice background. The pixel art lives in {@link SolaceArt}.
 */
public final class WorldCreationThemeRenderer {
    public static final int BACKGROUND = 0xFF0E0E0E;
    public static final int PANEL = 0xFF0A0A0A;
    public static final int TITLE = 0xFFFFFFFF;
    public static final int LIGHT = SolaceArt.LIGHT;
    public static final int LINE = SolaceArt.LINE;
    public static final int LINE_DIM = SolaceArt.DIM;
    public static final int TEXT = 0xFFE0E0E0;
    public static final int MUTED = 0xFF8A8A8A;
    public static final int MENU_HEADER_BOTTOM = 58;
    public static final int MENU_CONTENT_TOP = 84;

    private static final Map<AbstractWidget, ButtonState> BUTTON_STATES = new WeakHashMap<>();

    private WorldCreationThemeRenderer() {
    }

    public static void renderWorldCreationBackground(Screen screen, GuiGraphics graphics) {
        renderBackdrop(screen, graphics);

        Font font = Minecraft.getInstance().font;
        String title = "WORLD CREATION";
        int centerX = screen.width / 2;
        int titleY = getMenuTitleY(screen);
        graphics.drawCenteredString(font, Component.literal(title), centerX, titleY, TITLE);

        int lineHalfWidth = Mth.clamp(screen.width / 7, 105, 220);
        SolaceArt.headerRule(graphics::fill, centerX, titleY + font.lineHeight + 4, lineHalfWidth,
                SolaceArt.Tones.NORMAL);

        renderFooter(screen, graphics);
    }

    public static boolean isFrontEndScreen(Screen screen) {
        if (screen instanceof SolaceThemedScreen) {
            return true;
        }
        if (screen == null || Minecraft.getInstance().level != null || screen instanceof TitleScreen
                || screen instanceof GenericDirtMessageScreen) {
            return false;
        }

        String className = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return !className.contains("classselection")
                && !className.contains("enhancement");
    }

    public static boolean isThemedButtonScreen(Screen screen) {
        return screen instanceof TitleScreen || isFrontEndScreen(screen);
    }

    public static boolean isEmbeddiumVideoScreen(Screen screen) {
        return screen != null
                && screen.getClass().getName().equals("org.embeddedt.embeddium.gui.EmbeddiumVideoOptionsScreen");
    }

    /** Keybind Atlas draws its own frame; the themed header, title and footer lines overlap it. */
    public static boolean isKeybindAtlasScreen(Screen screen) {
        return screen != null
                && screen.getClass().getName().equals("com.example.keybindatlas.render.KeyboardScreen");
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
        if (!isFrontEndScreen(screen) || screen instanceof CreateWorldScreen || isEmbeddiumVideoScreen(screen)
                || isKeybindAtlasScreen(screen)) {
            return;
        }
        renderMenuHeader(screen, graphics);
        renderFooter(screen, graphics);
    }

    public static void renderMenuHeader(Screen screen, GuiGraphics graphics) {
        if (screen instanceof CreateWorldScreen || screen instanceof TitleScreen || isEmbeddiumVideoScreen(screen)
                || isKeybindAtlasScreen(screen)) {
            return;
        }

        String title = screen.getTitle().getString().trim();
        if (title.isEmpty()) {
            return;
        }

        Font font = Minecraft.getInstance().font;
        int centerX = screen.width / 2;
        int titleY = getMenuTitleY(screen);
        graphics.drawCenteredString(font, title.toUpperCase(Locale.ROOT), centerX, titleY, TITLE);

        int lineHalfWidth = Mth.clamp(screen.width / 6, 110, 260);
        SolaceArt.headerRule(graphics::fill, centerX, titleY + font.lineHeight + 4, lineHalfWidth,
                SolaceArt.Tones.NORMAL);
    }

    public static boolean shouldSuppressVanillaTitle(Screen screen, Component text, int y) {
        boolean duplicateHeader = isFrontEndScreen(screen)
                && !(screen instanceof CreateWorldScreen)
                && !isEmbeddiumVideoScreen(screen)
                && !isKeybindAtlasScreen(screen)
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
        if (!isFrontEndScreen(screen) || screen instanceof CreateWorldScreen || isEmbeddiumVideoScreen(screen)
                || isKeybindAtlasScreen(screen)) {
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
        // Capped so the meander under the title rule stays above the content at MENU_HEADER_BOTTOM.
        return Mth.clamp(screen.height / 11, 24, 36);
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

    private static ResourceLocation patternTexture;

    /** The background lattice tile, generated once from SolaceArt so it matches the previews. */
    private static ResourceLocation patternTexture() {
        if (patternTexture == null) {
            int size = SolaceArt.PATTERN_SIZE;
            NativeImage image = new NativeImage(size, size, false);
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    int argb = SolaceArt.patternPixel(x, y);
                    // NativeImage stores ABGR.
                    image.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF);
                }
            }
            patternTexture = Minecraft.getInstance().getTextureManager()
                    .register("solace_gui_pattern", new DynamicTexture(image));
        }
        return patternTexture;
    }

    public static void renderBackdrop(Screen screen, GuiGraphics graphics) {
        // Tiled lattice (the texture repeats past its size), darkened toward the bottom.
        int size = SolaceArt.PATTERN_SIZE;
        graphics.blit(patternTexture(), 0, 0, 0, 0.0F, 0.0F, screen.width, screen.height, size, size);
        graphics.fillGradient(0, 0, screen.width, screen.height, 0x00000000, 0x90000000);


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
        SolaceArt.footerRule(graphics::fill, screen.width / 2, footerY, Math.min(310, screen.width / 3),
                SolaceArt.Tones.NORMAL);
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

        // No hover scaling: pixel art smears when scaled by fractions.
        float emphasis = Math.max(state.hover, state.selected);
        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();

        int fill = !widget.active ? 0xC0050505
                : SolaceArt.lerp(height >= 60 ? 0xA60A0A0A : 0xE60A0A0A, SolaceArt.SLOT_HOVER, emphasis);
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, fill);
        SolaceArt.buttonFrame(graphics::fill, x, y, width, height, tones(widget.active, emphasis));

        Font font = Minecraft.getInstance().font;
        int textColor = !widget.active ? 0xFF4A4A4A : selected ? TITLE : lerpColor(state.hover, 0xFFC8C8C8, TITLE);
        String fitted = font.plainSubstrByWidth(widget.getMessage().getString(), Math.max(1, width - 10));
        graphics.drawCenteredString(font, fitted, x + width / 2, y + (height - font.lineHeight) / 2 + 1, textColor);
    }

    /** Line tones for a widget: normal at rest, brighter with hover or selection, dim when disabled. */
    public static SolaceArt.Tones tones(boolean active, float emphasis) {
        return active ? SolaceArt.Tones.NORMAL.mix(SolaceArt.Tones.HOVER, emphasis) : SolaceArt.Tones.DISABLED;
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
        graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2, widget.active ? 0xE60A0A0A : 0xC0050505);
        SolaceArt.Tones tones = tones(widget.active, state.hover);
        SolaceArt.buttonFrame(graphics::fill, x, y, width, height, tones);

        // Track as a recessed slot, handle as a small pill.
        int trackLeft = x + 8;
        int trackRight = x + width - 8;
        int trackY = y + height - 6;
        int handleX = Mth.clamp(trackLeft + (int) Math.round((trackRight - trackLeft) * value), trackLeft + 3,
                trackRight - 4);
        SolaceArt.slot(graphics::fill, trackLeft, trackY - 1, trackRight - trackLeft, 3, SolaceArt.SLOT);
        graphics.fill(trackLeft + 1, trackY, handleX, trackY + 1, widget.active ? LINE_DIM : 0xFF2A2A2A);
        SolaceArt.pill(graphics::fill, handleX, y + height / 2, tones);

        Font font = Minecraft.getInstance().font;
        int textColor = widget.active ? lerpColor(state.hover, 0xFFC8C8C8, TITLE) : 0xFF4A4A4A;
        String fitted = font.plainSubstrByWidth(widget.getMessage().getString(), Math.max(1, width - 20));
        graphics.drawCenteredString(font, fitted, x + width / 2, y + 3, textColor);
    }

    /**
     * Main menu entries are bare labels. Hover lights the label and grows a soft glow with a
     * hairline that reaches out to both sides from a small diamond next to the text.
     */
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

        Font font = Minecraft.getInstance().font;
        String label = widget.getMessage().getString().trim().toUpperCase(Locale.ROOT);
        int cx = widget.getX() + widget.getWidth() / 2;
        int cy = widget.getY() + widget.getHeight() / 2;
        float t = widget.active ? state.hover : 0.0F;
        float e = t * t * (3.0F - 2.0F * t);
        renderMenuGlow(graphics, cx, cy, font.width(label) / 2, e);

        int textColor = !widget.active ? 0xFF4A4A4A : lerpColor(e, 0xFF9A9A9A, TITLE);
        graphics.drawString(font, label, cx - font.width(label) / 2, cy - 4, textColor, false);
    }

    /** The hover glow of a main menu entry; {@code e} is 0 at rest and 1 fully lit. */
    static void renderMenuGlow(GuiGraphics graphics, int cx, int cy, int textHalf, float e) {
        if (e <= 0.01F) {
            return;
        }
        int reach = textHalf + 20 + Math.round(90 * e);
        // Haze: two stacked bands, brightest at the center and gone at the reach.
        for (int dx = -reach; dx < reach; dx += 2) {
            float f = 1.0F - Math.abs(dx + 1) / (float) reach;
            f *= f;
            graphics.fill(cx + dx, cy - 7, cx + dx + 2, cy + 7, Math.round(e * 22 * f) << 24 | 0xFFFFFF);
            graphics.fill(cx + dx, cy - 3, cx + dx + 2, cy + 3, Math.round(e * 26 * f) << 24 | 0xFFFFFF);
        }
        // Hairlines leaving the label on both sides, from a lit diamond, fading outward.
        int start = textHalf + 8;
        for (int x = start; x < reach; x++) {
            int color = Math.round(e * 230 * (1.0F - (x - start) / (float) (reach - start))) << 24 | 0xFFFFFF;
            graphics.fill(cx + x, cy, cx + x + 1, cy + 1, color);
            graphics.fill(cx - x - 1, cy, cx - x, cy + 1, color);
        }
        SolaceArt.Tones tones = SolaceArt.Tones.NORMAL.mix(SolaceArt.Tones.HOVER, e);
        SolaceArt.rivet(graphics::fill, cx + start - 4, cy - 1, tones);
        SolaceArt.rivet(graphics::fill, cx - start + 1, cy - 1, tones);
    }

    private static ResourceLocation titleSky;
    private static final int SKY_W = 256, SKY_H = 144;

    /** One sky pixel (ARGB): deep navy overhead, a violet haze rising behind the logo, dark corners. */
    static int skyPixel(int x, int y) {
        float u = x / (float) (SKY_W - 1), v = y / (float) (SKY_H - 1);
        float dx = (u - 0.5F) * 1.6F, dy = v - 0.78F;
        float haze = Mth.clamp(1.0F - (float) Math.sqrt(dx * dx + dy * dy * 2.2F), 0.0F, 1.0F);
        haze = haze * haze;
        float vignette = Mth.clamp(1.0F - ((u - 0.5F) * (u - 0.5F) + (v - 0.45F) * (v - 0.45F)) * 1.6F, 0.0F, 1.0F);
        int r = Math.round((4 + 22 * haze) * vignette);
        int g = Math.round((5 + 12 * haze + 3 * (1 - v)) * vignette);
        int b = Math.round((12 + 30 * haze + 8 * (1 - v)) * vignette);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /**
     * Main menu backdrop: a night sky matching the logo. Smooth gradient texture, then pixel
     * stars placed from a fixed seed so they never jump between frames; a few twinkle slowly.
     */
    public static void renderTitleBackdrop(Screen screen, GuiGraphics graphics) {
        if (titleSky == null) {
            NativeImage sky = new NativeImage(SKY_W, SKY_H, false);
            for (int y = 0; y < SKY_H; y++) {
                for (int x = 0; x < SKY_W; x++) {
                    int argb = skyPixel(x, y);
                    sky.setPixelRGBA(x, y, argb & 0xFF00FF00 | (argb & 0xFF) << 16 | argb >> 16 & 0xFF); // ABGR
                }
            }
            DynamicTexture texture = new DynamicTexture(sky);
            texture.setFilter(true, false); // smooth when stretched to the screen
            titleSky = Minecraft.getInstance().getTextureManager().register("solace_title_sky", texture);
        }
        int w = screen.width, h = screen.height;
        graphics.blit(titleSky, 0, 0, w, h, 0.0F, 0.0F, SKY_W, SKY_H, SKY_W, SKY_H);

        double time = Util.getMillis() / 1000.0;
        java.util.Random random = new java.util.Random(1999L);
        int count = w * h / 260;
        for (int i = 0; i < count; i++) {
            int x = random.nextInt(Math.max(1, w));
            // Denser toward the top, thinning near the horizon.
            int y = (int) (h * Math.pow(random.nextFloat(), 1.6));
            int base = 40 + random.nextInt(120);
            boolean twinkles = random.nextInt(5) == 0;
            double phase = random.nextDouble() * Math.PI * 2;
            boolean bright = random.nextInt(28) == 0;
            int alpha = twinkles ? (int) (base * (0.55 + 0.45 * Math.sin(time * 0.9 + phase))) : base;
            alpha = Math.round(alpha * Mth.clamp(1.3F - y / (float) h, 0.25F, 1.0F));
            int color = Mth.clamp(alpha, 0, 255) << 24 | (random.nextInt(3) == 0 ? 0xD8D0FF : 0xFFFFFF);
            graphics.fill(x, y, x + 1, y + 1, color);
            if (bright) {
                int arm = (color >>> 24) / 3 << 24 | color & 0xFFFFFF;
                graphics.fill(x - 1, y, x, y + 1, arm);
                graphics.fill(x + 1, y, x + 2, y + 1, arm);
                graphics.fill(x, y - 1, x + 1, y, arm);
                graphics.fill(x, y + 1, x + 1, y + 2, arm);
            }
        }
        graphics.fillGradient(0, h * 2 / 3, w, h, 0x00000000, 0x80000000);
    }

    public static void renderListRowBackdrop(GuiGraphics graphics, int left, int top, int width, int height,
                                               boolean hovered) {
        int right = left + Math.max(8, width - 4);
        int bottom = top + Math.max(8, height - 2);
        // Rows are inventory slots: a dark well with a black edge.
        SolaceArt.slot(graphics::fill, left + 1, top, right - left - 1, bottom - top,
                hovered ? 0xC0242424 : 0x9A141414);
    }

    public static void renderWorldJoinControl(GuiGraphics graphics, int left, int top, boolean hovered) {
        graphics.fill(left, top, left + 32, top + 32, hovered ? 0xFF1A1A1A : 0xFF050505);
        SolaceArt.frame(graphics::fill, left + 4, top + 4, 24, 24, tones(true, hovered ? 1.0F : 0.0F));
        renderChevron(graphics, left + 16, top + 16, 1, 0, hovered ? TITLE : LIGHT);
    }

    public static void renderResourcePackControls(GuiGraphics graphics, int left, int top,
                                                   int mouseX, int mouseY, boolean canSelect,
                                                   boolean canUnselect, boolean canMoveUp, boolean canMoveDown) {
        graphics.fill(left, top, left + 32, top + 32, 0xA8050505);
        if (canSelect) {
            boolean hovered = mouseX >= left && mouseX < left + 32 && mouseY >= top && mouseY < top + 32;
            graphics.fill(left + 3, top + 3, left + 29, top + 29, hovered ? 0xA6242424 : 0x66141414);
            SolaceArt.frame(graphics::fill, left + 4, top + 4, 24, 24, tones(true, hovered ? 1.0F : 0.0F));
            renderChevron(graphics, left + 16, top + 16, 1, 0, hovered ? TITLE : LIGHT);
            return;
        }

        graphics.fill(left + 16, top, left + 17, top + 32, 0x80808080);
        graphics.fill(left + 16, top + 16, left + 32, top + 17, 0x80808080);
        if (canUnselect) {
            boolean hovered = mouseX >= left && mouseX < left + 16 && mouseY >= top && mouseY < top + 32;
            graphics.fill(left + 1, top + 1, left + 16, top + 31, hovered ? 0xA6242424 : 0x40141414);
            renderChevron(graphics, left + 8, top + 16, -1, 0, hovered ? TITLE : LIGHT);
        }
        if (canMoveUp) {
            boolean hovered = mouseX >= left + 16 && mouseX < left + 32 && mouseY >= top && mouseY < top + 16;
            graphics.fill(left + 17, top + 1, left + 31, top + 16, hovered ? 0xA6242424 : 0x40141414);
            renderChevron(graphics, left + 24, top + 8, 0, -1, hovered ? TITLE : LIGHT);
        }
        if (canMoveDown) {
            boolean hovered = mouseX >= left + 16 && mouseX < left + 32 && mouseY >= top + 16 && mouseY < top + 32;
            graphics.fill(left + 17, top + 17, left + 31, top + 31, hovered ? 0xA6242424 : 0x40141414);
            renderChevron(graphics, left + 24, top + 24, 0, 1, hovered ? TITLE : LIGHT);
        }
    }

    public static void renderFavoriteControl(GuiGraphics graphics, int x, int y,
                                             boolean favorite, boolean hovered) {
        graphics.fill(x - 1, y - 1, x + 10, y + 10, hovered ? 0xE6242424 : 0xD9050505);
        renderBorder(graphics, x, y, 9, 9, hovered ? LIGHT : LINE);
        int color = favorite ? TITLE : (hovered ? TEXT : MUTED);
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
        graphics.fill(x + 2, y + 2, x + buttonWidth - 2, y + buttonHeight - 2,
                SolaceArt.lerp(0xFF0A0A0A, SolaceArt.SLOT_HOVER, hover));
        SolaceArt.buttonFrame(graphics::fill, x, y, buttonWidth, buttonHeight, tones(true, hover));

        Font font = Minecraft.getInstance().font;
        graphics.drawCenteredString(font, label, x + buttonWidth / 2, y + (buttonHeight - font.lineHeight) / 2 + 1,
                lerpColor(hover, 0xFFC8C8C8, TITLE));
    }

    public static void renderArrow(AbstractWidget widget, GuiGraphics graphics, boolean pointsLeft) {
        renderButton(widget, graphics, false);
        if (!widget.visible) {
            return;
        }

        int centerX = widget.getX() + widget.getWidth() / 2 + 2;
        int centerY = widget.getY() + widget.getHeight() / 2;
        int direction = pointsLeft ? -1 : 1;
        int color = widget.active ? LIGHT : 0xFF4A4A4A;
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
        float emphasis = Math.max(state.hover * 0.6F, state.selected);
        int x = widget.getX();
        int y = widget.getY();
        int width = widget.getWidth();
        int height = widget.getHeight();
        if (emphasis > 0.02F) {
            graphics.fill(x + 2, y + 2, x + width - 2, y + height - 2,
                    Math.round(emphasis * 120.0F) << 24 | 0x00242424);
        }
        SolaceArt.buttonFrame(graphics::fill, x, y, width, height, tones(true, emphasis));
    }

    public static int editBoxFillColor(EditBox editBox, int originalColor) {
        if (originalColor == 0xFF000000) {
            return SolaceArt.BLACK;
        }
        return editBox.isFocused() ? LIGHT : LINE;
    }

    /** Frames a region with the ornate panel frame, without filling it. */
    public static void renderBracketPanel(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        renderPanel(graphics, x, y, width, height, 0, SolaceArt.Tones.NORMAL.withLine(color),
                SolaceArt.PANEL_ORNATE);
    }

    /** A framed panel; {@code style} is one of SolaceArt's PANEL_ constants. A zero fill leaves it open. */
    public static void renderPanel(GuiGraphics graphics, int x, int y, int width, int height, int fill,
                                   SolaceArt.Tones tones, int style) {
        SolaceArt.panel(graphics::fill, x, y, width, height, fill, tones, style);
    }

    public static int withAlpha(int color, float alpha) {
        return Math.round(Mth.clamp(alpha, 0.0F, 1.0F) * (color >>> 24)) << 24 | color & 0xFFFFFF;
    }

    /** Darkens an accent toward black; 0 keeps it, 1 is black. */
    public static int shade(int color, float amount) {
        return lerpColor(amount, color, 0xFF000000);
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
}
