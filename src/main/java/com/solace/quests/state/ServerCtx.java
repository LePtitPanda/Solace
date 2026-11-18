package com.solace.quests.state;

import com.solace.quests.condition.Ctx;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.Target;
import com.solace.quests.api.QuestApi;
import com.solace.quests.network.QuestSync;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/** {@link Ctx} backed by a live player and the world's quest store. */
public final class ServerCtx extends Ctx {
    public final ServerPlayer player;
    private final QuestSavedData store;
    private final PlayerQuestData data;

    ServerCtx(ServerPlayer player) {
        this.player = player;
        this.store = QuestSavedData.get(player.server);
        // v1: every player is their own progress owner. Party progress changes only this line.
        this.data = store.owner(player.getUUID());
    }

    @Override
    public QuestContent content() {
        return QuestContent.current;
    }

    @Override
    public PlayerQuestData data() {
        return data;
    }

    @Override
    public long gameTime() {
        return player.server.overworld().getGameTime();
    }

    @Override
    public String dimension() {
        return player.level().dimension().location().toString();
    }

    @Override
    public long dayTime() {
        return player.level().getDayTime() % 24000L;
    }

    @Override
    public int countItem(Target item, boolean heldOnly) {
        if (heldOnly) {
            ItemStack main = player.getMainHandItem();
            ItemStack off = player.getOffhandItem();
            return (item.matches(main) ? main.getCount() : 0) + (item.matches(off) ? off.getCount() : 0);
        }
        // maxCount 0 = count without removing.
        return player.getInventory().clearOrCountMatchingItems(item::matches, 0, player.inventoryMenu.getCraftSlots());
    }

    @Override
    public boolean predicate(ResourceLocation id) {
        LootItemCondition condition = player.server.getLootData().getElement(LootDataType.PREDICATE, id);
        if (condition == null) {
            return false;
        }
        LootParams params = new LootParams.Builder(player.serverLevel())
                .withParameter(LootContextParams.ORIGIN, player.position())
                .withParameter(LootContextParams.THIS_ENTITY, player)
                .create(LootContextParamSets.COMMAND);
        return condition.test(new LootContext.Builder(params).create((ResourceLocation) null));
    }

    @Override
    public int reputation(ResourceLocation faction) {
        return Factions.get(this, faction);
    }

    @Override
    public boolean inBiome(Target biome) {
        Holder<Biome> here = player.level().getBiome(player.blockPosition());
        return biome.tag() ? here.is(TagKey.create(Registries.BIOME, biome.id())) : here.is(biome.id());
    }

    @Override
    public boolean inStructure(Target structure, String piece) {
        return structureStart(structure, piece, false) != null;
    }

    @Override
    public boolean hasAdvancement(ResourceLocation id) {
        Advancement advancement = player.server.getAdvancements().getAdvancement(id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    @Override
    public boolean inArea(String area) {
        return Areas.get(player.server).contains(area, dimension(), player.getX(), player.getZ());
    }

    @Override
    public boolean nearNpc(ResourceLocation binding, int radius) {
        com.solace.quests.content.Dialogue.NpcBinding npc = content().npcs.get(binding);
        return npc != null && com.solace.quests.npc.NpcRegistry.nearest(this, npc, radius) != null;
    }

    /** "dimension x z" of the start of the structure the player stands in, or null. */
    /**
     * Identity of the structure the player stands in ("dimension chunkX chunkZ"), or null. With {@code piece},
     * the player must also stand inside one of its jigsaw pieces whose template matches; {@code perPiece} adds
     * that piece's corner to the identity, so each piece counts once.
     */
    public String structureStart(Target structure, String piece, boolean perPiece) {
        ServerLevel level = player.serverLevel();
        BlockPos pos = player.blockPosition();
        if (!level.isLoaded(pos)) {
            return null;
        }
        net.minecraft.world.level.levelgen.structure.StructureStart start;
        if (structure.tag()) {
            start = level.structureManager().getStructureWithPieceAt(pos,
                    TagKey.create(Registries.STRUCTURE, structure.id()));
        } else {
            Structure exact = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(structure.id());
            start = exact == null ? null : level.structureManager().getStructureWithPieceAt(pos, exact);
        }
        if ((start == null || !start.isValid()) && structure.id().getPath().contains("village")) {
            start = com.solace.world.VillageBuildingLocator.homeStartAt(level, pos);
        }
        if (start == null || !start.isValid()) {
            return null;
        }
        String id = dimension() + " " + start.getChunkPos().x + " " + start.getChunkPos().z;
        if (piece == null) {
            return id;
        }
        for (net.minecraft.world.level.levelgen.structure.StructurePiece part : start.getPieces()) {
            if (part.getBoundingBox().isInside(pos) && pieceMatches(part, piece)) {
                return perPiece ? id + " " + part.getBoundingBox().minX() + " " + part.getBoundingBox().minY()
                        + " " + part.getBoundingBox().minZ() : id;
            }
        }
        return null;
    }

    /** {@code pattern}: a template id, an id prefix ending in "*", or "*" for any piece. */
    static boolean pieceMatches(net.minecraft.world.level.levelgen.structure.StructurePiece part, String pattern) {
        if (!(part instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool)
                || !(pool.getElement() instanceof net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement single)) {
            return false;
        }
        String template = ((com.solace.mixin.SinglePoolElementAccessor) single).solace$getTemplate().left()
                .map(ResourceLocation::toString).orElse(null);
        return template != null && templateMatches(template, pattern);
    }

    public static boolean templateMatches(String template, String pattern) {
        return pattern.endsWith("*") ? template.startsWith(pattern.substring(0, pattern.length() - 1))
                : template.equals(pattern);
    }

    @Override
    public boolean custom(String handler, String json) {
        QuestApi.ConditionHandler registered = QuestApi.CONDITIONS.get(handler);
        return registered != null && registered.test(player, json);
    }

    @Override
    public void changed() {
        data.index = null;
        store.setDirty();
        QuestSync.dirty(player);
    }
}
