package com.solace.quests.content;

import com.google.gson.JsonElement;
import com.solace.quests.condition.Condition;
import com.solace.quests.content.Dialogue.Bound;
import com.solace.quests.content.Dialogue.Choice;
import com.solace.quests.content.Dialogue.Node;
import com.solace.quests.content.Dialogue.NpcBinding;
import com.solace.quests.content.Dialogue.Start;
import com.solace.quests.content.Dialogue.Variant;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DialogueParser {
    private DialogueParser() {
    }

    public static Dialogue dialogue(ResourceLocation id, In in) {
        Map<String, Node> nodes = new LinkedHashMap<>();
        In nodesIn = in.obj("nodes");
        if (nodesIn == null) {
            in.err("nodes", "missing required field");
        } else {
            for (String nodeId : nodesIn.keys()) {
                In nodeIn = nodesIn.obj(nodeId);
                if (nodeIn != null) {
                    nodes.put(nodeId, node(nodeId, nodeIn));
                    nodeIn.done();
                }
            }
        }
        List<Start> start = new ArrayList<>();
        JsonElement startJson = in.raw("start");
        if (startJson != null && startJson.isJsonPrimitive()) {
            start.add(new Start(in.str("start", ""), null));
        } else {
            for (In startIn : in.objs("start")) {
                start.add(new Start(startIn.req("node"), QuestParser.condition(startIn, "when")));
                startIn.done();
            }
        }
        if (start.isEmpty()) {
            in.err("start", "expected a node id or a list of {\"node\": ..., \"when\": ...}");
        }
        for (Start entry : start) {
            target(in, "start", entry.node(), nodes, false);
        }
        nodes.values().forEach(node -> {
            if (node.to() != null) {
                target(in, "nodes." + node.id() + ".to", node.to(), nodes, true);
            }
            for (int i = 0; i < node.choices().size(); i++) {
                target(in, "nodes." + node.id() + ".choices[" + i + "].to", node.choices().get(i).to(), nodes, true);
            }
        });
        in.done();
        return new Dialogue(id, List.copyOf(start), nodes);
    }

    private static void target(In in, String key, String to, Map<String, Node> nodes, boolean allowClose) {
        if (!(allowClose && to.equals(Dialogue.CLOSE)) && !nodes.containsKey(to)) {
            in.err(key, "unknown node \"" + to + "\"");
        }
    }

    private static Node node(String id, In in) {
        // Where the web editor drew the node; means nothing to the game.
        in.skip("editor");
        boolean entityPortrait = true;
        ResourceLocation itemPortrait = null;
        In portrait = in.obj("portrait");
        if (portrait != null) {
            entityPortrait = portrait.bool("entity", false);
            itemPortrait = portrait.id("item", false);
            portrait.done();
        }
        List<Variant> text = new ArrayList<>();
        JsonElement textJson = in.raw("text");
        // A list where some entry has "when" is a variant list; any other JSON is one text component.
        boolean variants = textJson != null && textJson.isJsonArray() && textJson.getAsJsonArray().asList().stream()
                .anyMatch(e -> e.isJsonObject() && e.getAsJsonObject().has("when"));
        if (variants) {
            for (In variantIn : in.objs("text")) {
                text.add(new Variant(QuestParser.condition(variantIn, "when"), variantIn.text("text", true)));
                variantIn.done();
            }
        } else {
            text.add(new Variant(null, in.text("text", true)));
        }
        List<Choice> choices = new ArrayList<>();
        for (In choiceIn : in.objs("choices")) {
            choices.add(new Choice(choiceIn.text("text", true), choiceIn.str("to", Dialogue.CLOSE),
                    QuestParser.effects(choiceIn, "actions"), QuestParser.condition(choiceIn, "visible_when"),
                    QuestParser.condition(choiceIn, "enabled_when"), choiceIn.text("disabled_text", false),
                    choiceIn.integer("cooldown_seconds", 0, 0)));
            choiceIn.done();
        }
        String to = in.str("to", choices.isEmpty() ? Dialogue.CLOSE : null);
        if (!choices.isEmpty() && in.has("to")) {
            in.err("to", "a node has either \"choices\" or \"to\", not both");
        }
        Component speaker = in.text("speaker", false);
        return new Node(id, speaker, entityPortrait, itemPortrait, List.copyOf(text),
                QuestParser.effects(in, "actions"), List.copyOf(choices), to,
                in.integer("auto_advance_seconds", 0, 0), in.integer("cooldown_seconds", 0, 0));
    }

    public static NpcBinding npc(ResourceLocation id, In in) {
        Target entity = in.target("entity", "entity_type", true);
        Condition when = QuestParser.condition(in, "when");
        List<Bound> dialogues = new ArrayList<>();
        if (in.has("dialogue")) {
            dialogues.add(new Bound(in.ref("dialogue", "dialogues", true), null));
        }
        for (In boundIn : in.objs("dialogues")) {
            dialogues.add(new Bound(boundIn.ref("dialogue", "dialogues", true), QuestParser.condition(boundIn, "when")));
            boundIn.done();
        }
        if (dialogues.isEmpty()) {
            in.err("dialogue", "an NPC binding needs \"dialogue\" or \"dialogues\"");
        }
        String bind = in.str("bind", "none");
        if (!bind.equals("none") && !bind.equals("first_met")) {
            in.err("bind", "expected \"none\" or \"first_met\"");
        }
        NpcBinding binding = new NpcBinding(id, entity, when, List.copyOf(dialogues),
                in.integer("priority", 0, Integer.MIN_VALUE), in.bool("freeze", true),
                in.integer("max_distance", 6, 1), bind.equals("first_met"));
        in.done();
        return binding;
    }
}
