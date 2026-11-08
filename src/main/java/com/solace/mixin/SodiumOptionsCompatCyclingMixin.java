package com.solace.mixin;

import java.util.Locale;
import me.jellysquid.mods.sodium.client.gui.options.Option;
import me.jellysquid.mods.sodium.client.gui.options.control.CyclingControl;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Sodium Options Mod Compat 1.0.0 hard-codes the names of EMF/ETF enum options.
 * EMF 3.3.12 removed RenderModeChoice.GREEN, so the names array no longer matches
 * the enum and Embeddium's CyclingControl throws when Video Settings opens.
 * Rebuild the names from the enum constants, matching each one to the provided
 * translation key where possible.
 */
@Mixin(targets = {
        "toni.sodiumoptionsmodcompat.integration.emf.EmfModelsOptionPage",
        "toni.sodiumoptionsmodcompat.integration.etf.EtfTexturesOptionPage",
        "toni.sodiumoptionsmodcompat.integration.etf.EtfMiscOptionPage"
}, remap = false)
public abstract class SodiumOptionsCompatCyclingMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "*", require = 0, remap = false,
            at = @At(value = "NEW",
                    target = "(Lme/jellysquid/mods/sodium/client/gui/options/Option;Ljava/lang/Class;[Lnet/minecraft/network/chat/Component;)Lme/jellysquid/mods/sodium/client/gui/options/control/CyclingControl;",
                    remap = false))
    private static CyclingControl solace$matchEnumNames(Option option, Class enumClass, Component[] names) {
        return new CyclingControl(option, enumClass, solace$alignNames(enumClass, names));
    }

    private static Component[] solace$alignNames(Class<?> enumClass, Component[] names) {
        Object[] constants = enumClass.getEnumConstants();
        if (constants == null || names == null || constants.length == names.length) {
            return names;
        }
        Component[] aligned = new Component[constants.length];
        for (int i = 0; i < constants.length; i++) {
            String constant = ((Enum<?>) constants[i]).name().toLowerCase(Locale.ROOT);
            aligned[i] = solace$findName(constant, names);
        }
        return aligned;
    }

    private static Component solace$findName(String constant, Component[] names) {
        String compact = constant.replace("_and_", "_");
        for (Component name : names) {
            if (name != null && name.getContents() instanceof TranslatableContents translatable) {
                String key = translatable.getKey().toLowerCase(Locale.ROOT);
                if (key.endsWith("." + constant) || key.endsWith("." + compact)) {
                    return name;
                }
            }
        }
        String pretty = constant.replace('_', ' ');
        return Component.literal(Character.toUpperCase(pretty.charAt(0)) + pretty.substring(1));
    }
}
