package com.solace.quests.condition;

import com.solace.quests.content.QuestContent;
import com.solace.quests.content.Target;
import com.solace.quests.state.PlayerQuestData;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything quest logic may know about one progress owner. The server implementation wraps a
 * player; tests use a fake. v2 party progress swaps what {@link #data()} returns, nothing else.
 */
public abstract class Ctx {
    /** The quest whose conditions are being evaluated (for "objectives_complete"); may be null. */
    public ResourceLocation quest;
    /** The entity in context (NPC being talked to, mob just killed); a net.minecraft Entity or null. */
    public Object entity;

    public abstract QuestContent content();

    public abstract PlayerQuestData data();

    public abstract long gameTime();

    public abstract String dimension();

    public abstract long dayTime();

    public abstract int countItem(Target item, boolean heldOnly);

    public abstract boolean predicate(ResourceLocation id);

    public abstract int reputation(ResourceLocation faction);

    public abstract boolean inBiome(Target biome);

    /** Inside that structure; with {@code piece}, inside one of its jigsaw pieces whose template matches. */
    public abstract boolean inStructure(Target structure, String piece);

    public abstract boolean hasAdvancement(ResourceLocation advancement);

    /** The player stands inside the named area (see Areas); false when the area is not set. */
    public abstract boolean inArea(String area);

    /** An entity matching that NPC binding (its bound entity, for a persistent binding) is within radius. */
    public abstract boolean nearNpc(ResourceLocation binding, int radius);

    /** A condition registered from Java or KubeJS; {@code data} is its JSON as text. */
    public abstract boolean custom(String handler, String data);

    /** Called after every mutation of {@link #data()}. */
    public abstract void changed();
}
