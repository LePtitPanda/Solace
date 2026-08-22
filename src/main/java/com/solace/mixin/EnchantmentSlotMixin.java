package com.solace.mixin;

import com.solace.access.BookChoiceLevelsAccess;
import java.util.EnumMap;
import java.util.Map;
import net.backupcup.mcde.util.EnchantmentSlot;
import net.backupcup.mcde.util.SlotPosition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = EnchantmentSlot.class, remap = false)
public class EnchantmentSlotMixin implements BookChoiceLevelsAccess {
    @Unique
    private static final String SOLACE_LEVELS_KEY = "SolaceChoiceLevels";

    @Unique
    private final Map<SlotPosition, Integer> solace$choiceLevels = new EnumMap<>(SlotPosition.class);

    @Override
    public int solace$getChoiceLevel(SlotPosition position) {
        return solace$choiceLevels.getOrDefault(position, 0);
    }

    @Override
    public void solace$setChoiceLevel(SlotPosition position, int level) {
        if (level > 0) {
            solace$choiceLevels.put(position, level);
        } else {
            solace$choiceLevels.remove(position);
        }
    }

    @Inject(method = "toNbt", at = @At("RETURN"), remap = false)
    private void solace$writeChoiceLevels(CallbackInfoReturnable<CompoundTag> cir) {
        if (solace$choiceLevels.isEmpty()) {
            return;
        }

        CompoundTag levels = new CompoundTag();
        solace$choiceLevels.forEach((position, level) -> levels.putInt(position.name(), level));
        cir.getReturnValue().put(SOLACE_LEVELS_KEY, levels);
    }

    @Inject(method = "fromNbt", at = @At("RETURN"), remap = false)
    private static void solace$readChoiceLevels(CompoundTag tag, SlotPosition slotPosition,
                                                Map<Enchantment, Integer> enchantments,
                                                CallbackInfoReturnable<EnchantmentSlot> cir) {
        EnchantmentSlot slot = cir.getReturnValue();
        if (slot == null || !tag.contains(SOLACE_LEVELS_KEY, CompoundTag.TAG_COMPOUND)) {
            return;
        }

        CompoundTag levels = tag.getCompound(SOLACE_LEVELS_KEY);
        BookChoiceLevelsAccess access = (BookChoiceLevelsAccess) slot;
        for (SlotPosition position : SlotPosition.values()) {
            if (levels.contains(position.name(), CompoundTag.TAG_INT)) {
                access.solace$setChoiceLevel(position, levels.getInt(position.name()));
            }
        }
    }
}
