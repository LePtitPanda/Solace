package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.solace.audit.AuditSession;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlacedFeature.class)
public abstract class FeatureAudit {
    @WrapMethod(method = "placeWithContext")
    private boolean audit$place(PlacementContext context, RandomSource random, BlockPos pos, Operation<Boolean> original) {
        try (AuditSession.Ticket ticket = AuditSession.open("placed_feature")) {
            if (ticket != null) ticket.begin(context.getLevel().registryAccess().registryOrThrow(Registries.PLACED_FEATURE).getKey((PlacedFeature) (Object) this),
                    getClass(), context.getLevel().getLevel().dimension().location(), pos);
            return original.call(context, random, pos);
        }
    }
}
