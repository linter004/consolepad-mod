package dev.consolepad.split;

import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;

import java.util.Locale;

/**
 * Geometria de la pantalla de controles con diagrama del mando: silueta centrada, un icono sobre cada boton y una etiqueta por boton en dos
 * columnas (izquierda y derecha). Es pura (sin dibujar) para poder medirla en el autotest: ninguna etiqueta puede solaparse con otra ni salirse de
 * la zona libre entre el titulo y los botones inferiores. Las posiciones son las fisicas de cada mando (los codigos de PadState son posicionales:
 * A = abajo, B = derecha, X = izquierda, Y = arriba); los simbolos y su intercambio (A/B en Nintendo, cruz/circulo en PlayStation) salen del atlas
 * de iconos del tipo de mando.
 */
public final class ControlsLayout {
    /** Silueta que se dibuja: Joy-Con es el par de mandos sueltos; los demas, un mando de una pieza. */
    public enum Kind {
        XBOX("Xbox", PadType.XBOX), PLAYSTATION("PlayStation", PadType.PLAYSTATION), SWITCH_PRO("Switch Pro", PadType.NINTENDO), JOY_CON("Joy-Con", PadType.NINTENDO);

        public final String label;
        public final PadType family;

        Kind(String label, PadType family) {
            this.label = label;
            this.family = family;
        }
    }

    /** Botones remapeables, en el orden en que se indexan anclas y etiquetas. */
    public static final int[] CODES = {PadState.A, PadState.B, PadState.X, PadState.Y, PadState.LB, PadState.RB, PadState.LT, PadState.RT, PadState.BACK, PadState.START,
            PadState.L3, PadState.R3, PadState.DPAD_UP, PadState.DPAD_DOWN, PadState.DPAD_LEFT, PadState.DPAD_RIGHT};

    /** Hueco entre una columna de etiquetas y la silueta (por donde pasan las lineas), margen lateral, alto y paso de una etiqueta. */
    public static final int GAP = 14, MARGIN = 4, TOP = 20, BOTTOM_RESERVE = 40;

    private ControlsLayout() {}

    public static int index(int code) {
        for (int i = 0; i < CODES.length; i++) if (CODES[i] == code) return i;
        return -1;
    }

    /** Menu y pausa (Atras e Inicio) llevan su etiqueta arriba, centrada sobre la silueta, con una linea hacia abajo. */
    public static boolean topLabel(int code) {
        return code == PadState.BACK || code == PadState.START;
    }

    /** Lado de la etiqueta de cada boton: true = izquierda (columna izquierda, o la mitad izquierda de la fila de arriba); igual en todos los mandos. */
    public static boolean leftSide(int code) {
        return code == PadState.LT || code == PadState.LB || code == PadState.BACK || code == PadState.L3
                || code == PadState.DPAD_UP || code == PadState.DPAD_DOWN || code == PadState.DPAD_LEFT || code == PadState.DPAD_RIGHT;
    }

    public static Kind kindOf(PadManager.Device d) {
        if (d == null) return Kind.XBOX;
        if (d.type == PadType.PLAYSTATION) return Kind.PLAYSTATION;
        if (d.type == PadType.NINTENDO) return d.name.toLowerCase(Locale.ROOT).contains("joy-con") ? Kind.JOY_CON : Kind.SWITCH_PRO;
        return Kind.XBOX;
    }

    /** Silueta de un jugador: la de la familia de iconos que haya elegido en sus ajustes (0 = automatico: la de su mando). */
    public static Kind kindFor(PadManager.Device d, int iconStyle) {
        PadType[] types = PadType.values();
        if (iconStyle < 1 || iconStyle > types.length) return kindOf(d);
        PadType t = types[iconStyle - 1];
        if (d != null && d.type == t) return kindOf(d);
        return t == PadType.PLAYSTATION ? Kind.PLAYSTATION : t == PadType.NINTENDO ? Kind.JOY_CON : Kind.XBOX;
    }

