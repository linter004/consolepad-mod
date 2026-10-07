package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * Ayuda de pruebas, solo si se arranca con la variable de entorno CONSOLEPAD_AUTOTEST=<nombre del mundo>: abre ese mundo, mete a P2 y deja
 * en el log el estado cada 30 s durante 5 minutos. Sin la propiedad no hace nada.
 */
public final class AutoTest {
    private static final String WORLD = System.getenv("CONSOLEPAD_AUTOTEST");
    private static final int REPORT_EVERY = 20 * 30;
    private static final int TOTAL = 20 * 60 * 5;

    private static int state;
    private static int ticks;
    private static long lastRuns;

    private AutoTest() {}

    public static void tick() {
        if (WORLD == null) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        switch (state) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen && ticks > 60) {
                    ConsolePad.LOGGER.info("[autotest] abriendo el mundo '{}'", WORLD);
                    state = 1;
                    ticks = 0;
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> ConsolePad.LOGGER.error("[autotest] no se pudo abrir el mundo"));
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null && ticks > 200) {
                    ConsolePad.LOGGER.info("[autotest] mundo cargado; metiendo a P2");
                    SecondaryPlayers.join(mc);
                    state = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (ticks % REPORT_EVERY == 0 || ticks == TOTAL) {
                    PlayerContext c = SecondaryPlayers.context();
                    ConsolePad.LOGGER.info("[autotest] +{} s: P2 conectado={} nivel={} jugador={} gameMode={} paquetes={} fallos={}; P1 player={} level={}",
                            ticks / 20, SecondaryPlayers.isActive(),
                            c != null && c.level != null, c != null && c.player != null, c != null && c.gameMode != null,
                            ContextSwitcher.runs() - lastRuns, ContextSwitcher.violations(),
                            mc.player == null ? "null" : mc.player.getName().getString(),
                            mc.level != null && (c == null || mc.level != c.level));
                }
                if (ticks == TOTAL) {
                    ConsolePad.LOGGER.info("[autotest] 5 minutos completados");
                    state = 3;
                }
            }
            default -> { }
        }
    }
}
