package com.solace.compat.create6;

import net.minecraftforge.fml.loading.FMLLoader;
import org.apache.logging.log4j.LogManager;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodNode;
import org.spongepowered.asm.mixin.MixinEnvironment;
import org.spongepowered.asm.mixin.transformer.ClassInfo;
import org.spongepowered.asm.mixin.transformer.ext.IExtension;
import org.spongepowered.asm.mixin.transformer.ext.ITargetClassContext;

import java.util.List;
import java.util.Map;

/** Repairs legacy signatures before the DD bridge searches for its overwrites. */
public final class DDCreate6SignatureExtension implements IExtension {
    private static final String DD_BLOCKS = "uwu/lopyluna/create_dd/block/BlockProperties/";
    private static final String OLD_WORLD = "Lcom/jozufozu/flywheel/core/virtual/VirtualRenderWorld;";
    private static final String NEW_WORLD = "Lcom/simibubi/create/foundation/virtualWorld/VirtualRenderWorld;";
    private static final String MOVEMENT = "Lcom/simibubi/create/content/contraptions/behaviour/MovementContext;";
    private static final String MATRICES = "Lcom/simibubi/create/content/contraptions/render/ContraptionMatrices;";
    private static final String BUFFER = "Lnet/minecraft/client/renderer/MultiBufferSource;";

    private static final Signature CONTRAPTION_RENDER = new Signature("renderInContraption",
            "(" + MOVEMENT + OLD_WORLD + MATRICES + BUFFER + ")V", "renderInContraption",
            "(" + MOVEMENT + NEW_WORLD + MATRICES + BUFFER + ")V");
    private static final Signature DRILL_VISUAL = new Signature("createInstance",
            "(Lcom/jozufozu/flywheel/api/MaterialManager;" + OLD_WORLD + MOVEMENT + ")"
                    + "Lcom/simibubi/create/content/contraptions/render/ActorInstance;",
            "createVisual", "(Ldev/engine_room/flywheel/api/visualization/VisualizationContext;"
                    + NEW_WORLD + MOVEMENT + ")Lcom/simibubi/create/content/contraptions/render/ActorVisual;");

    // These nine methods are overwritten by DD to Create 6 1.1.1. Only their
    // signatures change here; the bridge supplies their new implementations.
    private static final Map<String, List<Signature>> SIGNATURES = Map.of(
            DD_BLOCKS + "flywheel/engine/FurnaceEngineBlock", List.of(new Signature("getFrameModel",
                    "()Lcom/jozufozu/flywheel/core/PartialModel;", "getFrameModel",
                    "()Ldev/engine_room/flywheel/lib/model/baked/PartialModel;")),
            DD_BLOCKS + "drill/bronze/BronzeDrillMovementBehaviour", List.of(DRILL_VISUAL),
            DD_BLOCKS + "drill/radiant/RadiantDrillMovementBehaviour", List.of(DRILL_VISUAL),
            DD_BLOCKS + "drill/shadow/ShadowDrillMovementBehaviour", List.of(DRILL_VISUAL),
            DD_BLOCKS + "drill/bronze/BronzeDrillRenderer", List.of(CONTRAPTION_RENDER),
            DD_BLOCKS + "drill/radiant/RadiantDrillRenderer", List.of(CONTRAPTION_RENDER),
            DD_BLOCKS + "drill/shadow/ShadowDrillRenderer", List.of(CONTRAPTION_RENDER),
            DD_BLOCKS + "bronze_saw/BronzeSawRenderer", List.of(CONTRAPTION_RENDER, new Signature(
                    "getRotatedModel", "(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;)"
                    + "Lcom/simibubi/create/foundation/render/SuperByteBuffer;", "getRotatedModel",
                    "(Lcom/simibubi/create/content/kinetics/base/KineticBlockEntity;)"
                    + "Lnet/createmod/catnip/render/SuperByteBuffer;")));

    private record Signature(String oldName, String oldDescriptor, String newName, String newDescriptor) { }

    @Override public boolean checkActive(MixinEnvironment environment) { return true; }

    @Override
    public void preApply(ITargetClassContext context) {
        ClassNode target = context.getClassNode();
        List<Signature> signatures = SIGNATURES.get(target.name);
        if (signatures == null) {
            return;
        }
        boolean bridgePresent = FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(mod -> mod.getModId().equals("ddtocreate6"));
        if (!bridgePresent) {
            return;
        }
        for (Signature signature : signatures) {
            for (MethodNode method : target.methods) {
                if (method.name.equals(signature.oldName) && method.desc.equals(signature.oldDescriptor)) {
                    ClassInfo.Method methodInfo = context.getClassInfo().findMethod(method);
                    if (methodInfo == null) {
                        throw new IllegalStateException("Missing DD method metadata: " + target.name + "." + method.name);
                    }
                    methodInfo.renameTo(signature.newName);
                    methodInfo.remapTo(signature.newDescriptor);
                    method.name = signature.newName;
                    method.desc = signature.newDescriptor;
                    LogManager.getLogger("Solace/Create6Compat").info(
                            "Migrated DD signature before bridge overwrite: {}.{}", target.name, method.name);
                }
            }
        }
    }

    @Override public void postApply(ITargetClassContext context) { }
    @Override public void export(MixinEnvironment environment, String name, boolean force, ClassNode classNode) { }
}
