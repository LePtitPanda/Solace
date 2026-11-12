package com.solace.quests.condition;

/** A pure check against a player's quest state and surroundings. Never mutates. */
@FunctionalInterface
public interface Condition {
    boolean test(Ctx ctx);
}
