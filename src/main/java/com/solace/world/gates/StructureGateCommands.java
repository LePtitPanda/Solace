package com.solace.world.gates;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.solace.world.gates.StructureGateConfig.Entry;
import com.solace.world.gates.StructureGateConfig.Mode;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceOrTagKeyArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** /solace gates list|status|open|close|reset|reload (permission level 2). */
final class StructureGateCommands {
    private static final SuggestionProvider<CommandSourceStack> GATED = (context, builder) -> {
        Set<String> options = new LinkedHashSet<>();
        StructureGates.entries().forEach(entry -> options.add(entry.key()));
        StructureGates.gated().keySet().forEach(id -> options.add(id.toString()));
        return SharedSuggestionProvider.suggest(options, builder);
    };

    private StructureGateCommands() {
    }

    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("solace").then(literal("gates").requires(source -> source.hasPermission(2))
                .then(literal("list").executes(StructureGateCommands::list))
                .then(literal("reload").executes(context -> {
                    StructureGates.reload();
                    context.getSource().sendSuccess(() -> Component.literal("Reloaded structure gates: "
                            + StructureGates.entries().size() + " entries, " + StructureGates.gated().size()
                            + " structures gated."), true);
                    return 1;
                }))
                .then(literal("status").then(structure().executes(StructureGateCommands::status)))
                .then(literal("open").then(structure()
                        .executes(context -> open(context, null))
                        .then(literal("once").executes(context -> open(context, Mode.ONCE)))
                        .then(literal("infinite").executes(context -> open(context, Mode.INFINITE)))))
                .then(literal("close").then(structure().executes(context -> change(context, "Closed",
                        StructureGates.close(context.getSource().getServer(), target(context))))))
                .then(literal("reset").then(structure().executes(context -> change(context, "Reset",
                        StructureGates.reset(context.getSource().getServer(), target(context))))))));
    }

    private static com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, ?> structure() {
        return argument("structure", ResourceOrTagKeyArgument.resourceOrTagKey(Registries.STRUCTURE)).suggests(GATED);
    }

    private static String target(CommandContext<CommandSourceStack> context) {
        return context.getArgument("structure", ResourceOrTagKeyArgument.Result.class).asPrintable();
    }

    private static int list(CommandContext<CommandSourceStack> context) {
        Map<ResourceLocation, Entry> gated = StructureGates.gated();
        CommandSourceStack source = context.getSource();
        if (gated.isEmpty()) {
            source.sendSuccess(() -> Component.literal("No structure is gated (config/solace/structure-gates.json)."),
                    false);
            return 0;
        }
        source.sendSuccess(() -> Component.literal(gated.size() + " gated structure(s):"), false);
        gated.forEach((id, entry) -> source.sendSuccess(() -> Component.literal(" " + id
                + (entry.tag() ? " (via " + entry.key() + ")" : "") + ": "
                + StructureGates.describe(source.getServer(), id)), false));
        return gated.size();
    }

    private static int status(CommandContext<CommandSourceStack> context) {
        String target = target(context);
        Map<ResourceLocation, Entry> matched = StructureGates.resolve(context.getSource().getServer(), target);
        if (matched.isEmpty()) {
            context.getSource().sendFailure(Component.literal(target + " is not in the structure gate config."));
            return 0;
        }
        matched.keySet().forEach(id -> context.getSource().sendSuccess(() -> Component.literal(id + ": "
                + StructureGates.describe(context.getSource().getServer(), id)), false));
        return matched.size();
    }

    private static int open(CommandContext<CommandSourceStack> context, Mode mode) {
        return change(context, "Opened", StructureGates.open(context.getSource().getServer(), target(context), mode));
    }

    private static int change(CommandContext<CommandSourceStack> context, String verb, int changed) {
        String target = target(context);
        if (changed < 0) {
            context.getSource().sendFailure(Component.literal(target + " is not in the structure gate config."));
            return 0;
        }
        context.getSource().sendSuccess(() -> Component.literal(verb + " " + changed + " gate(s) for " + target
                + (changed == 0 ? " (nothing to change)" : "") + "."), true);
        return Math.max(changed, 1);
    }
}
