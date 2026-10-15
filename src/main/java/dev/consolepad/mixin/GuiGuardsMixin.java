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

    @org.spongepowered.asm.mixin.Shadow
    private int toolHighlightTimer;
    @org.spongepowered.asm.mixin.Shadow
    private net.minecraft.world.item.ItemStack lastToolHighlight;

    /**
     * El nombre del objeto seleccionado (el que sale sobre la barra al cambiar de casilla) no se dibuja mientras el jugador del contexto activo
     * tiene una pantalla abierta: atravesaba el inventario y los contenedores. Contadores por jugador en SplitDiag para medirlo.
     */
    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("HEAD"), cancellable = true)
    private void consolepad$hideItemNameOverScreen(net.minecraft.client.gui.GuiGraphics graphics, int shift, CallbackInfo ci) {
        boolean visible = toolHighlightTimer > 0 && !lastToolHighlight.isEmpty();
        int slot = dev.consolepad.split.ReduceEffects.contextSlot();
        if (net.minecraft.client.Minecraft.getInstance().screen != null) {
            if (visible) dev.consolepad.split.SplitDiag.itemNameHidden[slot]++;
            ci.cancel();
        } else if (visible) {
            dev.consolepad.split.SplitDiag.itemNameShown[slot]++;
        }
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void consolepad$noSubtitle(Component subtitle, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }

    /** Accesibilidad: sin vineta en la region del jugador que lo pida (el contexto activo es el suyo; P1 si no hay ninguno). */
    @Inject(method = "renderVignette", at = @At("HEAD"), cancellable = true)
    private void consolepad$noVignette(net.minecraft.client.gui.GuiGraphics graphics, net.minecraft.world.entity.Entity entity, CallbackInfo ci) {
        if (dev.consolepad.split.SplitView.p1HudInset && !dev.consolepad.split.ContextSwitcher.isSecondaryActive()) {
            ci.cancel(); // dentro de una zona segura la vineta saldria como un rectangulo claro
            return;
        }
        if (dev.consolepad.split.ReduceEffects.contextOn()) {
            dev.consolepad.split.ReduceEffects.skippedVignette[dev.consolepad.split.ReduceEffects.contextSlot()]++;
            ci.cancel();
        }
    }
}
