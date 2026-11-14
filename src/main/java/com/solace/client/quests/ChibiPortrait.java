package com.solace.client.quests;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * A Minecraft-Live style chibi of a live entity: its real model, textures and layers (profession,
 * biome outfit, guard armour, held items) drawn with a big head and a squat body, flat-on with
 * the side faces sheared in, then inked with a dark line around the silhouette and each part.
 * Built offscreen and read back once a second, so outfit changes show up.
 */
public final class ChibiPortrait {
    private static final int CANVAS = 160;
    private static final float PX_PER_BLOCK = 16.0F; // one body texel = one pixel
    private static final float HEAD = 2.0F;          // head texels are 2x2 pixels
    private static final float HEAD_LIFT = 1.5F;     // texels the head is raised off the neck
    private static final float BRIM_TILT = 0.25F;    // radians; tips the brim's top face into view
    private static final float BRIM_DEPTH = 0.5F;    // brim front-to-back size in the portrait
    private static final float BODY_W = 1.0F;
    private static final float BODY_H = 0.75F;
    private static final float SIDE = 0.25F;         // side faces show at quarter width (oblique view)
    private static final float DEPTH_UNIT = 1.0F / 20000.0F; // one pixel of depth with the GUI ortho range
    // A side face recedes 1/SIDE px of depth per pixel, so horizontal neighbours need a wider gap to count as a new part.
    private static final float EDGE_V = 0.75F * DEPTH_UNIT;
    private static final float EDGE_H = (1.0F / SIDE + 0.75F) * DEPTH_UNIT;
    private static final long REBUILD_MILLIS = 1000L;
    private static final ResourceLocation TEXTURE = new ResourceLocation("solace", "dialogue/chibi_portrait");

    private static boolean reshaping;
    private static final List<Object[]> SAVED = new ArrayList<>();

    private TextureTarget target;
    private int entityId = Integer.MIN_VALUE;
    private long builtAt;
    private int width;
    private int height;

