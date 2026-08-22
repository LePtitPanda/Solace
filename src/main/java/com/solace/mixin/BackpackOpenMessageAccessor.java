package com.solace.mixin;

import net.p3pp3rf1y.sophisticatedbackpacks.network.BackpackOpenMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BackpackOpenMessage.class, remap = false)
public interface BackpackOpenMessageAccessor {
    @Accessor("slotIndex")
    int solace$getSlotIndex();

    @Accessor("identifier")
    String solace$getIdentifier();

    @Accessor("handlerName")
    String solace$getHandlerName();
}
