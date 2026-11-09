import com.google.common.cache.Cache;
import com.solace.performance.DensityFunctionHashMemo;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/** Real mapped Minecraft density-function trees, exercised without starting a game process. */
public final class VerifyNoiseHashMemo {
    private static final AtomicInteger HASH_CALLS = new AtomicInteger();
    private static final AtomicInteger EQUALS_CALLS = new AtomicInteger();

    private record Counted(int value) implements DensityFunction.SimpleFunction {
        @Override public int hashCode() { HASH_CALLS.incrementAndGet(); return 0; }
        @Override public boolean equals(Object other) {
            EQUALS_CALLS.incrementAndGet();
            return other instanceof Counted counted && value == counted.value;
        }
        @Override public double compute(DensityFunction.FunctionContext context) { return value; }
        @Override public double minValue() { return value; }
        @Override public double maxValue() { return value; }
        @Override public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("Test-only density function");
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static DensityFunction tree(int value) {
        DensityFunction function = DensityFunctions.add(
                DensityFunctions.yClampedGradient(-64, 320, value, value + 1),
                DensityFunctions.constant(value + 0.25));
        for (int depth = 0; depth < 8; depth++) {
            function = function.abs().square();
        }
        return DensityFunctions.flatCache(DensityFunctions.cache2d(function));
    }

    private static DensityFunction wrap(Map<DensityFunction, DensityFunction> map,
                                        DensityFunction key, boolean modernFix, AtomicInteger allocations) {
        if (!modernFix) {
            return map.computeIfAbsent(key, ignored -> new Counted(allocations.incrementAndGet()));
        }
        DensityFunction result = map.get(key);
        if (result == null) {
            result = new Counted(allocations.incrementAndGet());
            map.put(key, result);
        }
        return result;
    }

    private static void checkWrapping(boolean modernFix) {
        Map<DensityFunction, DensityFunction> vanilla = new HashMap<>();
        Map<DensityFunction, DensityFunction> memo = DensityFunctionHashMemo.newWrappingMap();
        AtomicInteger vanillaAllocations = new AtomicInteger();
        AtomicInteger memoAllocations = new AtomicInteger();
        for (int value = 0; value < 4_096; value++) {
            DensityFunction first = tree(value);
            DensityFunction equal = tree(value);
            check(first != equal && first.equals(equal), "Fixture must be equal but distinct");
            DensityFunction expected = wrap(vanilla, first, modernFix, vanillaAllocations);
            DensityFunction actual = wrap(memo, first, modernFix, memoAllocations);
            check(wrap(vanilla, equal, modernFix, vanillaAllocations) == expected, "HashMap sharing");
            check(wrap(memo, equal, modernFix, memoAllocations) == actual, "Lost equal-tree sharing");
            check(wrap(memo, first, modernFix, memoAllocations) == actual, "Lost identity hit");
        }
        check(memo.size() == vanilla.size() && memoAllocations.get() == vanillaAllocations.get(),
                "Wrapper counts differ after rehashes");
        for (DensityFunction key : vanilla.keySet()) {
            check(vanilla.get(key).equals(memo.get(key)), "Rehash changed a wrapper association");
        }
        System.out.println((modernFix ? "ModernFix get/put" : "Vanilla computeIfAbsent")
                + " passed, " + memo.size() + " wrappers for 8192 equal-paired trees");
    }

    private static void checkVisitorTraversal() {
        Map<DensityFunction, DensityFunction> vanilla = new HashMap<>();
        Map<DensityFunction, DensityFunction> memo = DensityFunctionHashMemo.newWrappingMap();
        AtomicInteger vanillaWrappers = new AtomicInteger();
        AtomicInteger memoWrappers = new AtomicInteger();
        DensityFunction.Visitor vanillaVisitor = key -> vanilla.computeIfAbsent(key, function ->
                function instanceof DensityFunctions.MarkerOrMarked ? new Counted(vanillaWrappers.incrementAndGet()) : function);
        DensityFunction.Visitor memoVisitor = key -> memo.computeIfAbsent(key, function ->
                function instanceof DensityFunctions.MarkerOrMarked ? new Counted(memoWrappers.incrementAndGet()) : function);
        for (int pass = 0; pass < 16; pass++) {
            for (int value = 0; value < 64; value++) {
                tree(value).mapAll(vanillaVisitor);
                tree(value).mapAll(memoVisitor);
            }
        }
        check(vanilla.size() == memo.size() && vanillaWrappers.get() == memoWrappers.get(),
                "Postorder traversal changed marker wrapper counts");
        System.out.println("Real mapAll traversal passed, " + memoWrappers.get() + " marker wrappers");
    }

