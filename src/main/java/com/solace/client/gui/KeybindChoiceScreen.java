package com.solace.client.gui;

import com.mojang.logging.LogUtils;
import com.solace.client.setup.SolaceClientSettings;
import java.util.function.Supplier;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

/** First-launch step two: keep the pack's keybinds, or open Keybind Atlas to edit them. */
public class KeybindChoiceScreen extends SolaceSetupScreen {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String ATLAS_SCREEN = "com.example.keybindatlas.render.KeyboardScreen";

    private final Supplier<Screen> next;
    private final Screen back;
    private boolean customize;

    public KeybindChoiceScreen(Supplier<Screen> next, Screen back) {
        super(Component.literal("Keybinds"),
                Component.literal("Astralis ships keybinds arranged so its mods don't clash. Keep them, or make them yours."),
                Component.literal("You can always edit keybinds later in Options > Controls."));
        this.next = next;
        this.back = back;
    }

    @Override
    protected void init() {
        cards.clear();
        SolaceChoiceCard keep = new SolaceChoiceCard(0, 0, 0, 0, Component.literal("Keep defaults"),
                Component.literal("Recommended"),
                Component.literal("Start playing with the keybinds set up by the modpack."),
                clicked -> {
                    customize = false;
                    select(clicked);
                });
        SolaceChoiceCard edit = new SolaceChoiceCard(0, 0, 0, 0, Component.literal("Customize"),
                Component.literal("Keybind Atlas"),
                Component.literal("Open the Keybind Atlas keyboard to see and change every binding before you play."),
                clicked -> {
                    customize = true;
                    select(clicked);
                });
        keep.setSelected(!customize);
        edit.setSelected(customize);
        cards.add(keep);
        cards.add(edit);
        layoutCards();

        int y = buttonRowY();
        int centerX = this.width / 2;
        if (back != null) {
            addRenderableWidget(Button.builder(CommonComponents.GUI_BACK, button -> this.minecraft.setScreen(back))
                    .bounds(centerX - 155, y, 150, BUTTON_HEIGHT).build());
            addRenderableWidget(Button.builder(Component.literal("Continue"), button -> finish())
                    .bounds(centerX + 5, y, 150, BUTTON_HEIGHT).build());
        } else {
            addRenderableWidget(Button.builder(Component.literal("Continue"), button -> finish())
                    .bounds(centerX - 100, y, 200, BUTTON_HEIGHT).build());
        }
    }

    private void finish() {
        SolaceClientSettings.markFirstLaunchComplete();
        Screen target = next.get();
        this.minecraft.setScreen(customize ? createKeybindEditor(target) : target);
    }

    private Screen createKeybindEditor(Screen parent) {
        if (ModList.get().isLoaded("keybindatlas")) {
            try {
                return (Screen) Class.forName(ATLAS_SCREEN).getConstructor(Screen.class).newInstance(parent);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                LOGGER.warn("[Solace] Could not open Keybind Atlas; falling back to vanilla key binds.", e);
            }
        }
        return new KeyBindsScreen(parent, this.minecraft.options);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
}
