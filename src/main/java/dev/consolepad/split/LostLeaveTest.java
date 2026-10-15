package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;

/**
 * Dentro de la casilla 49, con P2 dentro: un jugador extra cuyo mando se desconecta cuenta atras y sale de la partida solo (aqui con 2 s en lugar de 30);
 * volver a pedir jugadores (WorldEntry.requestPlayers, lo que hace "cuantos jugadores sois") lo mete otra vez; la pantalla de cantidad respeta los mandos
 * conectados; y el reparto de pantalla se recuerda por mundo.
 */
public final class LostLeaveTest {
    private static int t, phase;
    private static int left0;

    private LostLeaveTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Una vuelta por tick; true al terminar. */
    public static boolean run(Minecraft mc) {
        t++;
        if (t > 1500) {
            check("mando perdido: la prueba se quedo esperando en la fase " + phase, false);
            PadLost.leaveSecondsOverride = -1;
            ConsolePad.PADS.simulateReconnect(1);
            phase = 0;
            t = 0;
            return true;
        }
        switch (phase) {
            case 0 -> {
                if (t == 1) {
                    PadLost.leaveSecondsOverride = 2;
                    left0 = 0;
                    check("mando perdido: P2 esta dentro y con mando antes de desconectarlo", SecondaryPlayers.isActive(1) && ConsolePad.PADS.slot(1).device != null);
                    ConsolePad.PADS.simulateDisconnect(1);
                }
                if (t == 15) {
                    int left = PadLost.secondsLeft(1);
                    check("mando perdido: la pantalla cuenta atras (quedan " + left + " s de 2)", left >= 0 && left <= 2 && SecondaryPlayers.isActive(1));
                }
                if (!SecondaryPlayers.isActive(1) && t > 5) {
                    check("mando perdido: P2 sale solo tras la espera (tick " + t + ", entre 30 y 600)", t >= 30 && t < 600);
                    PadLost.leaveSecondsOverride = -1;
                    ConsolePad.PADS.simulateReconnect(1);
                    phase = 1;
                    t = 0;
                }
            }
            case 1 -> { // pedir 2 jugadores lo mete otra vez
                if (t == 40) WorldEntry.requestPlayers(2);
                if (t > 40) {
                    WorldEntry.tick(mc);
                    PlayerContext c = SecondaryPlayers.context(1);
                    if (SecondaryPlayers.isActive(1) && c != null && c.player != null && c.level != null && t > 100) {
                        check("jugadores al entrar: pedir 2 jugadores mete a P2 (pendientes " + WorldEntry.pendingPlayers() + ")", WorldEntry.pendingPlayers() == 0 || WorldEntry.pendingPlayers() == 2);
                        phase = 2;
                        t = 0;
                    }
                }
            }
            case 2 -> {
                var scr = new PlayerCountScreen(null);
                scr.init(mc, 427, 240);
                int pads = WorldEntry.connectedPlayers();
                check("jugadores al entrar: la pantalla ofrece hasta los mandos conectados (" + pads + "): 4 jugadores " + (scr.count(4).active ? "si" : "no") + ", 1 jugador si",
                        scr.count(1).active && scr.count(4).active == (pads >= 4) && scr.count(2).active == (pads >= 2));
                scr.choose(1);
                check("jugadores al entrar: elegir 1 no pide jugadores extra (pendientes " + WorldEntry.pendingPlayers() + ")", WorldEntry.pendingPlayers() == 1);
                WorldEntry.requestPlayers(0);
                mc.setScreen(null);
                String w = "zz_autotest_mundo";
                SplitSettings.rememberWorldOrientation(w, SplitSettings.Orientation.STACKED);
                var back = SplitSettings.worldOrientation(w);
                check("reparto por mundo: se recuerda el del mundo (" + back + ") y uno desconocido no tiene (" + SplitSettings.worldOrientation("zz_otro") + ")",
                        back == SplitSettings.Orientation.STACKED && SplitSettings.worldOrientation("zz_otro") == null);
                SplitSettings.forgetWorldOrientation(w);
                ConsolePad.LOGGER.info("[autotest] RESULTADO mando perdido, jugadores al entrar y reparto por mundo -> fin");
                phase = 0;
                t = 0;
                return true;
            }
            default -> { }
        }
        return false;
    }
}
