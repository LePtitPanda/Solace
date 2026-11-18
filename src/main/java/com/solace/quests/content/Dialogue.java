package com.solace.quests.content;

import com.solace.quests.condition.Condition;
import com.solace.quests.content.Quest.Reward;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Map;

public record Dialogue(ResourceLocation id, List<Start> start, Map<String, Node> nodes) {
    public static final String CLOSE = "@close";

    /** Entry points, first passing condition wins: lets one NPC greet differently by story state. */
    public record Start(String node, Condition when) {
    }

    /** {@code when == null} always matches. */
    public record Variant(Condition when, Component text) {
    }

    /** A node has {@code choices}, or a single {@code to} to continue to. */
    public record Node(String id, Component speaker, boolean entityPortrait, ResourceLocation itemPortrait,
                       List<Variant> text, List<Reward> actions, List<Choice> choices, String to,
                       int autoAdvanceSeconds, int cooldownSeconds) {
    }

    public record Choice(Component text, String to, List<Reward> actions, Condition visibleWhen,
                         Condition enabledWhen, Component disabledText, int cooldownSeconds) {
    }

    /**
     * Binds dialogues to entities. The first of {@code dialogues} whose condition passes is opened.
     * {@code firstMet}: the first matching entity talked to becomes the only one (see NpcRegistry).
     * A binding has either an {@code entity} or a {@code block} (right-clicking that block opens the dialogue).
     */
    public record NpcBinding(ResourceLocation id, Target entity, Condition when, List<Bound> dialogues, int priority,
                             boolean freeze, int maxDistance, boolean firstMet, Target block) {
    }

    public record Bound(ResourceLocation dialogue, Condition when) {
    }
}
