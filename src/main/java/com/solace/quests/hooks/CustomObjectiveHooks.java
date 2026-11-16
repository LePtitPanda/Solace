package com.solace.quests.hooks;

import com.solace.quests.api.QuestApi;
import com.solace.world.HomeVillage;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.ShieldBlockEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Feeds {"type": "solace:custom", "handler": …} objectives from game events. Handlers:
 * {@code target_hit_20/30/40} (an arrow of the player hits a target block at least that far away),
 * {@code dummy_hit}, {@code dummy_crit} (melee on a Dummmmmmy target dummy), {@code shield_block} (a hostile's
 * attack blocked), {@code sheep_led_home} (a sheep on the player's lead brought within 32 blocks of the home
 * village), {@code gift_given} (Thief), {@code combat_roll} (Combat Roll), {@code spell_inscribed} and
 * {@code sound_hit} (Iron's Spellbooks, Sound school damage).
 */
public final class CustomObjectiveHooks {
    private static final ResourceLocation DUMMY = new ResourceLocation("dummmmmmy", "target_dummy");
    private static final ResourceLocation SOUND_SCHOOL = new ResourceLocation("alshanex_familiars", "sound");
    private static final String LED_HOME_TAG = "solace_led_home";

    private CustomObjectiveHooks() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(CustomObjectiveHooks.class);
        if (ModList.get().isLoaded("thief")) MinecraftForge.EVENT_BUS.register(Thief.class);
        if (ModList.get().isLoaded("irons_spellbooks")) MinecraftForge.EVENT_BUS.register(Spells.class);
        if (ModList.get().isLoaded("combatroll")) Roll.register();
    }

    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getRayTraceResult().getType() != HitResult.Type.BLOCK
                || !(event.getProjectile().getOwner() instanceof ServerPlayer player)) return;
        BlockPos pos = ((BlockHitResult) event.getRayTraceResult()).getBlockPos();
        if (!event.getProjectile().level().getBlockState(pos).is(Blocks.TARGET)) return;
        double distance = Math.sqrt(player.distanceToSqr(pos.getCenter()));
        for (int range : new int[] {20, 30, 40}) {
            if (distance >= range) QuestApi.fire(player, "target_hit_" + range, 1);
        }
    }

    @SubscribeEvent
    public static void onCriticalHit(CriticalHitEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isDummy(event.getTarget())) return;
        QuestApi.fire(player, "dummy_hit", 1);
        if (event.isVanillaCritical() || event.getResult() == net.minecraftforge.eventbus.api.Event.Result.ALLOW) {
            QuestApi.fire(player, "dummy_crit", 1);
        }
    }

    @SubscribeEvent
    public static void onShieldBlock(ShieldBlockEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getDamageSource().getEntity() instanceof Enemy) {
            QuestApi.fire(player, "shield_block", 1);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0 || player.level().dimension() != ServerLevel.OVERWORLD) return;
        BlockPos home = HomeVillage.homeVillage(player.serverLevel());
        if (home == null || !home.closerToCenterThan(player.position(), 48)) return;
        for (Sheep sheep : player.level().getEntitiesOfClass(Sheep.class, player.getBoundingBox().inflate(12),
                sheep -> sheep.getLeashHolder() == player && !sheep.getTags().contains(LED_HOME_TAG))) {
            if (home.closerToCenterThan(sheep.position(), 32)) {
                sheep.addTag(LED_HOME_TAG);
                QuestApi.fire(player, "sheep_led_home", 1);
            }
        }
    }

    private static boolean isDummy(Entity entity) {
        return DUMMY.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    public static final class Thief {
        @SubscribeEvent
        public static void onGift(io.github.mortuusars.thief.forge.api.event.GiftGivenEvent event) {
            QuestApi.fire(event.player, "gift_given", 1);
        }

        private Thief() {
        }
    }

    public static final class Spells {
        @SubscribeEvent
        public static void onInscribe(io.redspace.ironsspellbooks.api.events.InscribeSpellEvent event) {
            if (event.getEntity() instanceof ServerPlayer player) QuestApi.fire(player, "spell_inscribed", 1);
        }

        @SubscribeEvent
        public static void onSpellDamage(io.redspace.ironsspellbooks.api.events.SpellDamageEvent event) {
            var source = event.getSpellDamageSource();
            if (source.getEntity() instanceof ServerPlayer player && source.spell() != null
                    && SOUND_SCHOOL.equals(source.spell().getSchoolType().getId())) {
                QuestApi.fire(player, "sound_hit", 1);
            }
        }

        private Spells() {
        }
    }

    static final class Roll {
        static void register() {
            net.combatroll.api.event.ServerSideRollEvents.PLAYER_START_ROLLING.register(
                    (player, velocity) -> QuestApi.fire(player, "combat_roll", 1));
        }

        private Roll() {
        }
    }
}
