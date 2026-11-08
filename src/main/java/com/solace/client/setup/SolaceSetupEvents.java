package com.solace.client.setup;

import com.solace.Solace;
import com.solace.client.gui.KeybindChoiceScreen;
import com.solace.client.gui.QualityPresetScreen;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.OptionsScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * First-launch setup (quality preset, then keybinds) in place of vanilla's narrator
 * onboarding, plus the quality preset entry in the Options screen.
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SolaceSetupEvents {
    private static boolean setupShownThisSession;

    private SolaceSetupEvents() {
    }

    @SubscribeEvent
    public static void onScreenOpening(ScreenEvent.Opening event) {
        Screen opening = event.getNewScreen();
        if (opening instanceof AccessibilityOnboardingScreen) {
            Options options = Minecraft.getInstance().options;
            options.onboardAccessibility = false;
            options.save();
            Supplier<Screen> title = () -> new TitleScreen(true);
            event.setNewScreen(shouldShowSetup() ? startSetup(title) : title.get());
        } else if (opening instanceof TitleScreen titleScreen && shouldShowSetup()) {
            event.setNewScreen(startSetup(() -> titleScreen));
        }
    }

    private static boolean shouldShowSetup() {
        return !setupShownThisSession && !SolaceClientSettings.isFirstLaunchComplete();
    }

    private static Screen startSetup(Supplier<Screen> next) {
        setupShownThisSession = true;
        QualityPresetScreen presetStep = QualityPresetScreen.firstLaunch(next);
        // A player who picked a preset but quit before the keybind step resumes there.
        if (SolaceClientSettings.getQualityPreset() != null) {
            return new KeybindChoiceScreen(next, presetStep);
        }
        return presetStep;
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof OptionsScreen screen)) {
            return;
        }
        AbstractWidget done = null;
        String doneText = CommonComponents.GUI_DONE.getString();
        for (GuiEventListener listener : event.getListenersList()) {
            if (listener instanceof AbstractWidget widget && widget.getMessage().getString().equals(doneText)) {
                done = widget;
            }
        }
        if (done == null) {
            return;
        }

        // Share the Done row: [Quality Presets] [Done], so the vanilla grid keeps its spacing.
        int centerX = screen.width / 2;
        done.setWidth(150);
        done.setX(centerX + 5);
        event.addListener(Button.builder(Component.literal("Quality Presets"),
                        button -> Minecraft.getInstance().setScreen(QualityPresetScreen.fromOptions(screen)))
                .bounds(centerX - 155, done.getY(), 150, done.getHeight())
                .build());
    }
}
