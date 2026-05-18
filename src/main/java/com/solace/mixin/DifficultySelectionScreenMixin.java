package com.solace.mixin;

import com.misanthropy.linggango.difficulty_enhancement.LinggangoEvents;
import com.solace.client.SolaceClientState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(targets = "com.misanthropy.linggango.difficulty_enhancement.client.DifficultySelectionScreen", remap = false)
public abstract class DifficultySelectionScreenMixin {
    @Shadow
    private List<?> difficultyButtons;

    @Mutable
    @Shadow
    @Final
    private Component randomMessage;

    @Invoker("selectDifficulty")
    protected abstract void solace$selectDifficulty(LinggangoEvents.DifficultyDef difficulty);

    @Inject(method = "<init>", at = @At("TAIL"))
    private void solace$replaceHardcoreBottomMessage(CallbackInfo ci) {
        if (solace$isHardcoreWorld()) {
            this.randomMessage = Component.literal("You thought you could choose?");
        }
    }

    @Inject(method = {"init", "m_7856_"}, at = @At("TAIL"), remap = false, require = 0)
    private void solace$forceExtremeInHardcore(CallbackInfo ci) {
        if (!solace$isHardcoreWorld()) {
            return;
        }

        LinggangoEvents.DifficultyDef extreme = LinggangoEvents.getDifficultyDef("extreme");
        if (extreme == null) {
            return;
        }

        this.solace$selectDifficulty(extreme);

        this.difficultyButtons.removeIf(button -> !solace$isExtremeButton(button));

        for (Object button : this.difficultyButtons) {
            if (!(button instanceof AbstractWidget widget) || !(button instanceof DifficultyButtonAccess access)) {
                continue;
            }

            LinggangoEvents.DifficultyDef difficulty = access.solace$getDifficulty();
            boolean isExtreme = difficulty != null && "extreme".equals(difficulty.id);
            widget.active = isExtreme;
            widget.visible = isExtreme;
        }

        AbstractWidget undoButton = this.solace$getUndoButton();
        if (undoButton != null) {
            undoButton.active = false;
            undoButton.visible = false;
        }
    }

    private static boolean solace$isExtremeButton(Object button) {
        if (!(button instanceof DifficultyButtonAccess access)) {
            return false;
        }

        LinggangoEvents.DifficultyDef difficulty = access.solace$getDifficulty();
        return difficulty != null && "extreme".equals(difficulty.id);
    }

    private AbstractWidget solace$getUndoButton() {
        try {
            java.lang.reflect.Field field = this.getClass().getDeclaredField("undoButton");
            field.setAccessible(true);
            Object value = field.get(this);
            return value instanceof AbstractWidget widget ? widget : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static boolean solace$isHardcoreWorld() {
        if (SolaceClientState.wasLastCreatedWorldHardcore()) {
            return true;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getLevelData().isHardcore()) {
            return true;
        }

        IntegratedServer server = minecraft.getSingleplayerServer();
        return server != null && server.isHardcore();
    }
}
