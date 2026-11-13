package com.solace.quests.locks;

import com.solace.quests.content.Locks.RecipeLock;
import com.solace.quests.content.QuestContent;
import com.solace.quests.content.Target;
import com.solace.quests.network.QuestNetwork;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.ServerCtx;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player recipe locks (technique notes). A locked recipe gives no result in the crafting grid and is
 * hidden in JEI/EMI. Unlocked by the flag {@link #flag}, or by the lock's "unlock_when".
 */
public final class RecipeLocks {
    /** Lock id -> resolved recipe ids, for one content snapshot and one recipe manager. */
    private static Map<ResourceLocation, Set<ResourceLocation>> resolved = Map.of();
    private static Object resolvedFor;
    private static RecipeManager resolvedRecipes;
    /** Player -> recipe ids locked for them right now. */
    private static final Map<UUID, Set<ResourceLocation>> LOCKED = new HashMap<>();

    private RecipeLocks() {
    }

    public static String flag(ResourceLocation lock) {
        return "recipes:" + lock;
    }

    private static Map<ResourceLocation, Set<ResourceLocation>> resolve(RecipeManager recipes, RegistryAccess access) {
        QuestContent content = QuestContent.current;
        if (resolvedFor != content || resolvedRecipes != recipes) {
            Map<ResourceLocation, Set<ResourceLocation>> map = new HashMap<>();
            for (RecipeLock lock : content.recipeLocks.values()) {
                Set<ResourceLocation> ids = new HashSet<>();
                for (Recipe<?> recipe : recipes.getRecipes()) {
                    ResourceLocation type = ForgeRegistries.RECIPE_TYPES.getKey(recipe.getType());
                    if (type != null && lock.types().contains(type) && covers(lock, recipe, access)) {
                        ids.add(recipe.getId());
                    }
                }
                map.put(lock.id(), Set.copyOf(ids));
            }
            resolved = map;
            resolvedFor = content;
            resolvedRecipes = recipes;
        }
        return resolved;
    }

    private static boolean covers(RecipeLock lock, Recipe<?> recipe, RegistryAccess access) {
        String id = recipe.getId().toString();
        for (String pattern : lock.recipes()) {
            if (pattern.endsWith("*") ? id.startsWith(pattern.substring(0, pattern.length() - 1)) : id.equals(pattern)) {
                return true;
            }
        }
        if (!lock.outputs().isEmpty()) {
            ItemStack result;
            try {
                result = recipe.getResultItem(access);
            } catch (RuntimeException exception) {
                return false;
            }
            for (Target output : lock.outputs()) {
                if (output.matches(result)) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean unlocked(ServerCtx ctx, RecipeLock lock) {
        return ctx.data().flags.contains(flag(lock.id())) || lock.unlockWhen() != null && lock.unlockWhen().test(ctx);
    }

    /** True when this recipe is locked for this player. Cheap: a set lookup. */
    public static boolean locked(ServerPlayer player, Recipe<?> recipe) {
        Set<ResourceLocation> locked = LOCKED.get(player.getUUID());
        if (locked == null) {
            locked = refresh(player);
        }
        return recipe != null && locked.contains(recipe.getId());
    }

    /** Recomputes the player's locked recipes; tells the client when the set changed. Called once a second. */
    public static Set<ResourceLocation> refresh(ServerPlayer player) {
        QuestContent content = QuestContent.current;
        Set<ResourceLocation> locked = new HashSet<>();
        if (!content.recipeLocks.isEmpty()) {
            Map<ResourceLocation, Set<ResourceLocation>> map = resolve(player.server.getRecipeManager(),
                    player.server.registryAccess());
            ServerCtx ctx = QuestEngine.ctx(player);
            for (RecipeLock lock : content.recipeLocks.values()) {
                if (!unlocked(ctx, lock)) {
                    locked.addAll(map.getOrDefault(lock.id(), Set.of()));
                }
            }
        }
        Set<ResourceLocation> before = LOCKED.put(player.getUUID(), locked);
        if (!locked.equals(before)) {
            QuestNetwork.lockedRecipes(player, List.copyOf(locked));
        }
        return locked;
    }

    /** Right-click with a lock's item (a technique note): learn it. Returns true when something was learned. */
    public static boolean learn(ServerPlayer player, ItemStack stack) {
        ServerCtx ctx = QuestEngine.ctx(player);
        boolean learned = false;
        for (RecipeLock lock : QuestContent.current.recipeLocks.values()) {
            if (lock.item() == null || !lock.item().equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))
                    || unlocked(ctx, lock)) {
                continue;
            }
            ctx.data().flags.add(flag(lock.id()));
            ctx.changed();
            player.sendSystemMessage((lock.learnText() != null ? lock.learnText().copy()
                    : Component.literal("You learned something new. Its recipes are now in your notes."))
                    .withStyle(ChatFormatting.GOLD));
            if (lock.consumeItem()) {
                stack.shrink(1);
            }
            learned = true;
        }
        if (learned) {
            refresh(player);
        }
        return learned;
    }

    public static void forget(UUID player) {
        LOCKED.remove(player);
    }
}
