package com.solace.quests.content;

import com.solace.quests.condition.Condition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public record Chapter(ResourceLocation id, Component title, Component description, int order, Condition unlock,
                      boolean hiddenUntilUnlocked) {
}
