package com.solace.client.quests;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.solace.client.gui.WorldCreationThemeRenderer;
import com.solace.quests.network.Views.MarkerView;
import com.solace.quests.network.Views.ObjectiveView;
import com.solace.quests.network.Views.QuestView;
import net.minecraft.network.chat.Component;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;

import java.util.List;

/** The on-screen quest tracker, its built-in compass, and in-world marker beams. */
public final class QuestHud {
    static final int WIDTH = 150;

    private QuestHud() {
    }

    public static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int screenWidth, int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        QuestClientConfig config = QuestClientConfig.get();
        if (!config.hud || minecraft.options.hideGui || minecraft.player == null
                || minecraft.screen != null) {
            return;
        }
        Font font = minecraft.font;
        int x = x(config, screenWidth);
        int y = y(config, screenHeight);
        String dimension = minecraft.player.level().dimension().location().toString();
        for (QuestView quest : ClientQuests.QUESTS.values()) {
            if (!quest.tracked() || quest.status() != QuestView.ACTIVE) {
                continue;
            }
            MarkerView marker = config.compass ? nearest(quest, dimension, minecraft.player.position()) : null;
            // Long titles and objectives wrap onto more lines instead of being cut.
            List<FormattedCharSequence> title = font.split(quest.title(), WIDTH - (marker != null ? 60 : 12));
            List<List<FormattedCharSequence>> lines = new java.util.ArrayList<>();
            List<String> counts = new java.util.ArrayList<>();
            int objectiveLines = 0;
            for (ObjectiveView objective : quest.objectives()) {
                boolean done = objective.progress() >= objective.count();
                String count = objective.count() > 1 ? objective.progress() + "/" + objective.count() : done ? "done" : "";
                int indent = objective.indent() ? 6 : 0;
                // A paused objective shows why it waits instead of what it asks.
                Component line = objective.paused() ? objective.pausedText() : objective.text();
                List<FormattedCharSequence> text = font.split(line, WIDTH - 20 - indent - font.width(count) - 4);
                lines.add(text);
                counts.add(count);
                objectiveLines += Math.max(1, text.size());
            }
            int titleHeight = 4 + Math.max(1, title.size()) * 10;
            int height = titleHeight + objectiveLines * 10 + quest.objectives().size() * 2;
            graphics.fill(x, y, x + WIDTH, y + height, 0x900C0C1C);
            graphics.fill(x, y, x + 2, y + height, WorldCreationThemeRenderer.GOLD);
            for (int i = 0; i < title.size(); i++) {
                graphics.drawString(font, title.get(i), x + 6, y + 3 + i * 10, WorldCreationThemeRenderer.GOLD, false);
            }

            if (marker != null) {
                double dx = marker.x() + 0.5 - minecraft.player.getX();
                double dz = marker.z() + 0.5 - minecraft.player.getZ();
                String distance = (int) Math.sqrt(dx * dx + dz * dz) + "m";
                graphics.drawString(font, distance, x + WIDTH - 16 - font.width(distance), y + 3,
                        WorldCreationThemeRenderer.TEXT, false);
                // Yaw to the target minus where the player looks: 0 = straight ahead = arrow up.
                float turn = Mth.wrapDegrees((float) Math.toDegrees(Math.atan2(-dx, dz)) - minecraft.player.getYRot());
                PoseStack pose = graphics.pose();
                pose.pushPose();
                pose.translate(x + WIDTH - 8, y + 7, 0);
                pose.mulPose(Axis.ZP.rotationDegrees(turn));
                graphics.fill(-1, -4, 1, 5, WorldCreationThemeRenderer.GOLD);
                graphics.fill(-2, -3, 2, -2, WorldCreationThemeRenderer.GOLD);
                graphics.fill(-3, -2, 3, -1, WorldCreationThemeRenderer.GOLD);
                pose.popPose();
            }
            int lineY = y + titleHeight;
            for (int o = 0; o < quest.objectives().size(); o++) {
                ObjectiveView objective = quest.objectives().get(o);
                boolean done = objective.progress() >= objective.count();
                int indent = objective.indent() ? 6 : 0;
                int color = done ? WorldCreationThemeRenderer.GOLD_DIM
                        : objective.paused() ? 0xFF808080 : WorldCreationThemeRenderer.TEXT;
                String count = counts.get(o);
                graphics.drawString(font, count, x + WIDTH - 4 - font.width(count), lineY, color, false);
                List<FormattedCharSequence> text = lines.get(o);
                // One bullet per objective; its wrapped lines sit indented under it, with a gap before the next.
                graphics.drawString(font, "\u2022", x + 8 + indent, lineY, color, false);
                for (FormattedCharSequence part : text) {
                    graphics.drawString(font, part, x + 14 + indent, lineY, color, false);
                    lineY += 10;
                }
                if (text.isEmpty()) lineY += 10;
                lineY += 2;
            }
            y += height + 4;
        }
    }

    static int x(QuestClientConfig config, int screenWidth) {
        return 6 + (int) (Mth.clamp(config.hudX, 0.0, 1.0) * (screenWidth - WIDTH - 12));
    }

    static int y(QuestClientConfig config, int screenHeight) {
        return 6 + (int) (Mth.clamp(config.hudY, 0.0, 1.0) * (screenHeight - 52));
    }

    /** Markers of a quest carry its id as a prefix ("quest#..."). */
    private static MarkerView nearest(QuestView quest, String dimension, Vec3 from) {
        MarkerView best = null;
        double bestDistance = Double.MAX_VALUE;
        String prefix = quest.id() + "#";
        for (MarkerView marker : ClientQuests.markers) {
            if (marker.id().startsWith(prefix) && marker.dimension().equals(dimension)) {
                double dx = marker.x() - from.x, dz = marker.z() - from.z;
                if (dx * dx + dz * dz < bestDistance) {
                    bestDistance = dx * dx + dz * dz;
                    best = marker;
                }
            }
        }
        return best;
    }

    /** A thin gold outline column at markers that ask for a beam, visible from afar without any map mod. */
    public static void renderBeams(RenderLevelStageEvent event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || minecraft.level == null
                || ClientQuests.markers.isEmpty() || !QuestClientConfig.get().beams) {
            return;
        }
        String dimension = minecraft.level.dimension().location().toString();
        Vec3 camera = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = null;
        for (MarkerView marker : ClientQuests.markers) {
            if (!marker.beam() || !marker.dimension().equals(dimension)
                    || Math.abs(marker.x() - camera.x) > 256 || Math.abs(marker.z() - camera.z) > 256) {
                continue;
            }
            if (lines == null) {
                lines = buffers.getBuffer(RenderType.lines());
            }
            AABB column = new AABB(marker.x() + 0.3, minecraft.level.getMinBuildHeight(), marker.z() + 0.3,
                    marker.x() + 0.7, minecraft.level.getMaxBuildHeight(), marker.z() + 0.7)
                    .move(-camera.x, -camera.y, -camera.z);
            LevelRenderer.renderLineBox(event.getPoseStack(), lines, column, 0.83F, 0.66F, 0.26F, 1.0F);
        }
        if (lines != null) {
            buffers.endBatch(RenderType.lines());
        }
    }
}
