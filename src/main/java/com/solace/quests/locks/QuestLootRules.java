package com.solace.quests.locks;

import com.solace.quests.content.Locks.Drop;
import com.solace.quests.content.Locks.QuestLoot;
import com.solace.quests.content.QuestContent;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.ServerCtx;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

/** Applies {@code loot/} content: extra items in generated loot while a quest condition holds. */
public final class QuestLootRules {
    private QuestLootRules() {
    }

    public static void apply(ResourceLocation table, ObjectArrayList<ItemStack> loot, LootContext context) {
        QuestContent content = QuestContent.current;
        if (content.loot.isEmpty() || table == null) {
            return;
        }
        ServerLevel level = context.getLevel();
        ServerPlayer player = player(context);
        Entity victim = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        BlockState block = context.getParamOrNull(LootContextParams.BLOCK_STATE);
        Vec3 origin = context.getParamOrNull(LootContextParams.ORIGIN);
        String id = table.toString();
        for (QuestLoot rule : content.loot.values()) {
            if (!rule.tables().isEmpty() && rule.tables().stream().noneMatch(pattern -> pattern.endsWith("*")
                    ? id.startsWith(pattern.substring(0, pattern.length() - 1)) : id.equals(pattern))) {
                continue;
            }
            // A rule with no table, entity or block filter is about containers: chest-type tables only.
            if (rule.tables().isEmpty() && rule.entity() == null && rule.block() == null
                    && !table.getPath().contains("chests/")) {
                continue;
            }
            if (rule.entity() != null && (victim == null || victim == player || !rule.entity().matches(victim))) {
                continue;
            }
            if (rule.block() != null && (block == null || !rule.block().matches(block))) {
                continue;
            }
            if (rule.structure() != null && (origin == null || !inStructure(level, BlockPos.containing(origin),
                    rule.structure()))) {
                continue;
            }
            // Rules about the player need one: loot rolled without a player never gets them.
            String onceFlag = "loot:" + rule.id();
            ServerCtx ctx = player == null ? null : QuestEngine.ctx(player);
            if ((rule.when() != null || rule.once()) && ctx == null
                    || rule.when() != null && !rule.when().test(ctx)
                    || rule.once() && ctx.data().flags.contains(onceFlag)
                    || context.getRandom().nextFloat() >= rule.chance()) {
                continue;
            }
            boolean added = false;
            for (Drop drop : rule.items()) {
                Item item = ForgeRegistries.ITEMS.getValue(drop.item());
                if (item == null || item == Items.AIR || context.getRandom().nextFloat() >= drop.chance()) {
                    continue;
                }
                ItemStack stack = new ItemStack(item, drop.min() + context.getRandom().nextInt(drop.max() - drop.min() + 1));
                if (drop.nbt() != null) {
                    stack.setTag(drop.nbt().copy());
                }
                loot.add(stack);
                added = true;
            }
            if (added && rule.once() && ctx != null) {
                ctx.data().flags.add(onceFlag);
                ctx.changed();
            }
        }
    }

    /** The player who opened, killed or broke: this entity if a player, else the killer. */
    private static ServerPlayer player(LootContext context) {
        if (context.getParamOrNull(LootContextParams.THIS_ENTITY) instanceof ServerPlayer player) {
            return player;
        }
        if (context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER) instanceof ServerPlayer player) {
            return player;
        }
        return context.getParamOrNull(LootContextParams.KILLER_ENTITY) instanceof ServerPlayer player ? player : null;
    }

    private static boolean inStructure(ServerLevel level, BlockPos pos, com.solace.quests.content.Target target) {
        if (!level.isLoaded(pos)) {
            return false;
        }
        if (target.tag()) {
            return level.structureManager().getStructureWithPieceAt(pos,
                    TagKey.create(Registries.STRUCTURE, target.id())).isValid();
        }
        Structure exact = level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(target.id());
        return exact != null && level.structureManager().getStructureWithPieceAt(pos, exact).isValid();
    }
}
