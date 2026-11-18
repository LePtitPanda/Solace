package com.solace.mixin;

import com.solace.quests.state.Factions;
import net.minecraft.world.entity.ai.gossip.GossipContainer;
import net.minecraft.world.entity.ai.gossip.GossipType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;
import java.util.function.Predicate;

/**
 * First-hand gossip (trades, cures, hurting or killing villagers, crimes) goes
 * through add/remove; villager-to-villager sharing and decay do not. Solace turns each change into
 * a step of the player's one world-wide villager standing.
 */
@Mixin(GossipContainer.class)
public abstract class GossipContainerReputationMixin {
    @Unique
    private int solace$before;

    @Shadow
    public abstract int getReputation(UUID id, Predicate<GossipType> types);

    @Inject(method = {"add", "remove(Ljava/util/UUID;Lnet/minecraft/world/entity/ai/gossip/GossipType;I)V"},
            at = @At("HEAD"))
    private void solace$before(UUID id, GossipType type, int amount, CallbackInfo ci) {
        solace$before = getReputation(id, t -> true);
    }

    @Inject(method = {"add", "remove(Ljava/util/UUID;Lnet/minecraft/world/entity/ai/gossip/GossipType;I)V"},
            at = @At("RETURN"))
    private void solace$after(UUID id, GossipType type, int amount, CallbackInfo ci) {
        int delta = getReputation(id, t -> true) - solace$before;
        if (delta != 0) {
            Factions.onGossip(this, id, delta);
        }
    }
}
