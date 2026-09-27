package com.solace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lets Dungeon Now Loading's preserver transform doors and trapdoors into a
 * mending aura. The mod's normal transformation path snapshots the complete
 * block entity tag before replacing the block and reloads it when restoring
 * the block, so lock data on Supplementaries' netherite variants is retained.
 */
@Mixin(
        targets = "dev.hexnowloading.dungeonnowloading.game_event_listener.PreserverBlockDestructionSystem$Listener",
        remap = false
)
public abstract class PreserverDoorCompatibilityMixin {
    @Inject(method = "ignoreBlockTransformation", at = @At("RETURN"), cancellable = true, remap = false)
    private void solace$allowDoorsAndTrapdoors(
            ServerLevel level,
            BlockPos pos,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (callbackInfo.getReturnValueZ()) {
            return;
        }

        Block block = level.getBlockState(pos).getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock) {
            callbackInfo.setReturnValue(true);
        }
    }
}
