package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.block.portal.DoorwayPortalBlockEntity;
import com.github.mim1q.minecells.dimension.MineCellsDimension;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = DoorwayPortalBlockEntity.class, remap = false)
public abstract class MineCellsDoorwayPortalBlockEntityMixin {
    @Redirect(
            method = {
                    "canPlayerEnter",
                    "teleportPlayer"
            },
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;of(Lnet/minecraft/world/level/Level;)Lcom/github/mim1q/minecells/dimension/MineCellsDimension;",
                    remap = false
            ),
            remap = false
    )
    private MineCellsDimension solace$useUndergardenAsDoorwaySource(@Coerce Object world) {
        return MineCellsDoorwayCompat.sourceDimensionFor(world);
    }

    @Redirect(
            method = "getLabel",
            at = @At(
                    value = "FIELD",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;translationKey:Ljava/lang/String;",
                    remap = false
            ),
            remap = false
    )
    private String solace$labelUndergardenHub(MineCellsDimension dimension) {
        return MineCellsDoorwayCompat.translationKeyFor(dimension);
    }

    @Redirect(
            method = "lambda$teleportPlayer$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;m_129783_()Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private static Object solace$returnToUndergarden(@Coerce Object server) {
        return MineCellsDoorwayCompat.sourceWorldFor(server);
    }

    @Redirect(
            method = "lambda$teleportPlayer$1",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/github/mim1q/minecells/dimension/MineCellsDimension;getWorld(Lnet/minecraft/server/level/ServerLevel;)Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private static Object solace$routeOverworldDoorwayToUndergarden(MineCellsDimension dimension, @Coerce Object currentWorld) {
        return MineCellsDoorwayCompat.worldForMineCellsDimension(dimension, currentWorld);
    }
}
