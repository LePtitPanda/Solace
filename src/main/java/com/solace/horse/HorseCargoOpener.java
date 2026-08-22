package com.solace.horse;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.NetworkHooks;

public final class HorseCargoOpener {
    private HorseCargoOpener() {
    }

    public static void open(Player player, Horse horse) {
        if (!(player instanceof ServerPlayer serverPlayer) || !horse.isTamed()) {
            return;
        }
        NetworkHooks.openScreen(serverPlayer,
                new SimpleMenuProvider((id, inventory, ignored) -> new HorseCargoMenu(id, inventory, horse),
                        Component.translatable("container.solace.horse_cargo")),
                buffer -> buffer.writeVarInt(horse.getId()));
    }
}
