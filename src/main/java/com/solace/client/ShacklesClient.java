package com.solace.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.solace.Solace;
import com.solace.world.Shackles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Shows the shackles: both hands held together in front, iron cuffs on the wrists and a link between them,
 * in first and third person; no sprinting; the chain clinks while walking.
 * shortcut: other players' cuffs show only when their effects reach this client (singleplayer, LAN host); fine for now.
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShacklesClient {
    private static final ResourceLocation IRON = new ResourceLocation("minecraft", "textures/block/anvil.png");
    private static ModelPart rightCuff;
    private static ModelPart leftCuff;
    private static int clink;

    private ShacklesClient() {
    }

    /** Arms forward, turned in until the wrists touch. First person raises them so the cuffs sit in view. */
    public static void pose(PlayerModel<?> model, boolean firstPerson) {
        float pitch = firstPerson ? -1.5F : -0.75F;
        float turn = firstPerson ? 0.40F : 0.60F;
        model.rightArm.xRot = pitch;
        model.rightArm.yRot = -turn;
        model.rightArm.zRot = 0.0F;
        model.leftArm.xRot = pitch;
        model.leftArm.yRot = turn;
        model.leftArm.zRot = 0.0F;
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftSleeve.copyFrom(model.leftArm);
    }

    private static void cuffs(PlayerModel<?> model, PoseStack pose, MultiBufferSource buffers, int light) {
        if (rightCuff == null) {
            MeshDefinition mesh = new MeshDefinition();
            // Arm-local boxes (pixels): a band round each wrist, and on the right one a short link reaching inward.
            mesh.getRoot().addOrReplaceChild("right", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-3.5F, 7.0F, -2.5F, 5.0F, 2.0F, 5.0F)
                    .texOffs(0, 8).addBox(1.5F, 7.5F, -0.5F, 3.0F, 1.0F, 1.0F), PartPose.ZERO);
            mesh.getRoot().addOrReplaceChild("left", CubeListBuilder.create()
                    .texOffs(0, 0).addBox(-1.5F, 7.0F, -2.5F, 5.0F, 2.0F, 5.0F), PartPose.ZERO);
            ModelPart root = LayerDefinition.create(mesh, 32, 32).bakeRoot();
            rightCuff = root.getChild("right");
            leftCuff = root.getChild("left");
        }
        VertexConsumer iron = buffers.getBuffer(RenderType.entityCutoutNoCull(IRON));
        pose.pushPose();
        model.rightArm.translateAndRotate(pose);
        rightCuff.render(pose, iron, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        pose.pushPose();
        model.leftArm.translateAndRotate(pose);
        leftCuff.render(pose, iron, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (!Shackles.on(player)) return;
        event.setCanceled(true);
        if (event.getHand() != InteractionHand.MAIN_HAND) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        PlayerModel<AbstractClientPlayer> model = renderer.getModel();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        pose.pushPose();
        // The body model in view space: shoulders just behind the eye, so only forearms and cuffs show, low and centred.
        pose.translate(0.0F, -0.02F, 0.22F);
        pose.scale(-1.0F, -1.0F, 1.0F);
        model.rightArm.setPos(-5.0F, 2.0F, 0.0F);
        model.leftArm.setPos(5.0F, 2.0F, 0.0F);
        pose(model, true);
        ResourceLocation skin = player.getSkinTextureLocation();
        VertexConsumer solid = buffers.getBuffer(RenderType.entitySolid(skin));
        model.rightArm.render(pose, solid, light, OverlayTexture.NO_OVERLAY);
        model.leftArm.render(pose, solid, light, OverlayTexture.NO_OVERLAY);
        VertexConsumer outer = buffers.getBuffer(RenderType.entityTranslucent(skin));
        model.rightSleeve.render(pose, outer, light, OverlayTexture.NO_OVERLAY);
        model.leftSleeve.render(pose, outer, light, OverlayTexture.NO_OVERLAY);
        cuffs(model, pose, buffers, light);
        pose.popPose();
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (event.phase != TickEvent.Phase.END || !Shackles.on(player)) return;
        player.setSprinting(false);
        if (player.onGround() && player.getDeltaMovement().horizontalDistanceSqr() > 1.0E-3 && ++clink % 14 == 0) {
            player.level().playLocalSound(player.getX(), player.getY(), player.getZ(), SoundEvents.CHAIN_STEP, SoundSource.PLAYERS,
                    0.35F, 0.9F + player.getRandom().nextFloat() * 0.3F, false);
        }
    }

    /** Third person: cuffs on the posed arms (the pose itself comes from PlayerModelShackleMixin). */
    static final class CuffLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        CuffLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
            super(parent);
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing,
                           float limbSwingAmount, float partialTick, float age, float headYaw, float headPitch) {
            if (Shackles.on(player) && !player.isInvisible()) cuffs(getParentModel(), pose, buffers, light);
        }
    }

    @Mod.EventBusSubscriber(modid = Solace.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {
        @SubscribeEvent
        @SuppressWarnings({"unchecked", "rawtypes"})
        public static void addLayers(EntityRenderersEvent.AddLayers event) {
            for (String skin : event.getSkins()) {
                LivingEntityRenderer renderer = event.getSkin(skin);
                if (renderer instanceof PlayerRenderer player) player.addLayer(new CuffLayer(player));
            }
        }

        private Setup() {
        }
    }
}
