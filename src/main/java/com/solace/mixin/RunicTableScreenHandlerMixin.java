package com.solace.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Block;

@Mixin(targets = "net.backupcup.mcde.screen.handler.RunicTableScreenHandler", remap = false)
public class RunicTableScreenHandlerMixin {

    @Shadow(remap = false)
    private ContainerLevelAccess context;

    @Inject(method = {"stillValid", "m_6875_", "canUse"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void solace$stillValid(Player player, CallbackInfoReturnable<Boolean> cir) {
        Block runicTable = null;
        try {
            Class<?> modBlocksClass = Class.forName("net.backupcup.mcde.block.ModBlocks");
            runicTable = (Block) modBlocksClass.getField("RUNIC_TABLE").get(null);
        } catch (Exception e) {
            // Runic table block might not be loaded, which is fine
        }
        
        boolean valid = AbstractContainerMenuAccessor.invokeStillValid(this.context, player, Blocks.ENCHANTING_TABLE)
            || (runicTable != null && AbstractContainerMenuAccessor.invokeStillValid(this.context, player, runicTable));
        
        cir.setReturnValue(valid);
    }
}
