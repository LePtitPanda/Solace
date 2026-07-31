package com.solace.mixin;

import it.hurts.sskirillss.relics.client.models.items.CurioModel;
import it.hurts.sskirillss.relics.items.relics.base.IRenderableCurio;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = CurioModel.class, remap = false)
public abstract class RelicsCurioModelMixin {

    @Redirect(
        method = "constructRoot",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/model/geom/EntityModelSet;m_171103_(Lnet/minecraft/client/model/geom/ModelLayerLocation;)Lnet/minecraft/client/model/geom/ModelPart;",
            remap = false
        ),
        remap = false
    )
    private static ModelPart solace$bakeMissingRelicLayer(
            EntityModelSet modelSet,
            ModelLayerLocation layerLocation,
            Item item
    ) {
        try {
            return modelSet.bakeLayer(layerLocation);
        } catch (IllegalArgumentException exception) {
            if (item instanceof IRenderableCurio renderableCurio) {
                return renderableCurio.constructLayerDefinition().bakeRoot();
            }
            throw exception;
        }
    }
}
