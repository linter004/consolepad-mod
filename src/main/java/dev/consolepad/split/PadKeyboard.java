package dev.consolepad.split;

import dev.consolepad.pad.PadMath;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen;
import org.lwjgl.glfw.GLFW;

/**
 * Teclado en pantalla de un jugador (uno por slot): una cuadricula de teclas que se maneja con el D-pad o el stick izquierdo y escribe en la
 * pantalla abierta con las mismas entradas que el teclado fisico (charTyped / keyPressed), asi sirve para chat, comandos, carteles, yunque y
 * nombre del mundo. Se abre solo en las pantallas de texto y al pulsar A sobre un campo de texto; B o Start lo cierran.
 */
public final class PadKeyboard {
    private static final PadKeyboard[] KEYBOARDS = {new PadKeyboard(0), new PadKeyboard(1), new PadKeyboard(2), new PadKeyboard(3)};

    public static PadKeyboard of(int slot) {
        return KEYBOARDS[slot];
    }

    private static final String[] LETTERS = {"1234567890", "qwertyuiop", "asdfghjklñ", "zxcvbnm,.-"};
    private static final String[] SYMBOLS = {"!\"#$%&/()=", "?'¿¡+*[]{}", "@_~|\\^<>`;", "áéíóúüç:;,"};
    /** Teclas de la fila de abajo: cada una ocupa 2 columnas. */
    private static final String[] SPECIAL = {"Mayús", "Símb.", "Espacio", "Borrar", "Enviar"};
    private static final int COLS = 10, ROWS = 5, KEY_W = 18, KEY_H = 14;
    /** Alto total del teclado dibujado (en unidades de la pantalla). */
    public static final int HEIGHT = ROWS * KEY_H + 4;
    private static final float REPEAT_DELAY = 0.35f, REPEAT_INTERVAL = 0.10f;

    private final int slot;
    private boolean active;
    private boolean skipFrame;
    private Screen lastScreen;
    private int row = 1, col;
    private boolean shift, symbols;
    private final boolean[] dirPrev = new boolean[4], dirActive = new boolean[4];
    private final float[] dirHeld = new float[4], dirNext = new float[4];
    /** Ultimo texto escrito (para el autotest). */
    public volatile String lastTyped = "";
    /** Diagnostico del ultimo A sobre una pantalla sin teclado (autotest). */
    public volatile String lastClickInfo = "";

    private PadKeyboard(int slot) {
        this.slot = slot;
    }

    public boolean active() {
        return active;
    }

    /** Pantallas en las que se escribe nada mas abrirlas. */
    public static boolean wantsKeyboard(Screen screen) {
        return screen instanceof ChatScreen || screen instanceof AbstractSignEditScreen || (screen instanceof PlayerScreen ps && ps.wantsKeyboard());
    }

    public void open() {
        active = true;
        skipFrame = true;
        row = 1;
        col = 0;
        shift = false;
        symbols = false;
        java.util.Arrays.fill(dirActive, false);
    }

    public void close() {
        active = false;
    }

    /** Cada fotograma con la pantalla actual del jugador (o null): se abre en las pantallas de texto y se cierra si la pantalla cambia. */
    public void sync(Screen screen) {
        if (screen == lastScreen) return;
        lastScreen = screen;
        if (screen != null && wantsKeyboard(screen)) open();
        else active = false;
    }

    /** Tras pulsar A: si hay un campo de texto enfocado bajo el cursor, abre el teclado. */
    public void tryOpenFromClick(Screen screen) {
        if (active || screen == null) return;
        lastClickInfo = "foco=" + screen.getFocused() + (screen.getFocused() instanceof EditBox b ? " activo=" + b.isActive() + " encima=" + b.isHovered() : "");
        boolean ok = screen.getFocused() instanceof EditBox box && box.isActive() && box.isHovered();
        if (ok) open();
    }

    private char keyChar(int r, int c) {
        char ch = (symbols ? SYMBOLS : LETTERS)[r].charAt(c);
        return shift && !symbols ? Character.toUpperCase(ch) : ch;
    }

    private void typeChar(Screen screen, char ch) {
        screen.charTyped(ch, 0);
        lastTyped = lastTyped + ch;
        if (shift) shift = false; // mayuscula de una sola letra
    }

    private void press(Screen screen, int glfwKey) {
        screen.keyPressed(glfwKey, 0, 0);
        screen.keyReleased(glfwKey, 0, 0);
    }

