package com.solace.mixin;

import com.solace.access.EnchantedShelfSlotsAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.ChiseledBookShelfBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChiseledBookShelfBlockEntity.class)
public class ChiseledBookShelfBlockEntityMixin implements EnchantedShelfSlotsAccess {
    @Unique
    private static final String solace$ENCHANTED_SLOTS_KEY = "SolaceEnchantedSlots";

    @Unique
    private int solace$enchantedSlotMask;

    @Inject(method = "load", at = @At("TAIL"))
    private void solace$readEnchantedShelfSlots(CompoundTag tag, CallbackInfo ci) {
        solace$enchantedSlotMask = tag.getInt(solace$ENCHANTED_SLOTS_KEY);
    }

    @Override
    public int solace$getEnchantedSlotMask() {
        return solace$enchantedSlotMask;
    }
}
