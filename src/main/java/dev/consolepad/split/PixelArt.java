package dev.consolepad.split;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Iconos de pixel art (la marca de lugar), dibujados con rectangulos (sin texturas). Cada fila es una cadena: '.' vacio, 'k' contorno,
 * 'y' amarillo, 'r' rojo, 'w' blanco, 'g' verde, 'c' el color del jugador.
 */
public final class PixelArt {
    private PixelArt() {}

    /** La marca de lugar: una chincheta del color del jugador. */
    public static final String[] PIN = {
            "..kkk..",
            ".kccck.",
            "kccwcck",
            "kccccck",
            ".kccck.",
            "..kck..",
            "...k..."};

    private static int color(char ch, int rgb) {
        return switch (ch) {
            case 'k' -> 0xFF101010;
            case 'y' -> 0xFFFFD84A;
            case 'r' -> 0xFFE03030;
            case 'w' -> 0xFFFFFFFF;
            case 'g' -> 0xFF40D060;
            case 'c' -> 0xFF000000 | rgb;
            default -> 0;
        };
    }

    /** Dibuja el icono con la esquina superior izquierda en (x, y), cada pixel de s x s; rgb es el color del jugador. Devuelve el ancho dibujado. */
    public static int draw(GuiGraphics g, String[] art, int x, int y, int s, int rgb) {
        for (int j = 0; j < art.length; j++) {
            String row = art[j];
            for (int i = 0; i < row.length(); i++) {
                int c = color(row.charAt(i), rgb);
                if (c != 0) g.fill(x + i * s, y + j * s, x + (i + 1) * s, y + (j + 1) * s, c);
            }
        }
        return art[0].length() * s;
    }
}
