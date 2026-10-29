package dev.consolepad.split;

import net.minecraft.client.Minecraft;

/**
 * Mapa grande del cuarto cuadrante (dentro de la casilla 49): con 3 jugadores y el ajuste activo el reparto es de cuadrantes, el mapa se dibuja con terreno
 * descubierto y los jugadores que abarca, y al apagarlo vuelve el reparto de siempre. Termina con P2 dentro y el ajuste como estaba.
 */
public final class BigMapTest {
    private static int t, phase;
    private static boolean old;

    private BigMapTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        switch (phase) {
            case 0 -> {
                if (t == 1) old = SplitSettings.mapQuadrant();
                if (!AutoTest.joinTo(mc, 3)) return false;
                SplitSettings.setMapQuadrant(true);
                BigMap.drawn = 0;
                t = 0;
                phase = 1;
            }
            case 1 -> {
                if (t < 90) return false;
                var win = mc.getWindow();
                int w = win.getGuiScaledWidth(), h = win.getGuiScaledHeight();
                int[][] r = SplitLayout.rects(3, w, h);
                int[] m = SplitLayout.mapRect(w, h);
                boolean quadrants = r[0][2] <= w / 2 + 1 && r[0][3] <= h / 2 + 1 && r[1][3] <= h / 2 + 1 && r[2][3] <= h / 2 + 1;
                check("mapa grande: con 3 jugadores y el ajuste el reparto es de cuadrantes y el cuarto queda para el mapa (P1 " + r[0][2] + "x" + r[0][3] + ", mapa " + m[2] + "x" + m[3] + " en " + m[0] + "," + m[1] + ")",
                        quadrants && m[0] >= w / 2 - 1 && m[1] >= h / 2 - 1);
                check("mapa grande: el mapa se dibuja con terreno y los jugadores que abarca (fotogramas " + BigMap.drawn + ", casillas descubiertas " + BigMap.lastExplored + ", jugadores dentro " + BigMap.lastPlayers + ")",
                        BigMap.drawn > 10 && BigMap.lastExplored > 20 && BigMap.lastPlayers >= 2);
                SplitSettings.setMapQuadrant(false);
                int[][] back = SplitLayout.rects(3, w, h);
                check("mapa grande: apagado vuelve el reparto de 3 jugadores de siempre (P1 " + back[0][2] + "x" + back[0][3] + ")", back[0][3] == h || back[0][2] == w); // lado a lado: P1 a toda la altura; apilado: a todo el ancho
                SplitSettings.setMapQuadrant(old);
                t = 0;
                phase = 2;
            }
            default -> {
                if (!AutoTest.leaveTo(mc, 2)) return false;
                t = 0;
                phase = 0;
                return true;
            }
        }
        return false;
    }
}
