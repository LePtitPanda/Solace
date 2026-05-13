package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.block.RiftBlock;
import com.github.mim1q.minecells.dimension.MineCellsDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = RiftBlock.class, remap = false)
public abstract class MineCellsRiftBlockMixin {
    @Redirect(
            method = "m_7892_",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;m_129783_()Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private Object solace$riftCollisionReturnsToUndergarden(@Coerce Object server) {
        return MineCellsDoorwayCompat.sourceWorldFor(server);
    }

    @Redirect(
            method = "getPlayerTeleportPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;of(Lnet/minecraft/world/level/Level;)Lcom/github/mim1q/minecells/dimension/MineCellsDimension;",
                    remap = false
            ),
            remap = false
    )
    private MineCellsDimension solace$lookupHubPortalDataFromUndergarden(@Coerce Object world) {
        return MineCellsDoorwayCompat.sourceDimensionFor(world);
    }

    @Redirect(
            method = "getPlayerTeleportPosition",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;m_129783_()Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private Object solace$riftFallbackSpawnInUndergarden(@Coerce Object server) {
        return MineCellsDoorwayCompat.sourceWorldFor(server);
    }
}
