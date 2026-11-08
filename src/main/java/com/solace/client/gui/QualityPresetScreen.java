package com.solace.client.gui;

import com.solace.client.setup.QualityPreset;
import com.solace.client.setup.QualityPresetApplier;
import com.solace.client.setup.SolaceClientSettings;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

/** Picks one of Solace's three quality presets, on first launch or from Options. */
public class QualityPresetScreen extends SolaceSetupScreen {
    private static final Component TITLE = Component.literal("Quality Presets");
    private static final Component SUBTITLE = Component.literal(
            "Solace tuned three presets for Astralis. Pick the one that fits your PC.");
    private static final Component FOOTNOTE = Component.literal(
            "Change it any time in Options. Some mod settings apply after a restart.");

    private final Screen parent;
    private final Supplier<Screen> firstLaunchNext;
    private QualityPreset selected;
    private SolaceHoldButton holdButton;

    private QualityPresetScreen(Screen parent, Supplier<Screen> firstLaunchNext) {
        super(TITLE, SUBTITLE, FOOTNOTE);
        this.parent = parent;
        this.firstLaunchNext = firstLaunchNext;
        QualityPreset current = SolaceClientSettings.getQualityPreset();
        this.selected = current != null ? current : QualityPreset.recommended();
    }

    /** First-launch step one; continues to the keybind step, then {@code next}. */
    public static QualityPresetScreen firstLaunch(Supplier<Screen> next) {
        return new QualityPresetScreen(null, next);
    }

    public static QualityPresetScreen fromOptions(Screen parent) {
        return new QualityPresetScreen(parent, null);
    }

    private boolean isFirstLaunch() {
        return firstLaunchNext != null;
    }

    @Override
    protected void init() {
        cards.clear();
        QualityPreset recommended = QualityPreset.recommended();
        for (QualityPreset preset : QualityPreset.values()) {
            Component tag = preset == recommended ? Component.literal("Suggested for your PC") : Component.empty();
            SolaceChoiceCard card = new SolaceChoiceCard(0, 0, 0, 0, Component.literal(preset.displayName()), tag,
                    Component.literal(preset.description()), clicked -> {
                        this.selected = preset;
                        select(clicked);
                        if (holdButton != null) {
                            holdButton.cancelHold();
                        }
                    }).withColors(preset.accent(), preset.background());
            card.setSelected(preset == selected);
            cards.add(card);
        }
        layoutCards();

        int y = buttonRowY();
        int centerX = this.width / 2;
        if (isFirstLaunch()) {
            holdButton = addRenderableWidget(new SolaceHoldButton(centerX - 100, y, 200, BUTTON_HEIGHT,
                    Component.literal("Hold to Continue"), this::selectedAccent, this::confirm));
        } else {
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                    .bounds(centerX - 155, y, 150, BUTTON_HEIGHT).build());
            holdButton = addRenderableWidget(new SolaceHoldButton(centerX + 5, y, 150, BUTTON_HEIGHT,
                    Component.literal("Hold to Apply"), this::selectedAccent, this::confirm));
        }
    }

    private void confirm() {
        playConfirmSound();
        // Switch screens first: a resource pack change starts a reload that should
        // fade back to the next screen, not to this one.
        Screen target = isFirstLaunch() ? new KeybindChoiceScreen(firstLaunchNext, this) : parent;
        this.minecraft.setScreen(target);
        QualityPresetApplier.apply(selected);
    }

    /**
     * Difficulty Enhancement's Normal confirm chime, for every preset. Played at full
     * volume: forUI's default 0.25 is near silent under a lowered master volume.
     */
    private void playConfirmSound() {
        this.minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0F, 1.0F));
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return !isFirstLaunch();
    }

    @Override
    public void onClose() {
        if (!isFirstLaunch()) {
            this.minecraft.setScreen(parent);
        }
    }
}
