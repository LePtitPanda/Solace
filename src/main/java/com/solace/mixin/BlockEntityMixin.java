package com.solace.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntity.class)
public abstract class BlockEntityMixin {
    @Unique
    private static final String solace$ENCHANTED_SLOTS_KEY = "SolaceEnchantedSlots";

    @Inject(method = "getUpdateTag", at = @At("RETURN"))
    private void solace$syncEnchantedShelfSlots(CallbackInfoReturnable<CompoundTag> cir) {
        if ((Object) this instanceof ChiseledBookShelfBlockEntity shelf) {
            int mask = 0;
            for (int slot = 0; slot < shelf.getContainerSize(); slot++) {
                if (shelf.getItem(slot).is(Items.ENCHANTED_BOOK)) {
                    mask |= 1 << slot;
                }
            }
            cir.getReturnValue().putInt(solace$ENCHANTED_SLOTS_KEY, mask);
        }
    }

    @Inject(method = "getUpdatePacket", at = @At("HEAD"), cancellable = true)
    private void solace$sendEnchantedShelfSlots(
            CallbackInfoReturnable<net.minecraft.network.protocol.Packet<net.minecraft.network.protocol.game.ClientGamePacketListener>> cir) {
        if ((Object) this instanceof ChiseledBookShelfBlockEntity) {
            cir.setReturnValue(ClientboundBlockEntityDataPacket.create((BlockEntity) (Object) this));
        }
    }
}
