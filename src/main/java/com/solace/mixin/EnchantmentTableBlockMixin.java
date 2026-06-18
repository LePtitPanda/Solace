package com.solace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantmentTableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EnchantmentTableBlock.class)
public class EnchantmentTableBlockMixin {

    @Inject(method = "getMenuProvider", at = @At("HEAD"), cancellable = true)
    private void solace$getMenuProvider(BlockState state, Level level, BlockPos pos, CallbackInfoReturnable<MenuProvider> cir) {
        cir.setReturnValue(new SimpleMenuProvider(
            (containerId, playerInventory, player) -> {
                try {
                    Class<?> handlerClass = Class.forName("net.backupcup.mcde.screen.handler.RunicTableScreenHandler");
                    java.lang.reflect.Constructor<?> constructor = handlerClass.getConstructor(
                        int.class,
                        net.minecraft.world.entity.player.Inventory.class,
                        net.minecraft.world.inventory.ContainerLevelAccess.class
                    );
                    return (AbstractContainerMenu) constructor.newInstance(containerId, playerInventory, ContainerLevelAccess.create(level, pos));
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            },
            Component.translatable("block.mcde.runic_table")
        ));
    }
}
