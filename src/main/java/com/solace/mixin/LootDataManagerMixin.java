package com.solace.mixin;

import com.solace.loot.LootEditorManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.LootDataId;
import net.minecraft.world.level.storage.loot.LootDataManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(LootDataManager.class)
public abstract class LootDataManagerMixin {
    @Shadow @Final private Map<LootDataId<?>, ?> elements;

    @Inject(method = "getElement", at = @At("HEAD"), cancellable = true)
    private <T> void solace$useEditedLootTable(LootDataId<T> id, CallbackInfoReturnable<T> callback) {
        if (id.type() != LootDataType.TABLE) {
            return;
        }
        ResourceLocation location = id.location();
        LootTable replacement = LootEditorManager.resolve(location, this.elements);
        if (replacement != null) {
            @SuppressWarnings("unchecked") T value = (T) replacement;
            callback.setReturnValue(value);
        }
    }
}
