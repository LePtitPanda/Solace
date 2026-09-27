package com.solace.mixin;

import com.solace.loot.LootEditorManager;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Makes an edited table authoritative when its per-table "Extras" switch is off. */
@Mixin(LootTable.class)
public abstract class LootTableMixin {
    @Redirect(
            method = "getRandomItems(Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraftforge/common/ForgeHooks;modifyLoot(Lnet/minecraft/resources/ResourceLocation;Lit/unimi/dsi/fastutil/objects/ObjectArrayList;Lnet/minecraft/world/level/storage/loot/LootContext;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;",
                    remap = false
            )
    )
    private ObjectArrayList<ItemStack> solace$applyOptionalGlobalModifiers(
            ResourceLocation lootTableId, ObjectArrayList<ItemStack> generatedLoot, LootContext context) {
        return com.solace.loot.LootExtraManager.applyModifiers(lootTableId, generatedLoot, context);
    }
}
