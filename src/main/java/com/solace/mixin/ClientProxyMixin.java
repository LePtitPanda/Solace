package com.solace.mixin;

import java.util.function.BiConsumer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.nbt.CompoundTag;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.api.fluid.JadeFluidObject;
import snownee.jade.util.ClientProxy;

@Mixin(value = ClientProxy.class, remap = false)
public class ClientProxyMixin {

    @Inject(method = "getFluidSpriteAndColor", at = @At("HEAD"), remap = false, cancellable = true)
    private static void solace$getFluidSpriteAndColor(JadeFluidObject fluidObject, BiConsumer<TextureAtlasSprite, Integer> consumer, CallbackInfo ci) {
        CompoundTag tag = fluidObject.getTag();
        System.out.println("[SolaceDebug] getFluidSpriteAndColor called! fluid: " + (fluidObject.getType() != null ? net.minecraft.core.registries.BuiltInRegistries.FLUID.getKey(fluidObject.getType()).toString() : "null") + ", tag: " + tag);
        if (tag != null && tag.contains("solace$color", 3)) { // 3 is IntTag
            int customColor = tag.getInt("solace$color");
            System.out.println("[SolaceDebug] getFluidSpriteAndColor overriding with color: " + customColor);
            net.minecraft.world.level.material.Fluid fluid = fluidObject.getType();
            net.minecraftforge.fluids.FluidStack stack = snownee.jade.util.CommonProxy.toFluidStack(fluidObject);
            net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions extensions = net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions.of(fluid);
            net.minecraft.resources.ResourceLocation texture = extensions.getStillTexture(stack);
            TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance().getTextureAtlas(net.minecraft.world.inventory.InventoryMenu.BLOCK_ATLAS).apply(texture);
            
            float alpha = snownee.jade.overlay.OverlayRenderer.alpha;
            int finalColor = customColor;
            if (alpha != 1.0F) {
                finalColor = snownee.jade.api.config.IWailaConfig.IConfigOverlay.applyAlpha(customColor, alpha);
            }
            consumer.accept(sprite, finalColor);
            ci.cancel();
        }
    }
}
