package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.gui.Gui;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Titulos y mensajes sobre la barra de P2 no deben aparecer en la pantalla de P1. */
@Mixin(Gui.class)
public abstract class GuiGuardsMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void consolepad$noOverlay(Component message, boolean animate, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void consolepad$noTitle(Component title, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void consolepad$noSubtitle(Component subtitle, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
