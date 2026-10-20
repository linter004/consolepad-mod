package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * El punto del companero se ve ENCIMA de la barra de experiencia (casilla 49, con P2 dentro): se lee el color del fotograma en el centro del punto de P1 y de P2 y
 * tiene que ser el del companero, no el verde o gris de la barra. (El HUD de P1 lo dibujan las capas de NeoForge, a mas altura que la de los jugadores extra.)
 */
public final class CompanionPixelTest {
    private static int t;
    private static final int[] seen = {-1, -1};
    private static final int[][] px = new int[2][];
    private static int frameSeen = -1;
    private static int[] framePx;
    private static double prevMin;

    private CompanionPixelTest() {}

    private static boolean near(int rgb, int want) {
        return Math.abs(((rgb >> 16) & 255) - ((want >> 16) & 255)) < 28 && Math.abs(((rgb >> 8) & 255) - ((want >> 8) & 255)) < 28 && Math.abs((rgb & 255) - (want & 255)) < 28;
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null) return true;
        if (t == 1) {
            mc.setScreen(null);
            SecondaryTick.scriptActive = false;
            prevMin = Companions.minDistanceOverride;
            Companions.minDistanceOverride = 0; // que el punto salga aunque esten juntos
            LowHealthFlash.resetForTest();
        }
        if (t >= 1 && t <= 14 && t % 3 == 1) { // P2 con poca vida (en pacifico se regenera enseguida): el destello sale en su region
            var srv = mc.getSingleplayerServer();
            ServerPlayer low = srv.getPlayerList().getPlayer(c.player.getUUID());
            srv.execute(() -> {
                low.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                low.setHealth(4f);
            });
        }
        if (t == 12) {
            // El marco rojo de la casilla seleccionada de P2 (esquina superior izquierda de la barra, dentro de los 14 pixeles del borde donde esta el destello).
            double k = Minecraft.getInstance().getWindow().getGuiScale();
            double hx = c.safeW / 2.0 - 91 - 1 + 20 * c.player.getInventory().selected + 4, hy = c.safeH - 22 - 1 + 1; // pixel del borde superior del marco de la casilla 1, en unidades de su HUD
            framePx = new int[] {(int) Math.round((c.gx + hx * c.hudRatio) * k), (int) Math.round((c.gy + hy * c.hudRatio) * k)};
            AutoTest.probe(img -> {
                int fa = img.getPixelRGBA(framePx[0], framePx[1]);
                frameSeen = ((fa & 255) << 16) | (fa & 0xFF00) | ((fa >> 16) & 255);
                for (int s = 0; s < 2; s++) {
                    px[s] = Companions.lastDotPx[s];
                    seen[s] = px[s] == null ? -1 : img.getPixelRGBA(px[s][0], px[s][1]);
                    if (seen[s] != -1) { // NativeImage.getPixelRGBA es ABGR
                        int abgr = seen[s];
                        seen[s] = ((abgr & 255) << 16) | (abgr & 0xFF00) | ((abgr >> 16) & 255);
                    }
                }
            });
        }
        if (t == 20) {
            for (int s = 0; s < 2; s++) {
                int want = PlayerLabels.themeRgb(1 - s);
                boolean ok = seen[s] != -1 && (near(seen[s], want) || near(seen[s], 0xFF3030));
                check("punto del companero encima de la barra de experiencia: el centro del punto de P" + (s + 1) + " es del color de P" + (2 - s) + " (" + Integer.toHexString(seen[s] == -1 ? 0 : seen[s])
                        + ", esperado " + Integer.toHexString(want) + ", en " + (px[s] == null ? "-" : px[s][0] + "," + px[s][1]) + ")", ok);
            }
            int fr = (frameSeen >> 16) & 255, fg = (frameSeen >> 8) & 255, fb = frameSeen & 255;
            check("destello con poca vida: el HUD de P2 sigue encima (el marco rojo de su casilla se ve en " + framePx[0] + "," + framePx[1] + ": " + Integer.toHexString(frameSeen) + ", destello " + LowHealthFlash.lastAlpha[1] + ")",
                    LowHealthFlash.lastAlpha[1] > 0f && fr > fg + 60 && fr > fb + 60);
            Companions.minDistanceOverride = prevMin;
            var srv = mc.getSingleplayerServer();
            ServerPlayer back = srv.getPlayerList().getPlayer(c.player.getUUID());
            srv.execute(() -> back.setHealth(20f));
            t = 0;
            return true;
        }
        return false;
    }

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }
}
