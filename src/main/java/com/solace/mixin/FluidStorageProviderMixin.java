package com.solace.mixin;

import com.solace.ElixirumCauldronHolder;
import io.redspace.ironsspellbooks.block.alchemist_cauldron.AlchemistCauldronTile;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import snownee.jade.addon.universal.FluidStorageProvider;
import snownee.jade.api.Accessor;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

@Mixin(FluidStorageProvider.class)
public class FluidStorageProviderMixin {

    @Inject(method = "append", at = @At("HEAD"), remap = false)
    private static void solace$append(ITooltip tooltip, Accessor<?> accessor, IPluginConfig config, CallbackInfo ci) {
        if (accessor.getTarget() instanceof AlchemistCauldronTile cauldronTile && cauldronTile instanceof ElixirumCauldronHolder holder) {
            dev.obscuria.elixirum.server.alchemy.brewing.IngredientMixer mixer = (dev.obscuria.elixirum.server.alchemy.brewing.IngredientMixer) holder.solace$getElixirumMixer();
            if (mixer != null && !mixer.isEmpty() && mixer.isComplete()) {
                CompoundTag serverData = accessor.getServerData();
                if (serverData.contains("JadeFluidStorage", 9)) { // 9 is ListTag
                    ListTag list = serverData.getList("JadeFluidStorage", 10); // 10 is CompoundTag
                    if (!list.isEmpty()) {
                        CompoundTag viewGroupTag = list.getCompound(0);
                        if (viewGroupTag.contains("Views", 9)) { // 9 is ListTag
                            ListTag viewsList = viewGroupTag.getList("Views", 10);
                            if (!viewsList.isEmpty()) {
                                CompoundTag fluidViewTag = viewsList.getCompound(0);
                                
                                // Create dummy elixir item to get correct hover name
                                net.minecraft.world.item.ItemStack dummyElixir = dev.obscuria.elixirum.common.registry.ElixirumItems.ELIXIR.get().getDefaultInstance();
                                dev.obscuria.elixirum.api.ArsElixirumAPI.setElixirContents(dummyElixir, mixer.getContents());
                                Component elixirName = dummyElixir.getHoverName();
                                
                                // Strip "Elixir of " or "elixir of " prefix robustly
                                String nameStr = elixirName.getString().trim();
                                if (nameStr.toLowerCase().startsWith("elixir of ")) {
                                    nameStr = nameStr.substring("elixir of ".length()).trim();
                                }
                                System.out.println("[SolaceDebug] Original name: '" + elixirName.getString() + "', Stripped name: '" + nameStr + "'");
                                
                                int amount = cauldronTile.fluidInventory.getFluidInTank(0).getAmount();
                                int capacity = cauldronTile.fluidInventory.getTankCapacity(0);
                                
                                Component nameComponent = Component.literal(nameStr).withStyle(elixirName.getStyle()).withStyle(ChatFormatting.WHITE);
                                Component amountComponent = Component.literal(String.format(" (%d/%d mB)", amount, capacity)).withStyle(ChatFormatting.GRAY);
                                Component finalFluidComponent = Component.empty().append(nameComponent).append(amountComponent);
                                
                                // Serialize component to JSON string and write to tag
                                String json = Component.Serializer.toJson(finalFluidComponent);
                                fluidViewTag.putString("solace$overrideText", json);
                                
                                // Write custom color to the fluid's NBT tag
                                int color = holder.solace$getElixirumColor();
                                CompoundTag fluidTag;
                                if (fluidViewTag.contains("tag", 10)) { // 10 is CompoundTag
                                    fluidTag = fluidViewTag.getCompound("tag");
                                } else {
                                    fluidTag = new CompoundTag();
                                    fluidViewTag.put("tag", fluidTag);
                                }
                                fluidTag.putInt("solace$color", color);
                            }
                        }
                    }
                }
            }
        }
    }
}
