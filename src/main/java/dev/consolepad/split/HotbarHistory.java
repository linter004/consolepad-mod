package dev.consolepad.split;

/**
 * Casilla anterior: pulsar LB y RB a la vez salta a la casilla que se usaba antes de la actual (y otra vez, vuelve), como "Q" de otros juegos. LB y RB solos
 * siguen cambiando de casilla de una en una. Un jugador no pulsa dos botones en el mismo fotograma, asi que si el segundo llega poco despues del primero
 * (400 ms) se deshace el cambio del primero y se va a la casilla anterior de verdad. La "casilla en uso" es la que lleva 400 ms sin cambiar; la anterior,
 * la que tenia antes. Una por jugador; el tiempo es el reloj de pared (milisegundos) para valer igual en fotogramas (P1) y en ticks (los demas).
 */
public final class HotbarHistory {
    /** El resultado de {@link #press}: sin acordes (el llamador cambia de casilla como siempre) o pulsacion repetida del mismo acorde (no hacer nada). */
    public static final int NORMAL = -1, IGNORE = -2;
    private static final long IDLE_MS = 400, DEDUPE_MS = 150;

    private int idleSel = -1, prevIdle = -1, burstStart = -1;
    private long lastPressMs = -100000, lastChordMs = -100000;

    /** Una vez por fotograma o tick, con la casilla seleccionada ahora. */
    public void tick(int selected, long nowMs) {
        if (nowMs - lastPressMs < IDLE_MS) return;
        if (idleSel < 0) {
            idleSel = selected;
        } else if (selected != idleSel) {
            prevIdle = idleSel;
            idleSel = selected;
        }
    }

    /**
     * Se pulso LB o RB; otherDown dice si el otro ya estaba pulsado. Devuelve NORMAL (cambiar de casilla como siempre), IGNORE (el mismo acorde, ya
     * resuelto) o la casilla a la que ir.
     */
    public int press(int selected, boolean otherDown, long nowMs) {
        if (nowMs - lastChordMs < DEDUPE_MS) return IGNORE;
        if (otherDown) {
            int start = nowMs - lastPressMs < IDLE_MS && burstStart >= 0 ? burstStart : selected; // la casilla de antes de que el primer boton la cambiara
            int target = prevIdle >= 0 && prevIdle != start ? prevIdle : start;
            lastChordMs = nowMs;
            lastPressMs = nowMs;
            burstStart = start;
            return target;
        }
        if (nowMs - lastPressMs >= IDLE_MS) burstStart = selected;
        lastPressMs = nowMs;
        return NORMAL;
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        HotbarHistory h = new HotbarHistory();
        h.tick(0, 0);
        if (h.press(0, false, 1000) != NORMAL) return "un boton solo debe cambiar de casilla como siempre";
        h.tick(1, 1500); // en uso: 1, antes: 0
        if (h.press(1, false, 2000) != NORMAL) return "el segundo cambio tambien es normal";
        h.tick(2, 2500); // en uso: 2, antes: 1
        int t1 = h.press(2, true, 3000);
        if (t1 != 1) return "el acorde debe ir a la casilla anterior (1): " + t1;
        if (h.press(2, true, 3000) != IGNORE) return "el mismo acorde dos veces en el mismo instante cuenta una sola vez";
        h.tick(1, 3500); // en uso: 1, antes: 2
        int t2 = h.press(1, true, 4000);
        if (t2 != 2) return "otro acorde debe volver a la anterior (2): " + t2;
        h.tick(2, 4500);
        // Un boton primero y el otro 50 ms despues (con la casilla ya cambiada por el primero): se deshace y se va a la anterior de verdad.
        if (h.press(2, false, 5000) != NORMAL) return "primer boton del acorde";
        int t3 = h.press(1, true, 5050); // el primero ya movio de 2 a 1
        if (t3 != 1) return "con el primer boton ya pulsado debe ir a la anterior (1): " + t3;
        HotbarHistory fresh = new HotbarHistory();
        fresh.tick(3, 0);
        int t4 = fresh.press(3, true, 1000);
        if (t4 != 3) return "sin casilla anterior conocida no cambia nada: " + t4;
        return null;
    }
}
