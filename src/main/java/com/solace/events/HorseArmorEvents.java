package com.solace.events;

import com.solace.Solace;
import com.solace.horse.HorseArmorStats;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.HorseArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HorseArmorEvents {
    private static final UUID SPEED_MODIFIER = UUID.fromString("25f27a53-701d-4742-bde8-57109640b5dd");

    private HorseArmorEvents() {
    }

    @SubscribeEvent
    public static void onHorseJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && event.getEntity() instanceof Horse horse) {
            updateSpeedModifier(horse);
        }
    }

    @SubscribeEvent
    public static void onHorseTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof Horse horse && horse.tickCount % 10 == 0) {
            updateSpeedModifier(horse);
        }
    }

    @SubscribeEvent
    public static void addHorseArmorTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (!(stack.getItem() instanceof HorseArmorItem)) {
            return;
        }
        if (HorseArmorStats.hasManagedCavernsAndChasmsStats(stack)) {
            removeCavernsAndChasmsBodyModifiers(event);
        }
        if (id != null && id.toString().equals("caverns_and_chasms:silver_horse_armor")) {
            return;
        }

        HorseArmorStats stats = HorseArmorStats.of(stack);
        event.getToolTip().add(Component.empty());
        event.getToolTip().add(Component.translatable("item.modifiers.horse").withStyle(ChatFormatting.GRAY));
        if (stats.armor() > 0) {
            event.getToolTip().add(positive(stats.armor(), Component.translatable("attribute.name.generic.armor")));
        }
        if (stats.speedMultiplier() > 0) {
            event.getToolTip().add(Component.translatable("attribute.modifier.plus.2", stats.speedMultiplier() * 100.0D,
                    Component.translatable("attribute.name.generic.movement_speed")).withStyle(ChatFormatting.BLUE));
        } else if (stats.speedMultiplier() < 0) {
            event.getToolTip().add(Component.translatable("attribute.modifier.take.2", -stats.speedMultiplier() * 100.0D,
                    Component.translatable("attribute.name.generic.movement_speed")).withStyle(ChatFormatting.RED));
        }
        if (!stats.nativeBonusKey().isEmpty()) {
            event.getToolTip().add(Component.translatable(stats.nativeBonusKey()).withStyle(ChatFormatting.BLUE));
        }
    }

    private static Component positive(double amount, Component attributeName) {
        return Component.translatable("attribute.modifier.plus.0", amount, attributeName).withStyle(ChatFormatting.BLUE);
    }

    private static void removeCavernsAndChasmsBodyModifiers(ItemTooltipEvent event) {
        int bodyHeader = -1;
        for (int i = 0; i < event.getToolTip().size(); i++) {
            if (isBodyModifierHeader(translationKey(event.getToolTip().get(i)))) {
                bodyHeader = i;
                break;
            }
        }
        if (bodyHeader < 0) {
            return;
        }
        event.getToolTip().removeIf(component -> {
            String key = translationKey(component);
            return isBodyModifierHeader(key) || key.startsWith("attribute.modifier.");
        });
        int precedingLine = bodyHeader - 1;
        if (precedingLine >= 0 && precedingLine < event.getToolTip().size()
                && event.getToolTip().get(precedingLine).getString().isEmpty()) {
            event.getToolTip().remove(precedingLine);
        }
    }

    private static String translationKey(Component component) {
        return component.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : "";
    }

    private static boolean isBodyModifierHeader(String key) {
        return key.equals("item.modifiers.chest") || key.equals("item.modifiers.body");
    }

    private static void updateSpeedModifier(Horse horse) {
        updateModifier(horse.getAttribute(Attributes.MOVEMENT_SPEED), SPEED_MODIFIER,
                manualSpeedBonus(horse.getArmor()), AttributeModifier.Operation.MULTIPLY_TOTAL, "Horse armor speed");
    }

    private static double manualSpeedBonus(ItemStack armor) {
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(armor.getItem());
        if (id == null) {
            return 0.0D;
        }
        String value = id.toString();
        if (value.equals("minecraft:leather_horse_armor") || value.equals("galosphere:sterling_horse_armor")) {
            return 0.10D;
        }
        if (value.equals("minecraft:golden_horse_armor")) {
            return 0.30D;
        }
        if (HorseArmorStats.isCopperArmor(armor)) {
            return -0.15D;
        }
        return 0.0D;
    }

    private static void updateModifier(AttributeInstance attribute, UUID id, double amount,
                                       AttributeModifier.Operation operation, String name) {
        if (attribute == null) {
            return;
        }
        AttributeModifier current = attribute.getModifier(id);
        if (current != null && current.getAmount() == amount && current.getOperation() == operation) {
            return;
        }
        if (current != null) {
            attribute.removeModifier(id);
        }
        if (amount != 0.0D) {
            attribute.addTransientModifier(new AttributeModifier(id, name, amount, operation));
        }
    }
}
