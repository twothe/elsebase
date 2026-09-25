package dev.elsebase.client.checks;

import dev.elsebase.client.TemplateChecks;
import net.minecraft.core.SectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-only ordering fixture: deliver appearance between Sodium's first mesh build and upload. */
@Pseudo
@Mixin(targets="net.caffeinemc.mods.sodium.client.render.chunk.RenderSection",remap=false)
public abstract class StartupMeshMixin {
    @Shadow public abstract SectionPos getPosition();
    @Inject(method="setInfo",at=@At("HEAD"))
    private void beforeUpload(CallbackInfoReturnable<Boolean> callback) {
        TemplateChecks.beforeSectionUpload(getPosition());
    }
}
