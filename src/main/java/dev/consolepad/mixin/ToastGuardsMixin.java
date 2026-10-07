package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ToastComponent.class)
public abstract class ToastGuardsMixin {
    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void consolepad$noToast(Toast toast, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
