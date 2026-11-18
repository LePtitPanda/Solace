package com.solace.registry;

import com.solace.Solace;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;

public final class SolaceEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, Solace.MOD_ID);

    /** Illager cuffs: hands chained together. What it blocks is in {@link com.solace.world.Shackles}. */
    public static final RegistryObject<MobEffect> SHACKLED = EFFECTS.register("shackled", () -> new MobEffect(MobEffectCategory.HARMFUL, 0x4A4A4A) {
        @Override
        public List<ItemStack> getCurativeItems() {
            return new ArrayList<>(); // milk doesn't open a lock
        }
    }.addAttributeModifier(Attributes.MOVEMENT_SPEED, "6f0b3c55-6a4c-4c1e-9d3e-5b1f0a7c2e11", -0.3, AttributeModifier.Operation.MULTIPLY_TOTAL));

    private SolaceEffects() {
    }
}
