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
        if (ContextSwitcher.isSecondaryActive()) {
            ci.cancel();
            dev.consolepad.split.SplitNotices.setOverlay(message);
        }
    }

    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void consolepad$noTitle(Component title, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) {
            ci.cancel();
            dev.consolepad.split.SplitNotices.setTitle(title);
        }
    }

    /** Las barras de jefe que llegan a un jugador extra van a las suyas (la de vanilla es la de P1 y se dibuja en su region). */
    @Inject(method = "getBossOverlay", at = @At("RETURN"), cancellable = true)
    private void consolepad$ownBossOverlay(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<net.minecraft.client.gui.components.BossHealthOverlay> cir) {
        dev.consolepad.split.PlayerContext c = ContextSwitcher.current();
        if (c != null) cir.setReturnValue(c.bossOverlay());
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void consolepad$noSubtitle(Component subtitle, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
