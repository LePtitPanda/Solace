package com.solace.performancechecks;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.solace.client.performance.DragonBannerIndex;
import com.solace.compat.performance.ClientPerformancePatches;
import it.unimi.dsi.fastutil.longs.Long2IntMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Vector4f;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Manual runtime equivalence checks, bundled only in the temporary validation JAR. */
public final class ClientPerformanceChecks {
    private static final Map<BlockPos, BlockState> DISPLAYED = new LinkedHashMap<>();
    private ClientPerformanceChecks() { }

    public static void run() throws Exception {
        Minecraft mc = Minecraft.getInstance();
        JsonObject report = new JsonObject();
        report.addProperty("bannerPatch", ClientPerformancePatches.bannerIndex());
        report.addProperty("glidersPatch", ClientPerformancePatches.gliderBlockGate());
        report.addProperty("sheepPatch", ClientPerformancePatches.sheepVariantRead());
        report.addProperty("lightPatch", ClientPerformancePatches.lightCacheMissPositions());
        Map<BlockPos, BlockState> originals = new LinkedHashMap<>();
        int oldRadius = mc.options.renderDistance().get();
        try {
            if (ClientPerformancePatches.bannerIndex()) {
                verifyBannerSelection();
                Block banner = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("block_factorys_bosses", "dragon_banner"));
                int x = mc.player.blockPosition().getX(), z = mc.player.blockPosition().getZ();
                BlockPos near = airAt(x + 2, z + 3);
                BlockPos farther = airAt(((x >> 4) + 3) * 16 + 1, z + 3);
                for (BlockPos pos : new BlockPos[]{near, farther}) {
                    originals.put(pos, mc.level.getBlockState(pos));
                    mc.level.setBlock(pos, banner.defaultBlockState(), 3);
                    verifyBannerSelection();
                }
                if (banner.defaultBlockState().hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
                    for (Direction facing : Direction.Plane.HORIZONTAL) {
                        mc.level.setBlock(near, banner.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, facing), 3);
                        verifyBannerSelection();
                    }
                }
                mc.options.renderDistance().set(2);
                verifyBannerSelection();
                mc.options.renderDistance().set(oldRadius);
                verifyBannerSelection();
                for (var entry : originals.entrySet()) {
                    mc.level.setBlock(entry.getKey(), entry.getValue(), 3);
                    verifyBannerSelection();
                }
                report.addProperty("bannerMutationAndRangeChecks", "passed");
            }
            int sheep = 0;
            if (ClientPerformancePatches.sheepVariantRead()) {
                for (var entity : mc.level.entitiesForRendering()) {
                    if (!(entity instanceof Sheep s)) continue;
                    CompoundTag reference = new CompoundTag();
                    s.addAdditionalSaveData(reference);
                    Object variant = s.getClass().getMethod("solace$getVariant").invoke(s);
                    String direct = variant.getClass().getMethod("getIdentifier").invoke(variant).toString();
                    require(direct.equals(reference.getString("VariantID")), "Sheep variant differs from serialized value");
                    sheep++;
                }
                require(sheep > 0, "No sheep loaded for variant comparison");
            }
            report.addProperty("sheepComparisons", sheep);
            if (ClientPerformancePatches.lightCacheMissPositions()) {
                Class<?> catnip = Class.forName("net.createmod.catnip.render.ShadeSeparatingSuperByteBuffer");
                Method lookup = catnip.getDeclaredMethod("getLight", BlockAndTintGetter.class, Vector4f.class);
                lookup.setAccessible(true);
                Field field = catnip.getDeclaredField("WORLD_LIGHT_CACHE"); field.setAccessible(true);
                Long2IntMap cache = (Long2IntMap) field.get(null);
                cache.clear();
                int calls = 0;
                for (float dx : new float[]{-1.01F, -0.01F, 0.0F, 0.99F, 1.01F}) {
                    for (float dz : new float[]{-1.01F, -0.01F, 0.0F, 0.99F, 1.01F}) {
                        Vector4f v = new Vector4f(mc.player.getBlockX() + dx, mc.player.getBlockY() + 0.99F, mc.player.getBlockZ() + dz, 1);
                        BlockPos pos = BlockPos.containing(v.x(), v.y(), v.z());
                        int expected = LevelRenderer.getLightColor(mc.level, pos);
                        for (int repeat = 0; repeat < 3; repeat++) {
                            int actual = (int) lookup.invoke(null, mc.level, v);
                            require(actual == expected, "Cached lighting differs at " + pos);
                            calls++;
                        }
                        cache.put(pos.asLong(), Integer.MIN_VALUE);
                        require((int) lookup.invoke(null, mc.level, v) == Integer.MIN_VALUE, "Cached sentinel changed");
                        cache.remove(pos.asLong());
                    }
                }
                cache.clear();
                report.addProperty("lightComparisons", calls);
            }
            report.addProperty("result", "passed");
        } catch (Throwable failure) {
            report.addProperty("result", "failed");
            report.addProperty("error", failure.toString());
            throw failure;
        } finally {
            mc.options.renderDistance().set(oldRadius);
            for (var entry : originals.entrySet()) mc.level.setBlock(entry.getKey(), entry.getValue(), 3);
            Path output = mc.gameDirectory.toPath().resolve("local/embeddium-client-fixes-2026-10-01/runtime-checks.json");
            Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
        }
    }

    private static void verifyBannerSelection() {
        Minecraft mc = Minecraft.getInstance();
        require(DragonBannerIndex.prepare(mc.level), "Banner index unavailable");
        Map<BlockPos, BlockEntity> reference = new LinkedHashMap<>();
        Block banner = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("block_factorys_bosses", "dragon_banner"));
        int cx = mc.player.getBlockX() >> 4, cz = mc.player.getBlockZ() >> 4;
        int radius = mc.options.getEffectiveRenderDistance();
        for (int dz = -radius; dz <= radius; dz++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (var entry : mc.level.getChunk(cx + dx, cz + dz).getBlockEntities().entrySet()) {
                    if (entry.getValue().getBlockState().getBlock() == banner) reference.put(entry.getKey(), entry.getValue());
                }
            }
        }
        Map<BlockPos, BlockEntity> actual = DragonBannerIndex.entries();
        require(reference.equals(actual), "Banner selection differs from legacy scan");
        require(reference.keySet().stream().toList().equals(actual.keySet().stream().toList()), "Banner iteration order differs");
    }

    private static BlockPos airAt(int x, int z) {
        Minecraft mc = Minecraft.getInstance();
        for (int y = mc.player.getBlockY() + 1; y < mc.player.getBlockY() + 30; y++) {
            BlockPos pos = new BlockPos(x, y, z);
            if (mc.level.getBlockState(pos).isAir()) return pos;
        }
        throw new AssertionError("No air space for disposable client-side check");
    }

    public static void showBanners() {
        Minecraft mc = Minecraft.getInstance();
        Block banner = ForgeRegistries.BLOCKS.getValue(new ResourceLocation("block_factorys_bosses", "dragon_banner"));
        int i = 0;
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockPos pos = airAt(mc.player.getBlockX() - 2 + i++, mc.player.getBlockZ() + 3);
            DISPLAYED.put(pos, mc.level.getBlockState(pos));
            BlockState state = banner.defaultBlockState();
            if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, facing);
            mc.level.setBlock(pos, state, 3);
        }
    }

    public static void captureAndRestore() {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, "codex-client-performance-check.png", mc.getMainRenderTarget(), ignored -> { });
        for (var entry : DISPLAYED.entrySet()) mc.level.setBlock(entry.getKey(), entry.getValue(), 3);
        DISPLAYED.clear();
        verifyBannerSelection();
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
