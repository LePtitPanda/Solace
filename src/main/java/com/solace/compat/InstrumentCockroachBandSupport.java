package com.solace.compat;

import com.github.alexthe666.alexsmobs.entity.EntityCockroach;
import immersive_melodies.item.InstrumentItem;
import immersive_melodies.resources.ServerMelodyManager;
import immersive_melodies.util.EntityEquiper;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

public final class InstrumentCockroachBandSupport {
    private static final String IMMERSIVE_MELODIES = "immersive_melodies";
    private static final String ALEXS_MOBS = "alexsmobs";
    private static final ResourceLocation COCKROACH_ID = new ResourceLocation(ALEXS_MOBS, "cockroach");

    private static final String BAND_SPAWNED = "SolaceInstrumentCockroachBandSpawnedV2";
    private static final String BAND_FOLLOWER = "SolaceInstrumentCockroachBandFollower";
    private static final String BAND_OWNER = "SolaceInstrumentCockroachBandOwner";
    private static final String BAND_INDEX = "SolaceInstrumentCockroachBandIndex";
    private static final String BAND_RETURNING = "SolaceInstrumentCockroachBandReturning";
    private static final double RETURN_START_DISTANCE_SQR = 64.0D;
    private static final double RETURN_DONE_DISTANCE_SQR = 4.0D;

    private static final double[][] BAND_OFFSETS = {
            {1.7D, 0.2D},
            {-1.3D, 1.2D},
            {0.3D, -1.6D}
    };

    private InstrumentCockroachBandSupport() {
    }

