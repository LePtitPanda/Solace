package com.solace.mixin;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.server.ServerStartedEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Spawn locates its Dodo Island on every new server even when a datapack has
 * disabled every eligible biome. Its fallback search can evaluate millions of
 * biome positions on the server thread. Treat an empty eligibility tag as the
 * authoritative off switch.
 */
@Pseudo
@Mixin(targets = "com.ninni.spawn.server.events.ForgeEvents", remap = false)
public abstract class SpawnDodoIslandSearchMixin {

    private static final Logger LOGGER = LogManager.getLogger("Solace Spawn Compatibility");
    private static final TagKey<Biome> DODO_ISLAND_GENERATES = TagKey.create(
            Registries.BIOME,
            new ResourceLocation("spawn", "dodo_island_generates")
    );

    @Inject(
            method = "findDodoIsland",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false
    )
    private static void solace$skipDisabledDodoIslandSearch(
            ServerStartedEvent event,
            CallbackInfo callback
    ) {
        boolean hasEligibleBiome = event.getServer()
                .registryAccess()
                .registryOrThrow(Registries.BIOME)
                .getTag(DODO_ISLAND_GENERATES)
                .filter(tag -> tag.size() > 0)
                .isPresent();

        if (!hasEligibleBiome) {
            LOGGER.info("[Solace] Skipping Spawn's Dodo Island search because its biome tag is empty.");
            callback.cancel();
        }
    }
}
