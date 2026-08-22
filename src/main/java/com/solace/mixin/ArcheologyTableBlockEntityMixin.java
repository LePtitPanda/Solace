package com.solace.mixin;

import com.solace.world.ArtifactEnchantingSupport;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.Pandarix.betterarcheology.block.entity.ArcheologyTableBlockEntity", remap = false)
public class ArcheologyTableBlockEntityMixin {
    @Inject(method = "generateCraftingLoot", at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$createConfiguredArtifact(BlockEntity blockEntity, Level level,
                                                 CallbackInfoReturnable<ItemStack> cir) {
        if (level != null && !level.isClientSide()) {
            cir.setReturnValue(ArtifactEnchantingSupport.createIdentifiedArtifact(level.getRandom()));
        }
    }
}
