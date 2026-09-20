package dev.elsebase.client.checks;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Test-launch-only hook: prevent cursor capture and warping before either reaches GLFW. */
@Mixin(MouseHandler.class)
public abstract class UncapturedMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void elsebase$preventTestMouseCapture(CallbackInfo callback) {
        callback.cancel();
    }
}
