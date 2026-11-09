import com.google.gson.JsonParser;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;

import java.nio.charset.StandardCharsets;
import java.util.jar.JarFile;

/** Verifies the actual reobfuscated distributable, without loading its game classes. */
public final class VerifyFeatureCycleArtifact {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static String read(JarFile jar, String name) throws Exception {
        try (var input = jar.getInputStream(jar.getJarEntry(name))) {
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            String target = "buildFeaturesPerStep(Ljava/util/List;Ljava/util/function/Function;Z)Ljava/util/List;";
            var mappings = JsonParser.parseString(read(jar, "solace.refmap.json")).getAsJsonObject();
            String mapped = mappings.getAsJsonObject("mappings").getAsJsonObject("com/solace/mixin/FeatureSorterCycleMixin")
                    .get(target).getAsString();
            check(mapped.equals("Lnet/minecraft/world/level/biome/FeatureSorter;m_220603_(Ljava/util/List;Ljava/util/function/Function;Z)Ljava/util/List;"),
                    "Missing exact method SRG mapping");
            check(read(jar, "solace.mixins.json").contains("\"FeatureSorterCycleMixin\""), "Mixin not registered");
            check(jar.getManifest().getMainAttributes().getValue("MixinConfigs").equals("solace.mixins.json"), "Mixin manifest missing");
            var nested = JsonParser.parseString(read(jar, "META-INF/jarjar/metadata.json")).getAsJsonObject()
                    .getAsJsonArray("jars").get(0).getAsJsonObject();
            check(nested.getAsJsonObject("identifier").get("artifact").getAsString().equals("mixinextras-forge"), "Wrong nested library");
            check(jar.getJarEntry(nested.get("path").getAsString()) != null, "Method-wrapper runtime absent");

            ClassNode resolver = new ClassNode();
            try (var input = jar.getInputStream(jar.getJarEntry("com/solace/worldgen/FeatureCycleResolver.class"))) {
                new ClassReader(input).accept(resolver, 0);
            }
            boolean remappedHolderValue = false;
            boolean remappedIndexLookup = false;
            for (var method : resolver.methods) {
                for (var instruction : method.instructions) {
                    if (instruction instanceof MethodInsnNode call) {
                        if (call.owner.equals("net/minecraft/core/Holder") && call.name.equals("m_203334_")) remappedHolderValue = true;
                        if (call.owner.equals("net/minecraft/Util") && call.name.equals("m_214634_")) remappedIndexLookup = true;
                    }
                }
            }
            check(remappedHolderValue && remappedIndexLookup, "Bundled resolver was not reobfuscated");
            ClassNode mixin = new ClassNode();
            try (var input = jar.getInputStream(jar.getJarEntry("com/solace/mixin/FeatureSorterCycleMixin.class"))) {
                new ClassReader(input).accept(mixin, 0);
            }
            var wrapper = mixin.methods.stream().filter(method -> method.name.equals("solace$resolveCycles")).findFirst().orElseThrow();
            check((wrapper.access & Opcodes.ACC_STATIC) != 0, "Wrapper must be static");
            var annotation = wrapper.visibleAnnotations.stream().filter(value -> value.desc.equals(
                    "Lcom/llamalad7/mixinextras/injector/wrapmethod/WrapMethod;")).findFirst().orElseThrow();
            check(annotation.values.toString().contains(target), "Wrapper targets wrong descriptor");
        }
        System.out.println("PASS feature-cycle distributable: exact SRG refmap, static method wrapper, manifest, nested runtime and reobfuscated resolver calls");
    }
}
