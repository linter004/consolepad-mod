package dev.consolepad.split;

import net.minecraft.client.Minecraft;

/**
 * Sacar a un jugador desde P1 (casilla 49, con P2 dentro): el menu de pausa de P1 tiene "Sacar a un jugador" cuando hay extras dentro, la lista tiene una
 * fila por cada uno y pulsarla lo saca de la partida; despues la prueba vuelve a meter a P2.
 */
public final class KickTest {
    private static int t;
    private static PlayerPauseScreen pause;
    private static boolean kicked;

    private KickTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        if (t == 1) {
            if (!SecondaryPlayers.isActive(1)) return true;
            mc.setScreen(null);
            pause = new PlayerPauseScreen(0);
            mc.setScreen(pause);
        }
        if (t == 8) {
            check("sacar jugadores: el menu de pausa de P1 con P2 dentro tiene la fila «Sacar a un jugador»", pause.kickButton() != null && pause.kickButton().visible);
            if (pause.kickButton() != null) pause.kickButton().onPress();
        }
        if (t == 14) {
            int extras = 0;
            for (int s = 1; s <= SecondaryPlayers.MAX_EXTRA; s++) if (SecondaryPlayers.isActive(s)) extras++;
            if (mc.screen instanceof KickPlayerScreen k) {
                check("sacar jugadores: la lista tiene una fila por cada jugador extra dentro (" + k.rowCount() + " de " + extras + ")", k.rowCount() == extras);
                if (k.firstKick() != null) k.firstKick().onPress();
            } else {
                check("sacar jugadores: se abre la lista (pantalla " + (mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName()) + ")", false);
            }
        }
        if (t == 60) {
            kicked = !SecondaryPlayers.isActive(1);
            check("sacar jugadores: pulsar la fila saca a P2 de la partida y se vuelve al menu de pausa (P2 dentro: " + SecondaryPlayers.isActive(1) + ", pantalla " + (mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName()) + ")",
                    kicked && mc.screen instanceof PlayerPauseScreen);
            mc.setScreen(null);
        }
        if (t > 60) { // vuelve a meter a P2 para el resto de pruebas
            if (AutoTest.joinTo(mc, 2)) {
                t = 0;
                return true;
            }
        }
        return false;
    }
}
