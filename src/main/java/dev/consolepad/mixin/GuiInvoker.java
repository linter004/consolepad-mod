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
}
