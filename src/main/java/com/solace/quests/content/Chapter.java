package com.solace.quests.content;

import com.solace.quests.condition.Condition;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** {@code icon} is "item:<id>" or "texture:<id>", or null. */
public record Chapter(ResourceLocation id, Component title, Component description, String icon, int order,
                      Condition unlock, boolean hiddenUntilUnlocked) {
}
