package com.solace.entity;

import com.solace.Solace;
import com.solace.quests.npc.NpcRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A killed Guildmaster comes back at home a minute later (once that chunk is loaded): same UUID, name and
 * quest identity, full health, so the NPC binding and every quest pointing at it keep working.
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID)
public final class GuildmasterRespawn extends SavedData {
    private static final String NAME = "solace_guildmaster_respawn";
    private static final int DELAY_TICKS = 20 * 60;
    private final ListTag pending = new ListTag();

    private static GuildmasterRespawn get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(tag -> {
            GuildmasterRespawn data = new GuildmasterRespawn();
            data.pending.addAll(tag.getList("pending", Tag.TAG_COMPOUND));
            return data;
        }, GuildmasterRespawn::new, NAME);
    }

    static void schedule(ServerLevel level, Guildmaster guildmaster, BlockPos home) {
        CompoundTag entity = guildmaster.saveWithoutId(new CompoundTag());
        for (String key : new String[] {"Health", "DeathTime", "HurtTime", "HurtByTimestamp", "ActiveEffects", "Fire",
                "Motion", "FallDistance", "Brain", "leash", "Leash"}) {
            entity.remove(key);
        }
        entity.putString("id", "solace:guildmaster");
        CompoundTag entry = new CompoundTag();
        entry.put("entity", entity);
        entry.putString("dimension", level.dimension().location().toString());
        entry.putLong("home", home.asLong());
        entry.putLong("due", level.getGameTime() + DELAY_TICKS);
        GuildmasterRespawn data = get(level.getServer());
        data.pending.add(entry);
        data.setDirty();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        MinecraftServer server = event.getServer();
        if (event.phase != TickEvent.Phase.END || server.getTickCount() % 20 != 0) {
            return;
        }
        GuildmasterRespawn data = get(server);
        for (int i = data.pending.size() - 1; i >= 0; i--) {
            CompoundTag entry = data.pending.getCompound(i);
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION,
                    new ResourceLocation(entry.getString("dimension"))));
            BlockPos home = BlockPos.of(entry.getLong("home"));
            if (level == null || level.getGameTime() < entry.getLong("due") || !level.isLoaded(home)) {
                continue;
            }
            Entity entity = EntityType.loadEntityRecursive(entry.getCompound("entity"), level, e -> {
                e.moveTo(home.getX() + 0.5, home.getY(), home.getZ() + 0.5, e.getYRot(), e.getXRot());
                return e;
            });
            if (entity != null && level.getEntity(entity.getUUID()) == null && level.addFreshEntity(entity)) {
                String identity = NpcRegistry.identity(entity);
                if (identity != null) {
                    NpcRegistry.get(server).bind(identity, entity, false);
                }
            }
            data.pending.remove(i);
            data.setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.put("pending", pending);
        return tag;
    }
}
