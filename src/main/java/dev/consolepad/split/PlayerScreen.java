package dev.consolepad.split;

/**
 * Pantallas propias del mod (menu de pausa, ajustes, controles, muerte de un jugador extra). Los jugadores extra solo admiten estas y las
 * de contenedor. Algunas necesitan leer el siguiente boton del mando en vez de dejarlo hacer clic o cerrar (remapeo).
 */
public interface PlayerScreen {
    /** True mientras la pantalla espera un boton del mando: el boton pulsado va a {@link #padButton} y no hace nada mas. */
    default boolean listeningForPad() {
        return false;
    }

    /** Boton del mando (codigo de PadState) que se acaba de pulsar mientras se escuchaba. */
    default void padButton(int code) {}
}
