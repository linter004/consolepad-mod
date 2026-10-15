package dev.consolepad.mixin;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Las dos piezas del HUD que se dibujan para P2 (privadas en Gui). */
@Mixin(Gui.class)
public interface GuiInvoker {
    @Invoker("renderCrosshair")
    void consolepad$renderCrosshair(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    @Invoker("renderHotbar")
    void consolepad$renderHotbar(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    @Invoker("maybeRenderExperienceBar")
    void consolepad$renderExperienceBar(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    /** Vida, armadura, comida y burbujas (solo si el modo de juego puede recibir dano). */
    @Invoker("maybeRenderPlayerHealth")
    void consolepad$renderPlayerHealth(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    /** Barra de salto de la montura (caballo, mula...): sustituye a la de experiencia mientras se monta. */
    @Invoker("maybeRenderJumpMeter")
    void consolepad$renderJumpMeter(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    /** Corazones de la montura (sobre la comida del jugador). */
    @Invoker("maybeRenderVehicleHealth")
    void consolepad$renderVehicleHealth(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    @Invoker("renderExperienceLevel")
    void consolepad$renderExperienceLevel(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    @Invoker("maybeRenderSelectedItemName")
    void consolepad$renderSelectedItemName(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    /** Viñeta, calabaza, nieve en polvo, portal y catalejo. */
    @Invoker("renderCameraOverlays")
    void consolepad$renderCameraOverlays(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    @Invoker("renderSleepOverlay")
    void consolepad$renderSleepOverlay(GuiGraphics guiGraphics, DeltaTracker deltaTracker);

    @Invoker("displayScoreboardSidebar")
    void consolepad$displayScoreboardSidebar(GuiGraphics guiGraphics, net.minecraft.world.scores.Objective objective);

    @Invoker("updateVignetteBrightness")
    void consolepad$updateVignetteBrightness(net.minecraft.world.entity.Entity entity);

    @Invoker("renderEffects")
    void consolepad$renderEffects(GuiGraphics guiGraphics, DeltaTracker deltaTracker);
}
