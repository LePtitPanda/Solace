package com.solace.mixin;

import com.solace.client.performance.SheepVariantAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.animal.Sheep;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.github.nyuppo.client.renderer.entity.layers.ShearedWoolColorLayer", remap = false)
public abstract class ShearedWoolVariantMixin {
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/animal/Sheep;m_7380_(Lnet/minecraft/nbt/CompoundTag;)V", remap = false), remap = false)
    private void solace$readVariantWithoutSavingSheep(Sheep sheep, CompoundTag tag) {
        if (sheep instanceof SheepVariantAccess access) tag.putString("VariantID", access.solace$getVariant().getIdentifier().toString());
        else sheep.addAdditionalSaveData(tag);
    }
}
