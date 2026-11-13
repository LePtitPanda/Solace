package com.solace.quests.state;

import com.solace.quests.content.Dialogue.NpcBinding;
import com.solace.quests.content.Marker;
import com.solace.quests.content.Quest;
import com.solace.quests.content.Quest.Objective;
import com.solace.quests.content.Quest.Phase;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.network.Views.MarkerView;
import com.solace.quests.state.PlayerQuestData.QuestState;
import com.solace.quests.state.PlayerQuestData.Status;
import com.solace.world.gates.StructureGates;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Works out where a player's quest markers are and tells the client when the list changes.
 * Markers are plain data here; drawing them (Xaero waypoints, compass, beam) is the client's job.
 */
public final class Markers {
    private static final int LOCATE_RADIUS_CHUNKS = 64;
    private static final Map<UUID, List<MarkerView>> SENT = new HashMap<>();

    private Markers() {
    }

    public static void forget(UUID player) {
        SENT.remove(player);
    }

    /** Recomputes the player's markers and sends them if they differ from what the client has. */
    public static void sync(ServerCtx ctx) {
        List<MarkerView> markers = compute(ctx);
        if (!markers.equals(SENT.put(ctx.player.getUUID(), markers))) {
            QuestNetwork.markers(ctx.player, markers);
        }
    }

    private static List<MarkerView> compute(ServerCtx ctx) {
        List<MarkerView> out = new ArrayList<>();
        // One structure search per pass at most: locating can be slow, so never queue several.
        boolean[] mayLocate = {true};
        ctx.data().quests.forEach((id, state) -> {
            Quest quest = ctx.content().quests.get(id);
            Phase phase = quest == null || state.status != Status.ACTIVE ? null : quest.phase(state.phase);
            if (phase == null) {
                return;
            }
            int index = 0;
            for (Marker marker : quest.markers()) {
                add(out, ctx, id + "#" + index++, marker, quest.title(), phase, state, mayLocate);
            }
            for (Marker marker : phase.markers()) {
                add(out, ctx, id + "#" + phase.id() + "#" + index++, marker, quest.title(), phase, state, mayLocate);
            }
        });
        for (ResourceLocation id : ctx.data().markers) {
            Marker marker = ctx.content().markers.get(id);
            if (marker != null) {
                add(out, ctx, id.toString(), marker, Component.literal(id.getPath()), null, null, mayLocate);
            }
        }
        return out;
    }

    private static void add(List<MarkerView> out, ServerCtx ctx, String key, Marker marker, Component fallbackLabel,
                            Phase phase, QuestState state, boolean[] mayLocate) {
        if (marker.ref() != null) {
            marker = ctx.content().markers.get(marker.ref());
            if (marker == null) {
                return;
            }
        }
        Component label = marker.label() != null ? marker.label() : fallbackLabel;
        ServerPlayer player = ctx.player;
        switch (marker.kind()) {
            case "position" -> out.add(new MarkerView(key, marker.dimension().toString(), marker.x(), marker.y(),
                    marker.z(), label, marker.color(), marker.beam()));
            case "objective" -> {
                // Follows a reach_location objective and disappears once it is done.
                Objective objective = phase == null ? null : phase.objective(marker.objective());
                if (objective != null && objective.type().equals("solace:reach_location")
                        && state.progress.getOrDefault(objective.id(), 0) < objective.count()) {
                    out.add(new MarkerView(key, objective.ref().toString(), objective.x(), objective.y(),
                            objective.z(), label, marker.color(), marker.beam()));
                }
            }
            case "structure" -> {
                String cacheKey = "structure:" + marker.structure() + "@" + ctx.dimension();
                String cached = ctx.data().positions.get(cacheKey);
                // "none" remembers the gate version it was searched under: a gate change makes it searchable again.
                if (cached != null && cached.startsWith("none") && !cached.equals("none " + StructureGates.version())) {
                    cached = null;
                }
                if (cached == null && mayLocate[0]) {
                    mayLocate[0] = false;
                    BlockPos found = StructureGates.find(player.serverLevel(), marker.structure().toString(),
                            player.blockPosition(), LOCATE_RADIUS_CHUNKS, false);
                    cached = found == null ? "none " + StructureGates.version()
                            : ctx.dimension() + " " + found.getX() + " 0 " + found.getZ();
                    ctx.data().positions.put(cacheKey, cached);
                    ctx.changed();
                }
                if (cached != null && !cached.startsWith("none")) {
                    String[] parts = cached.split(" ");
                    out.add(new MarkerView(key, parts[0], Integer.parseInt(parts[1]), MarkerView.NO_Y,
                            Integer.parseInt(parts[3]), label, marker.color(), marker.beam()));
                }
            }
            case "npc" -> {
                NpcBinding binding = ctx.content().npcs.get(marker.npc());
                if (binding == null) {
                    return;
                }
                // The nearest loaded match, otherwise where the player last talked to this NPC.
                ctx.entity = null;
                Entity nearest = null;
                for (Entity entity : player.level().getEntities(player, player.getBoundingBox().inflate(64),
                        binding.entity()::matches)) {
                    ctx.entity = entity;
                    if ((binding.when() == null || binding.when().test(ctx))
                            && (nearest == null || entity.distanceToSqr(player) < nearest.distanceToSqr(player))) {
                        nearest = entity;
                    }
                }
                ctx.entity = null;
                if (nearest != null) {
                    out.add(new MarkerView(key, ctx.dimension(), nearest.getBlockX(), nearest.getBlockY(),
                            nearest.getBlockZ(), label, marker.color(), marker.beam()));
                } else {
                    String seen = ctx.data().positions.get("npc:" + marker.npc());
                    if (seen != null) {
                        String[] parts = seen.split(" ");
                        out.add(new MarkerView(key, parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
                                Integer.parseInt(parts[3]), label, marker.color(), marker.beam()));
                    }
                }
            }
            default -> {
            }
        }
    }
}
