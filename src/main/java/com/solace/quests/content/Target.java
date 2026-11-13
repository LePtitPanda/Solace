package com.solace.quests.content;

import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * A registry id or a tag ("#namespace:path"). Tags are checked live, so datapack tag reloads just work.
 * {@code nbt} (items only, may be null) must be contained in the stack's tag.
 */
public record Target(boolean tag, ResourceLocation id, CompoundTag nbt) {
    public Target(boolean tag, ResourceLocation id) {
        this(tag, id, null);
    }

    public Target withNbt(CompoundTag wanted) {
        return new Target(tag, id, wanted);
    }

    public boolean matches(ItemStack stack) {
        return !stack.isEmpty() && (tag ? stack.is(TagKey.create(Registries.ITEM, id))
                : id.equals(ForgeRegistries.ITEMS.getKey(stack.getItem())))
                && (nbt == null || NbtUtils.compareNbt(nbt, stack.getTag(), true));
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
