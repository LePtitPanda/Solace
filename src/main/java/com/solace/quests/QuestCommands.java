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
import com.solace.quests.content.Problem;
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
    private static final Predicate<CommandSourceStack> OP = source -> source.hasPermission(2);

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
                .then(literal("claim").then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .executes(context -> {
                            int count = QuestEngine.claim(context.getSource().getPlayerOrException(), quest(context),
                                    null);
                            return reply(context, count > 0, "Claimed " + count + " reward(s)", "Nothing to claim");
                        })))
                .then(literal("check").then(argument("quest", ResourceLocationArgument.id()).suggests(QUESTS)
                        .then(argument("objective", StringArgumentType.word()).executes(context -> {
                            // The player-facing "tick this off" intent; only checkmark objectives accept it.
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            Quest target = quest(context);
                            String objectiveId = StringArgumentType.getString(context, "objective");
                            boolean checkmark = target.phases().stream().flatMap(phase -> phase.objectives().stream())
                                    .anyMatch(o -> o.id().equals(objectiveId) && o.type().equals("solace:checkmark"));
                            boolean done = checkmark && QuestLogic.setProgress(QuestEngine.ctx(player), target,
                                    objectiveId, 1, QuestEngine.events(player));
                            return reply(context, done, "Checked", "That is not a checkmark objective you can tick now");
                        }))));
        LiteralCommandNode<CommandSourceStack> node = dispatcher.register(root);
        dispatcher.register(literal("sq").redirect(node));
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
        line(source, content.chapters.size() + " chapters, " + content.quests.size() + " quests, " + errors
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
