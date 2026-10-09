package dev.consolepad.split;

import dev.consolepad.ConsolePad;

/**
 * Cuanto sube el chat de vanilla de P1 (campo de texto, mensajes y sugerencias) con un mando: lo mismo que ExtraChatScreen en P2 a P4, es decir, por encima
 * del teclado en pantalla y de la barra de ayuda. Sin mando no sube (no hay barra ni teclado).
 */
public final class ChatLift {
    /** Ultimo rectangulo {x, y, ancho, alto} del campo de texto de P1 dibujado, en unidades de su pantalla (para el autotest). */
    public static volatile int[] lastInput = {0, 0, 0, 0};

    private ChatLift() {
    }

    public static int lift() {
        if (ConsolePad.PADS == null || ConsolePad.PADS.slot(0).device == null) return 0;
        return PadIcons.H + 3 + PadKeyboard.HEIGHT + 4;
    }
}
