package com.solace.mixin;

import net.minecraft.CrashReportCategory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.Objects;

/** Generated/native frames can lack a source filename; keep the real report. */
@Mixin(CrashReportCategory.class)
public abstract class CrashReportCategoryMixin {
    @Redirect(method = "validateStackTrace", at = @At(value = "INVOKE",
            target = "Ljava/lang/String;equals(Ljava/lang/Object;)Z", ordinal = 1))
    private boolean solace$compareNullableSourceFile(String fileName, Object otherFileName) {
        return Objects.equals(fileName, otherFileName);
    }
}
