package com.solace.mixin;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

/** Replaces a full-server KubeJS entity scan with a cheap local entity hook. */
@Mixin(Entity.class)
public abstract class MoltenFluidEntityMixin {
    private static final Set<String> SOLACE$LAVA_LIKE_FLUIDS = Set.of(
            "fluid.kubejs.molten_brass",
            "fluid.kubejs.molten_netherite",
            "fluid.kubejs.molten_necromium",
            "fluid.kubejs.molten_sanguine"
    );

    @Inject(method = "baseTick", at = @At("TAIL"))
    private void solace$applyMoltenFluidEffects(CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        if (entity.level().isClientSide()) {
            return;
        }

        MinecraftServer server = entity.getServer();
        if (server == null || server.getTickCount() % 5 != 0) {
            return;
        }

        boolean inLavaLikeFluid = entity.isInFluidType(
                (fluidType, height) -> height > 0.0D && SOLACE$LAVA_LIKE_FLUIDS.contains(fluidType.getDescriptionId()),
                false
        );
        if (!inLavaLikeFluid) {
            return;
        }

        entity.setRemainingFireTicks(300);
        if (!(entity instanceof ItemEntity)) {
            entity.hurt(entity.damageSources().lava(), 4.0F);
        }
    }
}
