package com.solace.client.performance;

import com.solace.Solace;
import com.solace.compat.performance.ClientPerformancePatches;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.WeakHashMap;

/** Keeps discovery off the frame loop; the original mod still draws every selected banner. */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DragonBannerIndex {
    private static final boolean ENABLED = ClientPerformancePatches.bannerIndex();
    private static final ResourceLocation BANNER_ID = new ResourceLocation("block_factorys_bosses", "dragon_banner");
    private static final Map<LevelChunk, Boolean> KNOWN = new WeakHashMap<>();
    // Match the original renderer's outer Z / inner X traversal.
    private static final Map<ChunkPos, IndexedChunk> BANNER_CHUNKS = new TreeMap<>((a, b) -> {
        int z = Integer.compare(a.z, b.z);
        return z == 0 ? Integer.compare(a.x, b.x) : z;
    });
    private static ClientLevel level;
    private static Block bannerBlock;
    private static Map<BlockPos, BlockEntity> selected = Collections.emptyMap();
    private static int viewX, viewZ, viewRadius = -1;
    private static boolean dirty = true;

    private DragonBannerIndex() { }

    public static synchronized boolean prepare(ClientLevel current) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ENABLED || current != minecraft.level || minecraft.player == null) return false;
        if (level != current) {
            clear();
            level = current;
            bannerBlock = ForgeRegistries.BLOCKS.getValue(BANNER_ID);
        }
        if (bannerBlock == null) return false;
        BlockPos player = minecraft.player.blockPosition();
        int x = player.getX() >> 4, z = player.getZ() >> 4;
        int radius = minecraft.options.getEffectiveRenderDistance();
        if (viewRadius != radius || viewX != x || viewZ != z) {
            viewX = x; viewZ = z; viewRadius = radius;
            // Covers chunks already loaded before the subscriber/level became active.
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    LevelChunk chunk = current.getChunkSource().getChunk(x + dx, z + dz, false);
                    if (chunk != null && !KNOWN.containsKey(chunk)) refresh(chunk);
                }
            }
            dirty = true;
        }
        // Weak references and identity checks also cover view-distance cache replacement.
        Iterator<Map.Entry<ChunkPos, IndexedChunk>> iterator = BANNER_CHUNKS.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            LevelChunk chunk = entry.getValue().chunk.get();
            if (chunk == null || current.getChunkSource().getChunk(entry.getKey().x, entry.getKey().z, false) != chunk) {
                iterator.remove(); dirty = true;
            }
        }
        if (dirty) {
            Map<BlockPos, BlockEntity> result = new LinkedHashMap<>();
            for (var entry : BANNER_CHUNKS.entrySet()) {
                ChunkPos pos = entry.getKey();
                if (Math.abs(pos.x - viewX) > radius || Math.abs(pos.z - viewZ) > radius) continue;
                LevelChunk chunk = entry.getValue().chunk.get();
                if (chunk == null) continue;
                for (var entity : chunk.getBlockEntities().entrySet()) {
                    if (isBanner(entity.getValue())) result.put(entity.getKey(), entity.getValue());
                }
            }
            selected = result.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(result);
            dirty = false;
        }
        return true;
    }

    public static synchronized Map<BlockPos, BlockEntity> entries() { return selected; }

    public static synchronized void changed(LevelChunk chunk, BlockPos pos) {
        if (level == null || chunk.getLevel() != level || bannerBlock == null) return;
        BlockEntity entity = chunk.getBlockEntities().get(pos);
        IndexedChunk indexed = BANNER_CHUNKS.get(chunk.getPos());
        if (isBanner(entity)) {
            if (indexed == null || indexed.chunk.get() != chunk) {
                refresh(chunk);
            } else {
                indexed.positions.add(pos.immutable());
                dirty = true;
            }
        } else if (indexed != null && indexed.positions.remove(pos)) {
            if (indexed.positions.isEmpty()) BANNER_CHUNKS.remove(chunk.getPos());
            dirty = true;
        }
    }

    private static boolean isBanner(BlockEntity entity) {
        return entity != null && entity.getBlockState().getBlock() == bannerBlock;
    }

    private static void refresh(LevelChunk chunk) {
        KNOWN.put(chunk, Boolean.TRUE);
        Set<BlockPos> positions = new HashSet<>();
        for (BlockEntity entity : chunk.getBlockEntities().values()) {
            if (isBanner(entity)) positions.add(entity.getBlockPos().immutable());
        }
        if (!positions.isEmpty()) BANNER_CHUNKS.put(chunk.getPos(), new IndexedChunk(new WeakReference<>(chunk), positions));
        else BANNER_CHUNKS.remove(chunk.getPos());
        dirty = true;
    }

    @SubscribeEvent
    public static synchronized void loaded(ChunkEvent.Load event) {
        if (!ENABLED || event.getLevel() != level || !(event.getChunk() instanceof LevelChunk chunk)) return;
        refresh(chunk);
    }

    @SubscribeEvent
    public static synchronized void unloaded(ChunkEvent.Unload event) {
        if (event.getLevel() != level || !(event.getChunk() instanceof LevelChunk chunk)) return;
        KNOWN.remove(chunk);
        IndexedChunk indexed = BANNER_CHUNKS.get(chunk.getPos());
        if (indexed != null && indexed.chunk.get() == chunk) BANNER_CHUNKS.remove(chunk.getPos());
        dirty = true;
    }

    @SubscribeEvent
    public static synchronized void worldUnloaded(LevelEvent.Unload event) {
        if (event.getLevel() == level) clear();
    }

    private static void clear() {
        KNOWN.clear(); BANNER_CHUNKS.clear(); selected = Collections.emptyMap();
        level = null; bannerBlock = null; viewRadius = -1; dirty = true;
    }

    private record IndexedChunk(WeakReference<LevelChunk> chunk, Set<BlockPos> positions) { }
}
