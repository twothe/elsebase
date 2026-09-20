package dev.elsebase.client.checks;

import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Test-launch-only volume override, effective before audio initialization without changing saved options. */
@Mixin(Options.class)
public abstract class SilentAudioMixin {
    @Inject(method = "getSoundSourceVolume", at = @At("HEAD"), cancellable = true)
    private void elsebase$muteAutomatedTests(SoundSource source, CallbackInfoReturnable<Float> callback) {
        callback.setReturnValue(0.0F);
    }
}
