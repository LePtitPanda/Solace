package com.solace.performance;

import net.lixir.vminus.vision.util.ItemReplacement;
import net.lixir.vminus.vision.util.VisionUtils;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.tags.ITag;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Caches VMinus 3.3.1's item-only ingredient filtering decisions.
 *
 * <p>That release supports rarity and entity-NBT vision conditions. In an
 * ItemStack context, both replacement and ban decisions therefore depend only
 * on the Item. For an unchanged ingredient, the immutable recipe-value stack
 * is reused. That restores Forge's normal {@code Ingredient#getItems} contract
 * (which caches and reuses these same stack objects) and avoids millions of
 * redundant defensive copies during JEI/EMI indexing. Direct replacement
 * stacks are still copied by VMinus' ItemReplacement accessor.</p>
 */
public final class VMinusIngredientFilterCache {
    private static final AtomicLong VISION_EPOCH = new AtomicLong(1L);
    private static final ConcurrentHashMap<Item, StampedPlan> PLANS = new ConcurrentHashMap<>();
    private static final LongAdder CACHE_HITS = new LongAdder();
    private static final LongAdder CACHE_MISSES = new LongAdder();

    private VMinusIngredientFilterCache() {
    }

    public static void filter(ItemStack stack, List<ItemStack> output) {
        FilterPlan plan = planFor(stack);
        ItemReplacement replacement = plan.replacement();

        if (replacement != null) {
            ItemStack replacementStack = replacement.itemStack();
            if (replacementStack != null) {
                output.add(replacementStack);
                return;
            }

            TagKey<Item> replacementTag = replacement.tag();
            if (replacementTag != null) {
                ITag<Item> tag = ForgeRegistries.ITEMS.tags().getTag(replacementTag);
                if (!tag.isEmpty()) {
                    for (Item item : tag) {
                        output.add(item.getDefaultInstance());
                    }
                }
                // An empty replacement tag still suppresses the original item,
                // matching VisionUtils.filterIngredient exactly.
                return;
            }
        }

        if (!plan.suppressOriginal()) {
            output.add(stack);
        }
    }

    public static void reset() {
        VISION_EPOCH.incrementAndGet();
        PLANS.clear();
    }

    public static void markVisionDataChanged() {
        VISION_EPOCH.incrementAndGet();
    }

    public static String describeStatus() {
        return "VMinus ingredient cache: plans=" + PLANS.size()
                + ", hits=" + CACHE_HITS.sum()
                + ", misses=" + CACHE_MISSES.sum()
                + ", epoch=" + VISION_EPOCH.get();
    }

    private static FilterPlan planFor(ItemStack stack) {
        Item item = stack.getItem();

        while (true) {
            long epoch = VISION_EPOCH.get();
            StampedPlan cached = PLANS.get(item);
            if (cached != null && cached.epoch() == epoch) {
                CACHE_HITS.increment();
                return cached.plan();
            }

            CACHE_MISSES.increment();
            ItemReplacement replacement = ItemReplacement.from(stack);
            FilterPlan computed = new FilterPlan(
                    replacement,
                    VisionUtils.isItemBannedOrReplaced(stack, replacement)
            );

            if (VISION_EPOCH.get() == epoch) {
                PLANS.put(item, new StampedPlan(epoch, computed));
                return computed;
            }
        }
    }

    private record FilterPlan(ItemReplacement replacement, boolean suppressOriginal) {
    }

    private record StampedPlan(long epoch, FilterPlan plan) {
    }
}
