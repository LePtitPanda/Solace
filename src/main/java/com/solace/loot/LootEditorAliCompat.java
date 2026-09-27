package com.solace.loot;

import com.mojang.logging.LogUtils;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraftforge.fml.ModList;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/** Optional, reflection-isolated integration so Solace does not require ALI to launch. */
public final class LootEditorAliCompat {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final AtomicLong REVISION = new AtomicLong();

    private LootEditorAliCompat() {
    }

    public static void requestRefresh(MinecraftServer server) {
        if (!ModList.get().isLoaded("ali")) {
            return;
        }
        long revision = REVISION.incrementAndGet();
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS).execute(() -> server.execute(() -> {
            if (revision != REVISION.get() || !server.isRunning()) {
                return;
            }
            try {
                Class<?> aliMod = Class.forName("com.yanny.ali.forge.AliMod");
                Field serverField = aliMod.getField("SERVER");
                Object aliServer = serverField.get(null);
                Method rebuild = aliServer.getClass().getMethod("readLootTables", LootDataManager.class);
                Method sync = aliServer.getClass().getMethod("syncLootTables", Player.class);
                rebuild.invoke(aliServer, server.getLootData());
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    sync.invoke(aliServer, player);
                }
                LOGGER.info("[Solace] Refreshed Advanced Loot Info after in-game loot editing.");
            } catch (ReflectiveOperationException | LinkageError exception) {
                LOGGER.warn("[Solace] Could not refresh Advanced Loot Info; it will update on the next resource reload.", exception);
            }
        }));
    }
}
