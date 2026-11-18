package com.solace.client.loading;

import com.mojang.authlib.GameProfile;
import com.mojang.authlib.minecraft.MinecraftProfileTexture;
import com.mojang.blaze3d.vertex.PoseStack;
import com.solace.client.gui.SolaceArt;
import com.solace.client.gui.WorldCreationThemeRenderer;
import com.solace.config.DimensionLoadingScreenConfig;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ThreadLocalRandom;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

public final class DimensionLoadingScreenRenderer {
    private static final Map<Screen, Session> SESSIONS = new WeakHashMap<>();
    private static volatile UUID cachedProfileId;
    private static volatile ResourceLocation cachedSkin;
    private static volatile UUID requestedProfileId;
    private static volatile long skinRequestAt;

    private DimensionLoadingScreenRenderer() {
    }

    public static boolean render(Screen screen, GuiGraphics graphics, DimensionLoadingScreenConfig.Entry config,
                                 @Nullable Integer progress, @Nullable Component status) {
        if (config == null) {
            return false;
        }

        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        Session session = session(screen, config);
        int width = screen.width;
        int height = screen.height;
        int margin = Mth.clamp(width / 28, 18, 40);

        renderBackground(screen, graphics, config, width, height);
        graphics.fill(0, 0, width, height, config.backgroundTint());
        renderIdentity(graphics, minecraft, font, config, width, margin, Mth.clamp(height / 18, 28, 40));
        renderProgress(graphics, font, config, width, height, margin, progress, status);
        renderDimensionCopy(graphics, font, config, width, height, margin);
        renderTip(graphics, font, config, session.tip(), width, height, margin);
        renderCornerAnimation(graphics, config, session.startedAt(), width, height, margin);
        return true;
    }

    private static Session session(Screen screen, DimensionLoadingScreenConfig.Entry config) {
        Session existing = SESSIONS.get(screen);
        if (existing != null && existing.config() == config) {
            return existing;
        }
        List<String> tips = config.tips;
        String tip = tips.get(ThreadLocalRandom.current().nextInt(tips.size()));
        Session created = new Session(config, tip, System.currentTimeMillis());
        SESSIONS.put(screen, created);
        return created;
    }

    private static void renderBackground(Screen screen, GuiGraphics graphics, DimensionLoadingScreenConfig.Entry config,
                                         int width, int height) {
        LoadingScreenTextureCache.ImageTexture texture = LoadingScreenTextureCache.image(config.background);
        if (texture == null) {
            // No art for this dimension: the menus' lattice backdrop.
            WorldCreationThemeRenderer.renderBackdrop(screen, graphics);
            return;
        }
        blitCover(graphics, texture, 0, 0, width, height);
    }

    private static void renderIdentity(GuiGraphics graphics, Minecraft minecraft, Font font,
                                       DimensionLoadingScreenConfig.Entry config, int screenWidth, int margin, int size) {
        GameProfile profile = minecraft.getUser().getGameProfile();
        ResourceLocation skin = resolvePlayerSkin(minecraft, profile);
        int x = margin + 10;
        int y = margin + 10;
        renderAssetFrame(graphics, config, x, y, size);
        graphics.blit(skin, x, y, size, size, 8.0F, 8.0F, 8, 8, 64, 64);
        graphics.blit(skin, x, y, size, size, 40.0F, 8.0F, 8, 8, 64, 64);

        int textX = x + size + 15;
        float nameScale = 1.45F;
        int textY = y + Math.round((size - font.lineHeight * nameScale) / 2.0F);
        drawScaledFitted(graphics, font, profile.getName(), textX, textY, nameScale,
                Math.max(100, screenWidth / 2 - textX - 18), config.primaryTextColor(), true);
    }

    private static ResourceLocation resolvePlayerSkin(Minecraft minecraft, GameProfile profile) {
        captureCurrentPlayerSkin(minecraft);
        if (Objects.equals(cachedProfileId, profile.getId()) && isResolvedSkin(cachedSkin, profile.getId())) {
            return cachedSkin;
        }

        GameProfile enriched = new GameProfile(profile.getId(), profile.getName());
        enriched.getProperties().putAll(profile.getProperties());
        enriched.getProperties().putAll(minecraft.getProfileProperties());
        ResourceLocation immediate = minecraft.getSkinManager().getInsecureSkinLocation(enriched);
        if (isResolvedSkin(immediate, profile.getId())) {
            cachedSkin = immediate;
            cachedProfileId = profile.getId();
        }
        requestPlayerSkin(minecraft, enriched);
        return Objects.equals(cachedProfileId, profile.getId()) && cachedSkin != null
                ? cachedSkin
                : DefaultPlayerSkin.getDefaultSkin(profile.getId());
    }

