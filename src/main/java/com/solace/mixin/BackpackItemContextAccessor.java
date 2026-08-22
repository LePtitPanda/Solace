package com.solace.mixin;

import net.p3pp3rf1y.sophisticatedbackpacks.common.gui.BackpackContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BackpackContext.Item.class, remap = false)
public interface BackpackItemContextAccessor {
    @Accessor("handlerName")
    String solace$getHandlerName();

    @Accessor("identifier")
    String solace$getIdentifier();

    @Accessor("backpackSlotIndex")
    int solace$getSlotIndex();
}
