package com.solace.mixin;

import mcjty.incontrol.rules.support.RuleKeys;
import mcjty.incontrol.tools.typed.AttributeMap;
import mcjty.incontrol.tools.typed.Key;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

/**
 * Keeps condition-only block offsets available for InControl's condition parser.
 *
 * <p>InControl 9.4.1 processes actions before conditions. Its generic two-key
 * consumer invokes the set-block action when either {@code setblock} or
 * {@code blockoffset} is present, even though {@code blockoffset} is also a
 * valid modifier for the {@code block} condition. A condition-only offset then
 * reaches the set-block JSON parser with a null block and invalidates the whole
 * rules file.</p>
 */
@Mixin(value = AttributeMap.class, remap = false)
public abstract class InControlAttributeMapMixin {
    @Inject(method = "consume2", at = @At("HEAD"), cancellable = true)
    private <A, B> void solace$preserveConditionOnlyBlockOffset(
            Key<A> primaryKey,
            Key<B> secondaryKey,
            BiConsumer<A, B> consumer,
            CallbackInfo callbackInfo
    ) {
        AttributeMap attributes = (AttributeMap) (Object) this;
        if (primaryKey == RuleKeys.ACTION_SETBLOCK
                && secondaryKey == RuleKeys.BLOCKOFFSET
                && !attributes.has(primaryKey)) {
            callbackInfo.cancel();
        }
    }
}
