package com.solace.mixin;

import com.faboslav.structurify.common.Structurify;
import com.faboslav.structurify.common.config.data.StructureData;
import com.solace.world.gates.RawDisabled;
import com.solace.world.gates.StructureGates;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

/**
 * Every per-structure disable in Structurify (worldgen, its placement resolver, locate, map search)
 * reads StructureData#isDisabled. For a gated structure the gate decides instead, so this reports
 * "not disabled" and Solace's own hooks refuse closed gates. All other Structurify settings stay.
 */
@Pseudo
@Mixin(targets = "com.faboslav.structurify.common.config.data.StructureData", remap = false)
public abstract class StructurifyStructureDataMixin implements RawDisabled {
    @Shadow(remap = false)
    private boolean isDisabled;
    @Unique
    private volatile ResourceLocation solace$id;

    @Inject(method = "isDisabled", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void solace$gateDecides(CallbackInfoReturnable<Boolean> cir) {
        if (StructureGates.isGated(solace$id())) {
            cir.setReturnValue(false);
        }
    }

    @Override
    public boolean solace$rawDisabled() {
        return isDisabled;
    }

    /** StructureData does not know its own id; find it once in Structurify's map. */
    @Unique
    private ResourceLocation solace$id() {
        ResourceLocation id = solace$id;
        if (id == null) {
            try {
                for (Map.Entry<String, StructureData> entry : Structurify.getConfig().getStructureData().entrySet()) {
                    if (entry.getValue() == (Object) this) {
                        id = ResourceLocation.tryParse(entry.getKey());
                        solace$id = id;
                        break;
                    }
                }
            } catch (RuntimeException ignored) {
                // Structurify is (re)loading its config; try again on the next call.
            }
        }
        return id;
    }
}