    /** Resultado de {@link #compute}: todo en unidades de GUI de la pantalla. */
    public static final class Result {
        public int sx, sy, sw, sh, icon, colW, labelH;
        /** Centro del boton en la silueta, por indice de CODES. */
        public final int[] ax = new int[CODES.length], ay = new int[CODES.length];
        /** Caja de la etiqueta {x, y, w, h}, por indice de CODES. */
        public final int[][] label = new int[CODES.length][];
    }

    public static Result compute(Kind kind, int width, int height) {
        Result r = new Result();
        int top = TOP, bottom = Math.max(top + 40, height - BOTTOM_RESERVE);
        int band = 12 + 9; // fila de arriba (menu y pausa) y el hueco para su linea
        int availH = bottom - top - band;
        int colW = clamp(Math.round(width * 0.30f), 60, 170);
        int sw = width - 2 * (MARGIN + colW + GAP);
        if (sw < 56) {
            colW = Math.max(40, (width - 2 * (MARGIN + GAP) - 56) / 2);
            sw = Math.max(40, width - 2 * (MARGIN + colW + GAP));
        }
        sw = Math.min(sw, 200);
        double aspect = PadSilhouette.hasImage(kind) ? PadSilhouette.imageAspect(kind) : kind == Kind.JOY_CON ? 1.25 : 1.4;
        int sh = (int) Math.min(sw / aspect, availH);
        sw = Math.min(sw, (int) (sh * aspect));
        r.sw = sw;
        r.sh = sh;
        r.sx = (width - sw) / 2;
        r.sy = top + band + (availH - sh) / 2;
        r.colW = colW;
        int ic = clamp(Math.round(sw / 16f), 7, 11);
        r.icon = ic;
        int d = PadSilhouette.hasImage(kind) ? Math.max(ic + 1, Math.round(sw * .06f)) : ic + 1;
        int t1 = r.sy + ic / 2 + 1, t2 = t1 + ic + 2; // gatillo arriba y bumper debajo

        double trigL = kind == Kind.JOY_CON ? .22 : .20, trigR = kind == Kind.JOY_CON ? .78 : .80;
        if (kind == Kind.XBOX) { trigL = .27; trigR = .78; } // posiciones medidas en las imagenes de docs/reference
        else if (kind == Kind.PLAYSTATION) { trigL = .13; trigR = .70; }
        else if (kind == Kind.JOY_CON) { trigL = .14; trigR = .76; }
        double[] back, start, l3, r3, dpad, face;
        switch (kind) {
            case PLAYSTATION -> {
                back = new double[] {.30, .165};
                start = new double[] {.60, .20};
                dpad = new double[] {.20, .30};
                l3 = new double[] {.323, .485};
                face = new double[] {.777, .285};
                r3 = new double[] {.617, .485};
            }
            case JOY_CON -> {
                back = new double[] {.22, .18};
                start = new double[] {.61, .18};
                l3 = new double[] {.148, .30};
                dpad = new double[] {.148, .57};
                face = new double[] {.68, .31};
                r3 = new double[] {.672, .56};
            }
            case SWITCH_PRO -> {
                back = new double[] {.42, .24};
                start = new double[] {.58, .24};
                l3 = new double[] {.22, .38};
                dpad = new double[] {.38, .66};
                face = new double[] {.78, .42};
                r3 = new double[] {.62, .66};
            }
            default -> {
                back = new double[] {.445, .38};
                start = new double[] {.604, .38};
                l3 = new double[] {.257, .38};
                dpad = new double[] {.388, .615};
                face = new double[] {.804, .39};
                r3 = new double[] {.665, .615};
            }
        }
        set(r, PadState.LT, r.sx + (int) (trigL * sw), t1);
        set(r, PadState.LB, r.sx + (int) (trigL * sw), t2);
        set(r, PadState.RT, r.sx + (int) (trigR * sw), t1);
        set(r, PadState.RB, r.sx + (int) (trigR * sw), t2);
        set(r, PadState.BACK, r.sx + (int) (back[0] * sw), r.sy + (int) (back[1] * sh));
        set(r, PadState.START, r.sx + (int) (start[0] * sw), r.sy + (int) (start[1] * sh));
        set(r, PadState.L3, r.sx + (int) (l3[0] * sw), r.sy + (int) (l3[1] * sh));
        set(r, PadState.R3, r.sx + (int) (r3[0] * sw), r.sy + (int) (r3[1] * sh));
        int dx = r.sx + (int) (dpad[0] * sw), dy = r.sy + (int) (dpad[1] * sh);
        set(r, PadState.DPAD_UP, dx, dy - d);
        set(r, PadState.DPAD_DOWN, dx, dy + d);
        set(r, PadState.DPAD_LEFT, dx - d, dy);
        set(r, PadState.DPAD_RIGHT, dx + d, dy);
        int fx = r.sx + (int) (face[0] * sw), fy = r.sy + (int) (face[1] * sh);
        set(r, PadState.Y, fx, fy - d); // arriba
        set(r, PadState.A, fx, fy + d); // abajo
        set(r, PadState.X, fx - d, fy); // izquierda
        set(r, PadState.B, fx + d, fy); // derecha

        // Etiquetas: cada columna ordenada por la altura de su boton y empujada hacia abajo/arriba hasta que no se pisen.
        int colTop = top, colH = bottom - top;
        int pitch = 13, h = 12;
        int nLeft = 0, nRight = 0;
        for (int c : CODES) if (!topLabel(c)) { if (leftSide(c)) nLeft++; else nRight++; }
        int most = Math.max(nLeft, nRight);
        if (most * pitch > colH) {
            pitch = Math.max(9, colH / most);
            h = pitch - 1;
        }
        r.labelH = h;
        // Fila de arriba: Atras a la izquierda del centro, Inicio a la derecha; cada una ocupa media silueta mas los huecos (hasta el borde de las columnas).
        int cx = r.sx + sw / 2, half = (sw + 2 * GAP - 4) / 2 - 2;
        r.label[index(PadState.BACK)] = new int[] {cx - 2 - half, top, half, h};
        r.label[index(PadState.START)] = new int[] {cx + 2, top, half, h};
        for (boolean left : new boolean[] {true, false}) {
            Integer[] order = new Integer[left ? nLeft : nRight];
            int k = 0;
            for (int i = 0; i < CODES.length; i++) if (!topLabel(CODES[i]) && leftSide(CODES[i]) == left) order[k++] = i;
            final boolean lf = left;
            // Misma altura: el boton mas cercano a la columna va primero, para que las lineas no se crucen.
            java.util.Arrays.sort(order, (p, q) -> r.ay[p] != r.ay[q] ? Integer.compare(r.ay[p], r.ay[q]) : (lf ? Integer.compare(r.ax[p], r.ax[q]) : Integer.compare(r.ax[q], r.ax[p])));
            int[] ys = new int[order.length];
            for (int i = 0; i < ys.length; i++) {
                int ideal = r.ay[order[i]] - h / 2;
                ys[i] = Math.max(ideal, i == 0 ? top : ys[i - 1] + pitch);
            }
            if (ys.length > 0 && ys[ys.length - 1] + h > bottom) {
                ys[ys.length - 1] = bottom - h;
                for (int i = ys.length - 2; i >= 0; i--) ys[i] = Math.min(ys[i], ys[i + 1] - pitch);
            }
            int x = left ? r.sx - GAP - colW : r.sx + sw + GAP;
            for (int i = 0; i < ys.length; i++) r.label[order[i]] = new int[] {x, ys[i], colW, h};
        }
        return r;
    }

    private static void set(Result r, int code, int x, int y) {
        int i = index(code);
        r.ax[i] = x;
        r.ay[i] = y;
    }

    private static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }
}
