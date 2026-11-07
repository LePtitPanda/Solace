package com.solace.audit;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ContentHooks {
    private ContentHooks() { }
    public static AuditSession.Ticket entity(String category, Entity entity, Class<?> implementation) {
        AuditSession.Ticket ticket = AuditSession.open(category);
        if (ticket != null) ticket.begin(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), implementation,
                entity.level().dimension().location(), entity.blockPosition());
        return ticket;
    }
    public static AuditSession.Ticket blockEntity(String category, BlockEntity entity, Class<?> implementation) {
        AuditSession.Ticket ticket = AuditSession.open(category);
        if (ticket != null) ticket.begin(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()), implementation,
                entity.getLevel() == null ? "item" : entity.getLevel().dimension().location(), entity.getBlockPos());
        return ticket;
    }
    public static AuditSession.Ticket implementation(String category, Object object) {
        AuditSession.Ticket ticket = AuditSession.open(category);
        if (ticket != null) ticket.begin(object.getClass().getName(), object.getClass(), "client", "");
        return ticket;
    }
}
