package com.solace.audit.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.solace.audit.AuditSession;
import com.solace.audit.ContentHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientLevel.class)
public abstract class ClientEntityTickAudit {
    @WrapOperation(method = "tickNonPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;tick()V"))
    private void audit$tick(Entity entity, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.entity("client_entity_tick", entity, entity.getClass())) { original.call(entity); }
    }
    @WrapOperation(method = "tickPassenger", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;rideTick()V"))
    private void audit$passenger(Entity entity, Operation<Void> original) {
        try (AuditSession.Ticket ticket = ContentHooks.entity("client_passenger_tick", entity, entity.getClass())) { original.call(entity); }
    }
}
