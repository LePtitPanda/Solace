package com.solace.item;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;

public class IdentifiedArtifactItem extends EnchantedBookItem {
    public IdentifiedArtifactItem(Properties properties) {
        super(properties);
    }

    @Override
    public void verifyTagAfterLoad(CompoundTag tag) {
        super.verifyTagAfterLoad(tag);
        if (!tag.contains(TAG_STORED_ENCHANTMENTS, Tag.TAG_LIST)
                && tag.contains("Enchantments", Tag.TAG_LIST)) {
            tag.put(TAG_STORED_ENCHANTMENTS, tag.getList("Enchantments", Tag.TAG_COMPOUND).copy());
            tag.remove("Enchantments");
        }
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }
}
