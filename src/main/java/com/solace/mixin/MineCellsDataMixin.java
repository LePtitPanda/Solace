package com.solace.mixin;

import com.solace.compat.MineCellsDoorwayCompat;
import com.github.mim1q.minecells.world.state.MineCellsData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = MineCellsData.class, remap = false)
public abstract class MineCellsDataMixin {
    @Redirect(
            method = "get",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/server/MinecraftServer;m_129783_()Lnet/minecraft/server/level/ServerLevel;",
                    remap = false
            ),
            remap = false
    )
    @Coerce
    private static Object solace$storeDoorwayDataInUndergarden(@Coerce Object server) {
        return MineCellsDoorwayCompat.sourceWorldFor(server);
    }
}
