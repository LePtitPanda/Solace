package com.solace.compat.radiance;

import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/** Composes only the startup redirects replaced by Radiance or Connector. */
public final class RadianceCompatibilityExtension implements IExtension {
    private static final String RADIANCE_CLIENT =
            "com.radiance.mixins.opengl_ui.OpenGLUiMinecraftClientMixins";
    private static final String CONNECTOR_CLIENT =
            "dev.su5ed.sinytra.connectorextras.geckolibcompat.mixin.MinecraftMixin";
    private static final String RADIANCE_WINDOW =
            "com.radiance.mixins.opengl_ui.OpenGLUiWindowMixins";
    private static final String EMBEDDIUM_WINDOW =
            "me.jellysquid.mods.sodium.mixin.workarounds.context_creation.WindowMixin";
    private static final String TTF_GLYPH =
            "com.radiance.mixins.vanilla_resource_tracker.TtfGlyphMixins";

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    private static boolean hasMod(String id) {
        return FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(mod -> mod.getModId().equals(id));
    }

    public static boolean needsConnectorHook() {
        return hasMod("radiance") && hasMod("connectorextras_geckolib_fabric_compat");
    }

    @Override
    public void preApply(ITargetClassContext context) {
        String target = context.getClassNode().name;
        if ((!target.equals("net/minecraft/client/Minecraft")
                && !target.equals("com/mojang/blaze3d/platform/Window")
                && !target.equals("com/mojang/blaze3d/font/TrueTypeGlyphProvider$Glyph"))
                || !hasMod("radiance")) return;
        try {
            Field mixinsField = context.getClass().getDeclaredField("mixins");
            mixinsField.setAccessible(true);
            Collection<?> mixins = (Collection<?>) mixinsField.get(context);
            if (target.equals("com/mojang/blaze3d/font/TrueTypeGlyphProvider$Glyph")) {
                IMixinInfo glyph = findMixin(mixins, TTF_GLYPH);
                if (glyph != null) repairGlyphProviderShadow(glyph, context.getClassNode());
            } else if (target.equals("net/minecraft/client/Minecraft")) {
                IMixinInfo radiance = findMixin(mixins, RADIANCE_CLIENT);
                if (radiance == null || findMixin(mixins, CONNECTOR_CLIENT) == null) return;
                if (findMixin(mixins, "com.solace.mixin.RadianceConnectorResourceMixin") == null) {
                    throw new IllegalStateException("Solace's replacement Radiance texture-loader hook is missing");
                }
                makeRedirectOptional(radiance, "registerAuxiliaryTextureReloader",
                        "(Lnet/minecraft/client/Minecraft;"
                                + "Lnet/minecraft/server/packs/resources/ReloadableResourceManager;)V");
            } else {
                IMixinInfo embeddium = findMixin(mixins, EMBEDDIUM_WINDOW);
                if (embeddium == null || findMixin(mixins, RADIANCE_WINDOW) == null) return;
                makeRedirectOptional(embeddium, "wrapGlfwCreateWindow",
                        "(Ljava/util/function/IntSupplier;Ljava/util/function/IntSupplier;"
                                + "Ljava/util/function/Supplier;Ljava/util/function/LongSupplier;)J");
                makeRedirectOptional(embeddium, "postWindowCreated", "()Lorg/lwjgl/opengl/GLCapabilities;");
            }
        } catch (ReflectiveOperationException exception) {
            throw new IllegalStateException("Could not compose Radiance's startup redirects", exception);
        }
    }

    private static IMixinInfo findMixin(Collection<?> mixins, String name) {
        for (Object candidate : mixins) {
            IMixinInfo info = (IMixinInfo) candidate;
            if (info.getClassName().equals(name)) return info;
        }
        return null;
    }

    private static void makeRedirectOptional(IMixinInfo mixin, String name, String descriptor)
            throws ReflectiveOperationException {
        ClassNode node = getValidatedMixinNode(mixin);
        for (MethodNode method : node.methods) {
            if (!method.name.equals(name) || !method.desc.equals(descriptor)
                    || method.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : method.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/injection/Redirect;")) continue;
                if (annotation.values == null) annotation.values = new ArrayList<>();
                int index = annotation.values.indexOf("require");
                if (index >= 0) annotation.values.set(index + 1, 0);
                else {
                    annotation.values.add("require");
                    annotation.values.add(0);
                }
                LogManager.getLogger("Solace/RadianceCompat").info(
                        "Adapted conflicting startup redirect: {}.{}", mixin.getClassName(), name);
                return;
            }
        }
        throw new IllegalStateException("Startup redirect changed; compatibility must be reviewed: "
                + mixin.getClassName() + "." + name);
    }

    private static ClassNode getValidatedMixinNode(IMixinInfo mixin) throws ReflectiveOperationException {
        // Connector's Mixin 0.8.5 clones this validated node for application.
        // Editing the public getClassNode() copy would have no effect.
        Field stateField = mixin.getClass().getDeclaredField("state");
        stateField.setAccessible(true);
        Object state = stateField.get(mixin);
        Field nodeField = state.getClass().getDeclaredField("classNode");
        nodeField.setAccessible(true);
        return (ClassNode) nodeField.get(state);
    }

    private static void repairGlyphProviderShadow(IMixinInfo mixin, ClassNode target)
            throws ReflectiveOperationException {
        String descriptor = "Lcom/mojang/blaze3d/font/TrueTypeGlyphProvider;";
        if (target.fields.stream().anyMatch(field -> field.name.equals("this$0")
                && field.desc.equals(descriptor))) return;
        if (target.fields.stream().noneMatch(field -> field.name.equals("f_83873_")
                && field.desc.equals(descriptor))) {
            throw new IllegalStateException("Forge's TrueType glyph provider field changed");
        }
        // Radiance uses the development-only this$0 name with remap=false.
        // Mixin's shadow alias also remaps accesses in the generated inner class.
        for (FieldNode field : getValidatedMixinNode(mixin).fields) {
            if (!field.name.equals("this$0") || !field.desc.equals(descriptor)
                    || field.visibleAnnotations == null) continue;
            for (AnnotationNode annotation : field.visibleAnnotations) {
                if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Shadow;")) continue;
                if (annotation.values == null) annotation.values = new ArrayList<>();
                int index = annotation.values.indexOf("aliases");
                if (index >= 0) {
                    @SuppressWarnings("unchecked")
                    List<String> aliases = (List<String>) annotation.values.get(index + 1);
                    if (!aliases.contains("f_83873_")) aliases.add("f_83873_");
                }
                else {
                    annotation.values.add("aliases");
                    annotation.values.add(new ArrayList<>(List.of("f_83873_")));
                }
                LogManager.getLogger("Solace/RadianceCompat").info(
                        "Mapped Radiance's TrueType glyph provider shadow to Forge's f_83873_");
                return;
            }
        }
        throw new IllegalStateException("Radiance's TrueType glyph shadow changed");
    }

    @Override public void postApply(ITargetClassContext context) { }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode classNode) { }
}
