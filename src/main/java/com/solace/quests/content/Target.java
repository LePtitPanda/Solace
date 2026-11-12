package com.solace.quests.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/** A registry id or a tag ("#namespace:path"). Tags are checked live, so datapack tag reloads just work. */
public record Target(boolean tag, ResourceLocation id) {
    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && (tag ? stack.is(TagKey.create(Registries.ITEM, id))
                : id.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())));
    }

    public boolean matches(BlockState state) {
        return tag ? state.is(TagKey.create(Registries.BLOCK, id))
                : id.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    public boolean matches(Entity entity) {
        return tag ? entity.getType().is(TagKey.create(Registries.ENTITY_TYPE, id))
                : id.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()));
    }

    @Override
    public String toString() {
        return (tag ? "#" : "") + id;
    }
}
