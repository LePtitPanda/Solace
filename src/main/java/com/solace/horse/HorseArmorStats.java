package com.solace.horse;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Set;

public record HorseArmorStats(int armor, int sackCapacity, double speedMultiplier, String nativeBonusKey) {
    private static final Set<String> COPPER_ARMORS = Set.of(
            "copper_horse_armor", "exposed_copper_horse_armor", "weathered_copper_horse_armor",
            "oxidized_copper_horse_armor", "waxed_copper_horse_armor", "waxed_exposed_copper_horse_armor",
            "waxed_weathered_copper_horse_armor", "waxed_oxidized_copper_horse_armor"
    );

    public static HorseArmorStats of(ItemStack stack) {
        return stack.isEmpty() ? empty() : of(stack.getItem());
    }

    public static HorseArmorStats of(Item item) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(item);
        if (id == null) {
            return empty();
        }

        String namespace = id.getNamespace();
        String path = id.getPath();
        if (namespace.equals("minecraft")) {
            return switch (path) {
                case "leather_horse_armor" -> new HorseArmorStats(3, 1, 0.10D, "");
                case "iron_horse_armor" -> new HorseArmorStats(5, 2, 0.0D, "");
                case "golden_horse_armor" -> new HorseArmorStats(7, 0, 0.30D, "");
                case "diamond_horse_armor" -> new HorseArmorStats(11, 3, 0.0D, "");
                default -> vanilla(item);
            };
        }
        if (namespace.equals("galosphere") && path.equals("sterling_horse_armor")) {
            return new HorseArmorStats(4, 2, 0.10D, "");
        }
        if (namespace.equals("caverns_and_chasms")) {
            if (COPPER_ARMORS.contains(path)) {
                return new HorseArmorStats(4, 4, -0.35D, "");
            }
            if (path.equals("netherite_horse_armor")) {
                return new HorseArmorStats(11, 4, 0.0D, "solace.horse_armor.knockback_resistance");
            }
            if (path.equals("necromium_horse_armor")) {
                return new HorseArmorStats(11, 4, 0.0D, "solace.horse_armor.slowness_infliction");
            }
            if (path.equals("silver_horse_armor")) {
                return empty();
            }
        }
        return vanilla(item);
    }

    private static HorseArmorStats vanilla(Item item) {
        return item instanceof HorseArmorItem armor ? new HorseArmorStats(armor.getProtection(), 0, 0.0D, "") : empty();
    }

    public static HorseArmorStats empty() {
        return new HorseArmorStats(0, 0, 0.0D, "");
    }

    public static boolean isCopperArmor(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id != null && id.getNamespace().equals("caverns_and_chasms") && COPPER_ARMORS.contains(id.getPath());
    }

    public static boolean hasManagedCavernsAndChasmsStats(ItemStack stack) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id == null || !id.getNamespace().equals("caverns_and_chasms")) {
            return false;
        }
        return COPPER_ARMORS.contains(id.getPath()) || id.getPath().equals("netherite_horse_armor")
                || id.getPath().equals("necromium_horse_armor") || id.getPath().equals("silver_horse_armor");
    }
}
