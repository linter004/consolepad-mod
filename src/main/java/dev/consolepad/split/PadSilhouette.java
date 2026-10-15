package dev.consolepad.split;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Siluetas de mando dibujadas por codigo (elipses y rectangulos en coordenadas normalizadas, pintados fila a fila con fill): ningun recurso grafico
 * de terceros. Un mando es la union de sus piezas; el contorno sale de pintar la union engordada un pixel debajo del relleno.
 */
public final class PadSilhouette {
    private static final int OUTLINE = 0xFF9A9A9A, BODY = 0xFF282828, DETAIL = 0xFF171717, BUMPER = 0xFF6A6A6A;

    /** Pieza: 0 = elipse (a, b = centro; c, d = radios), 1 = rectangulo (a, b = esquina; c, d = esquina opuesta); todo entre 0 y 1. */
    private record Piece(int type, double a, double b, double c, double d) {}

    private PadSilhouette() {}

    private static Piece ell(double cx, double cy, double rx, double ry) {
        return new Piece(0, cx, cy, rx, ry);
    }

    private static Piece rect(double x0, double y0, double x1, double y1) {
        return new Piece(1, x0, y0, x1, y1);
    }

    private static List<Piece> body(ControlsLayout.Kind kind) {
        List<Piece> p = new ArrayList<>();
        switch (kind) {
            case PLAYSTATION -> {
                p.add(ell(.5, .34, .40, .28));
                p.add(rect(.16, .10, .84, .50));
                p.add(ell(.20, .70, .12, .30));
                p.add(ell(.80, .70, .12, .30));
            }
            case SWITCH_PRO -> {
                p.add(ell(.5, .38, .38, .32));
                p.add(rect(.14, .10, .86, .62));
                p.add(ell(.19, .72, .13, .26));
                p.add(ell(.81, .72, .13, .26));
            }
            case JOY_CON -> { // dos mandos sueltos
                for (double cx : new double[] {.225, .775}) {
                    p.add(rect(cx - .155, .12, cx + .155, .88));
                    p.add(ell(cx, .12, .155, .08));
                    p.add(ell(cx, .88, .155, .08));
                }
            }
            default -> {
                p.add(ell(.5, .34, .40, .30));
                p.add(rect(.18, .10, .82, .56));
                p.add(ell(.22, .68, .15, .30));
                p.add(ell(.78, .68, .15, .30));
            }
        }
        return p;
    }

    /** Intervalo horizontal (en pixeles) que ocupa la pieza en la fila y, engordada grow pixeles; null si no la toca. */
    private static double[] span(Piece p, double y, int sw, int sh, double grow) {
        if (p.type == 0) {
            double cx = p.a * sw, cy = p.b * sh, rx = p.c * sw + grow, ry = p.d * sh + grow;
            if (rx <= 0 || ry <= 0) return null;
            double dy = (y - cy) / ry;
            if (Math.abs(dy) > 1) return null;
            double half = rx * Math.sqrt(1 - dy * dy);
            return new double[] {cx - half, cx + half};
        }
        double x0 = p.a * sw - grow, y0 = p.b * sh - grow, x1 = p.c * sw + grow, y1 = p.d * sh + grow;
        return y < y0 || y > y1 ? null : new double[] {x0, x1};
    }

    private static void rows(GuiGraphics g, List<Piece> pieces, int sx, int sy, int sw, int sh, double grow, int color) {
        for (int row = -(int) Math.ceil(grow); row < sh + Math.ceil(grow); row++) {
            double y = row + .5;
            List<double[]> spans = new ArrayList<>();
            for (Piece p : pieces) {
                double[] s = span(p, y, sw, sh, grow);
                if (s != null) spans.add(s);
            }
            spans.sort((m, n) -> Double.compare(m[0], n[0]));
            double curA = 0, curB = 0;
            boolean open = false;
            for (double[] s : spans) {
                if (open && s[0] <= curB + 0.5) {
                    curB = Math.max(curB, s[1]);
                } else {
                    if (open) g.fill(sx + (int) Math.round(curA), sy + row, sx + (int) Math.round(curB), sy + row + 1, color);
                    curA = s[0];
                    curB = s[1];
                    open = true;
                }
            }
            if (open) g.fill(sx + (int) Math.round(curA), sy + row, sx + (int) Math.round(curB), sy + row + 1, color);
        }
    }

    /** Una mitad de Joy-Con (centrada en cx): barra redondeada. */
    private static List<Piece> joyHalf(double cx) {
        List<Piece> p = new ArrayList<>();
        p.add(rect(cx - .155, .12, cx + .155, .88));
        p.add(ell(cx, .12, .155, .08));
        p.add(ell(cx, .88, .155, .08));
        return p;
    }

