package com.solace.quests.api;

import com.solace.quests.content.Faction;
import com.solace.quests.content.Quest;
import com.solace.quests.content.QuestContent;
import com.solace.quests.state.Factions;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.QuestLogic;
import com.solace.quests.state.ServerCtx;
import com.solace.world.gates.StructureGateConfig;
import com.solace.world.gates.StructureGates;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Public entry points for other mods and for KubeJS (exposed there as the {@code SolaceQuests}
 * binding). Server thread only. Ids are plain strings such as "astralis:village/first_steps".
 */
public final class QuestApi {
    @FunctionalInterface
    public interface ConditionHandler {
        boolean test(ServerPlayer player, String dataJson);
    }

    @FunctionalInterface
    public interface EffectHandler {
        void run(ServerPlayer player, String dataJson);
    }

    public static final Map<String, ConditionHandler> CONDITIONS = new ConcurrentHashMap<>();
    public static final Map<String, EffectHandler> EFFECTS = new ConcurrentHashMap<>();

    private QuestApi() {
    }

    /** Backs {"type": "solace:custom", "handler": id, "data": ...} conditions. */
    public static void registerCondition(String id, ConditionHandler handler) {
        CONDITIONS.put(id, handler);
    }

    /** Backs {"type": "solace:custom", "handler": id, "data": ...} rewards and actions. */
    public static void registerEffect(String id, EffectHandler handler) {
        EFFECTS.put(id, handler);
    }

    /** Adds progress to an objective of the quest's current phase; this is how "solace:custom" objectives advance. */
    public static boolean progress(ServerPlayer player, String quest, String objective, int amount) {
        Quest definition = quest(quest);
        return definition != null && QuestLogic.addProgress(QuestEngine.ctx(player), definition, objective, amount,
                QuestEngine.events(player));
    }

    public static boolean start(ServerPlayer player, String quest) {
        Quest definition = quest(quest);
        return definition != null && QuestLogic.start(QuestEngine.ctx(player), definition, false,
                QuestEngine.events(player));
    }

    /** locked, available, active, claimable, completed or failed; "unknown" for an id that is not loaded. */
    public static String state(ServerPlayer player, String quest) {
        Quest definition = quest(quest);
        return definition == null ? "unknown"
                : QuestLogic.view(QuestEngine.ctx(player), definition).name().toLowerCase(Locale.ROOT);
    }

    public static boolean hasFlag(ServerPlayer player, String flag) {
        return QuestEngine.ctx(player).data().flags.contains(flag);
    }

    public static void setFlag(ServerPlayer player, String flag, boolean value) {
        ServerCtx ctx = QuestEngine.ctx(player);
        if (value ? ctx.data().flags.add(flag) : ctx.data().flags.remove(flag)) {
            ctx.changed();
            QuestLogic.evaluate(ctx, QuestEngine.events(player));
        }
    }

    public static int getVariable(ServerPlayer player, String name) {
        return QuestEngine.ctx(player).data().variables.getOrDefault(name, 0);
    }

    public static void setVariable(ServerPlayer player, String name, int value) {
        ServerCtx ctx = QuestEngine.ctx(player);
        ctx.data().variables.put(name, value);
        ctx.changed();
        QuestLogic.evaluate(ctx, QuestEngine.events(player));
    }

    public static int getReputation(ServerPlayer player, String faction) {
        ResourceLocation id = ResourceLocation.tryParse(faction);
        return id == null ? 0 : Factions.get(QuestEngine.ctx(player), id);
    }

    public static void addReputation(ServerPlayer player, String faction, int amount) {
        ResourceLocation id = ResourceLocation.tryParse(faction);
        Faction definition = id == null ? null : QuestContent.current.factions.get(id);
        if (definition != null) {
            ServerCtx ctx = QuestEngine.ctx(player);
            Factions.add(ctx, definition, amount, true);
            QuestLogic.evaluate(ctx, QuestEngine.events(player));
        }
    }

    /**
     * Same switch as {@code /solace gates open} and the solace:open_structure_gate effect. {@code structure}
     * is an id or "#tag"; {@code mode} "once", "infinite" or null for the config's. Returns how many
     * gates changed, or -1 if nothing in config/solace-structure-gates.json matches.
     */
    public static int openStructureGate(MinecraftServer server, String structure, String mode) {
        return StructureGates.open(server, structure, StructureGateConfig.Mode.parse(mode));
    }

    public static int closeStructureGate(MinecraftServer server, String structure) {
        return StructureGates.close(server, structure);
    }

    /** "open (once)", "closed ...", or "not gated". */
    public static String structureGateStatus(MinecraftServer server, String structure) {
        ResourceLocation id = ResourceLocation.tryParse(structure);
        return id == null ? "not gated" : StructureGates.describe(server, id);
    }

    private static Quest quest(String id) {
        ResourceLocation key = ResourceLocation.tryParse(id);
        return key == null ? null : QuestContent.current.quests.get(key);
    }
}
