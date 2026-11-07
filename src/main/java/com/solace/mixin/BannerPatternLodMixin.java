package com.solace.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solace.compat.performance.ClientPerformancePatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BannerBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.List;

/**
 * Banners render one full cloth quad per pattern layer. Villages and structures place many layered
 * banners, which cost ~3 s of render-thread time over a 3-minute village capture. Beyond the configured
 * distance the extra layers are a few pixels wide, so only the base colour is drawn there.
 */
@Mixin(BannerRenderer.class)
public abstract class BannerPatternLodMixin {
    @ModifyVariable(method = "render(Lnet/minecraft/world/level/block/entity/BannerBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("STORE"), ordinal = 0)
    private List<?> solace$baseLayerWhenFar(List<?> patterns, BannerBlockEntity banner, float partialTick,
                                             PoseStack poseStack, MultiBufferSource buffers, int light, int overlay) {
        if (patterns.size() <= 1 || banner.getLevel() == null) return patterns;
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        BlockPos pos = banner.getBlockPos();
        double dx = pos.getX() + 0.5D - camera.x, dy = pos.getY() + 0.5D - camera.y, dz = pos.getZ() + 0.5D - camera.z;
        double limit = ClientPerformancePatches.bannerLodDistance();
        return dx * dx + dy * dy + dz * dz > limit * limit ? patterns.subList(0, 1) : patterns;
    }
}
