package com.solace.quests.locks;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.solace.Solace;
import com.solace.item.KnowledgeBookItem;
import com.solace.quests.api.QuestApi;
import com.solace.quests.content.Locks;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.Target;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.state.QuestEngine;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Ponder knowledge: every Ponder scene is closed until the player has read a knowledge book listing its item
 * ({@code knowledge/*.json}). Read books are flags "knowledge:&lt;id&gt;"; {@link #ALL} opens everything.
 * The client gets the open item ids and Ponder asks it (PonderSceneLockMixin).
 */
@Mod.EventBusSubscriber(modid = Solace.MOD_ID)
public final class PonderLocks {
    public static final String ALL = "knowledge:*";

    private record State(boolean all, Set<ResourceLocation> items) {
    }

    private static final Map<UUID, State> SENT = new HashMap<>();

    private PonderLocks() {
    }

    public static String flag(ResourceLocation id) {
        return "knowledge:" + id;
    }

    /** Recomputes the player's open ponders; tells the client when they changed. Called once a second. */
    public static void refresh(ServerPlayer player) {
        Set<String> flags = QuestEngine.ctx(player).data().flags;
        Set<ResourceLocation> items = new TreeSet<>();
        for (Locks.Knowledge book : QuestContent.current.knowledge.values()) {
            if (flags.contains(flag(book.id()))) {
                book.ponders().forEach(target -> expand(target, items));
            }
        }
        State state = new State(flags.contains(ALL), items);
        if (!state.equals(SENT.put(player.getUUID(), state))) {
            QuestNetwork.ponderUnlocks(player, state.all, List.copyOf(items));
        }
    }

    private static void expand(Target target, Set<ResourceLocation> into) {
        if (!target.tag()) {
            into.add(target.id());
            return;
        }
        BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, target.id()))
                .forEach(holder -> holder.unwrapKey().ifPresent(key -> into.add(key.location())));
    }

    public static void forget(UUID player) {
        SENT.remove(player);
    }

    /** Reading a knowledge book: learns it; the book is kept. Returns true when something was learned. */
    public static boolean learn(ServerPlayer player, ItemStack stack) {
        ResourceLocation id = KnowledgeBookItem.knowledge(stack);
        Locks.Knowledge book = id == null ? null : QuestContent.current.knowledge.get(id);
        if (book == null) {
            player.displayClientMessage(Component.literal("The pages are blank.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        if (QuestEngine.ctx(player).data().flags.contains(flag(id))) {
            player.displayClientMessage(Component.literal("You already know all of this.").withStyle(ChatFormatting.GRAY), true);
            return false;
        }
        QuestApi.setFlag(player, flag(id), true);
        player.sendSystemMessage(Component.literal("Learned: ").append(book.name()).withStyle(ChatFormatting.GOLD));
        player.level().playSound(null, player.blockPosition(), SoundEvents.BOOK_PAGE_TURN, SoundSource.PLAYERS, 1.0F, 1.0F);
        refresh(player);
        return true;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("solace").then(Commands.literal("knowledge")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("grant").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("knowledge", StringArgumentType.greedyString()).suggests((c, b) -> ids(b))
                                .executes(c -> set(c, true)))))
                .then(Commands.literal("revoke").then(Commands.argument("targets", EntityArgument.players())
                        .then(Commands.argument("knowledge", StringArgumentType.greedyString()).suggests((c, b) -> ids(b))
                                .executes(c -> set(c, false)))))
                .then(Commands.literal("list").then(Commands.argument("targets", EntityArgument.players())
                        .executes(PonderLocks::list)))));
    }

    private static java.util.concurrent.CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> ids(
            com.mojang.brigadier.suggestion.SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(Stream.concat(Stream.of("all"),
                QuestContent.current.knowledge.keySet().stream().map(ResourceLocation::toString)), builder);
    }

    /** "all" sets every book plus {@link #ALL} (every ponder, books or not). */
    private static int set(CommandContext<CommandSourceStack> context, boolean value) throws CommandSyntaxException {
        String name = StringArgumentType.getString(context, "knowledge");
        List<String> flags;
        if (name.equals("all")) {
            flags = Stream.concat(Stream.of(ALL), QuestContent.current.knowledge.keySet().stream().map(PonderLocks::flag)).toList();
        } else {
            ResourceLocation id = ResourceLocation.tryParse(name);
            if (id == null || !QuestContent.current.knowledge.containsKey(id)) {
                context.getSource().sendFailure(Component.literal("No knowledge \"" + name + "\"."));
                return 0;
            }
            flags = List.of(flag(id));
        }
        Collection<ServerPlayer> players = EntityArgument.getPlayers(context, "targets");
        for (ServerPlayer player : players) {
            flags.forEach(f -> QuestApi.setFlag(player, f, value));
            refresh(player);
        }
        context.getSource().sendSuccess(() -> Component.literal((value ? "Granted " : "Revoked ") + name + " for "
                + players.size() + " player(s)."), true);
        return players.size();
    }

    private static int list(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        for (ServerPlayer player : EntityArgument.getPlayers(context, "targets")) {
            Set<String> flags = QuestEngine.ctx(player).data().flags;
            List<String> known = flags.stream().filter(f -> f.startsWith("knowledge:")).map(f -> f.substring(10)).toList();
            context.getSource().sendSuccess(() -> Component.literal(player.getGameProfile().getName() + ": "
                    + (known.isEmpty() ? "nothing" : String.join(", ", known))), false);
        }
        return 1;
    }
}
