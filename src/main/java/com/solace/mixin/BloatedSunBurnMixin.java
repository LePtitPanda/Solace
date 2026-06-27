package com.solace.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.thirdlife.iterrpg.entity.BloatedEntity", remap = false)
public abstract class BloatedSunBurnMixin extends Monster {

    protected BloatedSunBurnMixin(EntityType<? extends Monster> entityType, Level level) {
        super(entityType, level);
    }

    @Redirect(
        method = "m_6075_",
        at = @At(
            value = "INVOKE",
            target = "Lnet/thirdlife/iterrpg/procedures/UndeadBurnProcedure;execute(Lnet/minecraft/world/level/LevelAccessor;DDDLnet/minecraft/world/entity/Entity;)V",
            remap = false
        ),
        remap = false,
        require = 0
    )
    private void solace$useVanillaUndeadSunBurn(LevelAccessor level, double x, double y, double z, Entity entity) {
        if (this.isInWaterRainOrBubble() || this.isInPowderSnow) {
            this.setRemainingFireTicks(0);
            return;
        }

        if (this.solace$shouldStartBurningInSun()) {
            this.setSecondsOnFire(8);
        }
    }

    private boolean solace$shouldStartBurningInSun() {
        if (!this.isAlive() || !this.level().isDay() || this.level().isClientSide) {
            return false;
        }

        ItemStack headStack = this.getItemBySlot(EquipmentSlot.HEAD);
        if (!headStack.isEmpty()) {
            return false;
        }

        float brightness = this.getLightLevelDependentMagicValue();
        if (brightness <= 0.5F) {
            return false;
        }

        BlockPos headPos = BlockPos.containing(this.getX(), this.getEyeY(), this.getZ());
        return this.getRandom().nextFloat() * 30.0F < (brightness - 0.4F) * 2.0F
            && this.level().canSeeSky(headPos);
    }
}
