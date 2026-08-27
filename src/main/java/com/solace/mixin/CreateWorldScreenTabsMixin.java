package com.solace.mixin;

import com.mojang.logging.LogUtils;
import com.solace.client.SolaceClientState;
import com.solace.client.gui.WorldCreationThemeRenderer;
import de.keksuccino.modernworldcreation.ModernWorldCreationGameTab;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.components.tabs.Tab;
import net.minecraft.client.gui.components.tabs.TabManager;
import net.minecraft.client.gui.components.tabs.TabNavigationBar;
import net.minecraft.client.gui.layouts.GridLayout;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.GenericDirtMessageScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.Util;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.WorldLoader;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.server.packs.repository.ServerPacksSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModLoader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.slf4j.Logger;

@Mixin(value = CreateWorldScreen.class, priority = 500)
public abstract class CreateWorldScreenTabsMixin extends Screen {
    private static final Logger SOLACE_LOGGER = LogUtils.getLogger();

    @Shadow
    @Final
    private TabManager tabManager;

    @Shadow
    @Final
    private WorldCreationUiState uiState;

    @Shadow
    private GridLayout bottomButtons;

    protected CreateWorldScreenTabsMixin(Component title) {
        super(title);
    }

    @Inject(method = "openFresh", at = @At("HEAD"), cancellable = true)
    private static void solace$prepareWorldCreationWithoutBlocking(Minecraft minecraft, Screen previousScreen,
                                                                   CallbackInfo ci) {
        minecraft.forceSetScreen(new GenericDirtMessageScreen(Component.translatable("createWorld.preparing")));

        PackRepository repository = new PackRepository(new ServerPacksSource());
        ModLoader.get().postEvent(new AddPackFindersEvent(PackType.SERVER_DATA, repository::addPackFinder));
        WorldLoader.PackConfig packConfig = new WorldLoader.PackConfig(
                repository, WorldDataConfiguration.DEFAULT, false, true);
        WorldLoader.InitConfig initConfig = new WorldLoader.InitConfig(
                packConfig, Commands.CommandSelection.INTEGRATED, 2);

        CompletableFuture<WorldCreationContext> preparation = WorldLoader.load(
                initConfig,
                context -> new WorldLoader.DataLoadOutput<>(
                        new SolaceDataPackReloadCookie(
                                new WorldGenSettings(
                                        WorldOptions.defaultWithRandomSeed(),
                                        WorldPresets.createNormalWorldDimensions(context.datapackWorldgen())),
                                context.dataConfiguration()),
                        context.datapackDimensions()),
                (resourceManager, dataPackResources, registries, cookie) -> {
                    resourceManager.close();
                    return new WorldCreationContext(
                            cookie.worldGenSettings(), registries, dataPackResources, cookie.dataConfiguration());
                },
                Util.backgroundExecutor(),
                minecraft);

        preparation.whenCompleteAsync((context, error) -> {
            if (error != null) {
                SOLACE_LOGGER.error("Failed to prepare the world creation screen", error);
                minecraft.setScreen(previousScreen);
                return;
            }
            minecraft.setScreen(CreateWorldScreenInvoker.solace$create(
                    minecraft, previousScreen, context, Optional.of(WorldPresets.NORMAL), OptionalLong.empty()));
        }, minecraft);
        ci.cancel();
    }

    @ModifyArg(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;addTabs([Lnet/minecraft/client/gui/components/tabs/Tab;)Lnet/minecraft/client/gui/components/tabs/TabNavigationBar$Builder;"
            )
    )
    private Tab[] solace$onlyShowGameTab(Tab[] tabs) {
        return Arrays.stream(tabs)
                .filter(CreateWorldScreenTabsMixin::solace$isGameTab)
                .toArray(Tab[]::new);
    }

