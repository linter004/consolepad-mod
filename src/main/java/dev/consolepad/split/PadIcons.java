package dev.consolepad.split;

import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Iconos de botones de mando dibujados con rectangulos y texto (sin texturas): los cuatro botones de cara son circulos con el color o
 * el simbolo de su familia de mando (Xbox, PlayStation, Nintendo); los demas, una pastilla con el nombre corto del boton.
 */
public final class PadIcons {
    /** Alto del icono en unidades de GUI. */
    public static final int H = 11;

    private PadIcons() {}

    private static boolean isFace(int code) {
        return code == PadState.A || code == PadState.B || code == PadState.X || code == PadState.Y;
    }

    /** Texto corto que va dentro del icono (letra, simbolo o nombre del boton). */
    public static String glyph(int code, PadType type) {
        if (isFace(code)) {
            if (type == PadType.PLAYSTATION) {
                if (code == PadState.A) return "×";
                if (code == PadState.B) return "○";
                if (code == PadState.X) return "□";
                return "△";
            }
            return PadNames.button(code, type);
        }
        if (code == PadState.DPAD_UP) return "D↑";
        if (code == PadState.DPAD_DOWN) return "D↓";
        if (code == PadState.DPAD_LEFT) return "D←";
        if (code == PadState.DPAD_RIGHT) return "D→";
        return PadNames.button(code, type);
    }

    public static int width(Font font, int code, PadType type) {
        return isFace(code) ? H : font.width(glyph(code, type)) + 8;
    }

    /** Dibuja el icono con su esquina superior izquierda en (x, y) y devuelve su ancho. */
    public static int draw(GuiGraphics g, Font font, int x, int y, int code, PadType type) {
        String s = glyph(code, type);
        if (isFace(code)) {
            int bg = 0xFF2A2A2A;
            int fg = 0xFFFFFFFF;
            if (type == PadType.XBOX) {
                bg = code == PadState.A ? 0xFF3FA535 : code == PadState.B ? 0xFFD8322F : code == PadState.X ? 0xFF2F6FD8 : 0xFFE0A818;
            } else if (type == PadType.PLAYSTATION) {
                fg = code == PadState.A ? 0xFF7BA7FF : code == PadState.B ? 0xFFFF6B6B : code == PadState.X ? 0xFFE79CD8 : 0xFF6BD99A;
            } else {
                bg = 0xFF3A3A3A;
            }
            circle(g, x + H / 2, y + H / 2, H / 2, 0xFFE8E8E8);
            circle(g, x + H / 2, y + H / 2, H / 2 - 1, bg);
            g.drawString(font, s, x + (H - font.width(s)) / 2 + 1, y + 1, fg, false);
            return H;
        }
        int w = font.width(s) + 8;
        g.fill(x + 1, y, x + w - 1, y + H, 0xFFE8E8E8);
        g.fill(x, y + 1, x + w, y + H - 1, 0xFFE8E8E8);
        g.fill(x + 2, y + 1, x + w - 2, y + H - 1, 0xFF2A2A2A);
        g.fill(x + 1, y + 2, x + w - 1, y + H - 2, 0xFF2A2A2A);
        g.drawString(font, s, x + 4, y + 1, 0xFFFFFFFF, false);
        return w;
    }

    private static void circle(GuiGraphics g, int cx, int cy, int r, int color) {
        for (int dy = -r; dy <= r; dy++) {
            int half = (int) Math.round(Math.sqrt(Math.max(0, (double) r * r + r - (double) dy * dy)));
            g.fill(cx - half, cy + dy, cx + half + 1, cy + dy + 1, color);
        }
    }
}
