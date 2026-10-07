package dev.consolepad.split;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.consolepad.mixin.GuiInvoker;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fase 3, paso 5: HUD dividido. El HUD de P1 se dibuja entero pero centrado en la mitad izquierda (el ancho de la interfaz
 * pasa a ser el de la mitad), y P2 recibe punto de mira y hotbar en la mitad derecha, dentro de su contexto.
 * Los menus y pantallas siguen a ventana completa.
 */
public final class SplitHud {
    private SplitHud() {}

    public static void render(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker) {
        PlayerContext p2 = SplitRender.activeContext();
        if (p2 == null) {
            gui.render(graphics, deltaTracker);
            return;
        }
        int full = graphics.guiWidth(); // sin sustituir todavia: ancho real de la ventana
        int half = full / 2;
        try {
            SplitView.guiWidth = half;
            gui.render(graphics, deltaTracker);

            PoseStack pose = graphics.pose();
            pose.pushPose();
            try {
                pose.translate(half, 0, 0);
                SplitView.guiWidth = full - half;
                GuiInvoker invoker = (GuiInvoker) gui;
                ContextSwitcher.run(p2, () -> {
                    invoker.consolepad$renderCrosshair(graphics, deltaTracker);
                    invoker.consolepad$renderHotbar(graphics, deltaTracker);
                });
            } finally {
                pose.popPose();
            }
        } finally {
            SplitView.guiWidth = 0;
        }
    }
}
