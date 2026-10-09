package dev.consolepad.pad;

/** Mantener o alternar: el estado de una accion (agacharse, correr, atacar...) segun el modo del jugador. */
public final class HoldToggle {
    private HoldToggle() {}

    /**
     * Estado nuevo de la accion. {@code on}: estado actual; {@code pressed}: el boton acaba de pulsarse; {@code down}: el boton esta pulsado.
     * Alternar: cada pulsacion invierte el estado y soltar no hace nada. Mantener: el estado es lo que diga el boton.
     */
    public static boolean next(boolean toggle, boolean on, boolean pressed, boolean down) {
        return toggle ? (pressed != on) : down;
    }
}
