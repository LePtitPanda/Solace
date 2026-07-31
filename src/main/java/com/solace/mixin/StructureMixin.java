package com.solace.mixin;

import com.solace.world.IntroVillageStructureProtection;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(Structure.class)
public class StructureMixin {

    @Inject(
            method = "findValidGenerationPoint",
            at = @At("HEAD"),
            cancellable = true
    )
    private void solace$preventSpawnNearVillage(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Structure structure = (Structure) (Object) this;
        ResourceLocation id = context.registryAccess().registryOrThrow(Registries.STRUCTURE).getKey(structure);

        // If the structure is a village, do not restrict it
        if (id != null && id.getPath().contains("village")) {
            return;
        }

        if (IntroVillageStructureProtection.shouldPreventStructure(
                context.biomeSource(),
                context.seed(),
                context.chunkPos()
        )) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
