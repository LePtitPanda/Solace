package com.solace.quests.content;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Where to point the player. {@code kind} is position, structure, npc, objective or building (a template in the home village); when
 * {@code ref} is set the marker is a reference to a markers/ file and the other fields are unused.
 */
public record Marker(ResourceLocation ref, String kind, ResourceLocation dimension, int x, int y, int z,
                     Target structure, ResourceLocation npc, String objective, Component label, String color,
                     boolean beam, ResourceLocation building) {
}
