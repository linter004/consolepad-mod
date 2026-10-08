package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Aviso de P1 "[Start] Unirse como el jugador N" para cada mando conectado cuyo jugador aun no esta dentro. Pulsar ese boton en
 * ese mando mete al jugador (PadDriver). Se dibuja abajo en el centro de la region de P1, encima de la barra de objetos.
 */
public final class JoinPrompt {
    /** Ultimo dibujo (para el autotest): mascara de slots avisados, caja {x, y, ancho, alto} en unidades de GUI de P1 y tamano de la region. */
    public static volatile int lastSlots;
    public static volatile int[] lastBox = {0, 0, 0, 0};
    public static volatile int[] lastRegion = {0, 0};

    private JoinPrompt() {}

    /** Slots (1 a 3) con mando conectado y jugador fuera, con P1 en un mundo local. */
    public static int pendingSlots(Minecraft mc) {
        if (mc.level == null || mc.player == null || mc.getSingleplayerServer() == null) return 0;
        PadManager pads = ConsolePad.PADS;
        if (pads == null) return 0;
        int mask = 0;
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            if (pads.slot(i).device != null && !SecondaryPlayers.isActive(i)) mask |= 1 << i;
        }
        return mask;
    }

    /** Dibuja el aviso en una region de P1 de ancho w y alto h (unidades de GUI de P1). */
    public static void render(Minecraft mc, GuiGraphics g, int w, int h) {
        lastSlots = 0;
        if (mc.screen != null || mc.options.hideGui) return;
        int mask = pendingSlots(mc);
        if (mask == 0) return;
        Font font = mc.font;
        PadManager pads = ConsolePad.PADS;
        int row = 0;
        int minX = Integer.MAX_VALUE, maxX = 0, minY = Integer.MAX_VALUE, maxY = 0;
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            if ((mask & (1 << i)) == 0) continue;
            var type = pads.slot(i).device.type;
            String text = "Unirse como el jugador " + (i + 1);
            int iconW = PadIcons.width(font, PadState.START, type);
            int totalW = iconW + 4 + font.width(text);
            int x = (w - totalW) / 2;
            int y = h - 82 - row * (PadIcons.H + 4);
            g.fill(x - 4, y - 2, x + totalW + 4, y + PadIcons.H + 2, 0x80000000);
            PadIcons.draw(g, font, x, y, PadState.START, type);
            g.drawString(font, text, x + iconW + 4, y + 1, 0xFFFFFFFF, true);
            minX = Math.min(minX, x - 4);
            maxX = Math.max(maxX, x + totalW + 4);
            minY = Math.min(minY, y - 2);
            maxY = Math.max(maxY, y + PadIcons.H + 2);
            row++;
        }
        lastSlots = mask;
        lastBox = new int[] {minX, minY, maxX - minX, maxY - minY};
        lastRegion = new int[] {w, h};
    }
}
