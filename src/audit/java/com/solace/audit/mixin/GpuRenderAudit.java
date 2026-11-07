package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.solace.audit.GpuQueries;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(GameRenderer.class)
public abstract class GpuRenderAudit {
    @WrapMethod(method = "render")
    private void audit$render(float partialTick, long nanos, boolean renderLevel, Operation<Void> original) {
        int query = GpuQueries.begin(!Minecraft.getInstance().noRender);
        try { original.call(partialTick, nanos, renderLevel); }
        finally { GpuQueries.end(query); }
    }
}
