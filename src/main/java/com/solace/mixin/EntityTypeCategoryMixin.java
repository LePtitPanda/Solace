package com.solace.mixin;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeCategoryMixin {
    @Unique
    private static final ResourceLocation SOLACE$TUFF_GOLEM_ID = new ResourceLocation("friendsandfoes", "tuff_golem");

    @Inject(method = "getCategory", at = @At("RETURN"), cancellable = true)
    private void solace$classifyTuffGolemAsCreature(CallbackInfoReturnable<MobCategory> callbackInfo) {
        if (callbackInfo.getReturnValue() != MobCategory.MISC) {
            return;
        }
        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey((EntityType<?>) (Object) this);
        if (SOLACE$TUFF_GOLEM_ID.equals(entityId)) {
            callbackInfo.setReturnValue(MobCategory.CREATURE);
        }
    }
}