    /** Disco relleno de radio r pixeles centrado en (cx, cy). */
    public static void disc(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt((double) r * r - (double) dy * dy));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }

    /** Stick: anillo exterior, pozo oscuro, capuchon y un brillo (el icono del boton se dibuja encima). */
    public static void stick(GuiGraphics g, int cx, int cy, int r) {
        disc(g, cx, cy, r + 1, 0xFF0A0A0A);
        disc(g, cx, cy, r, 0xFF444444);
        disc(g, cx, cy, Math.max(1, Math.round(r * .80f)), 0xFF131313);
        disc(g, cx, cy, Math.max(1, Math.round(r * .58f)), 0xFF2B2B2B);
        disc(g, cx - Math.round(r * .22f), cy - Math.round(r * .22f), Math.max(1, Math.round(r * .16f)), 0xFF4C4C4C);
    }

    /** Boton redondo oscuro con borde (botones de la cruceta de los Joy-Con, boton de inicio...). */
    public static void button(GuiGraphics g, int cx, int cy, int r) {
        disc(g, cx, cy, r + 1, 0xFF0A0A0A);
        disc(g, cx, cy, r, 0xFF2A2A2A);
    }

    private static void box(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        g.fill(x0, y0, x1, y1, color);
    }

    /** Mandos con imagen propia (recortes de docs/reference, sin las lineas de las etiquetas): Xbox, PlayStation y Joy-Con; Switch Pro se dibuja por codigo. */
    public static boolean hasImage(ControlsLayout.Kind kind) {
        return kind != ControlsLayout.Kind.SWITCH_PRO;
    }

    private static String imageName(ControlsLayout.Kind kind) {
        return kind == ControlsLayout.Kind.PLAYSTATION ? "pad_ps" : kind == ControlsLayout.Kind.JOY_CON ? "pad_joycon" : "pad_xbox";
    }

    private static int[] imageSize(ControlsLayout.Kind kind) {
        return kind == ControlsLayout.Kind.PLAYSTATION ? new int[] {300, 200} : kind == ControlsLayout.Kind.JOY_CON ? new int[] {305, 250} : new int[] {245, 182};
    }

    public static double imageAspect(ControlsLayout.Kind kind) {
        int[] s = imageSize(kind);
        return (double) s[0] / s[1];
    }

    /** Dibuja la silueta del tipo de mando en el rectangulo (sx, sy, sw, sh): cuerpo oscuro con bisel y contorno claro, bumpers grises arriba. */
    public static void draw(GuiGraphics g, ControlsLayout.Kind kind, int sx, int sy, int sw, int sh) {
        if (hasImage(kind)) {
            int[] is = imageSize(kind);
            g.blit(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("consolepad", "textures/gui/pads/" + imageName(kind) + ".png"), sx, sy, sw, sh, 0, 0, is[0], is[1], is[0], is[1]);
            return;
        }
        int small = Math.max(2, Math.round(sw * .035f));
        if (kind == ControlsLayout.Kind.JOY_CON) { // dos mandos sueltos, azul neon (izquierdo) y rojo neon (derecho)
            int[][] colors = {{0xFFB8EEFF, 0xFF1FB4E6, 0xFF55CBF0}, {0xFFFFC9C2, 0xFFE4483A, 0xFFEE7366}};
            double[] cxs = {.225, .775};
            for (int k = 0; k < 2; k++) {
                List<Piece> half = joyHalf(cxs[k]);
                rows(g, half, sx, sy, sw, sh, 1.0, colors[k][0]);
                rows(g, half, sx, sy, sw, sh, 0.0, colors[k][1]);
                if (sw >= 90) rows(g, half, sx, sy, sw, sh, -2.5, colors[k][2]); // bisel
                double rail = k == 0 ? cxs[k] + .13 : cxs[k] - .155; // guia lateral oscura (la que se engancha a la consola)
                rows(g, List.of(rect(rail, .22, rail + .025, .78)), sx, sy, sw, sh, 0.0, 0xFF1A1A1A);
                rows(g, List.of(rect(cxs[k] - .12, .03, cxs[k] + .12, .11)), sx, sy, sw, sh, 1.0, 0xFF0E0E0E); // gatillo y bumper
                rows(g, List.of(rect(cxs[k] - .12, .03, cxs[k] + .12, .11)), sx, sy, sw, sh, 0.0, BUMPER);
                int cx = sx + (int) (cxs[k] * sw), cy = sy + (int) (.92 * sh);
                if (k == 0) { // boton de captura (cuadrado) y de inicio (redondo)
                    box(g, cx - small, cy - small, cx + small + 1, cy + small + 1, 0xFF0A0A0A);
                    box(g, cx - small + 1, cy - small + 1, cx + small, cy + small, 0xFF2A2A2A);
                } else {
                    button(g, cx, cy, small);
                }
            }
            return;
        }
        List<Piece> body = body(kind);
        rows(g, body, sx, sy, sw, sh, 1.0, OUTLINE);
        rows(g, body, sx, sy, sw, sh, 0.0, BODY);
        if (sw >= 90) rows(g, body, sx, sy, sw, sh, -2.5, 0xFF313131); // bisel: la cara superior algo mas clara
        List<Piece> bump = List.of(rect(.08, .02, .33, .10), rect(.67, .02, .92, .10));
        rows(g, bump, sx, sy, sw, sh, 1.0, 0xFF0E0E0E);
        rows(g, bump, sx, sy, sw, sh, 0.0, BUMPER);
        if (kind == ControlsLayout.Kind.PLAYSTATION) { // panel tactil y boton PS
            List<Piece> pad = List.of(rect(.35, .11, .65, .31));
            rows(g, pad, sx, sy, sw, sh, 1.0, 0xFF0E0E0E);
            rows(g, pad, sx, sy, sw, sh, 0.0, 0xFF4A4A4A);
            button(g, sx + sw / 2, sy + (int) (.62 * sh), small);
        } else { // logotipo / boton de inicio, arriba en el centro
            button(g, sx + sw / 2, sy + (int) (.19 * sh), small);
        }
    }

    /** Cruz de la cruceta centrada en (cx, cy): brazos de media longitud arm y grosor thick, con borde. */
    public static void cross(GuiGraphics g, int cx, int cy, int arm, int thick) {
        for (int pass = 0; pass < 2; pass++) {
            int grow = pass == 0 ? 1 : 0;
            int a = arm + grow, t = thick / 2 + grow;
            int color = pass == 0 ? 0xFF0A0A0A : 0xFF2A2A2A;
            g.fill(cx - a, cy - t, cx + a + 1, cy + t + 1, color);
            g.fill(cx - t, cy - a, cx + t + 1, cy + a + 1, color);
        }
    }

}
