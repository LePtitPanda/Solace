package com.solace.compat.jade;

import com.solace.horse.HorseCargoData;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.animal.horse.Horse;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

public class HorseCargoJadeProvider implements IEntityComponentProvider {
    public static final ResourceLocation UID = new ResourceLocation("solace", "horse_sack_capacity");

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (accessor.getEntity() instanceof Horse horse) {
            Component capacity = Component.literal(Integer.toString(HorseCargoData.totalCapacity(horse)))
                    .withStyle(ChatFormatting.WHITE);
            tooltip.add(Component.translatable("solace.jade.horse.sack_capacity", capacity)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