    /** Entrada del mando con el teclado abierto, una vez por fotograma. Consume todo el mando. */
    public void handle(PadState s, PadSettings cfg, float dt, Screen screen) {
        if (!active || screen == null) return;
        if (skipFrame) { // el boton que lo abrio (A sobre el campo, o la cruceta del chat) sigue pulsado: no cuenta como pulsacion del teclado
            skipFrame = false;
            PadMath.navDirections(s, true, dirPrev);
            return;
        }
        // Navegacion: D-pad y stick izquierdo, con repeticion al mantener.
        boolean[] down = new boolean[4];
        PadMath.navDirections(s, true, down);
        for (int i = 0; i < 4; i++) {
            boolean fire = false;
            if (down[i] && !dirPrev[i]) {
                dirActive[i] = true;
                dirHeld[i] = 0f;
                dirNext[i] = REPEAT_DELAY;
                fire = true;
            } else if (dirActive[i] && down[i]) {
                dirHeld[i] += dt;
                if (dirHeld[i] >= dirNext[i]) {
                    dirNext[i] += REPEAT_INTERVAL;
                    fire = true;
                }
            } else {
                dirActive[i] = false;
            }
            dirPrev[i] = down[i];
            if (fire) move(i);
        }
        if (s.pressed(PadState.A)) activate(screen);
        if (s.pressed(PadState.X)) press(screen, GLFW.GLFW_KEY_BACKSPACE);
        if (s.pressed(PadState.Y)) typeChar(screen, ' ');
        if (s.pressed(PadState.LB)) press(screen, GLFW.GLFW_KEY_LEFT);
        if (s.pressed(PadState.RB)) press(screen, GLFW.GLFW_KEY_RIGHT);
        if (s.pressed(PadState.LT)) shift = !shift;
        if (s.pressed(PadState.B) || s.pressed(PadState.START)) {
            active = false;
        }
    }

    /** Dir: 0 arriba, 1 abajo, 2 izquierda, 3 derecha. */
    private void move(int dir) {
        switch (dir) {
            case 0 -> row = (row + ROWS - 1) % ROWS;
            case 1 -> row = (row + 1) % ROWS;
            case 2 -> col = row == ROWS - 1 ? (col + COLS - 2) % COLS : (col + COLS - 1) % COLS;
            default -> col = row == ROWS - 1 ? (col + 2) % COLS : (col + 1) % COLS;
        }
        if (row == ROWS - 1) col = col / 2 * 2;
    }

    private void activate(Screen screen) {
        if (row < ROWS - 1) {
            typeChar(screen, keyChar(row, col));
            return;
        }
        switch (col / 2) {
            case 0 -> shift = !shift;
            case 1 -> symbols = !symbols;
            case 2 -> typeChar(screen, ' ');
            case 3 -> press(screen, GLFW.GLFW_KEY_BACKSPACE);
            default -> {
                press(screen, GLFW.GLFW_KEY_ENTER);
                active = false;
            }
        }
    }

    /** Dibuja el teclado abajo en el centro de una pantalla de w x h unidades, encima de la barra de ayuda. */
    public void render(GuiGraphics g, Font font, int w, int h) {
        if (!active) return;
        int width = COLS * KEY_W;
        int x0 = (w - width) / 2, y0 = h - (PadIcons.H + 3) - HEIGHT;
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        g.fill(x0 - 3, y0 - 1, x0 + width + 3, y0 + HEIGHT, 0xE0101010);
        for (int r = 0; r < ROWS; r++) {
            int y = y0 + 2 + r * KEY_H;
            if (r < ROWS - 1) {
                for (int c = 0; c < COLS; c++) drawKey(g, font, x0 + c * KEY_W, y, KEY_W, String.valueOf(keyChar(r, c)), r == row && c == col, false);
            } else {
                for (int k = 0; k < SPECIAL.length; k++) {
                    boolean on = (k == 0 && shift) || (k == 1 && symbols);
                    drawKey(g, font, x0 + k * 2 * KEY_W, y, 2 * KEY_W, SPECIAL[k], row == r && col / 2 == k, on);
                }
            }
        }
        g.pose().popPose();
    }

    private static void drawKey(GuiGraphics g, Font font, int x, int y, int w, String label, boolean selected, boolean on) {
        int bg = selected ? 0xFFFFFFFF : on ? 0xFF3F6FA8 : 0xFF3A3A3A;
        g.fill(x + 1, y, x + w - 1, y + KEY_H - 1, bg);
        g.drawString(font, label, x + (w - font.width(label)) / 2, y + 3, selected ? 0xFF000000 : 0xFFFFFFFF, false);
    }

    // ------------------------------------------------------------------ para el autotest

    public int row() {
        return row;
    }

    public int col() {
        return col;
    }

    public void resetTyped() {
        lastTyped = "";
    }
}
