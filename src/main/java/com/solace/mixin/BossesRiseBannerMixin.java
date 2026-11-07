package com.solace.mixin;

import com.solace.client.performance.DragonBannerIndex;
import net.minecraft.client.Options;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.eventbus.api.Event;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;

@Pseudo
@Mixin(targets = "net.unusual.blockfactorysbosses.procedures.RenderDragonBannerProcedure", remap = false)
public abstract class BossesRiseBannerMixin {
    @Unique private static boolean solace$indexedCall;

    @Inject(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;D)V",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void solace$prepareBannerIndex(Event event, LevelAccessor world, double partialTick, CallbackInfo ci) {
        solace$indexedCall = world instanceof ClientLevel client && DragonBannerIndex.prepare(client);
        if (solace$indexedCall && DragonBannerIndex.entries().isEmpty()) ci.cancel();
    }

    @Redirect(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;D)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;m_193772_()I", remap = false), remap = false)
    private static int solace$visitIndexedEntriesOnce(Options options) {
        return solace$indexedCall ? 0 : options.getEffectiveRenderDistance();
    }

    @Redirect(method = "execute(Lnet/minecraftforge/eventbus/api/Event;Lnet/minecraft/world/level/LevelAccessor;D)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/chunk/LevelChunk;m_62954_()Ljava/util/Map;", remap = false), remap = false)
    private static Map<BlockPos, BlockEntity> solace$onlyBannerEntries(LevelChunk chunk) {
        return solace$indexedCall ? DragonBannerIndex.entries() : chunk.getBlockEntities();
    }
}
