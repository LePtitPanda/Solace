package com.solace.quests.state;

import com.solace.Solace;
import com.solace.quests.content.Faction;
import com.solace.quests.content.QuestContent;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Village defenders ({@code #solace:village_defenders}: iron golems, guards, clay golems) and the
 * {@code defenders_attack_at} rank of a villager-gossip faction. A defender goes for a player only when the
 * player's standing is at that rank or lower, when the player hurt it, or when it saw the player hurt a villager
 * or another defender. Thief crimes and vanilla per-villager gossip no longer set defenders on anyone.
 * Killed by a defender while at that rank or lower, the player's standing is lifted to the start of the rank above.
 * Without a faction that sets {@code defenders_attack_at}, nothing here changes vanilla behaviour.
 */
public final class VillageDefenders {
    public static final TagKey<EntityType<?>> DEFENDERS =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(Solace.MOD_ID, "village_defenders"));
    /** How long a defender stays allowed to chase a player it saw attacking. */
    private static final int PROVOKED_TICKS = 200;
    private static final double SIGHT_RANGE = 24;
    private static final double AGGRO_RANGE = 16;

    private record Provocation(UUID player, long until) {
    }

    /** Defender -> the player it last saw attacking. */
    private static final Map<UUID, Provocation> PROVOKED = new HashMap<>();

    private VillageDefenders() {
    }

    private static Faction faction(QuestContent content) {
        for (Faction faction : content.factions.values()) {
            if (faction.gossip() && faction.defendersAttackAt() != null) {
                return faction;
            }
        }
        return null;
    }

    /** True if defenders attack this player on sight. */
    public static boolean hostile(ServerPlayer player) {
        Faction faction = faction(QuestContent.current);
        if (faction == null || player.isCreative() || player.isSpectator()) {
            return false;
        }
        return Factions.get(QuestEngine.ctx(player), faction.id()) < faction.defendersSafeFrom();
    }

    private static boolean provoked(LivingEntity defender, ServerPlayer player) {
        Provocation provocation = PROVOKED.get(defender.getUUID());
        if (provocation == null) {
            return false;
        }
        if (provocation.until() < defender.level().getGameTime()) {
            PROVOKED.remove(defender.getUUID());
            return false;
        }
        return provocation.player().equals(player.getUUID());
    }

    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event) {
        LivingEntity defender = event.getEntity();
        if (event.getNewTarget() instanceof ServerPlayer player && defender.getType().is(DEFENDERS)
                && faction(QuestContent.current) != null
                && defender.getLastHurtByMob() != player && !provoked(defender, player) && !hostile(player)) {
            event.setCanceled(true);
        }
    }

    /** Defenders that see a player hurt a villager or a defender may chase him, and do if they are idle. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer player) || player.isCreative() || player.isSpectator()
                || !(victim instanceof AbstractVillager) && !victim.getType().is(DEFENDERS)
                || faction(QuestContent.current) == null) {
            return;
        }
        long until = victim.level().getGameTime() + PROVOKED_TICKS;
        for (Mob defender : victim.level().getEntitiesOfClass(Mob.class, victim.getBoundingBox().inflate(SIGHT_RANGE),
                mob -> mob != victim && mob.getType().is(DEFENDERS) && mob.hasLineOfSight(victim))) {
            PROVOKED.put(defender.getUUID(), new Provocation(player.getUUID(), until));
            if (defender.getTarget() == null) {
                defender.setTarget(player);
            }
        }
    }

    /** Once a second, idle defenders that can see a hostile player go for him. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || !hostile(player)) {
            return;
        }
        for (Mob defender : player.level().getEntitiesOfClass(Mob.class, player.getBoundingBox().inflate(AGGRO_RANGE),
                mob -> mob.getType().is(DEFENDERS) && mob.getTarget() == null && mob.hasLineOfSight(player))) {
            defender.setTarget(player);
        }
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSource().getEntity() instanceof LivingEntity killer) || !killer.getType().is(DEFENDERS)) {
            return;
        }
        PROVOKED.values().removeIf(provocation -> provocation.player().equals(player.getUUID()));
        Faction faction = faction(QuestContent.current);
        if (faction == null) {
            return;
        }
        ServerCtx ctx = QuestEngine.ctx(player);
        if (Factions.get(ctx, faction.id()) < faction.defendersSafeFrom()) {
            Factions.set(ctx, faction, faction.defendersSafeFrom());
        }
    }
}
