package com.solace.quests.locks;

import com.solace.quests.content.Locks.BlockLock;
import com.solace.quests.content.QuestContent;
import com.solace.quests.state.QuestEngine;
import com.solace.quests.state.ServerCtx;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;

/** Per-player block locks: a locked block cannot be used (right-clicked) until the lock's condition holds. */
public final class BlockLocks {
    private BlockLocks() {
    }

    /** The first lock that keeps this player from using this block, or null. */
    public static BlockLock locked(ServerPlayer player, BlockState state) {
        QuestContent content = QuestContent.current;
        if (content.blockLocks.isEmpty()) {
            return null;
        }
        ServerCtx ctx = null;
        for (BlockLock lock : content.blockLocks.values()) {
            if (lock.block() == null || !lock.block().matches(state)) {
                continue;
            }
            if (ctx == null) {
                ctx = QuestEngine.ctx(player);
            }
            if ((lock.when() == null || lock.when().test(ctx)) && !lock.unlockWhen().test(ctx)) {
                return lock;
            }
        }
        return null;
    }
}
