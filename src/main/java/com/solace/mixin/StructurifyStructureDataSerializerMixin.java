package com.solace.mixin;

import com.faboslav.structurify.common.config.data.StructureData;
import com.solace.world.gates.RawDisabled;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Structurify saves its own is_disabled value, never the gate override, so config/structurify.json is unchanged. */
@Pseudo
@Mixin(targets = "com.faboslav.structurify.common.config.serialization.StructureDataSerializer", remap = false)
public abstract class StructurifyStructureDataSerializerMixin {
    @Redirect(method = "save", remap = false, require = 0, at = @At(value = "INVOKE", remap = false,
            target = "Lcom/faboslav/structurify/common/config/data/StructureData;isDisabled()Z"))
    private static boolean solace$saveStoredValue(StructureData data) {
        return ((RawDisabled) data).solace$rawDisabled();
    }
}