    /** Draws the chibi centred on (centerX, centerY), at most maxHeight GUI pixels tall. */
    public void draw(GuiGraphics graphics, LivingEntity entity, int centerX, int centerY, int maxHeight) {
        if (entity.getId() != entityId || Util.getMillis() - builtAt > REBUILD_MILLIS) {
            graphics.flush();
            build(entity);
        }
        if (width <= 0) {
            return;
        }
        int scale = Math.max(1, Math.min(3, maxHeight / height));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX - width * scale / 2.0F, centerY - height * scale / 2.0F, 0);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.blit(TEXTURE, 0, 0, 0, 0, width, height, width, height);
        graphics.pose().popPose();
    }

    public void close() {
        Minecraft.getInstance().getTextureManager().release(TEXTURE);
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        width = 0;
        entityId = Integer.MIN_VALUE;
    }

    private void build(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        entityId = entity.getId();
        builtAt = Util.getMillis();
        if (target == null) {
            target = new TextureTarget(CANVAS, CANVAS, true, Minecraft.ON_OSX);
        }
        target.setClearColor(0, 0, 0, 0);
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);

        Matrix4f oldProjection = RenderSystem.getProjectionMatrix();
        VertexSorting oldSorting = RenderSystem.getVertexSorting();
        RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, CANVAS, CANVAS, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack view = RenderSystem.getModelViewStack();
        view.pushPose();
        view.setIdentity();
        view.translate(0, 0, -11000);
        RenderSystem.applyModelViewMatrix();

        float bodyRot = entity.yBodyRot, yRot = entity.getYRot(), xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO, headRot = entity.yHeadRot;
        EntityRenderDispatcher dispatcher = mc.getEntityRenderDispatcher();
        Entity picked = dispatcher.crosshairPickEntity;
        try {
            entity.yBodyRot = 180.0F;
            entity.setYRot(180.0F);
            entity.setXRot(0.0F);
            entity.yHeadRot = 180.0F;
            entity.yHeadRotO = 180.0F;
            dispatcher.crosshairPickEntity = null; // no name tag over the portrait

            PoseStack pose = new PoseStack();
            pose.translate(CANVAS / 2.0F, CANVAS - 16.0F, 0);
            pose.mulPoseMatrix(new Matrix4f().scaling(PX_PER_BLOCK, PX_PER_BLOCK, -PX_PER_BLOCK));
            pose.mulPose(new Quaternionf().rotateZ((float) Math.PI));
            // Oblique view: x += SIDE * z, so boxes stay axis-aligned pixel rectangles with one side face showing.
            pose.mulPoseMatrix(new Matrix4f(1, 0, 0, 0, 0, 1, 0, 0, SIDE, 0, 1, 0, 0, 0, 0, 1));
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
            reshaping = true;
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1.0F, pose, buffers, 0xF000F0));
            buffers.endBatch();
        } finally {
            reshaping = false;
            restore();
            dispatcher.setRenderShadow(true);
            dispatcher.crosshairPickEntity = picked;
            entity.yBodyRot = bodyRot;
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.yHeadRotO = headRotO;
            entity.yHeadRot = headRot;
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(oldProjection, oldSorting);
            Lighting.setupFor3DItems();
        }

        ByteBuffer color = MemoryUtil.memAlloc(CANVAS * CANVAS * 4);
        FloatBuffer depth = MemoryUtil.memAllocFloat(CANVAS * CANVAS);
        try {
            GL11.glPixelStorei(GL11.GL_PACK_ALIGNMENT, 1);
            GL11.glReadPixels(0, 0, CANVAS, CANVAS, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, color);
            GL11.glReadPixels(0, 0, CANVAS, CANVAS, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, depth);
            mc.getMainRenderTarget().bindWrite(true);
            upload(ink(color, depth));
        } finally {
            MemoryUtil.memFree(color);
            MemoryUtil.memFree(depth);
        }
    }

    /** Pixels in NativeImage ABGR, top row first; 0 = empty. Adds the outline and inner part lines. */
    static int[] ink(ByteBuffer color, FloatBuffer depthBuffer) {
        int n = CANVAS;
        int[] src = new int[n * n];
        float[] depth = new float[n * n];
        for (int y = 0; y < n; y++) {
            int row = n - 1 - y; // GL rows start at the bottom
            for (int x = 0; x < n; x++) {
                int i = (row * n + x) * 4;
                int a = color.get(i + 3) & 0xFF;
                if (a > 0) {
                    src[y * n + x] = 0xFF000000 | (color.get(i + 2) & 0xFF) << 16 | (color.get(i + 1) & 0xFF) << 8
                            | (color.get(i) & 0xFF);
                }
                depth[y * n + x] = depthBuffer.get(row * n + x);
            }
        }
        int[] out = src.clone();
        int[][] neighbours = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int q = y * n + x;
                for (int[] d : neighbours) {
                    int nx = x + d[0], ny = y + d[1];
                    if (nx < 0 || ny < 0 || nx >= n || ny >= n) {
                        continue;
                    }
                    int p = ny * n + nx;
                    if (src[p] == 0) {
                        continue;
                    }
                    // Line goes on the empty or farther pixel, hugging the nearer part.
                    if (src[q] == 0 || depth[p] < depth[q] - (d[1] == 0 ? EDGE_H : EDGE_V)) {
                        out[q] = dark(src[p]);
                        break;
                    }
                }
            }
        }
        return out;
    }

    private static int dark(int abgr) {
        int r = (abgr & 0xFF) * 3 / 10, g = (abgr >> 8 & 0xFF) * 3 / 10, b = (abgr >> 16 & 0xFF) * 3 / 10;
        return 0xFF000000 | b << 16 | g << 8 | r;
    }

    private void upload(int[] pixels) {
        int n = CANVAS, minX = n, minY = n, maxX = -1, maxY = -1;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (pixels[y * n + x] != 0) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minY = Math.min(minY, y);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0) {
            width = 0;
            return;
        }
        width = maxX - minX + 1;
        height = maxY - minY + 1;
        NativeImage image = new NativeImage(width, height, true);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setPixelRGBA(x, y, pixels[(y + minY) * n + x + minX]);
            }
        }
        Minecraft.getInstance().getTextureManager().register(TEXTURE, new DynamicTexture(image));
    }

    /** Called by the renderer mixin right after setupAnim: big head, squat body, only while a portrait is drawn. */
    public static void reshape(EntityModel<?> model) {
        if (!reshaping || !SAVED.isEmpty()) {
            return;
        }
        if (model instanceof HumanoidModel<?> humanoid) {
            scale(HEAD, HEAD, humanoid.head, humanoid.hat);
            scale(BODY_W, BODY_H, humanoid.body, humanoid.rightArm, humanoid.leftArm, humanoid.rightLeg, humanoid.leftLeg);
        } else if (model instanceof VillagerModel<?> villager) {
            ModelPart root = villager.root();
            ModelPart head = root.getChild("head");
            scale(HEAD, HEAD, head);
            if (head.hasChild("hat") && head.getChild("hat").hasChild("hat_rim")) {
                // A flat brim is edge-on in this view; tilt it so its top face shows as a band, and lift it
                // by the front edge's drop (half-depth 8 texels) so the brim's front stays above the eyes.
                ModelPart rim = head.getChild("hat").getChild("hat_rim");
                save(rim);
                rim.xRot += BRIM_TILT;
                rim.yScale *= BRIM_DEPTH; // a shallower brim slants less under the oblique shear
                rim.y -= 8.0F * BRIM_DEPTH * Mth.sin(BRIM_TILT);
            }
            for (String name : new String[]{"body", "arms", "right_leg", "left_leg"}) {
                if (root.hasChild(name)) {
                    scale(BODY_W, BODY_H, root.getChild(name));
                }
            }
        }
    }

    /** Scales parts about the neck (model y = 0): positions and size together. */
    private static void scale(float horizontal, float vertical, ModelPart... parts) {
        for (ModelPart part : parts) {
            save(part);
            if (horizontal == vertical) {
                part.xScale *= horizontal; // head: grow in place around its pivot
                part.yScale *= vertical;
                part.zScale *= horizontal;
                // The doubled hat/hood overlay hangs ~1 texel below the neck; lift the head so it sits on the body.
                part.y -= HEAD_LIFT;
                continue;
            }
            part.x *= horizontal;
            part.y *= vertical;
            part.z *= horizontal;
            part.xScale *= horizontal;
            part.yScale *= vertical;
            part.zScale *= horizontal;
        }
    }

    private static void save(ModelPart part) {
        SAVED.add(new Object[]{part, new float[]{part.x, part.y, part.z, part.xScale, part.yScale, part.zScale, part.xRot}});
    }

    /** Puts the shared model back the way the world renderer expects it. */
    public static void restore() {
        for (Object[] entry : SAVED) {
            ModelPart part = (ModelPart) entry[0];
            float[] v = (float[]) entry[1];
            part.xRot = v[6];
            part.x = v[0];
            part.y = v[1];
            part.z = v[2];
            part.xScale = v[3];
            part.yScale = v[4];
            part.zScale = v[5];
        }
        SAVED.clear();
    }
}
