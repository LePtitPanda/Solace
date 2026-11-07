package com.solace.mixin;

import com.github.nyuppo.variant.MobVariant;
import com.solace.client.performance.SheepVariantAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.gen.Accessor;

// Accessors are generated after the ordinary mixin fields have been merged.
@Pseudo
@Mixin(targets = "net.minecraft.world.entity.animal.Sheep", priority = 900, remap = false)
public interface SheepVariantReadMixin extends SheepVariantAccess {
    @Dynamic("Field added by More Mob Variants 1.3.0.1")
    @Accessor(value = "variant", remap = false)
    MobVariant solace$getVariant();
}
