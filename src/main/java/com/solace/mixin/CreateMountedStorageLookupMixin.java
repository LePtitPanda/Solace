package com.solace.mixin;

import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Arrays;

/**
 * Backport of PoppyBlossom's mounted-storage lookup in Create (MIT), commit
 * 5f0910c6cefa67f53b62a2f0d966bca6162567f8, PR #9706.
 * Copyright (c) The Create Team / The Creators of Create. See licenses/create-MIT.txt.
 * https://github.com/Creators-of-Create/Create/pull/9706
 */
@Pseudo
@Mixin(targets = "com.simibubi.create.api.contraption.storage.item.MountedItemStorageWrapper", remap = false)
public abstract class CreateMountedStorageLookupMixin extends CombinedInvWrapper {
    @Unique private int[] solace$slotToStorage;

    protected CreateMountedStorageLookupMixin(IItemHandlerModifiable... handlers) {
        super(handlers);
    }

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void solace$indexSlots(CallbackInfo ci) {
        int[] lookup = new int[slotCount];
        int start = 0;
        for (int storage = 0; storage < baseIndex.length; storage++) {
            int end = baseIndex[storage];
            Arrays.fill(lookup, start, end, storage);
            start = end;
        }
        // Use Forge's frozen offsets, including zero-slot handlers. No second
        // getSlots() call into addons, and no change to local-slot translation.
        solace$slotToStorage = lookup;
    }

    /** Adds an override of the inherited Forge method only on this Create wrapper. */
    @Override
    protected int getIndexForSlot(int slot) {
        int[] lookup = solace$slotToStorage;
        // Retain superclass behavior if an addon calls during construction.
        if (lookup == null) return super.getIndexForSlot(slot);
        return slot < 0 || slot >= lookup.length ? -1 : lookup[slot];
    }
}
