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

    public abstract QuestContent content();

    public abstract PlayerQuestData data();

    public abstract long gameTime();

    public abstract String dimension();

    public abstract long dayTime();

    public abstract int countItem(Target item, boolean heldOnly);

    public abstract boolean predicate(ResourceLocation id);

    /** Called after every mutation of {@link #data()}. */
    public abstract void changed();
}
