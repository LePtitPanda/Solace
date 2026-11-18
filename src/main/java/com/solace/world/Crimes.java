package com.solace.world;

import com.solace.Solace;
import com.solace.quests.content.Faction;
import com.solace.quests.state.Factions;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.ServerCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.LongArrayTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Crimes in villages (what the Thief mod used to do): breaking or opening village property, killing village
 * animals, kicking a villager out of bed or sleeping in theirs. Only inside {@code #solace:crime_protected}
 * structures, and only when a witness ({@code #solace:crime_witnesses}) sees it: 32 blocks in plain sight,
 * less when sneaking, in the dark or invisible, always within 3 blocks. Each crime costs standing with the
 * villager-gossip factions. Blocks the player placed are theirs; the Hero of the Village may take anything;
 * the flag {@code crime.harvest_ok} lets the player cut crops (the Farmer asked).
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID)
public final class Crimes {
    /** Standing lost per crime: Thief's gossip (minor + 5 x major) for light, medium and heavy. */
    private static final int LIGHT = 15, MEDIUM = 50, HEAVY = 150;
    private static final double NOTICE = 32, ALWAYS = 3;
    private static final TagKey<Structure> PROTECTED = TagKey.create(Registries.STRUCTURE, id("crime_protected"));
    private static final TagKey<EntityType<?>> WITNESSES = TagKey.create(Registries.ENTITY_TYPE, id("crime_witnesses"));

    private Crimes() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(Solace.MOD_ID, path);
    }

    private static TagKey<Block> blocks(String path) {
        return TagKey.create(Registries.BLOCK, id("crime/" + path));
    }

    private static int severity(BlockState state, String kind) {
        if (state.is(blocks(kind + "_heavy"))) return HEAVY;
        if (state.is(blocks(kind + "_medium"))) return MEDIUM;
        return state.is(blocks(kind + "_light")) ? LIGHT : 0;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) return;
        BlockState state = event.getState();
        if (state.getBlock() instanceof CropBlock && QuestEngine.ctx(player).data().flags.contains("crime.harvest_ok")) return;
        BlockPos pos = event.getPos();
        commit(player, pos, owned(player.serverLevel(), own(state, pos), true) ? 0 : severity(state, "break"), "breaking village property");
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof ServerPlayer player
                && (severity(event.getPlacedBlock(), "break") > 0 || severity(event.getPlacedBlock(), "interact") > 0)) {
            Owned.of(player.serverLevel()).add(own(event.getPlacedBlock(), event.getPos()));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInteract(PlayerInteractEvent.RightClickBlock event) {
        if (event.isCanceled() || event.getHand() != net.minecraft.world.InteractionHand.MAIN_HAND
                || !(event.getEntity() instanceof ServerPlayer player)) return;
        if (player.isSecondaryUseActive() && !event.getItemStack().isEmpty()) return; // placing against it, not opening
        BlockPos pos = event.getPos();
        BlockState state = player.level().getBlockState(pos);
        if (state.getBlock() instanceof BedBlock && state.getValue(BedBlock.OCCUPIED)) {
            BlockPos head = state.getValue(BedBlock.PART) == BedPart.HEAD ? pos : pos.relative(BedBlock.getConnectedDirection(state));
            boolean villagerAsleep = !player.level().getEntitiesOfClass(Villager.class, new AABB(head).inflate(1),
                    v -> v.isSleeping() && v.getSleepingPos().map(head::equals).orElse(false)).isEmpty();
            commit(player, pos, villagerAsleep ? LIGHT : 0, "waking a villager in their bed");
            return;
        }
        commit(player, pos, owned(player.serverLevel(), own(state, pos), false) ? 0 : severity(state, "interact"),
                "going through village property");
    }

    @SubscribeEvent
    public static void onSleep(PlayerSleepInBedEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getResultStatus() != null) return;
        BlockPos bed = event.getPos();
        if (bed.equals(player.getRespawnPosition())) return; // their own bed
        boolean claimed = player.serverLevel().getPoiManager().getInSquare(type -> type.is(PoiTypes.HOME), bed, 1,
                PoiManager.Occupancy.IS_OCCUPIED).findAny().isPresent();
        commit(player, bed, claimed ? MEDIUM : 0, "sleeping in a villager's bed");
    }

    @SubscribeEvent
    public static void onKill(LivingDeathEvent event) {
        if (event.getSource().getEntity() instanceof ServerPlayer player) {
            EntityType<?> type = event.getEntity().getType();
            int severity = type.is(entities("kill_heavy")) ? HEAVY : type.is(entities("kill_medium")) ? MEDIUM
                    : type.is(entities("kill_light")) ? LIGHT : 0;
            commit(player, event.getEntity().blockPosition(), severity, "killing " + event.getEntity().getName().getString());
        }
    }

    /** Village iron golems (not player-built) defend a villager the player hits, as long as the player is seen. */
    @SubscribeEvent
    public static void onHurt(LivingHurtEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager victim) || !(event.getSource().getEntity() instanceof ServerPlayer player)
                || player.isCreative() || player.isSpectator() || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)) return;
        for (IronGolem golem : player.level().getEntitiesOfClass(IronGolem.class, victim.getBoundingBox().inflate(NOTICE),
                golem -> golem.isAlive() && !golem.isPlayerCreated())) {
            golem.setPersistentAngerTarget(player.getUUID());
            golem.startPersistentAngerTimer();
            golem.setTarget(player);
        }
    }

    private static TagKey<EntityType<?>> entities(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, id("crime/" + path));
    }

    private static void commit(ServerPlayer player, BlockPos pos, int severity, String act) {
        if (severity <= 0 || player.isCreative() || player.isSpectator()
                || player.hasEffect(MobEffects.HERO_OF_THE_VILLAGE)
                || !protectedAt(player.serverLevel(), pos)) {
            return;
        }
        List<LivingEntity> witnesses = witnesses(player);
        if (witnesses.isEmpty()) return;
        for (LivingEntity witness : witnesses) {
            if (witness instanceof Villager villager) {
                villager.setUnhappyCounter(40);
                player.serverLevel().sendParticles(ParticleTypes.ANGRY_VILLAGER, villager.getX(), villager.getEyeY() + 0.3,
                        villager.getZ(), 1, 0.2, 0.1, 0.2, 0);
            }
        }
        LivingEntity first = witnesses.get(0);
        player.level().playSound(null, first.blockPosition(), SoundEvents.VILLAGER_NO, SoundSource.NEUTRAL, 1.0F, 1.0F);
        String kind = severity >= HEAVY ? "heavy" : severity >= MEDIUM ? "medium" : "light";
        player.displayClientMessage(Component.literal("You were seen " + act + ": a " + kind + " crime.")
                .withStyle(ChatFormatting.RED), false);
        ServerCtx ctx = QuestEngine.ctx(player);
        for (Faction faction : ctx.content().factions.values()) {
            if (faction.gossip()) {
                Factions.add(ctx, faction, -severity, true);
            }
        }
    }

    /**
     * Inside a protected structure's pieces. The structure lookup misses the home village (see
     * VillageBuildingLocator), so its 112-block square around the stored start counts too.
     */
    private static boolean protectedAt(ServerLevel level, BlockPos pos) {
        if (level.structureManager().getStructureWithPieceAt(pos, PROTECTED).isValid()) return true;
        BlockPos home = level.dimension() == net.minecraft.world.level.Level.OVERWORLD ? HomeVillage.homeVillage(level) : null;
        return home != null && Math.abs(pos.getX() - home.getX()) <= 112 && Math.abs(pos.getZ() - home.getZ()) <= 112;
    }

    /** Witnesses who notice the player right now. */
    static List<LivingEntity> witnesses(ServerPlayer player) {
        double range = NOTICE * visibility(player);
        return player.level().getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(Math.max(range, ALWAYS)),
                witness -> witness.isAlive() && witness.getType().is(WITNESSES) && notices(witness, player, range));
    }

    private static boolean notices(LivingEntity witness, ServerPlayer player, double range) {
        double distance = witness.distanceTo(player);
        if (distance <= (witness.isSleeping() ? ALWAYS / 2 : ALWAYS)) return true;
        return !witness.isSleeping() && distance <= range && witness.hasLineOfSight(player);
    }

    /** 0..1: invisible x0.25, sneaking x0.5, darkness down to x0.25 at light 0. */
    private static double visibility(ServerPlayer player) {
        double visibility = 1.0;
        if (player.hasEffect(MobEffects.INVISIBILITY)) visibility *= 0.25;
        if (player.isCrouching()) visibility *= 0.5;
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        level.updateSkyBrightness();
        int light = Math.max(level.getBrightness(LightLayer.BLOCK, pos), level.getBrightness(LightLayer.SKY, pos) - level.getSkyDarken());
        visibility *= Mth.clampedMap(light, 0, 15, 0.25F, 1.0F);
        return Mth.clamp(visibility, 0, 1);
    }

    /** A bed is owned by its foot. */
    private static BlockPos own(BlockState state, BlockPos pos) {
        return state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART) == BedPart.HEAD
                ? pos.relative(BedBlock.getConnectedDirection(state)) : pos;
    }

    private static boolean owned(ServerLevel level, BlockPos pos, boolean forget) {
        Owned owned = Owned.of(level);
        return forget ? owned.remove(pos) : owned.contains(pos);
    }

    /** Positions of protected blocks (containers, beds, stations...) a player placed: those are theirs. */
    private static final class Owned extends SavedData {
        private final Set<Long> positions = new HashSet<>();

        static Owned of(ServerLevel level) {
            return level.getDataStorage().computeIfAbsent(tag -> {
                Owned owned = new Owned();
                for (long pos : tag.getLongArray("positions")) owned.positions.add(pos);
                return owned;
            }, Owned::new, "solace_owned_blocks");
        }

        void add(BlockPos pos) {
            if (positions.add(pos.asLong())) setDirty();
        }

        boolean contains(BlockPos pos) {
            return positions.contains(pos.asLong());
        }

        boolean remove(BlockPos pos) {
            boolean was = positions.remove(pos.asLong());
            if (was) setDirty();
            return was;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.put("positions", new LongArrayTag(positions.stream().mapToLong(Long::longValue).toArray()));
            return tag;
        }
    }
}