    private static void checkCollisionsAndHotHits() {
        Map<DensityFunction, DensityFunction> map = DensityFunctionHashMemo.newWrappingMap();
        for (int value = 0; value < 96; value++) {
            map.put(new Counted(value), new Counted(-value));
        }
        for (int value = 0; value < 96; value++) {
            check(map.get(new Counted(value)).equals(new Counted(-value)), "Hash collision merged unequal keys");
        }
        DensityFunction hot = DensityFunctions.flatCache(new Counted(1_000).abs().square());
        Map<DensityFunction, DensityFunction> hotMap = DensityFunctionHashMemo.newWrappingMap();
        hotMap.put(hot, hot);
        HASH_CALLS.set(0);
        EQUALS_CALLS.set(0);
        for (int repeat = 0; repeat < 10_000; repeat++) {
            check(hotMap.get(hot) == hot && hotMap.computeIfAbsent(hot, ignored -> null) == hot, "Hot miss");
        }
        check(HASH_CALLS.get() == 0 && EQUALS_CALLS.get() == 0, "Hot hits repeated structural work");
        hotMap.put(null, hot);
        check(hotMap.get(null) == hot, "Null key handling");
        System.out.println("Collisions and hot hits passed, zero repeated hashCode/equals calls");
    }

    @SuppressWarnings("unchecked")
    private static Cache<DensityFunction, Integer> hashes() throws Exception {
        Field field = DensityFunctionHashMemo.class.getDeclaredField("HASHES");
        field.setAccessible(true);
        return (Cache<DensityFunction, Integer>) field.get(null);
    }

    private static void checkConcurrentMaps() throws Exception {
        DensityFunction shared = tree(99);
        var workers = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int worker = 0; worker < 8; worker++) {
                tasks.add(() -> {
                    Map<DensityFunction, DensityFunction> map = DensityFunctionHashMemo.newWrappingMap();
                    for (int repeat = 0; repeat < 1_000; repeat++) {
                        check(map.computeIfAbsent(shared, key -> key) == shared, "Concurrent memo failure");
                        check(map.get(tree(99)) == shared, "Concurrent equality failure");
                    }
                    return null;
                });
            }
            for (var result : workers.invokeAll(tasks)) {
                result.get();
            }
        } finally {
            workers.shutdownNow();
        }
        System.out.println("Eight concurrent chunk-local maps passed");
    }

    private static WeakReference<DensityFunction> addTemporaryTree() {
        DensityFunction temporary = tree(-999);
        DensityFunctionHashMemo.newWrappingMap().put(temporary, temporary);
        return new WeakReference<>(temporary);
    }

    private static void checkMemoryBound() throws Exception {
        Cache<DensityFunction, Integer> cache = hashes();
        cache.invalidateAll();
        DensityFunction retained = tree(-222);
        Map<DensityFunction, DensityFunction> retainedMap = DensityFunctionHashMemo.newWrappingMap();
        retainedMap.put(retained, retained);
        List<DensityFunction> live = new ArrayList<>();
        for (int value = 0; value < 80_000; value++) {
            DensityFunction key = DensityFunctions.constant(value);
            live.add(key);
            DensityFunctionHashMemo.newWrappingMap().put(key, key);
        }
        cache.cleanUp();
        check(cache.size() > 60_000 && cache.size() <= 65_536, "Memo entry cap failed with live keys");
        check(live.size() == 80_000, "Capacity fixture lost keys");
        check(cache.getIfPresent(retained) == null, "Capacity fixture failed to evict an old hash");
        check(retainedMap.get(retained) == retained && retainedMap.get(tree(-222)) == retained,
                "Hash eviction changed an existing wrapper association");
        WeakReference<DensityFunction> reference = addTemporaryTree();
        for (int attempt = 0; attempt < 40 && reference.get() != null; attempt++) {
            System.gc();
            Thread.sleep(25);
        }
        check(reference.get() == null, "Global memo retained a temporary tree");
        cache.cleanUp();
        Reference.reachabilityFence(live);
        System.out.println("Memo cap and weak-key collection passed");
    }

    public static void main(String[] args) throws Exception {
        // These fixtures use constructors and mapAll only, with no codec decoding or world access.
        // Permit static codec initialization without Forge's bootstrap/network/eventbus startup.
        SharedConstants.tryDetectVersion();
        Field bootstrapped = Bootstrap.class.getDeclaredField("isBootstrapped");
        bootstrapped.setAccessible(true);
        bootstrapped.setBoolean(null, true);
        checkWrapping(false);
        checkWrapping(true);
        checkVisitorTraversal();
        checkCollisionsAndHotHits();
        checkConcurrentMaps();
        checkMemoryBound();
        System.out.println("Noise hash memo offline checks passed");
    }
}
