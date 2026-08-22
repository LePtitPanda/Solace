package com.solace.mixin;

import com.solace.horse.HorseCargoOpener;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractHorse.class)
public abstract class HorseInventoryScreenMixin {
    @Inject(method = "openCustomInventoryScreen", at = @At("HEAD"), cancellable = true)
    private void solace$openCargoScreen(Player player, CallbackInfo ci) {
        if ((Object) this instanceof Horse horse) {
            HorseCargoOpener.open(player, horse);
            ci.cancel();
        }
    }
}
