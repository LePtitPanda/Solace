package com.solace.world;

import com.mojang.logging.LogUtils;
import com.solace.config.EnchantingReworkConfig;
import com.solace.registry.SolaceItems;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.slf4j.Logger;

public final class ArtifactEnchantingSupport {
    public static final String ARTIFACT_TAG = "SolaceIdentifiedArtifact";
    private static final String TEXTURE_KEY = "Texture";
    private static final String ENCHANTMENT_KEY = "Enchantment";
    private static final String LEVEL_KEY = "Level";
    private static final ResourceLocation INFUSED_BOOK = new ResourceLocation("kubejs", "infused_book");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static List<ResourceLocation> configuredBag = List.of();
    private static final List<ResourceLocation> remainingBag = new ArrayList<>();

    private ArtifactEnchantingSupport() {
    }

    public static synchronized ItemStack createIdentifiedArtifact(RandomSource random) {
        Map<ResourceLocation, Enchantment> enchantments = new LinkedHashMap<>();
        for (ResourceLocation id : EnchantingReworkConfig.artifactEnchantments()) {
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
            if (enchantment != null && BuiltInRegistries.ENCHANTMENT.getKey(enchantment).equals(id)) {
                enchantments.put(id, enchantment);
            } else {
                LOGGER.warn("[Solace] Ignoring unknown identified artifact enchantment {}", id);
            }
        }

        if (enchantments.isEmpty()) {
            LOGGER.error("[Solace] No valid identified artifact enchantments are configured");
            return ItemStack.EMPTY;
        }

        List<ResourceLocation> configured = List.copyOf(enchantments.keySet());
        if (!configured.equals(configuredBag)) {
            configuredBag = configured;
            remainingBag.clear();
        }
        if (remainingBag.isEmpty()) {
            remainingBag.addAll(configuredBag);
            for (int index = remainingBag.size() - 1; index > 0; index--) {
                int swapIndex = random.nextInt(index + 1);
                ResourceLocation value = remainingBag.get(index);
                remainingBag.set(index, remainingBag.get(swapIndex));
                remainingBag.set(swapIndex, value);
            }
        }

        ResourceLocation enchantmentId = remainingBag.remove(remainingBag.size() - 1);
        Enchantment enchantment = enchantments.get(enchantmentId);
        int level = Math.max(1, enchantment.getMinLevel());
        int texture = random.nextInt(3) + 1;

        ItemStack artifact = new ItemStack(SolaceItems.IDENTIFIED_ARTIFACT.get());
        EnchantedBookItem.addEnchantment(artifact, new EnchantmentInstance(enchantment, level));
        artifact.getOrCreateTag().putInt("CustomModelData", texture);
        CompoundTag marker = new CompoundTag();
        marker.putInt(TEXTURE_KEY, texture);
        marker.putString(ENCHANTMENT_KEY, enchantmentId.toString());
        marker.putInt(LEVEL_KEY, level);
        artifact.getOrCreateTag().put(ARTIFACT_TAG, marker);
        return artifact;
    }

    public static boolean isIdentifiedArtifact(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return stack.is(SolaceItems.IDENTIFIED_ARTIFACT.get()) && tag != null && tag.contains(ARTIFACT_TAG, 10);
    }

    public static Optional<ArtifactEnchantment> artifactEnchantment(ItemStack stack) {
        if (!isIdentifiedArtifact(stack)) {
            return Optional.empty();
        }

        CompoundTag marker = stack.getTag().getCompound(ARTIFACT_TAG);
        if (!marker.contains(ENCHANTMENT_KEY, CompoundTag.TAG_STRING)
                || !marker.contains(LEVEL_KEY, CompoundTag.TAG_INT)) {
            return Optional.empty();
        }

        try {
            ResourceLocation id = new ResourceLocation(marker.getString(ENCHANTMENT_KEY));
            Enchantment enchantment = BuiltInRegistries.ENCHANTMENT.get(id);
            int level = marker.getInt(LEVEL_KEY);
            if (enchantment == null || !id.equals(BuiltInRegistries.ENCHANTMENT.getKey(enchantment))
                    || level < enchantment.getMinLevel() || level > enchantment.getMaxLevel()) {
                return Optional.empty();
            }
            return Optional.of(new ArtifactEnchantment(enchantment, level));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    public static ItemStack arcaneAnvilResult(ItemStack left, ItemStack right) {
        ItemStack artifact;
        ItemStack catalyst;
        if (isIdentifiedArtifact(left)) {
            artifact = left;
            catalyst = right;
        } else if (isIdentifiedArtifact(right)) {
            artifact = right;
            catalyst = left;
        } else {
            return ItemStack.EMPTY;
        }

        Item infusedBook = BuiltInRegistries.ITEM.get(INFUSED_BOOK);
        if (infusedBook == null || !catalyst.is(infusedBook)) {
            return ItemStack.EMPTY;
        }

        Optional<ArtifactEnchantment> stored = artifactEnchantment(artifact);
        if (stored.isEmpty()) {
            return ItemStack.EMPTY;
        }

        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        EnchantedBookItem.addEnchantment(result,
                new EnchantmentInstance(stored.get().enchantment(), stored.get().level()));
        return result;
    }

    public record ArtifactEnchantment(Enchantment enchantment, int level) {
    }
}