    private static void requestPlayerSkin(Minecraft minecraft, GameProfile profile) {
        long now = System.currentTimeMillis();
        if (Objects.equals(requestedProfileId, profile.getId()) && now - skinRequestAt < 10_000L) {
            return;
        }
        requestedProfileId = profile.getId();
        skinRequestAt = now;
        minecraft.getSkinManager().registerSkins(profile, (type, location, texture) -> {
            if (type == MinecraftProfileTexture.Type.SKIN
                    && Objects.equals(profile.getId(), requestedProfileId)
                    && isResolvedSkin(location, profile.getId())) {
                cachedProfileId = profile.getId();
                cachedSkin = location;
            }
        }, false);
    }

    public static void captureCurrentPlayerSkin(Minecraft minecraft) {
        if (minecraft.player == null) {
            return;
        }

        GameProfile playerProfile = minecraft.player.getGameProfile();
        ResourceLocation playerSkin = minecraft.player.getSkinTextureLocation();
        if (playerProfile.getId() != null && playerSkin != null) {
            if (isResolvedSkin(playerSkin, playerProfile.getId())) {
                cachedProfileId = playerProfile.getId();
                cachedSkin = playerSkin;
            }
        }
    }

    private static boolean isResolvedSkin(@Nullable ResourceLocation location, @Nullable UUID profileId) {
        if (location == null || profileId == null) {
            return false;
        }
        return !location.equals(DefaultPlayerSkin.getDefaultSkin())
                && !location.equals(DefaultPlayerSkin.getDefaultSkin(profileId));
    }

    private static void renderProgress(GuiGraphics graphics, Font font,
                                       DimensionLoadingScreenConfig.Entry config, int width, int height, int margin,
                                       @Nullable Integer progress, @Nullable Component status) {
        int size = Mth.clamp(height / 18, 28, 40);
        int iconX = width - margin - 10 - size;
        int iconY = margin + 10;
        renderAssetFrame(graphics, config, iconX, iconY, size);

        LoadingScreenTextureCache.ImageTexture icon = LoadingScreenTextureCache.image(config.blockTexture);
        if (icon != null) {
            blitCover(graphics, icon, iconX, iconY, size, size);
        }

        String text = progress != null
                ? Mth.clamp(progress, 0, 100) + "%"
                : status == null ? null : status.getString().toUpperCase(java.util.Locale.ROOT);
        if (text == null || text.isBlank()) {
            return;
        }

        float scale = progress != null ? 1.55F : 1.18F;
        int right = iconX - 15;
        int textY = iconY + Math.round((size - font.lineHeight * scale) / 2.0F);
        drawRightScaledFitted(graphics, font, text, right, textY, scale,
                Math.min(210, Math.max(100, width / 3)), config.primaryTextColor());
    }

    private static void renderAssetFrame(GuiGraphics graphics, DimensionLoadingScreenConfig.Entry config,
                                         int x, int y, int size) {
        SolaceArt.panel(graphics::fill, x - 5, y - 5, size + 10, size + 10, 0xE80A0A0A, SolaceArt.Tones.NORMAL,
                SolaceArt.PANEL_PLAIN);
    }

    private static void renderDimensionCopy(GuiGraphics graphics, Font font,
                                            DimensionLoadingScreenConfig.Entry config,
                                            int width, int height, int margin) {
        int x = margin + 18;
        int labelY = Math.max(margin + 84, Math.round(height * 0.29F));
        float labelScale = 1.45F;
        drawScaled(graphics, font, config.dimensionLabel, x, labelY, labelScale,
                config.dimensionLabelColor(), true);
        int labelWidth = Math.round(font.width(config.dimensionLabel) * labelScale);
        int labelBottom = labelY + Math.round(font.lineHeight * labelScale);
        graphics.fill(x, labelBottom + 5, x + Math.max(54, labelWidth), labelBottom + 6, config.lineColor());

        int titleY = labelBottom + 24;
        float titleScale = Mth.clamp(height / 150.0F, 2.8F, 4.25F);
        int titleMaxWidth = Mth.clamp(Math.round(width * 0.58F), 260, 650);
        float fittedTitleScale = fittedScale(font, config.dimensionTitle, titleScale, titleMaxWidth);
        drawScaled(graphics, font, config.dimensionTitle, x, titleY, fittedTitleScale,
                config.primaryTextColor(), true);

        int descriptionY = titleY + Math.round(font.lineHeight * fittedTitleScale) + 19;
        int descriptionWidth = Mth.clamp(Math.round(width * 0.46F), 240, 540);
        drawWrappedScaled(graphics, font, config.description, x, descriptionY, descriptionWidth,
                1.18F, config.secondaryTextColor(), 4);
    }

