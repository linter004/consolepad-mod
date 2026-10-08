package dev.consolepad.split;

import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Reparto de la ventana entre los jugadores locales (P1 + extras en orden de slot).
 * 2: mitad izquierda / mitad derecha. 3: P1 en la mitad izquierda, P2 arriba a la derecha y P3 abajo a la derecha.
 * 4: cuadrantes (P1 arriba izquierda, P2 arriba derecha, P3 abajo izquierda, P4 abajo derecha).
 * Las mismas particiones se calculan en unidades de GUI (origen arriba a la izquierda) y en pixeles (origen abajo a la izquierda).
 */
public final class SplitLayout {
    /** Unidades logicas minimas que necesita una pantalla de contenedor para entrar sin recortes (inventario: 176x166). */
    private static final float MIN_SCREEN_W = 200f, MIN_SCREEN_H = 180f;

    private SplitLayout() {}

    /** Rectangulos {x, yArriba, ancho, alto} de cada jugador (indice 0 = P1) para un area de w x h unidades, con origen arriba a la izquierda. */
    public static int[][] rects(int n, int w, int h) {
        int left = w / 2, right = w - left;
        int top = h / 2, bottom = h - top;
        return switch (n) {
            case 1 -> new int[][] {{0, 0, w, h}};
            case 2 -> new int[][] {{0, 0, left, h}, {left, 0, right, h}};
            case 3 -> new int[][] {{0, 0, left, h}, {left, 0, right, top}, {left, top, right, bottom}};
            default -> new int[][] {{0, 0, left, top}, {left, 0, right, top}, {0, top, left, bottom}, {left, top, right, bottom}};
        };
    }

    /** Calcula y guarda las regiones de P1 (en SplitView) y de cada contexto extra, para la ventana actual. */
    public static void update(Minecraft mc, List<PlayerContext> extras) {
        int n = 1 + extras.size();
        var win = mc.getWindow();
        int[][] gui = rects(n, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        int[][] pix = rects(n, win.getWidth(), win.getHeight());
        int H = win.getHeight();
        SplitView.p1Gui = gui[0];
        SplitView.p1Px = new int[] {pix[0][0], H - (pix[0][1] + pix[0][3]), pix[0][2], pix[0][3]};
        SplitView.p1ScreenScale = Math.min(1f, Math.min(gui[0][3] / MIN_SCREEN_H, gui[0][2] / MIN_SCREEN_W));
        for (int i = 0; i < extras.size(); i++) {
            PlayerContext c = extras.get(i);
            c.gx = gui[i + 1][0];
            c.gy = gui[i + 1][1];
            c.gw = gui[i + 1][2];
            c.gh = gui[i + 1][3];
            c.px = pix[i + 1][0];
            c.py = H - (pix[i + 1][1] + pix[i + 1][3]);
            c.pw = pix[i + 1][2];
            c.ph = pix[i + 1][3];
            c.screenScale = Math.min(1f, Math.min(c.gh / MIN_SCREEN_H, c.gw / MIN_SCREEN_W));
        }
    }

    /** Tamano logico (unidades de GUI antes de aplicar la escala) de las pantallas de un jugador extra. */
    public static int logicalWidth(Minecraft mc, PlayerContext c) {
        if (c.gw <= 0) return mc.getWindow().getGuiScaledWidth() / 2; // sin region todavia: media ventana
        return Math.round(c.gw / c.screenScale);
    }

    public static int logicalHeight(Minecraft mc, PlayerContext c) {
        if (c.gh <= 0) return mc.getWindow().getGuiScaledHeight();
        return Math.round(c.gh / c.screenScale);
    }
}
