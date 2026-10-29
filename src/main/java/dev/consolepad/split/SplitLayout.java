package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Reparto de la ventana entre los jugadores locales (P1 + extras en orden de slot), segun {@link SplitSettings#orientation()}.
 * Lado a lado: 2 = mitad izquierda / mitad derecha; 3 = P1 en la mitad izquierda, P2 arriba a la derecha y P3 abajo a la derecha.
 * Apilado: 2 = mitad superior / mitad inferior; 3 = P1 en la mitad superior, P2 abajo a la izquierda y P3 abajo a la derecha.
 * Con 4 jugadores son cuadrantes (P1 arriba izquierda, P2 arriba derecha, P3 abajo izquierda, P4 abajo derecha) en ambos casos.
 * Las mismas particiones se calculan en unidades de GUI (origen arriba a la izquierda) y en pixeles (origen abajo a la izquierda).
 * Ademas cada region tiene su propia escala de interfaz (entera, automatica o fija por jugador) y el tamano de su HUD y sus pantallas.
 */
public final class SplitLayout {
    /** Unidades logicas minimas que necesita una pantalla de contenedor para entrar sin recortes (inventario: 176x166). */
    private static final float MIN_SCREEN_W = 200f, MIN_SCREEN_H = 180f;
    /** Lo que exige Minecraft a su escala automatica: la interfaz mide al menos 320x240 unidades. */
    private static final int AUTO_MIN_W = 320, AUTO_MIN_H = 240;
    /** Escalas fijas que se pueden elegir. */
    public static final int MAX_FIXED_SCALE = 4;

    private SplitLayout() {}

    /** Rectangulos {x, yArriba, ancho, alto} de cada jugador (indice 0 = P1) para un area de w x h unidades, con origen arriba a la izquierda. */
    public static int[][] rects(int n, int w, int h) {
        return rects(n, w, h, SplitSettings.orientation());
    }

    public static int[][] rects(int n, int w, int h, SplitSettings.Orientation orientation) {
        int left = w / 2, right = w - left;
        int top = h / 2, bottom = h - top;
        boolean stacked = orientation == SplitSettings.Orientation.STACKED;
        if (BigMap.active(n)) return new int[][] {{0, 0, left, top}, {left, 0, right, top}, {0, top, left, bottom}}; // cuadrantes: el cuarto es del mapa
        return switch (n) {
            case 1 -> new int[][] {{0, 0, w, h}};
            case 2 -> stacked
                    ? new int[][] {{0, 0, w, top}, {0, top, w, bottom}}
                    : new int[][] {{0, 0, left, h}, {left, 0, right, h}};
            case 3 -> stacked
                    ? new int[][] {{0, 0, w, top}, {0, top, left, bottom}, {left, top, right, bottom}}
                    : new int[][] {{0, 0, left, h}, {left, 0, right, top}, {left, top, right, bottom}};
            default -> new int[][] {{0, 0, left, top}, {left, 0, right, top}, {0, top, left, bottom}, {left, top, right, bottom}};
        };
    }

    /**
     * Escala de interfaz de una region: la elegida por el jugador (1 a 4) o, en automatico, la mayor que deja la interfaz en al menos
     * 320x240 unidades (la regla de la escala automatica de Minecraft, aplicada a su region). No depende de la opcion global de
     * interfaz: esa es la de P1 cuando juega solo.
     */
    public static int effectiveScale(int setting, int pxW, int pxH) {
        if (setting >= 1) return Math.min(setting, MAX_FIXED_SCALE);
        int s = 1;
        while (pxW / (s + 1) >= AUTO_MIN_W && pxH / (s + 1) >= AUTO_MIN_H) s++;
        return s;
    }

    /** El cuarto cuadrante {x, y, ancho, alto} en unidades de GUI de la ventana (donde va el mapa grande con 3 jugadores). */
    public static int[] mapRect(int w, int h) {
        return rects(4, w, h, SplitSettings.Orientation.SIDE_BY_SIDE)[3];
    }

    /** Calcula y guarda las regiones de P1 (en SplitView) y de cada contexto extra, para la ventana actual. */
    public static void update(Minecraft mc, List<PlayerContext> extras) {
        int n = 1 + extras.size();
        var win = mc.getWindow();
        int[][] gui = rects(n, win.getGuiScaledWidth(), win.getGuiScaledHeight());
        int[][] pix = rects(n, win.getWidth(), win.getHeight());
        int H = win.getHeight();
        int G = Math.max(1, (int) win.getGuiScale());
        var pads = ConsolePad.PADS;
        SplitView.p1Gui = gui[0];
        SplitView.p1Px = new int[] {pix[0][0], H - (pix[0][1] + pix[0][3]), pix[0][2], pix[0][3]};
        int s1 = effectiveScale(pads == null ? 0 : pads.slot(0).settings.guiScale, pix[0][2], pix[0][3]);
        SplitView.p1GuiScaleEff = s1;
        SplitView.p1HudRatio = (float) s1 / G;
        SplitView.p1HudW = Math.max(1, pix[0][2] / s1);
        SplitView.p1HudH = Math.max(1, pix[0][3] / s1);
        float k1 = Math.min(1f, Math.min(SplitView.p1HudH / MIN_SCREEN_H, SplitView.p1HudW / MIN_SCREEN_W));
        SplitView.p1ScreenScale = SplitView.p1HudRatio * k1;
        SplitView.p1LogicalW = Math.max(1, Math.round(pix[0][2] / (s1 * k1)));
        SplitView.p1LogicalH = Math.max(1, Math.round(pix[0][3] / (s1 * k1)));
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
            int s = effectiveScale(pads == null ? 0 : pads.slot(c.slotIndex).settings.guiScale, c.pw, c.ph);
            c.guiScaleEff = s;
            c.hudRatio = (float) s / G;
            c.hudW = Math.max(1, c.pw / s);
            c.hudH = Math.max(1, c.ph / s);
            float k = Math.min(1f, Math.min(c.hudH / MIN_SCREEN_H, c.hudW / MIN_SCREEN_W));
            c.screenScale = c.hudRatio * k;
            c.logicalW = Math.max(1, Math.round(c.pw / (s * k)));
            c.logicalH = Math.max(1, Math.round(c.ph / (s * k)));
        }
    }

    /** Tamano logico (unidades antes de aplicar la escala) de las pantallas de un jugador extra. */
    public static int logicalWidth(Minecraft mc, PlayerContext c) {
        if (c.logicalW <= 0) return mc.getWindow().getGuiScaledWidth() / 2; // sin region todavia: media ventana
        return c.logicalW;
    }

    public static int logicalHeight(Minecraft mc, PlayerContext c) {
        if (c.logicalH <= 0) return mc.getWindow().getGuiScaledHeight();
        return c.logicalH;
    }
}
