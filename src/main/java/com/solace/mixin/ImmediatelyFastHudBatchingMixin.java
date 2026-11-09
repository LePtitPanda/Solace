package com.solace.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.raphimc.immediatelyfastapi.BatchingAccess;
import net.raphimc.immediatelyfastapi.ImmediatelyFastApi;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Isolate LDLib renderers that flush the item buffer while drawing a hotbar slot. */
@Mixin(Gui.class)
public abstract class ImmediatelyFastHudBatchingMixin {
    @Unique
    private static final Map<Class<?>, Boolean> solace$itemRendererProviders = new ConcurrentHashMap<>();

    @Unique
    private int solace$unbatchedSlotDepth;

    @Inject(method = "renderSlot", at = @At("HEAD"))
    private void solace$pauseHudBatching(GuiGraphics graphics, int x, int y, float partialTick,
                                         Player player, ItemStack stack, int seed, CallbackInfo ci) {
        // A renderer may render another slot while its outer slot has batching paused.
        if (solace$unbatchedSlotDepth > 0) {
            solace$unbatchedSlotDepth++;
            return;
        }
        if (!solace$itemRendererProviders.computeIfAbsent(stack.getItem().getClass(),
                ImmediatelyFastHudBatchingMixin::solace$isItemRendererProvider)) {
            return;
        }
        BatchingAccess batching = ImmediatelyFastApi.getApiImpl().getBatching();
        if (batching.isHudBatching()) {
            batching.endHudBatching();
            solace$unbatchedSlotDepth = 1;
        }
    }

    @Inject(method = "renderSlot", at = @At("RETURN"))
    private void solace$resumeHudBatching(GuiGraphics graphics, int x, int y, float partialTick,
                                          Player player, ItemStack stack, int seed, CallbackInfo ci) {
        if (solace$unbatchedSlotDepth == 0) return;
        solace$unbatchedSlotDepth--;
        if (solace$unbatchedSlotDepth == 0) {
            ImmediatelyFastApi.getApiImpl().getBatching().beginHudBatching();
        }
    }

    @Unique
    private static boolean solace$isItemRendererProvider(Class<?> type) {
        if (type.getName().equals("com.lowdragmc.lowdraglib.client.renderer.IItemRendererProvider")) {
            return true;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            if (solace$isItemRendererProvider(implemented)) return true;
        }
        Class<?> parent = type.getSuperclass();
        return parent != null && solace$isItemRendererProvider(parent);
    }
}
