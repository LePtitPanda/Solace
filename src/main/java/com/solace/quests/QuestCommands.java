package com.solace.quests;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.solace.quests.content.Dialogue;
import com.solace.quests.content.Faction;
import com.solace.quests.content.Problem;
import com.solace.quests.dialogue.DialogueManager;
import com.solace.quests.editor.EditorServer;
import com.solace.quests.state.Factions;
import com.solace.quests.content.Quest;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.QuestContentLoader;
import com.solace.quests.state.PlayerQuestData;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.QuestLogic;
import com.solace.quests.state.QuestSavedData;
import com.solace.quests.state.ServerCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Predicate;
import java.util.stream.Collectors;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /solacequests (alias /sq). Admin subcommands need permission level 2; claim and check are for players. */
public final class QuestCommands {
    private static final SimpleCommandExceptionType UNKNOWN_QUEST =
            new SimpleCommandExceptionType(Component.literal("Unknown quest"));
    private static final SuggestionProvider<CommandSourceStack> QUESTS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestContent.current.quests.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> DIALOGUES = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestContent.current.dialogues.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestContent.current.factions.keySet(), builder);
    private static final Predicate<CommandSourceStack> OP = source -> source.hasPermission(2);
    private static final SuggestionProvider<CommandSourceStack> NPCS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestContent.current.npcs.keySet(), builder);

    private QuestCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> quest = literal("quest").requires(OP);
        for (String action : new String[]{"start", "complete", "fail", "reset"}) {
            quest.then(literal(action).then(argument("player", EntityArgument.player())
                    .then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                            .executes(context -> questAction(context, action)))));
        }
        quest.then(literal("phase").then(argument("player", EntityArgument.player())
                .then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .then(argument("phase", StringArgumentType.word()).executes(context -> {
                            ServerPlayer player = EntityArgument.getPlayer(context, "player");
                            boolean moved = QuestLogic.move(QuestEngine.ctx(player), quest(context),
                                    StringArgumentType.getString(context, "phase"), "command",
                                    QuestEngine.events(player));
                            return reply(context, moved, "Phase set", "Quest is not active or the phase is unknown");
                        })))));
        quest.then(literal("progress").then(argument("player", EntityArgument.player())
                .then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .then(argument("objective", StringArgumentType.word())
                                .then(argument("value", IntegerArgumentType.integer(0)).executes(context -> {
                                    ServerPlayer player = EntityArgument.getPlayer(context, "player");
                                    boolean set = QuestLogic.setProgress(QuestEngine.ctx(player), quest(context),
                                            StringArgumentType.getString(context, "objective"),
                                            IntegerArgumentType.getInteger(context, "value"),
                                            QuestEngine.events(player));
                                    return reply(context, set, "Progress set",
                                            "That objective is not in the quest's current phase");
                                }))))));

        LiteralArgumentBuilder<CommandSourceStack> root = literal("solacequests")
                .then(literal("reload").requires(OP).executes(context -> {
                    QuestContent content = QuestContentLoader.reloadNow(context.getSource().getServer()
                            .getResourceManager());
                    return report(context.getSource(), content);
                }))
                .then(literal("validate").requires(OP)
                        .executes(context -> report(context.getSource(), QuestContent.current)))
                .then(literal("list").requires(OP).executes(context -> {
                    QuestContent content = QuestContent.current;
                    content.chapters.forEach((id, chapter) -> {
                        line(context.getSource(), id + " ", ChatFormatting.GOLD);
                        content.quests.values().stream().filter(q -> q.chapter().equals(id))
                                .forEach(q -> line(context.getSource(), "  " + q.id(), ChatFormatting.WHITE));
                    });
                    return content.quests.size();
                }))
                .then(quest)
                .then(literal("flag").requires(OP)
                        .then(literal("set").then(flagArgs(true)))
                        .then(literal("clear").then(flagArgs(false))))
                .then(literal("var").requires(OP)
                        .then(literal("set").then(varArgs(false)))
                        .then(literal("add").then(varArgs(true))))
                .then(literal("debug").requires(OP).then(argument("player", EntityArgument.player())
                        .executes(context -> debug(context.getSource(), EntityArgument.getPlayer(context, "player")))))
                .then(literal("dialogue").requires(OP).then(literal("open")
                        .then(argument("player", EntityArgument.player())
                                .then(argument("dialogue", ResourceLocationArgument.id()).suggests(DIALOGUES)
                                        .executes(context -> dialogue(context, null))
                                        .then(argument("node", StringArgumentType.word())
                                                .executes(context -> dialogue(context,
                                                        StringArgumentType.getString(context, "node"))))))))
                .then(literal("rep").requires(OP)
                        .then(literal("get").then(argument("player", EntityArgument.player())
                                .then(argument("faction", ResourceLocationArgument.id()).suggests(FACTIONS)
                                        .executes(context -> rep(context, 0)))))
                        .then(literal("set").then(argument("player", EntityArgument.player())
                                .then(argument("faction", ResourceLocationArgument.id()).suggests(FACTIONS)
                                        .then(argument("value", IntegerArgumentType.integer())
                                                .executes(context -> rep(context, 1))))))
                        .then(literal("add").then(argument("player", EntityArgument.player())
                                .then(argument("faction", ResourceLocationArgument.id()).suggests(FACTIONS)
                                        .then(argument("value", IntegerArgumentType.integer())
                                                .executes(context -> rep(context, 2)))))))
                .then(literal("editor").requires(OP).executes(context -> EditorServer.open(context.getSource())))
                .then(literal("claim").then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .executes(context -> {
                            int count = QuestEngine.claim(context.getSource().getPlayerOrException(), quest(context),
                                    null);
                            return reply(context, count > 0, "Claimed " + count + " reward(s)", "Nothing to claim");
                        })))
                .then(literal("check").then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .then(argument("objective", StringArgumentType.word()).executes(context -> {
                            // The player-facing "tick this off" intent; only checkmark objectives accept it.
                            boolean done = QuestEngine.check(context.getSource().getPlayerOrException(),
                                    quest(context), StringArgumentType.getString(context, "objective"));
                            return reply(context, done, "Checked", "That is not a checkmark objective you can tick now");
                        }))));
        root.then(literal("area").requires(OP)
                .then(literal("list").executes(context -> {
                    var areas = com.solace.quests.state.Areas.get(context.getSource().getServer()).all();
                    context.getSource().sendSuccess(() -> Component.literal(areas.isEmpty() ? "No areas"
                            : String.join("\n", areas.entrySet().stream()
                            .map(entry -> entry.getKey() + ": " + entry.getValue()).toList())), false);
                    return areas.size();
                }))
                .then(literal("set").then(argument("name", StringArgumentType.word())
                        .then(argument("radius", IntegerArgumentType.integer(0)).executes(context -> {
                            CommandSourceStack source = context.getSource();
                            int radius = IntegerArgumentType.getInteger(context, "radius");
                            com.solace.quests.state.Areas.get(source.getServer()).set(
                                    StringArgumentType.getString(context, "name"),
                                    com.solace.quests.state.Areas.Area.around(source.getLevel().dimension().location()
                                            .toString(), (int) source.getPosition().x, (int) source.getPosition().z, radius),
                                    true);
                            return reply(context, true, "Area set around you", "");
                        }))))
                .then(literal("remove").then(argument("name", StringArgumentType.word()).executes(context ->
                        reply(context, com.solace.quests.state.Areas.get(context.getSource().getServer())
                                .remove(StringArgumentType.getString(context, "name")), "Area removed", "No such area")))));
        root.then(literal("npc").requires(OP)
                .then(literal("list").executes(context -> {
                    var entries = com.solace.quests.npc.NpcRegistry.get(context.getSource().getServer()).all();
                    context.getSource().sendSuccess(() -> Component.literal(entries.isEmpty() ? "No bound NPCs"
                            : String.join("\n", entries.entrySet().stream().map(entry -> entry.getKey() + ": "
                            + (entry.getValue().uuid == null ? "unbound" : entry.getValue().uuid) + " at "
                            + entry.getValue().dimension + " " + entry.getValue().pos.toShortString()).toList())), false);
                    return entries.size();
                }))
                .then(literal("unbind").then(argument("binding", ResourceLocationArgument.id()).suggests(NPCS)
                        .executes(context -> reply(context, com.solace.quests.npc.NpcRegistry
                                .get(context.getSource().getServer())
                                .unbind(ResourceLocationArgument.getId(context, "binding").toString()),
                                "Unbound: the next one met takes over", "Not bound"))))
                .then(literal("bind").then(argument("binding", ResourceLocationArgument.id()).suggests(NPCS)
                        .then(argument("entity", EntityArgument.entity()).executes(context -> {
                            com.solace.quests.npc.NpcRegistry.get(context.getSource().getServer()).bind(
                                    ResourceLocationArgument.getId(context, "binding").toString(),
                                    EntityArgument.getEntity(context, "entity"), false);
                            return reply(context, true, "Bound", "");
                        }))))
                .then(literal("free").then(argument("entity", EntityArgument.entity())
                        .executes(context -> free(context, new ResourceLocation("minecraft", "villager")))
                        .then(argument("into", ResourceLocationArgument.id())
                                .executes(context -> free(context, ResourceLocationArgument.getId(context, "into")))))));
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
        dispatcher.register(literal("sq").redirect(node));
    }

    private static int free(CommandContext<CommandSourceStack> context, ResourceLocation into)
            throws CommandSyntaxException {
        return reply(context, com.solace.quests.npc.NpcRegistry.get(context.getSource().getServer())
                .free(EntityArgument.getEntity(context, "entity"), into, null) != null, "Freed", "Could not free it");
    }

    private static int dialogue(CommandContext<CommandSourceStack> context, String node) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        Dialogue dialogue = QuestContent.current.dialogues.get(ResourceLocationArgument.getId(context, "dialogue"));
        return reply(context, dialogue != null && DialogueManager.open(player, dialogue, null, node, null),
                "Dialogue opened", "Unknown dialogue or node, or no start entry matches");
    }

    private static int rep(CommandContext<CommandSourceStack> context, int mode) throws CommandSyntaxException {
        ServerCtx ctx = QuestEngine.ctx(EntityArgument.getPlayer(context, "player"));
        Faction faction = ctx.content().factions.get(ResourceLocationArgument.getId(context, "faction"));
        if (faction == null) {
            return reply(context, false, "", "Unknown faction");
        }
        if (mode == 1) {
            Factions.set(ctx, faction, IntegerArgumentType.getInteger(context, "value"));
        } else if (mode == 2) {
            Factions.add(ctx, faction, IntegerArgumentType.getInteger(context, "value"), true);
        }
        QuestLogic.evaluate(ctx, QuestEngine.events(ctx.player));
        int value = Factions.get(ctx, faction.id());
        return reply(context, true, faction.id() + " = " + value + " ("
                + faction.ranks().get(faction.rankIndex(value)).id() + ")", "");
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> flagArgs(boolean value) {
        return argument("player", EntityArgument.player()).then(argument("flag", StringArgumentType.word())
                .executes(context -> {
                    ServerCtx ctx = QuestEngine.ctx(EntityArgument.getPlayer(context, "player"));
                    String flag = StringArgumentType.getString(context, "flag");
                    if (value) {
                        ctx.data().flags.add(flag);
                    } else {
                        ctx.data().flags.remove(flag);
                    }
                    ctx.changed();
                    QuestLogic.evaluate(ctx, QuestEngine.events(ctx.player));
                    return reply(context, true, "Flag " + flag + " = " + value, "");
                }));
    }

    private static com.mojang.brigadier.builder.ArgumentBuilder<CommandSourceStack, ?> varArgs(boolean add) {
        return argument("player", EntityArgument.player()).then(argument("name", StringArgumentType.word())
                .then(argument("value", IntegerArgumentType.integer()).executes(context -> {
                    ServerCtx ctx = QuestEngine.ctx(EntityArgument.getPlayer(context, "player"));
                    String name = StringArgumentType.getString(context, "name");
                    int value = IntegerArgumentType.getInteger(context, "value");
                    int result = ctx.data().variables.merge(name, value, (old, v) -> add ? old + v : v);
                    ctx.changed();
                    QuestLogic.evaluate(ctx, QuestEngine.events(ctx.player));
                    return reply(context, true, name + " = " + result, "");
                })));
    }

    private static Quest quest(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        Quest quest = QuestEngine.quest(ResourceLocationArgument.getId(context, "quest"));
        if (quest == null) {
            throw UNKNOWN_QUEST.create();
        }
        return quest;
    }

    private static int questAction(CommandContext<CommandSourceStack> context, String action)
            throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(context, "player");
        ServerCtx ctx = QuestEngine.ctx(player);
        QuestLogic.Events events = QuestEngine.events(player);
        if (action.equals("reset")) {
            // Works on orphaned ids too, so no content lookup.
            ResourceLocation id = ResourceLocationArgument.getId(context, "quest");
            return reply(context, QuestLogic.reset(ctx, id), "Reset " + id, "The player has no state for " + id);
        }
        Quest quest = quest(context);
        boolean done = switch (action) {
            case "start" -> QuestLogic.start(ctx, quest, true, events);
            case "complete" -> {
                if (!QuestLogic.is(ctx, quest.id(), QuestLogic.View.ACTIVE)) {
                    QuestLogic.start(ctx, quest, true, events);
                }
                yield QuestLogic.move(ctx, quest, Quest.COMPLETE, "command", events);
            }
            default -> QuestLogic.move(ctx, quest, Quest.FAIL, "command", events);
        };
        return reply(context, done, action + ": " + quest.id(), "Could not " + action + " " + quest.id());
    }

    private static int report(CommandSourceStack source, QuestContent content) {
        for (Problem problem : content.problems) {
            line(source, problem.toString(), problem.error() ? ChatFormatting.RED : ChatFormatting.YELLOW);
        }
        long errors = content.errorCount();
        line(source, content.chapters.size() + " chapters, " + content.quests.size() + " quests, "
                + content.dialogues.size() + " dialogues, " + content.npcs.size() + " npcs, " + content.factions.size()
                + " factions, " + errors
                + " errors, " + (content.problems.size() - errors) + " warnings",
                errors > 0 ? ChatFormatting.RED : ChatFormatting.GREEN);
        return (int) errors;
    }

    private static int debug(CommandSourceStack source, ServerPlayer player) {
        ServerCtx ctx = QuestEngine.ctx(player);
        PlayerQuestData data = ctx.data();
        if (QuestSavedData.get(player.server).frozen()) {
            line(source, "Quest save is from a newer Solace: progress is not being saved!", ChatFormatting.RED);
        }
        line(source, "flags: " + data.flags + "  variables: " + data.variables + "  unlocked: " + data.unlocked,
                ChatFormatting.GRAY);
        line(source, "reputation: " + data.reputation + "  tracked: " + data.tracked + "  markers: " + data.markers,
                ChatFormatting.GRAY);
        data.quests.forEach((id, state) -> {
            boolean orphaned = !ctx.content().quests.containsKey(id);
            line(source, id + (orphaned ? " (orphaned)" : "") + " " + state.status + " phase=" + state.phase
                    + " progress=" + state.progress + " completions=" + state.completions + " claimed="
                    + state.claimed, orphaned ? ChatFormatting.DARK_GRAY : ChatFormatting.WHITE);
            line(source, "  path: " + state.history.stream().map(entry -> entry.phase() + "(" + entry.via() + ")")
                    .collect(Collectors.joining(" > ")), ChatFormatting.GRAY);
        });
        return data.quests.size();
    }

    private static int reply(CommandContext<CommandSourceStack> context, boolean ok, String success, String failure) {
        if (ok) {
            context.getSource().sendSuccess(() -> Component.literal(success), true);
            return 1;
        }
        context.getSource().sendFailure(Component.literal(failure));
        return 0;
    }

    private static void line(CommandSourceStack source, String text, ChatFormatting color) {
        source.sendSuccess(() -> Component.literal(text).withStyle(color), false);
    }
}
