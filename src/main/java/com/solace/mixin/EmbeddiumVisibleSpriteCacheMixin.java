package com.solace.mixin;

import com.solace.compat.performance.ClientPerformancePatches;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSection;
import me.jellysquid.mods.sodium.client.render.chunk.RenderSectionManager;
import me.jellysquid.mods.sodium.client.render.chunk.lists.ChunkRenderList;
import me.jellysquid.mods.sodium.client.render.chunk.lists.SortedRenderLists;
import me.jellysquid.mods.sodium.client.render.chunk.region.RenderRegion;
import me.jellysquid.mods.sodium.client.render.texture.SpriteUtil;
import me.jellysquid.mods.sodium.client.util.iterator.ByteIterator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;

/**
 * Embeddium marks animated sprites "active" every frame by walking every visible region and section
 * ({@code tickVisibleRenders}). The visible set only changes when the render lists are rebuilt or
 * rebuilt chunk meshes are uploaded, and most sections share the same few sprites (water, lava, fire,
 * modded machines). This keeps a de-duplicated sprite array, rebuilds it only when Embeddium swaps
 * in new render lists or uploads chunks, and otherwise marks just that array.
 *
 * Idea credited to Celeritas (embeddedt, "Refactor visible sprite ticking"); this is an independent
 * implementation for Embeddium 0.3.x on 1.20.1. Launch with -Dsolace.spriteTickStats=true to log
 * the average cost of this method with the cache on or off.
 */
@Mixin(value = RenderSectionManager.class, remap = false)
public abstract class EmbeddiumVisibleSpriteCacheMixin {
    @Unique private static final boolean solace$CACHE = ClientPerformancePatches.visibleSpriteCache();
    @Unique private static final boolean solace$STATS = Boolean.getBoolean("solace.spriteTickStats");
    @Unique private static final Logger solace$LOGGER = LogManager.getLogger("Solace/VisibleSpriteCache");
    @Unique private static final TextureAtlasSprite[] solace$NONE = new TextureAtlasSprite[0];

    @Shadow private SortedRenderLists renderLists;

    @Unique private SortedRenderLists solace$cachedFor;
    @Unique private boolean solace$dirty = true;
    @Unique private TextureAtlasSprite[] solace$sprites = solace$NONE;
    @Unique private final ReferenceOpenHashSet<TextureAtlasSprite> solace$scratch = new ReferenceOpenHashSet<>();

    @Unique private long solace$startNanos;
    @Unique private long solace$totalNanos;
    @Unique private long solace$calls;
    @Unique private long solace$rebuilds;

    @Inject(method = "tickVisibleRenders", at = @At("HEAD"), cancellable = true)
    private void solace$tickCachedSprites(CallbackInfo ci) {
        long start = solace$STATS ? System.nanoTime() : 0L;
        if (!solace$CACHE) {
            this.solace$startNanos = start;
            return;
        }

        SortedRenderLists lists = this.renderLists;
        if (this.solace$dirty || lists != this.solace$cachedFor) {
            this.solace$rebuild(lists);
        }
        TextureAtlasSprite[] sprites = this.solace$sprites;
        for (TextureAtlasSprite sprite : sprites) {
            SpriteUtil.markSpriteActive(sprite);
        }
        if (solace$STATS) this.solace$record(System.nanoTime() - start);
        ci.cancel();
    }

    @Inject(method = "tickVisibleRenders", at = @At("TAIL"))
    private void solace$tickOriginalStats(CallbackInfo ci) {
        if (solace$STATS && !solace$CACHE) this.solace$record(System.nanoTime() - this.solace$startNanos);
    }

    /** Rebuilt chunk meshes can change a section's sprites without a new render list yet. */
    @Inject(method = "uploadChunks", at = @At("TAIL"))
    private void solace$invalidateOnUpload(CallbackInfo ci) {
        this.solace$dirty = true;
    }

    @Unique
    private void solace$rebuild(SortedRenderLists lists) {
        ReferenceOpenHashSet<TextureAtlasSprite> set = this.solace$scratch;
        set.clear();
        Iterator<ChunkRenderList> it = lists.iterator();
        while (it.hasNext()) {
            ChunkRenderList renderList = it.next();
            ByteIterator sections = renderList.sectionsWithSpritesIterator();
            if (sections == null) continue;
            RenderRegion region = renderList.getRegion();
            while (sections.hasNext()) {
                RenderSection section = region.getSection(sections.nextByteAsInt());
                if (section == null) continue;
                TextureAtlasSprite[] sprites = section.getAnimatedSprites();
                if (sprites == null) continue;
                for (TextureAtlasSprite sprite : sprites) {
                    set.add(sprite);
                }
            }
        }
        this.solace$sprites = set.isEmpty() ? solace$NONE : set.toArray(new TextureAtlasSprite[0]);
        this.solace$cachedFor = lists;
        this.solace$dirty = false;
        this.solace$rebuilds++;
    }

    @Unique
    private void solace$record(long nanos) {
        this.solace$totalNanos += nanos;
        if (++this.solace$calls == 5000) {
            solace$LOGGER.info("tickVisibleRenders cache={} avg={} us over {} frames, rebuilds={}, unique sprites={}",
                    solace$CACHE, String.format("%.2f", this.solace$totalNanos / 1000.0 / this.solace$calls),
                    this.solace$calls, this.solace$rebuilds, this.solace$sprites.length);
            this.solace$totalNanos = 0L;
            this.solace$calls = 0L;
            this.solace$rebuilds = 0L;
        }
    }
}
