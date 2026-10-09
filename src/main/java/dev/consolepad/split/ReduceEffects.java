package dev.consolepad.split;

import dev.consolepad.ConsolePad;

/** Accesibilidad: menos efectos de pantalla por jugador (vineta, balanceo y sacudida de la camara, FOV al correr). Contadores de lo omitido para las pruebas. */
public final class ReduceEffects {
    public static final int[] skippedVignette = new int[4], skippedBob = new int[4], skippedFov = new int[4];

    private ReduceEffects() {}

    /** Slot del jugador cuyo contexto esta activo (P1 si no hay contexto extra). */
    public static int contextSlot() {
        PlayerContext c = ContextSwitcher.current();
        return c == null ? 0 : c.slotIndex;
    }

    public static boolean contextOn() {
        return ConsolePad.PADS != null && ConsolePad.PADS.slot(contextSlot()).settings.reduceEffects;
    }

    /** Lo mismo para la pasada de dibujo del mundo (SplitRender.passSlot). */
    public static boolean passOn() {
        int s = SplitRender.passSlot;
        return ConsolePad.PADS != null && s >= 0 && s < 4 && ConsolePad.PADS.slot(s).settings.reduceEffects;
    }
}
