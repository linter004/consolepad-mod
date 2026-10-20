package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Mando desconectado: el jugador de ese mando queda en pausa (su region muestra {@link PadLostScreen}, no se mueve y el servidor lo hace invulnerable
 * mientras tanto) hasta que el mando vuelva; entonces el aviso se cierra solo y se restaura lo que tuviera. Un mando que vuelve recupera su jugador
 * ({@link PadManager}). P1 puede descartar el aviso para seguir con teclado y raton (no reaparece hasta la siguiente desconexion). No se abre sobre
 * una pantalla de muerte (no hay nada que pausar).
 */
public final class PadLost {
    private static final boolean[] dismissed = new boolean[4];
    private static final boolean[] wasShowing = new boolean[4];
    private static final boolean[] protectedNow = new boolean[4];
    /** El servidor ya tiene el estado de protectedNow para ese slot (al entrar se fuerza una vez: limpia una invulnerabilidad guardada en el mundo). */
    private static final boolean[] synced = new boolean[4];

    /** Desde cuando (nanoTime) lleva cada jugador extra sin mando; 0 = con mando. */
    private static final long[] lostSince = new long[4];
    /** Pruebas: segundos de espera en lugar de los de split.json (-1 = los de split.json). */
    public static volatile double leaveSecondsOverride = -1;

    private PadLost() {}

    /** Segundos de espera antes de sacar a un jugador extra sin mando (0 = nunca). En el autotest solo si una prueba lo pide. */
    private static double leaveSeconds() {
        if (leaveSecondsOverride >= 0) return leaveSecondsOverride;
        return AutoTest.running() ? 0 : SplitSettings.padLostLeaveSeconds();
    }

    /** Segundos que le quedan a un jugador extra sin mando antes de salir de la partida (-1 si no cuenta atras). */
    public static int secondsLeft(int slot) {
        double total = leaveSeconds();
        if (slot < 1 || slot > 3 || total <= 0 || lostSince[slot] == 0) return -1;
        return (int) Math.max(0, Math.ceil(total - (System.nanoTime() - lostSince[slot]) / 1e9));
    }

    private static Screen screenOf(Minecraft mc, int slot) {
        if (slot == 0) return mc.screen;
        PlayerContext c = SecondaryPlayers.context(slot);
        return c == null ? null : c.screen;
    }

    /** Una vez por fotograma, fuera de cualquier contexto extra. */
    public static void frame(Minecraft mc) {
        if (ConsolePad.PADS == null || ContextSwitcher.isSecondaryActive()) return;
        if (mc.player == null || mc.level == null) {
            java.util.Arrays.fill(dismissed, false);
            java.util.Arrays.fill(wasShowing, false);
            java.util.Arrays.fill(protectedNow, false);
            java.util.Arrays.fill(synced, false);
            java.util.Arrays.fill(lostSince, 0L);
            return;
        }
        for (int slot = 0; slot < PadManager.MAX_PLAYERS; slot++) {
            PadManager.Slot s = ConsolePad.PADS.slot(slot);
            PlayerContext ctx = slot == 0 ? null : SecondaryPlayers.context(slot);
            boolean in = slot == 0 || (ctx != null && ctx.player != null && SecondaryPlayers.isActive(slot));
            Screen screen = in ? screenOf(mc, slot) : null;
            boolean showing = screen instanceof PadLostScreen;
            if (!in) { // fuera de la partida no hay nada que pausar ni recordar
                s.lost = false;
                dismissed[slot] = false;
                wasShowing[slot] = false;
                protectedNow[slot] = false;
                synced[slot] = false;
                lostSince[slot] = 0;
                continue;
            }
            if (wasShowing[slot] && !showing && s.lost && s.device == null) dismissed[slot] = true; // P1 lo cerro con Esc o con el boton
            wasShowing[slot] = showing;
            if (s.device == null && s.lost) {
                if (!dismissed[slot] && !showing && !(screen instanceof net.minecraft.client.gui.screens.DeathScreen) && !(screen instanceof ExtraDeathScreen)) {
                    open(mc, slot, ctx);
                    wasShowing[slot] = true;
                }
                protect(mc, slot, ctx, !dismissed[slot]);
                if (slot >= 1 && leaveSeconds() > 0) { // un jugador extra sin mando mucho rato sale de la partida (su inventario se guarda)
                    if (lostSince[slot] == 0) lostSince[slot] = System.nanoTime();
                    else if ((System.nanoTime() - lostSince[slot]) / 1e9 >= leaveSeconds()) {
                        lostSince[slot] = 0;
                        mc.player.displayClientMessage(I18n.lit(I18n.fmt("El jugador {0} salió de la partida: su mando lleva {1} s desconectado.", slot + 1, (int) leaveSeconds())), false);
                        SecondaryPlayers.leave(mc, slot);
                    }
                }
            } else {
                lostSince[slot] = 0;
                if (showing) close(mc, slot, ctx);
                dismissed[slot] = false;
                wasShowing[slot] = false;
                protect(mc, slot, ctx, false);
            }
        }
    }

    private static void open(Minecraft mc, int slot, PlayerContext ctx) {
        if (slot == 0) mc.setScreen(new PadLostScreen(0));
        else ContextSwitcher.run(ctx, () -> mc.setScreen(new PadLostScreen(slot)));
    }

    private static void close(Minecraft mc, int slot, PlayerContext ctx) {
        if (slot == 0) mc.setScreen(null);
        else ContextSwitcher.run(ctx, () -> mc.setScreen(null));
    }

    /** Invulnerable en el servidor mientras el aviso este puesto; al volver (o al entrar en la partida) deja de serlo. */
    private static void protect(Minecraft mc, int slot, PlayerContext ctx, boolean on) {
        if (synced[slot] && on == protectedNow[slot]) return;
        var server = mc.getSingleplayerServer();
        java.util.UUID id = slot == 0 ? mc.player.getUUID() : ctx != null && ctx.player != null ? ctx.player.getUUID() : null;
        if (server == null || id == null) return;
        protectedNow[slot] = on;
        synced[slot] = true;
        server.execute(() -> {
            var sp = server.getPlayerList().getPlayer(id);
            if (sp == null) return;
            // Un jugador solo es invulnerable (marca de entidad) por este aviso: /data no puede cambiarla en jugadores. Si el juego se cerro con el
            // aviso puesto, la marca quedo guardada en el mundo; por eso al quitar la proteccion se pone a false y no al valor anterior.
            sp.setInvulnerable(on);
        });
    }
}
