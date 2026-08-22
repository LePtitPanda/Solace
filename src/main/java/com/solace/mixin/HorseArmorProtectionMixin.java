package com.solace.mixin;

import com.solace.horse.HorseArmorStats;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.HorseArmorItem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Horse.class)
public abstract class HorseArmorProtectionMixin {
    @Redirect(method = "setArmorEquipment", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/item/HorseArmorItem;getProtection()I"))
    private int solace$useBalancedArmorValue(HorseArmorItem armor) {
        return HorseArmorStats.of(armor).armor();
    }
}
