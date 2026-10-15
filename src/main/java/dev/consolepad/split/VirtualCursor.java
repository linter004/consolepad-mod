package dev.consolepad.split;

import net.minecraft.client.gui.GuiGraphics;

/** Flecha del cursor del mando (la misma para P1, cuando lo maneja el mando, y para P2 a P4). */
public final class VirtualCursor {
    private VirtualCursor() {}

    /** Dibuja la flecha con la punta en (cx, cy), por encima de todo lo demas de la pantalla. */
    public static void draw(GuiGraphics graphics, int cx, int cy) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 500);
        for (int r = 0; r < 10; r++) { // contorno negro
            int width = Math.min(r / 2 + 2, 6);
            graphics.fill(cx, cy + r, cx + width + 1, cy + r + 1, 0xFF000000);
        }
        for (int r = 1; r < 9; r++) { // relleno blanco
            int width = Math.min(r / 2 + 1, 5);
            graphics.fill(cx + 1, cy + r, cx + width, cy + r + 1, 0xFFFFFFFF);
        }
        graphics.pose().popPose();
    }
}
