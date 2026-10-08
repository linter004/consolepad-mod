package dev.consolepad.mixin;

import dev.consolepad.split.SplitDiag;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Diagnostico del HUD dividido: anota, por capa dibujada, el jugador cuyos datos usa (getCameraPlayer) y el ancho de
 * interfaz de la region. Solo actua mientras el autotest graba (SplitDiag.hudRecording).
 */
@Mixin(Gui.class)
public abstract class GuiDiagMixin {
    @Shadow
    protected abstract Player getCameraPlayer();

    @Inject(method = "renderCrosshair", at = @At("HEAD"))
    private void consolepad$diagCrosshair(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        SplitDiag.onHudLayer("mira", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderItemHotbar", at = @At("HEAD"))
    private void consolepad$diagHotbar(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        SplitDiag.onHudLayer("hotbar", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderExperienceBar", at = @At("HEAD"))
    private void consolepad$diagXpBar(GuiGraphics g, int x, CallbackInfo ci) {
        SplitDiag.onHudLayer("barra de experiencia", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderHealthLevel", at = @At("HEAD"))
    private void consolepad$diagHealth(GuiGraphics g, CallbackInfo ci) {
        SplitDiag.onHudLayer("vida", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderArmorLevel", at = @At("HEAD"))
    private void consolepad$diagArmor(GuiGraphics g, CallbackInfo ci) {
        SplitDiag.onHudLayer("armadura", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderFoodLevel", at = @At("HEAD"))
    private void consolepad$diagFood(GuiGraphics g, CallbackInfo ci) {
        SplitDiag.onHudLayer("comida", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderAirLevel", at = @At("HEAD"))
    private void consolepad$diagAir(GuiGraphics g, CallbackInfo ci) {
        SplitDiag.onHudLayer("burbujas", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderExperienceLevel", at = @At("HEAD"))
    private void consolepad$diagXpLevel(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        SplitDiag.onHudLayer("nivel de experiencia", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderSelectedItemName(Lnet/minecraft/client/gui/GuiGraphics;I)V", at = @At("HEAD"))
    private void consolepad$diagItemName(GuiGraphics g, int shift, CallbackInfo ci) {
        SplitDiag.onHudLayer("nombre del objeto", getCameraPlayer(), g.guiWidth());
    }

    @Inject(method = "renderEffects", at = @At("HEAD"))
    private void consolepad$diagEffects(GuiGraphics g, DeltaTracker d, CallbackInfo ci) {
        SplitDiag.onHudLayer("efectos", getCameraPlayer(), g.guiWidth());
    }
}
