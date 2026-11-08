package com.solace.mixin;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Vanilla answers any failed resource reload (including ones caused by a broken
 * mod, not a pack) by deselecting every resource pack and saving that to
 * options.txt, so the player's pack list is lost. This keeps the in-session
 * fallback to default packs (needed to recover) but writes the previous pack
 * selection back before options are saved, so it returns on next launch.
 */
@Mixin(Minecraft.class)
public abstract class ResourcePackRecoveryMixin {
    @Shadow @Final public Options options;

    @Unique private List<String> solace$savedPacks;
    @Unique private List<String> solace$savedIncompatiblePacks;

    @Inject(method = "clearResourcePacksOnError", at = @At("HEAD"))
    private void solace$rememberPacks(Throwable error, @Nullable Component message, CallbackInfo ci) {
        solace$savedPacks = new ArrayList<>(options.resourcePacks);
        solace$savedIncompatiblePacks = new ArrayList<>(options.incompatibleResourcePacks);
    }

    @Redirect(
            method = "clearResourcePacksOnError",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Options;save()V")
    )
    private void solace$saveWithoutWipingPacks(Options opts) {
        if (solace$savedPacks != null && !solace$savedPacks.isEmpty()) {
            opts.resourcePacks.clear();
            opts.resourcePacks.addAll(solace$savedPacks);
            opts.incompatibleResourcePacks.clear();
            opts.incompatibleResourcePacks.addAll(solace$savedIncompatiblePacks);
        }
        opts.save();
        solace$savedPacks = null;
        solace$savedIncompatiblePacks = null;
    }
}