    private static boolean solace$isGameTab(Tab tab) {
        if (tab instanceof ModernWorldCreationGameTab) {
            return true;
        }

        ComponentContents contents = tab.getTabTitle().getContents();
        return contents instanceof TranslatableContents translatableContents
                && "createWorld.tab.game.title".equals(translatableContents.getKey());
    }

    @Redirect(
            method = "init",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/worldselection/CreateWorldScreen;addRenderableWidget(Lnet/minecraft/client/gui/components/events/GuiEventListener;)Lnet/minecraft/client/gui/components/events/GuiEventListener;",
                    ordinal = 0
            )
    )
    private GuiEventListener solace$skipAddingTabNavigationBar(CreateWorldScreen screen, GuiEventListener widget) {
        return widget;
    }

    @Inject(method = "repositionElements", at = @At("RETURN"))
    private void solace$useFullContentArea(CallbackInfo ci) {
        if (this.bottomButtons != null) {
            this.tabManager.setTabArea(new ScreenRectangle(0, 0, this.width, this.bottomButtons.getY()));
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void solace$renderSelectedGameModeInfo(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        ModeInfo modeInfo = ModeInfo.forGameMode(this.uiState.getGameMode());
        WidgetBounds controlRowBounds = this.solace$getLowestControlRowBounds();
        int controlsBottom = controlRowBounds.bottom();
        int footerTop = this.bottomButtons != null ? this.bottomButtons.getY() - 18 : this.height - 56;
        int availableHeight = footerTop - controlsBottom - 18;

        if (availableHeight < 88 || controlRowBounds.width() < 280) {
            return;
        }

        int panelWidth = controlRowBounds.width();
        int panelHeight = Math.min(112, availableHeight);
        int x = controlRowBounds.left();
        int y = Math.min(controlsBottom + 18, footerTop - panelHeight);
        this.solace$renderModePanel(guiGraphics, modeInfo, x, y, panelWidth, panelHeight);
    }

    @Redirect(
            method = "render",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Lnet/minecraft/resources/ResourceLocation;IIFFIIII)V"
            )
    )
    private void solace$replaceFooterSeparator(GuiGraphics guiGraphics, ResourceLocation texture,
                                               int x, int y, float sourceX, float sourceY,
                                               int width, int height, int textureWidth, int textureHeight) {
        WorldCreationThemeRenderer.renderFooter((Screen) (Object) this, guiGraphics);
    }

    @Inject(method = {"init", "tick"}, at = @At("RETURN"))
    private void solace$preventEasyDifficulty(CallbackInfo ci) {
        SolaceClientState.setLastCreatedWorldWasHardcore(this.uiState.getGameMode() == WorldCreationUiState.SelectedGameMode.HARDCORE);
        if (this.uiState.getDifficulty() == Difficulty.EASY) {
            this.uiState.setDifficulty(Difficulty.NORMAL);
        }
    }

    private void solace$renderModePanel(GuiGraphics guiGraphics, ModeInfo modeInfo, int x, int y, int width, int height) {
        guiGraphics.fill(x, y, x + width, y + height, 0xE60C0C1C);
        WorldCreationThemeRenderer.renderBracketPanel(guiGraphics, x, y, width, height,
                WorldCreationThemeRenderer.GOLD);
        guiGraphics.fill(x + 66, y + 14, x + 67, y + height - 14,
                WorldCreationThemeRenderer.GOLD_DIM);

        this.solace$renderPanelIcon(guiGraphics, modeInfo.iconTexture(), x + 17, y + (height - 32) / 2);

        int textX = x + 84;
        int textRightPadding = 16;
        int textWidth = width - 84 - textRightPadding;
        guiGraphics.drawString(this.font, modeInfo.title(), textX, y + 13,
                WorldCreationThemeRenderer.GOLD, true);

        int lineY = y + 33;
        List<FormattedCharSequence> bodyLines = this.font.split(Component.literal(modeInfo.description()), textWidth);
        for (int i = 0; i < Math.min(3, bodyLines.size()); i++) {
            guiGraphics.drawString(this.font, bodyLines.get(i), textX, lineY + i * 10,
                    WorldCreationThemeRenderer.MUTED, false);
        }

        int noteY = y + 78;
        guiGraphics.fill(textX, noteY - 5, x + width - 18, noteY - 4,
                WorldCreationThemeRenderer.GOLD_DIM);
        List<FormattedCharSequence> noteLines = this.font.split(Component.literal(modeInfo.note()), textWidth);
        int maxNoteLines = Math.min(Math.max(1, (y + height - 8 - noteY) / 10), noteLines.size());
        for (int i = 0; i < maxNoteLines; i++) {
            guiGraphics.drawString(this.font, noteLines.get(i), textX, noteY + i * 10,
                    WorldCreationThemeRenderer.TEXT, false);
        }
    }

    private void solace$renderPanelIcon(GuiGraphics guiGraphics, ResourceLocation texture, int x, int y) {
        int[] frames = {0, 1, 2, 3, 4, 3, 2, 1};
        int frame = frames[(int) (Util.getMillis() / 150L % frames.length)];
        guiGraphics.blit(texture, x, y, 32, 32, 0.0F, frame * 16.0F, 16, 16, 16, 80);
    }

    private WidgetBounds solace$getLowestControlRowBounds() {
        int controlsBottom = 0;
        int footerCutoff = this.bottomButtons != null ? this.bottomButtons.getY() - 8 : this.height - 48;

        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget) {
                int widgetBottom = widget.getY() + widget.getHeight();
                if (widgetBottom < footerCutoff) {
                    controlsBottom = Math.max(controlsBottom, widgetBottom);
                }
            }
        }

        if (controlsBottom == 0) {
            return new WidgetBounds(48, this.width - 48, 0);
        }

        int left = this.width;
        int right = 0;
        for (Renderable renderable : this.renderables) {
            if (renderable instanceof AbstractWidget widget) {
                int widgetBottom = widget.getY() + widget.getHeight();
                if (Math.abs(widgetBottom - controlsBottom) <= 4) {
                    left = Math.min(left, widget.getX());
                    right = Math.max(right, widget.getX() + widget.getWidth());
                }
            }
        }

        if (right <= left) {
            return new WidgetBounds(48, this.width - 48, controlsBottom);
        }

        return new WidgetBounds(left, right, controlsBottom);
    }

    private record ModeInfo(String title, String description, String note, ResourceLocation iconTexture, int accentColor) {
        private static ModeInfo forGameMode(WorldCreationUiState.SelectedGameMode gameMode) {
            return switch (gameMode) {
                case CREATIVE -> new ModeInfo(
                        "CREATIVE MODE",
                        "Build freely, test mechanics, inspect structures, and explore the world of Astralis without limits.",
                        "All blocks and items are available.",
                        new ResourceLocation("solace", "textures/gui/gamemode_icons/enchanted_soul.png"),
                        0xFFFF78B8
                );
                case HARDCORE -> new ModeInfo(
                        "HARDCORE MODE",
                        "Survive carefully in a brutal world where every fight, fall, and mistake can be your last.",
                        "If you die, the world is lost forever.",
                        new ResourceLocation("solace", "textures/gui/gamemode_icons/corrupt_soul.png"),
                        0xFF432077
                );
                default -> new ModeInfo(
                        "SURVIVAL MODE",
                        "Progress through the story, explore new worlds, and forge your own fate.",
                        "Play with hunger, health, and limited resources.",
                        new ResourceLocation("solace", "textures/gui/gamemode_icons/soul.png"),
                        0xFFC9D7E8
                );
            };
        }
    }

    private record WidgetBounds(int left, int right, int bottom) {
        private int width() {
            return this.right - this.left;
        }
    }

    private record SolaceDataPackReloadCookie(WorldGenSettings worldGenSettings,
                                               WorldDataConfiguration dataConfiguration) {
    }
}
