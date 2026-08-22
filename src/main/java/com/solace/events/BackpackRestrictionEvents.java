package com.solace.events;

import com.solace.Solace;
import com.solace.backpack.BackpackRestrictions;
import com.solace.mixin.BackpackItemContextAccessor;
import net.minecraft.world.entity.player.Player;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContainer;
import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = Solace.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class BackpackRestrictionEvents {
    private BackpackRestrictionEvents() {
    }

    @SubscribeEvent
    public static void rejectUnequippedBackpackMenus(PlayerContainerEvent.Open event) {
        if (!(event.getContainer() instanceof BackpackContainer container)) {
            return;
        }
        BackpackContext context = container.getBackpackContext();
        boolean equippedBackSlot = false;
        if (context instanceof BackpackContext.Item) {
            BackpackItemContextAccessor accessor = (BackpackItemContextAccessor) context;
            equippedBackSlot = BackpackRestrictions.isAllowedEquippedContext(event.getEntity(),
                    accessor.solace$getHandlerName(), accessor.solace$getIdentifier(), accessor.solace$getSlotIndex());
        }
        if (!equippedBackSlot) {
            Player player = event.getEntity();
            player.closeContainer();
        }
    }
}