    private static void renderTip(GuiGraphics graphics, Font font, DimensionLoadingScreenConfig.Entry config,
                                  String tip, int width, int height, int margin) {
        int panelWidth = Mth.clamp(Math.round(width * 0.37F), 250, 390);
        int panelHeight = Mth.clamp(height / 6, 84, 112);
        int x = margin + 10;
        int y = height - margin - panelHeight - 10;

        SolaceArt.panel(graphics::fill, x, y, panelWidth, panelHeight, config.panelColor(), SolaceArt.Tones.NORMAL,
                SolaceArt.PANEL_ORNATE);

        float labelScale = 1.35F;
        drawScaled(graphics, font, config.tipLabel, x + 14, y + 13, labelScale,
                config.tipLabelColor(), true);
        int dividerY = y + 13 + Math.round(font.lineHeight * labelScale) + 8;
        graphics.fill(x + 14, dividerY, x + panelWidth - 14, dividerY + 1, config.lineColor());
        drawWrappedScaled(graphics, font, tip, x + 14, dividerY + 11, panelWidth - 28,
                1.12F, config.secondaryTextColor(), 3);
    }

    private static void renderCornerAnimation(GuiGraphics graphics, DimensionLoadingScreenConfig.Entry config,
                                              long startedAt, int width, int height, int margin) {
        LoadingScreenTextureCache.AnimatedTexture animation = LoadingScreenTextureCache.animation(config.cornerAnimation);
        if (animation == null) {
            return;
        }
        int size = Mth.clamp(config.cornerAnimationSize, 32, Math.max(32, height / 3));
        int x = width - margin - size;
        int y = height - margin - size;
        int frame = animation.frame(System.currentTimeMillis() - startedAt);
        int sourceX = frame % animation.columns() * animation.frameSize();
        int sourceY = frame / animation.columns() * animation.frameSize();
        graphics.blit(animation.location(), x, y, size, size, (float) sourceX, (float) sourceY,
                animation.frameSize(), animation.frameSize(), animation.sheetWidth(), animation.sheetHeight());
    }

    private static void blitCover(GuiGraphics graphics, LoadingScreenTextureCache.ImageTexture texture,
                                  int x, int y, int width, int height) {
        float sourceAspect = (float) texture.width() / texture.height();
        float targetAspect = (float) width / Math.max(1, height);
        float sourceX = 0;
        float sourceY = 0;
        int sourceWidth = texture.width();
        int sourceHeight = texture.height();
        if (sourceAspect > targetAspect) {
            sourceWidth = Math.max(1, Math.round(texture.height() * targetAspect));
            sourceX = (texture.width() - sourceWidth) / 2.0F;
        } else if (sourceAspect < targetAspect) {
            sourceHeight = Math.max(1, Math.round(texture.width() / targetAspect));
            sourceY = (texture.height() - sourceHeight) / 2.0F;
        }
        graphics.blit(texture.location(), x, y, width, height, sourceX, sourceY,
                sourceWidth, sourceHeight, texture.width(), texture.height());
    }

    private static void drawScaled(GuiGraphics graphics, Font font, String text, int x, int y,
                                   float scale, int color, boolean shadow) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, shadow);
        pose.popPose();
    }

    private static void drawScaledFitted(GuiGraphics graphics, Font font, String text, int x, int y,
                                         float preferredScale, int maxWidth, int color, boolean shadow) {
        drawScaled(graphics, font, text, x, y, fittedScale(font, text, preferredScale, maxWidth), color, shadow);
    }

    private static void drawRightScaledFitted(GuiGraphics graphics, Font font, String text, int right, int y,
                                              float preferredScale, int maxWidth, int color) {
        int textWidth = Math.max(1, font.width(text));
        float scale = fittedScale(font, text, preferredScale, maxWidth);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(right, y, 0);
        pose.scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -textWidth, 0, color, true);
        pose.popPose();
    }

    private static float fittedScale(Font font, String text, float preferredScale, int maxWidth) {
        return Math.min(preferredScale, (float) maxWidth / Math.max(1, font.width(text)));
    }

    private static void drawWrappedScaled(GuiGraphics graphics, Font font, String text, int x, int y,
                                          int width, float scale, int color, int maxLines) {
        int unscaledWidth = Math.max(1, Math.round(width / scale));
        List<FormattedCharSequence> lines = font.split(Component.literal(text), unscaledWidth);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1.0F);
        for (int index = 0; index < Math.min(maxLines, lines.size()); index++) {
            graphics.drawString(font, lines.get(index), 0, index * (font.lineHeight + 2), color, true);
        }
        pose.popPose();
    }

    private record Session(DimensionLoadingScreenConfig.Entry config, String tip, long startedAt) {
    }
}
