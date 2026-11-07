package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.solace.audit.AuditSession;
import com.solace.audit.ContentHooks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ServerLevel.class)
public abstract class EntityTickAudit {
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void audit$tick(Entity entity, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.entity("server_entity_tick", entity, entity.getClass())) { original.call(entity); }
    }
    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void audit$passenger(Entity entity, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.entity("server_passenger_tick", entity, entity.getClass())) { original.call(entity); }
    }
}
