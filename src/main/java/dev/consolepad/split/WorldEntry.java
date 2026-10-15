package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;

/**
 * Lo que pasa al entrar en un mundo de un jugador: se recupera el reparto de pantalla guardado para ese mundo y, si hay varios mandos conectados, se pregunta
 * cuantos jugadores son ({@link PlayerCountScreen}) y se mete a todos a la vez, uno detras de otro, sin pulsar Start en cada mando. Despues, mientras queden
 * jugadores por meter, los va conectando cuando el anterior ya esta listo. Nunca durante el autotest ni la generacion de capturas.
 */
public final class WorldEntry {
    private static net.minecraft.client.multiplayer.ClientLevel lastLevel;
    private static int ticksIn;
    private static int pendingPlayers; // jugadores en total que faltan por tener dentro (incluido P1)
    private static int joinGap;

    private WorldEntry() {}

    /** Identificador del mundo local (nombre del nivel) o null. */
    public static String worldId(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        return server == null ? null : server.getWorldData().getLevelName();
    }

    /** Jugadores con mando conectado (el jugador 1 cuenta aunque use teclado). */
    public static int connectedPlayers() {
        int n = 0;
        for (int i = 0; i < dev.consolepad.pad.PadManager.MAX_PLAYERS; i++) if (ConsolePad.PADS.slot(i).device != null) n++;
        return Math.max(1, n);
    }

    /** Pide meter a n jugadores en total (P1 ya esta). */
    public static void requestPlayers(int n) {
        pendingPlayers = Math.max(1, Math.min(dev.consolepad.pad.PadManager.MAX_PLAYERS, n));
        joinGap = 0;
    }

    public static int pendingPlayers() {
        return pendingPlayers;
    }

    /** Una vez por tick de cliente. */
    public static void tick(Minecraft mc) {
        if (mc.level == null || mc.player == null) {
            lastLevel = null;
            ticksIn = 0;
            pendingPlayers = 0;
            return;
        }
        if (mc.level != lastLevel) {
            lastLevel = mc.level;
            ticksIn = 0;
            pendingPlayers = 0;
        }
        ticksIn++;
        boolean quiet = AutoTest.running() || ShotsTest.ENABLED || mc.getSingleplayerServer() == null; // sin preguntas ni reparto guardado (las pruebas piden jugadores a mano)
        if (mc.getSingleplayerServer() == null) return;
        if (ticksIn == 5 && !quiet) {
            SplitSettings.Orientation saved = SplitSettings.worldOrientation(worldId(mc));
            if (saved != null && saved != SplitSettings.orientation()) SplitSettings.setOrientation(saved);
        }
        if (ticksIn == 80 && !quiet && mc.screen == null && SplitSettings.askPlayers() && connectedPlayers() >= 2 && !SecondaryPlayers.anyActive()) {
            mc.setScreen(new PlayerCountScreen(mc.screen));
        }
        // Meter a los jugadores pedidos, de uno en uno
        if (pendingPlayers > 1) {
            if (joinGap > 0) {
                joinGap--;
                return;
            }
            for (int slot = 1; slot < pendingPlayers; slot++) {
                if (!SecondaryPlayers.isActive(slot)) {
                    SecondaryPlayers.join(mc, slot);
                    joinGap = 30;
                    return;
                }
                PlayerContext c = SecondaryPlayers.context(slot);
                if (c == null || c.player == null || c.level == null) return; // el anterior aun esta entrando
            }
            pendingPlayers = 0;
        }
    }
}
