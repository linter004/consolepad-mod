package dev.consolepad.split;

import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * Iconos de botones de mando del pack "Free Input Prompts" de JulioCacko (CC0, ver CREDITS), empaquetados por tools/PromptAtlas.java en
 * un atlas por estilo ({@link SplitSettings#promptStyle}). Cada tipo de mando usa su set (Xbox, PlayStation, Nintendo, como Bedrock);
 * un boton sin icono en el set no dibuja nada y mide 0.
 */
public final class PadIcons {
    /** Alto del icono en unidades de GUI (el disco del boton). */
    public static final int H = 11;
    /** Lado con el que se dibuja la celda del atlas (el icono ocupa ~80 % de ella) y ancho que avanza el texto. */
    private static final int SIZE = 14, ADVANCE = 12;

    /** Orden de los iconos dentro de la familia; debe coincidir con ICONS de tools/PromptAtlas.java. */
    public enum Icon {
        A, B, X, Y, LB, RB, LT, RT, BACK, START, L3, R3, DPAD_UP, DPAD_DOWN, DPAD_LEFT, DPAD_RIGHT, DPAD, L, R
    }

    public static final int CELL = 32, COLS = 8, ATLAS = 256;

    /** Iconos dibujados desde el arranque (para el autotest). */
    public static volatile int drawn;

    private PadIcons() {}

    /** Icono de un codigo de boton, o null si el boton no tiene icono. */
    public static Icon icon(int code) {
        if (code == PadState.A) return Icon.A;
        if (code == PadState.B) return Icon.B;
        if (code == PadState.X) return Icon.X;
        if (code == PadState.Y) return Icon.Y;
        if (code == PadState.LB) return Icon.LB;
        if (code == PadState.RB) return Icon.RB;
        if (code == PadState.LT) return Icon.LT;
        if (code == PadState.RT) return Icon.RT;
        if (code == PadState.BACK) return Icon.BACK;
        if (code == PadState.START) return Icon.START;
        if (code == PadState.L3) return Icon.L3;
        if (code == PadState.R3) return Icon.R3;
        if (code == PadState.DPAD_UP) return Icon.DPAD_UP;
        if (code == PadState.DPAD_DOWN) return Icon.DPAD_DOWN;
        if (code == PadState.DPAD_LEFT) return Icon.DPAD_LEFT;
        if (code == PadState.DPAD_RIGHT) return Icon.DPAD_RIGHT;
        return null;
    }

    /** Celda del atlas (0-based, en filas de COLS) del icono de ese tipo de mando. */
    public static int cell(PadType type, Icon icon) {
        return type.ordinal() * Icon.values().length + icon.ordinal();
    }

    public static ResourceLocation atlas(String style) {
        return ResourceLocation.fromNamespaceAndPath("consolepad", "textures/gui/prompts/" + style + ".png");
    }

    public static boolean has(int code, PadType type) {
        return icon(code) != null;
    }

    /** Texto corto con el nombre del boton (para mensajes y pruebas; en pantalla se dibuja el icono). */
    public static String glyph(int code, PadType type) {
        if (code == PadState.A || code == PadState.B || code == PadState.X || code == PadState.Y) {
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
        return has(code, type) ? ADVANCE : 0;
    }

    /** Dibuja el icono centrado en (cx, cy) con un disco de `size` unidades (el diagrama del mando usa tamanos distintos); 0 si el boton no tiene icono. */
    public static int drawCentered(GuiGraphics g, int cx, int cy, int size, int code, PadType type) {
        Icon icon = icon(code);
        if (icon == null) return 0;
        int cellSize = Math.round(size * (float) SIZE / H);
        int idx = cell(type, icon);
        g.blit(atlas(SplitSettings.promptStyle()), cx - cellSize / 2, cy - cellSize / 2, cellSize, cellSize, (idx % COLS) * CELL, (idx / COLS) * CELL, CELL, CELL, ATLAS, ATLAS);
        drawn++;
        return size;
    }

    /** Dibuja el icono con su esquina superior izquierda en (x, y) y devuelve su ancho (0 si el boton no tiene icono). */
    public static int draw(GuiGraphics g, Font font, int x, int y, int code, PadType type) {
        Icon icon = icon(code);
        if (icon == null) return 0;
        int idx = cell(type, icon);
        g.blit(atlas(SplitSettings.promptStyle()), x - 1, y - 1, SIZE, SIZE, (idx % COLS) * CELL, (idx / COLS) * CELL, CELL, CELL, ATLAS, ATLAS);
        drawn++;
        return ADVANCE;
    }
}
