package dev.consolepad.split;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Linea fina entre las regiones de los jugadores: en cada borde compartido, una franja de 2 pixeles dentro de cada region, sin color (negra, como el marco
 * de la barra de objetos), para no cambiar el aspecto del juego.
 * Se apaga con "separators": false en split.json. Las regiones se dan en unidades de GUI de la ventana {x, y, ancho, alto}; la linea se dibuja en pixeles
 * reales (una franja de 2 px mide 2 px a cualquier escala de interfaz).
 */
public final class SplitSeparators {
    public static final int THICK = 2;
    /** Color de la linea (RGB): negro, sin el color de ningun jugador. */
    public static final int RGB = 0x000000;
    /** Cuantos tramos se dibujaron en el ultimo fotograma y sus colores {izquierda/arriba, derecha/abajo}; para las pruebas. */
    public static volatile int lastCount;
    public static volatile int[][] lastColors = new int[0][];

    /** Un borde compartido: vertical (x = pos, de a a b) u horizontal (y = pos, de a a b) entre la region A (izquierda o arriba) y la B. */
    public record Segment(boolean vertical, int a, int b, int pos, int regionA, int regionB) {}

    private SplitSeparators() {}

    /** Los bordes que comparten las regiones (con 1 unidad de tolerancia) y la longitud que se solapan. */
    public static List<Segment> segments(List<int[]> rects) {
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < rects.size(); i++) {
            for (int j = 0; j < rects.size(); j++) {
                if (i == j) continue;
                int[] a = rects.get(i), b = rects.get(j);
                if (Math.abs(a[0] + a[2] - b[0]) <= 1) { // a a la izquierda de b
                    int y0 = Math.max(a[1], b[1]), y1 = Math.min(a[1] + a[3], b[1] + b[3]);
                    if (y1 - y0 > 2) out.add(new Segment(true, y0, y1, a[0] + a[2], i, j));
                }
                if (Math.abs(a[1] + a[3] - b[1]) <= 1) { // a encima de b
                    int x0 = Math.max(a[0], b[0]), x1 = Math.min(a[0] + a[2], b[0] + b[2]);
                    if (x1 - x0 > 2) out.add(new Segment(false, x0, x1, a[1] + a[3], i, j));
                }
            }
        }
        return out;
    }

    /** Dibuja las lineas; rects.get(i) es la region del jugador slots.get(i). */
    public static void render(Minecraft mc, GuiGraphics g, List<int[]> rects, List<Integer> slots) {
        lastCount = 0;
        if (!SplitSettings.separators()) {
            lastColors = new int[0][];
            return;
        }
        List<Segment> segs = segments(rects);
        int[][] colors = new int[segs.size()][];
        double scale = mc.getWindow().getGuiScale();
        PoseStack pose = g.pose();
        pose.pushPose();
        pose.translate(0, 0, Companions.OVERLAY_Z);
        pose.scale((float) (1.0 / scale), (float) (1.0 / scale), 1f); // a partir de aqui las coordenadas son pixeles reales
        try {
            int k = 0;
            for (Segment s : segs) {
                int ca = RGB, cb = RGB;
                int pos = (int) Math.round(s.pos() * scale), a = (int) Math.round(s.a() * scale), b = (int) Math.round(s.b() * scale);
                if (s.vertical()) {
                    g.fill(pos - THICK, a, pos, b, 0xFF000000 | ca);
                    g.fill(pos, a, pos + THICK, b, 0xFF000000 | cb);
                } else {
                    g.fill(a, pos - THICK, b, pos, 0xFF000000 | ca);
                    g.fill(a, pos, b, pos + THICK, 0xFF000000 | cb);
                }
                colors[k++] = new int[] {ca, cb};
            }
        } finally {
            pose.popPose();
        }
        lastColors = colors;
        lastCount = segs.size();
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        int w = 960, h = 540;
        var two = segments(List.of(new int[] {0, 0, w / 2, h}, new int[] {w / 2, 0, w / 2, h}));
        if (two.size() != 1 || !two.get(0).vertical() || two.get(0).pos() != w / 2 || two.get(0).b() - two.get(0).a() != h || two.get(0).regionA() != 0 || two.get(0).regionB() != 1) return "dos lado a lado: " + two;
        var stacked = segments(List.of(new int[] {0, 0, w, h / 2}, new int[] {0, h / 2, w, h / 2}));
        if (stacked.size() != 1 || stacked.get(0).vertical() || stacked.get(0).pos() != h / 2 || stacked.get(0).b() - stacked.get(0).a() != w) return "dos apilados: " + stacked;
        var four = segments(List.of(new int[] {0, 0, w / 2, h / 2}, new int[] {w / 2, 0, w / 2, h / 2}, new int[] {0, h / 2, w / 2, h / 2}, new int[] {w / 2, h / 2, w / 2, h / 2}));
        if (four.size() != 4) return "cuatro cuadrantes: " + four.size();
        var three = segments(List.of(new int[] {0, 0, w / 2, h}, new int[] {w / 2, 0, w / 2, h / 2}, new int[] {w / 2, h / 2, w / 2, h / 2}));
        if (three.size() != 3) return "tres (mitad y dos cuartos): " + three.size();
        if (!segments(List.of(new int[] {0, 0, w, h})).isEmpty()) return "una sola region no tiene bordes";
        return null;
    }
}