    public static void tryGiveInstrumentToMob(PlayerInteractEvent.EntityInteract event) {
        if (!isImmersiveMelodiesLoaded()) {
            return;
        }

        Player player = event.getEntity();
        if (!(event.getTarget() instanceof Mob mob)) {
            return;
        }

        ItemStack held = player.getItemInHand(event.getHand());
        if (!(held.getItem() instanceof InstrumentItem instrumentItem)) {
            return;
        }

        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide()));
        event.setCanceled(true);

        if (!(event.getLevel() instanceof ServerLevel level) || !EntityEquiper.canPickUp(mob) || hasInstrument(mob)) {
            return;
        }

        EquipmentSlot slot = mob.getItemBySlot(EquipmentSlot.MAINHAND).isEmpty()
                ? EquipmentSlot.MAINHAND
                : mob.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty() ? EquipmentSlot.OFFHAND : null;
        if (slot == null) {
            return;
        }

        ItemStack instrumentStack = held.copyWithCount(1);
        startPlaying(instrumentItem, instrumentStack, level, mob);
        mob.setItemSlot(slot, instrumentStack);
        mob.setPersistenceRequired();

        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }

        if (isAlexsMobsLoaded() && !mob.getPersistentData().getBoolean(BAND_SPAWNED)) {
            spawnCockroachBand(level, mob);
        }

        event.setCancellationResult(InteractionResult.SUCCESS);
        event.setCanceled(true);
    }

    public static void tick(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return;
        }

        if (isAlexsMobsLoaded() && entity instanceof EntityCockroach cockroach) {
            tickBandFollower(cockroach, level);
            return;
        }

        if (!isImmersiveMelodiesLoaded() || !isAlexsMobsLoaded() || !(entity instanceof Mob mob)) {
            return;
        }

        if (mob.tickCount % 20 != 0 || mob.getPersistentData().getBoolean(BAND_SPAWNED)) {
            return;
        }

        if (ensureHeldInstrumentPlaying(mob, level)) {
            spawnCockroachBand(level, mob);
        }
    }

    private static void startPlaying(InstrumentItem item, ItemStack stack, ServerLevel level, Entity entity) {
        ResourceLocation melody = ServerMelodyManager.getRandomMelody();
        if (melody != null) {
            item.play(stack, melody, level, entity);
        } else {
            item.play(stack, level);
        }
    }

    private static boolean hasInstrument(LivingEntity entity) {
        return isInstrument(entity.getItemBySlot(EquipmentSlot.MAINHAND))
                || isInstrument(entity.getItemBySlot(EquipmentSlot.OFFHAND));
    }

    private static boolean isInstrument(ItemStack stack) {
        return stack.getItem() instanceof InstrumentItem;
    }

    private static boolean ensureHeldInstrumentPlaying(LivingEntity entity, ServerLevel level) {
        boolean foundInstrument = false;
        foundInstrument |= ensureStackPlaying(entity.getItemBySlot(EquipmentSlot.MAINHAND), level, entity);
        foundInstrument |= ensureStackPlaying(entity.getItemBySlot(EquipmentSlot.OFFHAND), level, entity);
        return foundInstrument;
    }

    private static boolean ensureStackPlaying(ItemStack stack, ServerLevel level, Entity entity) {
        if (!(stack.getItem() instanceof InstrumentItem item)) {
            return false;
        }

        if (!item.isPlaying(stack)) {
            startPlaying(item, stack, level, entity);
        }
        return true;
    }

    private static void spawnCockroachBand(ServerLevel level, Mob musician) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(COCKROACH_ID);
        if (type == null) {
            return;
        }

        int spawned = 0;
        for (int i = 0; i < BAND_OFFSETS.length; i++) {
            Entity entity = type.create(level);
            if (!(entity instanceof EntityCockroach cockroach)) {
                continue;
            }

            double[] offset = BAND_OFFSETS[i];
            BlockPos spawnPos = findCockroachSpot(level, musician, offset[0], offset[1]);
            cockroach.moveTo(spawnPos.getX() + 0.5D, spawnPos.getY(), spawnPos.getZ() + 0.5D, level.random.nextFloat() * 360.0F, 0.0F);

            cockroach.setPersistenceRequired();
            cockroach.setMaracas(false);
            cockroach.setDancing(true);
            CompoundTag data = cockroach.getPersistentData();
            data.putBoolean(BAND_FOLLOWER, true);
            data.putUUID(BAND_OWNER, musician.getUUID());
            data.putInt(BAND_INDEX, i);
            level.addFreshEntity(cockroach);
            spawned++;
        }

        if (spawned > 0) {
            musician.getPersistentData().putBoolean(BAND_SPAWNED, true);
        }
    }

    private static BlockPos findCockroachSpot(ServerLevel level, Entity owner, double xOffset, double zOffset) {
        BlockPos start = BlockPos.containing(owner.getX() + xOffset, owner.getY(), owner.getZ() + zOffset);
        for (int dy = 1; dy >= -4; dy--) {
            BlockPos candidate = start.offset(0, dy, 0);
            if (canPlaceCockroach(level, candidate)) {
                return candidate;
            }
        }

        return owner.blockPosition();
    }

    private static boolean canPlaceCockroach(ServerLevel level, BlockPos pos) {
        BlockPos below = pos.below();
        return level.getWorldBorder().isWithinBounds(pos)
                && level.isEmptyBlock(pos)
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    private static void tickBandFollower(EntityCockroach cockroach, ServerLevel level) {
        CompoundTag data = cockroach.getPersistentData();
        if (!data.getBoolean(BAND_FOLLOWER) || !data.hasUUID(BAND_OWNER)) {
            return;
        }

        cockroach.setPersistenceRequired();
        cockroach.setMaracas(false);
        cockroach.setDancing(!data.getBoolean(BAND_RETURNING));

        if (cockroach.tickCount % 10 != 0) {
            return;
        }

        UUID ownerId = data.getUUID(BAND_OWNER);
        Entity owner = level.getEntity(ownerId);
        if (!(owner instanceof LivingEntity livingOwner) || !livingOwner.isAlive()) {
            return;
        }

        double[] offset = BAND_OFFSETS[Math.floorMod(data.getInt(BAND_INDEX), BAND_OFFSETS.length)];
        BlockPos target = findCockroachSpot(level, owner, offset[0], offset[1]);
        double targetX = target.getX() + 0.5D;
        double targetY = target.getY();
        double targetZ = target.getZ() + 0.5D;
        double targetDistanceSqr = cockroach.distanceToSqr(targetX, targetY, targetZ);
        boolean returning = data.getBoolean(BAND_RETURNING);

        if (!returning && targetDistanceSqr > RETURN_START_DISTANCE_SQR) {
            data.putBoolean(BAND_RETURNING, true);
            returning = true;
        }

        if (returning) {
            cockroach.setDancing(false);
            if (targetDistanceSqr <= RETURN_DONE_DISTANCE_SQR) {
                data.putBoolean(BAND_RETURNING, false);
                cockroach.getNavigation().stop();
                cockroach.setDancing(true);
            } else {
                cockroach.getNavigation().moveTo(targetX, targetY, targetZ, 1.1D);
            }
        }
    }

    private static boolean isImmersiveMelodiesLoaded() {
        return ModList.get().isLoaded(IMMERSIVE_MELODIES);
    }

    private static boolean isAlexsMobsLoaded() {
        return ModList.get().isLoaded(ALEXS_MOBS);
    }
}
