package com.solace.mixin;

import com.illusivesoulworks.polymorph.api.common.base.IRecipePair;
import com.illusivesoulworks.polymorph.common.PolymorphForgePacketDistributor;
import com.solace.quests.locks.RecipeLocks;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.SortedSet;
import java.util.TreeSet;

/** Recipe locks: Polymorph's recipe-switch button never lists a recipe the player has not learned. */
@Mixin(value = PolymorphForgePacketDistributor.class, remap = false)
public abstract class PolymorphRecipeLockMixin {
    @ModifyVariable(method = {
            "sendRecipesListS2C(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/SortedSet;)V",
            "sendRecipesListS2C(Lnet/minecraft/server/level/ServerPlayer;Ljava/util/SortedSet;Lnet/minecraft/resources/ResourceLocation;)V"},
            at = @At("HEAD"), argsOnly = true)
    private SortedSet<IRecipePair> solace$recipeLocks(SortedSet<IRecipePair> recipes, ServerPlayer player) {
        if (recipes == null || recipes.isEmpty()) {
            return recipes;
        }
        SortedSet<IRecipePair> shown = new TreeSet<>(recipes);
        shown.removeIf(pair -> RecipeLocks.locked(player, pair.getResourceLocation()));
        return shown;
    }
}
