import com.google.common.collect.ImmutableMap;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.launch.MixinBootstrap;
import org.spongepowered.asm.launch.platform.container.ContainerHandleVirtual;
import org.spongepowered.asm.launch.platform.container.IContainerHandle;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.transformer.IMixinTransformer;
import org.spongepowered.asm.mixin.transformer.IMixinTransformerFactory;
import org.spongepowered.asm.service.*;

import java.io.*;
import java.lang.reflect.*;
import java.net.URL;
import java.nio.file.*;
import java.util.*;

/** Offline: applies the real compiled mixin to the installed Create class. Never starts Minecraft. */
public final class VerifyMountedStorage {
    private static final String TARGET = "com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper";

    public static final class Service extends MixinServiceAbstract implements IClassProvider, IClassBytecodeProvider {
        public String getName() { return "Solace offline verification"; }
        public boolean isValid() { return true; }
        public IClassProvider getClassProvider() { return this; }
        public IClassBytecodeProvider getBytecodeProvider() { return this; }
        public ITransformerProvider getTransformerProvider() { return null; }
        public IClassTracker getClassTracker() { return null; }
        public IMixinAuditTrail getAuditTrail() { return null; }
        public Collection<String> getPlatformAgents() { return List.of(); }
        public IContainerHandle getPrimaryContainer() { return new ContainerHandleVirtual("offline"); }
        public InputStream getResourceAsStream(String name) { return getClass().getClassLoader().getResourceAsStream(name); }
        public URL[] getClassPath() { return new URL[0]; }
        public Class<?> findClass(String name) throws ClassNotFoundException { return findClass(name, false); }
        public Class<?> findClass(String name, boolean init) throws ClassNotFoundException {
            return Class.forName(name, init, getClass().getClassLoader());
        }
        public Class<?> findAgentClass(String name, boolean init) throws ClassNotFoundException { return findClass(name, init); }
        public ClassNode getClassNode(String name) throws IOException, ClassNotFoundException { return getClassNode(name, false); }
        public ClassNode getClassNode(String name, boolean transform) throws IOException, ClassNotFoundException {
            try (InputStream in = getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) throw new ClassNotFoundException(name);
                ClassNode node = new ClassNode();
                new ClassReader(in).accept(node, 0);
                return node;
            }
        }
        public IMixinTransformer transformer() { return getInternal(IMixinTransformerFactory.class).createTransformer(); }
    }

    public static final class Properties implements IGlobalPropertyService {
        private record Key(String name) implements IPropertyKey { }
        private final Map<IPropertyKey, Object> values = new HashMap<>();
        public IPropertyKey resolveKey(String name) { return new Key(name); }
        @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key) { return (T) values.get(key); }
        public void setProperty(IPropertyKey key, Object value) { values.put(key, value); }
        @SuppressWarnings("unchecked") public <T> T getProperty(IPropertyKey key, T fallback) { return (T) values.getOrDefault(key, fallback); }
        public String getPropertyString(IPropertyKey key, String fallback) { return String.valueOf(values.getOrDefault(key, fallback)); }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        MixinBootstrap.init();
        Mixins.addConfiguration("verify.mixins.json");
        Method phase = MixinEnvironment.class.getDeclaredMethod("gotoPhase", MixinEnvironment.Phase.class);
        phase.setAccessible(true);
        phase.invoke(null, MixinEnvironment.Phase.DEFAULT);
        MixinEnvironment.getDefaultEnvironment().setSide(MixinEnvironment.Side.SERVER);
        IMixinTransformer transformer = ((Service) MixinService.getService()).transformer();
        byte[] original;
        try (InputStream in = VerifyMountedStorage.class.getClassLoader().getResourceAsStream(TARGET.replace('.', '/') + ".class")) {
            original = in.readAllBytes();
        }
        byte[] transformed = transformer.transformClassBytes(TARGET, TARGET, original);
        check(!Arrays.equals(original, transformed), "Mixin was not applied");
        Files.write(Path.of(args[0], "transformed-wrapper.class"), transformed);
        Class<?> patched = new ClassLoader(VerifyMountedStorage.class.getClassLoader()) {
            Class<?> define() { return defineClass(TARGET, transformed, 0, transformed.length); }
        }.define();
        Class<?> baseline = Class.forName("net.minecraftforge.items.wrapper.CombinedInvWrapper");
        Class<?> handler = Class.forName("net.minecraftforge.items.IItemHandlerModifiable");
        Method index = patched.getDeclaredMethod("getIndexForSlot", int.class);
        index.setAccessible(true);
        Method oldIndex = baseline.getDeclaredMethod("getIndexForSlot", int.class);
        oldIndex.setAccessible(true);
        Constructor<?> ctor = patched.getConstructor(ImmutableMap.class);
        Constructor<?> oldCtor = baseline.getConstructor(Array.newInstance(handler, 0).getClass());
        Random random = new Random(9706);
        long checks = 0;
        for (int trial = 0; trial < 250; trial++) {
            int count = trial == 0 ? 0 : trial == 1 ? 256 : random.nextInt(65);
            Object handlers = Array.newInstance(handler, count);
            ImmutableMap.Builder<Object, Object> map = ImmutableMap.builder();
            int total = 0;
            for (int h = 0; h < count; h++) {
                int slots = random.nextInt(32); // Includes empty handlers and consecutive empty handlers.
                int id = h;
                Object proxy = Proxy.newProxyInstance(handler.getClassLoader(), new Class<?>[]{handler}, (p, m, a) -> {
                    return switch (m.getName()) {
                        case "getSlots" -> slots;
                        case "getSlotLimit" -> id * 1000 + (int) a[0];
                        case "isItemValid" -> (int) a[0] % 2 == 0;
                        case "hashCode" -> id;
                        case "equals" -> p == a[0];
                        default -> null;
                    };
                });
                Array.set(handlers, h, proxy);
                map.put(h, proxy); // Erased map type allows test handlers without bootstrapping Create registries.
                total += slots;
            }
            Object before = oldCtor.newInstance(handlers);
            Object after = ctor.newInstance(map.build());
            for (int slot = -2; slot <= total + 2; slot++) {
                check(oldIndex.invoke(before, slot).equals(index.invoke(after, slot)), "Index mismatch at " + slot);
                if (slot >= 0 && slot < total) {
                    Method limit = baseline.getMethod("getSlotLimit", int.class);
                    check(limit.invoke(before, slot).equals(limit.invoke(after, slot)), "Inherited dispatch/local slot mismatch");
                }
                checks++;
            }
            for (int slot : new int[]{Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                check(oldIndex.invoke(before, slot).equals(index.invoke(after, slot)), "Extreme invalid slot mismatch");
            }
        }
        System.out.println("PASS: real Mixin 0.8.5 transformed installed Create 6.0.8; " + checks
                + " slot checks across 250 layouts, inherited Forge dispatch, zero-slot and invalid-slot cases.");
    }
}
