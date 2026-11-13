package com.solace.world.gates;

import com.solace.world.gates.StructureGateConfig.Entry;
import com.solace.world.gates.StructureGateConfig.Mode;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructureGateDataTest {
    private static final ResourceLocation FORT = new ResourceLocation("test", "fort");
    private static final ResourceLocation OVERWORLD = new ResourceLocation("minecraft", "overworld");
    private static final ResourceLocation NETHER = new ResourceLocation("minecraft", "the_nether");
    private static final Entry ONCE = new Entry(false, FORT, Mode.ONCE, false);

    @Test
    void closedByDefaultAndOnceClaimIsExclusiveAcrossThreads() throws Exception {
        StructureGateData data = new StructureGateData();
        assertFalse(data.isOpen(FORT, ONCE));
        assertFalse(data.claim(FORT, ONCE, new StructureGateData.SavedPos(OVERWORLD, BlockPos.ZERO)));
        assertTrue(data.open(FORT, ONCE, Mode.ONCE));
        assertFalse(data.open(FORT, ONCE, Mode.ONCE), "opening an open gate changes nothing");

        int threads = 16;
        AtomicInteger won = new AtomicInteger();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        for (int i = 0; i < threads; i++) {
            int x = i;
            pool.submit(() -> {
                start.await();
                if (data.claim(FORT, ONCE, new StructureGateData.SavedPos(OVERWORLD, new BlockPos(x, 64, 0)))) {
                    won.incrementAndGet();
                }
                return null;
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(1, won.get());
        assertFalse(data.isOpen(FORT, ONCE));
        assertEquals(1, data.positions(FORT).size());
        assertFalse(data.open(FORT, ONCE, Mode.ONCE), "a once gate whose copy exists stays closed");
        assertTrue(data.open(FORT, ONCE, Mode.INFINITE), "infinite may still be opened explicitly");
    }

    @Test
    void stateSurvivesSaveAndLoadAndResetForgets() {
        StructureGateData data = new StructureGateData();
        data.open(FORT, ONCE, Mode.ONCE);
        data.claim(FORT, ONCE, new StructureGateData.SavedPos(NETHER, new BlockPos(100, 70, -200)));
        StructureGateData loaded = StructureGateData.load(data.save(new CompoundTag()));
        assertFalse(loaded.isOpen(FORT, ONCE));
        assertEquals(new StructureGateData.SavedPos(NETHER, new BlockPos(100, 70, -200)), loaded.positions(FORT).get(0));
        assertTrue(loaded.reset(FORT));
        assertTrue(loaded.positions(FORT).isEmpty());
        assertTrue(loaded.isOpen(FORT, new Entry(false, FORT, Mode.INFINITE, true)), "reset falls back to initially_open");
    }
}
