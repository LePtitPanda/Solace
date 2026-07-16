package com.solace.compat;

import ad_astra_giselle_addon.common.content.proof.EntityProofDurationAccessor;
import ad_astra_giselle_addon.common.registry.AddonProofs;
import net.minecraft.world.entity.Entity;

/** Loaded only when the optional Ad Astra Giselle addon is present. */
public final class GulingSentinelProofSupport {
    private GulingSentinelProofSupport() {
    }

    public static void applyGravityProof(Entity entity) {
        if (entity instanceof EntityProofDurationAccessor proofAccessor) {
            proofAccessor.ad_astra_giselle_addon$setProofDuration(AddonProofs.GRAVITY, 1_000_000);
        }
    }
}
