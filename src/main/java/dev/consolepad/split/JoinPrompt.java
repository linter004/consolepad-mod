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
    /** Diagnostico del ultimo fotograma: por que se dibujo o no. */
    public static volatile String lastWhy = I18n.tr("sin llamar");
    public static volatile int[] lastBox = {0, 0, 0, 0};
    public static volatile int[] lastRegion = {0, 0};

    /** Segundos que se ve el aviso de un mando desde que aparece; solo vuelve a salir si se conecta un mando nuevo. */
    public static final long SHOW_NANOS = 12_000_000_000L;
    private static final PadManager.Device[] seen = new PadManager.Device[PadManager.MAX_PLAYERS];
    private static final long[] shownAt = new long[PadManager.MAX_PLAYERS];

    private JoinPrompt() {}

    /** Solo pruebas: el siguiente dibujo trata el mando de ese slot como recien conectado (reloj de 12 s de nuevo). */
    public static void forget(int slot) {
        seen[slot] = null;
    }

    /** Esta pendiente y su aviso aun no ha caducado (el reloj de cada mando empieza la primera vez que se dibuja). */
    private static boolean visible(int slot, PadManager.Device device, long now) {
        if (seen[slot] != device) { // mando nuevo en el slot: el aviso vuelve a empezar
            seen[slot] = device;
            shownAt[slot] = now;
        }
        return now - shownAt[slot] < SHOW_NANOS;
    }

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
        lastWhy = "pantalla/oculto";
        if (mc.screen != null || mc.options.hideGui) return;
        int mask = pendingSlots(mc);
        lastWhy = I18n.tr("sin pendientes");
        if (mask == 0) return;
        Font font = mc.font;
        PadManager pads = ConsolePad.PADS;
        int row = 0;
        long now = System.nanoTime();
        int visibleMask = 0;
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            if ((mask & (1 << i)) != 0 && visible(i, pads.slot(i).device, now)) visibleMask |= 1 << i;
        }
        lastWhy = "pendientes " + Integer.toBinaryString(mask) + " visibles " + Integer.toBinaryString(visibleMask) + " edad1=" + (now - shownAt[1]) / 1000000 + "ms edad2=" + (now - shownAt[2]) / 1000000 + "ms";
        mask = visibleMask;
        if (mask == 0) return;
        int minX = Integer.MAX_VALUE, maxX = 0, minY = Integer.MAX_VALUE, maxY = 0;
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            if ((mask & (1 << i)) == 0) continue;
            var type = pads.slot(i).iconType();
            String text = I18n.fmt("Unirse como el jugador {0}", i + 1);
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
