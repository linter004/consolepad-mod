package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controles de un jugador con un diagrama del mando: la silueta del tipo de mando de ESE jugador (Xbox, PlayStation, Switch Pro o Joy-Con), un icono
 * sobre cada boton y, unida con una linea, una etiqueta con su funcion actual. Se elige una etiqueta (cursor, D-pad o raton), se pulsa el boton
 * nuevo del mando de ESE jugador y se guarda en su slotN.json. Si el boton ya lo usa otra accion avisa y pide pulsarlo otra vez para intercambiarlos;
 * "Restablecer" vuelve a los botones de fabrica. La geometria esta en {@link ControlsLayout} (sin solapes, medida en el autotest).
 */
public final class PlayerControlsScreen extends PlayerMenuScreen {
    /** Ticks que se espera un boton antes de cancelar (5 s). */
    private static final int LISTEN_TICKS = 100;
    /** Solo pruebas: fuerza la silueta (si no, la del mando conectado a ese jugador). */
    public static volatile ControlsLayout.Kind forcedKind;

    private final Screen parent;
    private PadAction listening;
    private int listenCode = -1;
    private int listenTicks;
    private int pendingConfirm = -1;
    private Component warning = Component.empty();
    private final List<ControlLabel> labels = new ArrayList<>();
    private Button reset, back;
    private ControlsLayout.Kind kind = ControlsLayout.Kind.XBOX;
    private ControlsLayout.Result lay;

    public PlayerControlsScreen(int slot, Screen parent) {
        super(slot, Component.literal("Controles del jugador " + (slot + 1) + " · " + (forcedKind != null ? forcedKind : ControlsLayout.kindFor(ConsolePad.PADS.slot(slot).device, ConsolePad.PADS.slot(slot).settings.iconStyle)).label));
        this.parent = parent;
    }

    /** Familia de los iconos y de los nombres de botones: la elegida en los ajustes del jugador o, si no, la del mando (la silueta sigue a la familia elegida). */
    private PadType padType() {
        int st = settings().iconStyle;
        return st >= 1 && st <= PadType.values().length ? PadType.values()[st - 1] : kind.family;
    }

    public PadType iconType() {
        return padType();
    }

    /** Botones de una accion en el orden de los codigos. */
    public List<Integer> codesOf(PadAction a) {
        List<Integer> out = new ArrayList<>();
        for (Map.Entry<Integer, PadAction> e : settings().bindings.entrySet()) {
            if (e.getValue() == a) out.add(e.getKey());
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** Etiqueta de un boton: la accion que tiene hoy ("Sin acción" si ninguna). */
    public String labelText(int code) {
        PadAction a = settings().bindings.get(code);
        return a == null ? "Sin acción" : PadNames.action(a);
    }

    /** Una etiqueta del diagrama: un boton de la pantalla (el D-pad y el cursor la alcanzan como cualquier otro) que dibuja icono y funcion. */
    private final class ControlLabel extends Button {
        final int code;
        final boolean left;

        ControlLabel(int code, int[] box) {
            super(box[0], box[1], box[2], box[3], Component.literal(PadNames.button(code, padType())), b -> select(code), DEFAULT_NARRATION);
            this.code = code;
            this.left = ControlsLayout.leftSide(code);
        }

        @Override
        protected void renderWidget(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
            Font f = PlayerControlsScreen.this.font;
            boolean sel = listenCode == code;
            int x = getX(), y = getY(), w = getWidth(), h = getHeight();
            if (sel) {
                g.fill(x, y, x + w, y + h, 0x88555500);
                g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFF55);
            } else if (isHoveredOrFocused()) {
                g.fill(x, y, x + w, y + h, 0x66FFFFFF);
                g.fill(x, y + h - 1, x + w, y + h, 0xFFFFFFFF);
            }
            int adv = PadIcons.width(f, code, padType()) + 1;
            String text = sel ? "Pulsa un botón" : labelText(code);
            int room = w - 4 - adv;
            if (f.width(text) > room) text = f.plainSubstrByWidth(text, Math.max(0, room - f.width("…"))) + "…";
            int ty = y + (h - f.lineHeight) / 2 + 1;
            int color = !sel && settings().bindings.get(code) == null ? 0xFF909090 : 0xFFFFFFFF;
            int iy = y + (h - PadIcons.H) / 2;
            // El icono va pegado a la linea (el de la izquierda a la derecha del texto, el de la derecha a su izquierda), como en un diagrama de consola.
            if (left) {
                PadIcons.draw(g, f, x + w - 2 - PadIcons.width(f, code, padType()), iy, code, padType());
                g.drawString(f, text, x + w - 2 - adv - f.width(text), ty, color);
            } else {
                PadIcons.draw(g, f, x + 2, iy, code, padType());
                g.drawString(f, text, x + 2 + adv, ty, color);
            }
        }
    }

    private void select(int code) {
        PadAction a = settings().bindings.get(code);
        if (a == null) {
            cancelListening();
            warning = Component.literal(PadNames.button(code, padType()) + " no tiene acción: elige otra etiqueta y pulsa este botón");
            return;
        }
        startListening(a, code);
    }

    @Override
    protected void init() {
        labels.clear();
        kind = forcedKind != null ? forcedKind : ControlsLayout.kindFor(ConsolePad.PADS.slot(slot).device, settings().iconStyle);
        lay = ControlsLayout.compute(kind, this.width, this.height);
        for (int i = 0; i < ControlsLayout.CODES.length; i++) {
            ControlLabel l = addRenderableWidget(new ControlLabel(ControlsLayout.CODES[i], lay.label[i]));
            labels.add(l);
        }
        int bw = Math.min(88, (this.width - 12) / 2);
        reset = addRenderableWidget(Button.builder(Component.literal("Restablecer"), b -> resetDefaults()).bounds(this.width / 2 - bw - 2, this.height - 22, bw, 18).build());
        back = addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> this.onClose()).bounds(this.width / 2 + 2, this.height - 22, bw, 18).build());
    }

    private void startListening(PadAction a, int code) {
        listening = a;
        listenCode = code;
        listenTicks = LISTEN_TICKS;
        pendingConfirm = -1;
        warning = Component.literal("Pulsa el botón nuevo para «" + PadNames.action(a) + "»");
    }

    private void cancelListening() {
        listening = null;
        listenCode = -1;
        pendingConfirm = -1;
        warning = Component.empty();
    }

    private void resetDefaults() {
        PadSettings s = settings();
        s.bindings.clear();
        s.bindings.putAll(PadSettings.defaultBindings());
        ConsolePad.PADS.saveSettings();
        listening = null;
        listenCode = -1;
        pendingConfirm = -1;
        warning = Component.literal("Controles de fábrica restablecidos");
    }

    @Override
    public boolean listeningForPad() {
        return listening != null;
    }

    public PadAction listeningAction() {
        return listening;
    }

    public Component warning() {
        return warning;
    }

    @Override
    public void padButton(int code) {
        if (listening == null) return;
        Map<Integer, PadAction> b = settings().bindings;
        PadAction other = b.get(code);
        PadType type = padType();
        List<Integer> mine = codesOf(listening);
        if (other != null && other != listening) {
            if (pendingConfirm != code) { // avisa: ese boton ya lo usa otra accion
                pendingConfirm = code;
                listenTicks = LISTEN_TICKS;
                String btn = PadNames.button(code, type);
                warning = Component.literal("«" + PadNames.action(other) + "» ya usa " + btn + ". Pulsa " + btn + " otra vez para intercambiar");
                return;
            }
            for (int c : mine) b.remove(c); // intercambio: la otra accion se queda con el boton que dejas
            if (!mine.isEmpty()) b.put(mine.get(0), other);
        } else {
            for (int c : mine) b.remove(c);
        }
        b.put(code, listening);
        ConsolePad.PADS.saveSettings();
        warning = Component.literal("«" + PadNames.action(listening) + "» ahora es " + PadNames.button(code, type));
        listening = null;
        listenCode = -1;
        pendingConfirm = -1;
    }

    @Override
    public void tick() {
        if (listening != null && --listenTicks <= 0) cancelListening();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (listening != null && keyCode == 256) { // Esc cancela la escucha, no cierra la pantalla
            cancelListening();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return listening == null;
    }

    @Override
    public void onClose() {
        ConsolePad.PADS.saveSettings();
        Minecraft.getInstance().setScreen(parent);
    }

    /** Linea de 1 unidad entre dos puntos (Bresenham con fill): une la etiqueta con su boton. */
    private static void line(GuiGraphics g, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            g.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** El diagrama (silueta, lineas e iconos) va detras de las etiquetas: Screen.render pinta primero el fondo y luego los widgets. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.renderBackground(graphics, mouseX, mouseY, partialTick);
        PadSilhouette.draw(graphics, kind, lay.sx, lay.sy, lay.sw, lay.sh);
        PadType type = padType();
        int ic = lay.icon;
        // Adornos bajo los botones: sticks grandes, cruceta en cruz (o 4 botones redondos en los Joy-Con).
        int stick = Math.max(ic + 3, Math.round(lay.sw * 0.085f));
        boolean img = PadSilhouette.hasImage(kind); // la imagen ya trae sticks, cruceta y botones
        for (int code : img ? new int[0] : new int[] {PadState.L3, PadState.R3}) {
            int i = ControlsLayout.index(code);
            PadSilhouette.stick(graphics, lay.ax[i], lay.ay[i], stick);
        }
        int up = ControlsLayout.index(PadState.DPAD_UP), dn = ControlsLayout.index(PadState.DPAD_DOWN);
        if (img) {
            // nada: ya dibujado en la imagen
        } else if (kind == ControlsLayout.Kind.JOY_CON) {
            for (int code : new int[] {PadState.DPAD_UP, PadState.DPAD_DOWN, PadState.DPAD_LEFT, PadState.DPAD_RIGHT}) {
                int i = ControlsLayout.index(code);
                PadSilhouette.button(graphics, lay.ax[i], lay.ay[i], ic / 2 + 2);
            }
        } else {
            PadSilhouette.cross(graphics, lay.ax[up], (lay.ay[up] + lay.ay[dn]) / 2, ic + 1 + ic / 2 + 1, ic + 2);
        }
        for (int i = 0; i < ControlsLayout.CODES.length; i++) {
            int code = ControlsLayout.CODES[i];
            int[] box = lay.label[i];
            boolean sel = listenCode == code;
            int col = sel ? 0xFFFFFF55 : 0xFFE6E6E6;
            if (ControlsLayout.topLabel(code)) { // de la etiqueta de arriba, hacia abajo, hasta el boton
                int x0 = Math.max(box[0] + 4, Math.min(lay.ax[i], box[0] + box[2] - 4));
                line(graphics, x0, box[1] + box[3], lay.ax[i], lay.ay[i], col);
                continue;
            }
            boolean left = ControlsLayout.leftSide(code);
            int ex = left ? box[0] + box[2] : box[0], ey = box[1] + box[3] / 2;
            int kx = left ? lay.sx : lay.sx + lay.sw; // horizontal hasta el borde de la silueta y de ahi al boton
            line(graphics, ex, ey, kx, ey, col);
            line(graphics, kx, ey, lay.ax[i], lay.ay[i], col);
        }
        for (int i = 0; i < ControlsLayout.CODES.length; i++) {
            int code = ControlsLayout.CODES[i];
            if (listenCode == code) graphics.fill(lay.ax[i] - ic / 2 - 2, lay.ay[i] - ic / 2 - 2, lay.ax[i] + ic / 2 + 3, lay.ay[i] + ic / 2 + 3, 0xFFFFFF55);
            PadIcons.drawCentered(graphics, lay.ax[i], lay.ay[i], ic, code, type);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!warning.getString().isEmpty()) {
            String t = warning.getString();
            if (this.font.width(t) > this.width - 8) t = this.font.plainSubstrByWidth(t, this.width - 8 - this.font.width("…")) + "…";
            graphics.drawCenteredString(this.font, t, this.width / 2, this.height - 36, 0xFFFF55);
        }
    }

    // ---- ayudas para pruebas ----

    public ControlsLayout.Kind kind() {
        return kind;
    }

    public ControlsLayout.Result layout() {
        return lay;
    }

    public List<? extends Button> labelButtons() {
        return labels;
    }

    public int[] labelBox(int code) {
        return lay.label[ControlsLayout.index(code)];
    }

    public int[] labelCenter(int code) {
        int[] b = labelBox(code);
        return new int[] {b[0] + b[2] / 2, b[1] + b[3] / 2};
    }

    public int[] rowCenter(PadAction a) {
        List<Integer> codes = codesOf(a);
        return codes.isEmpty() ? null : labelCenter(codes.get(0));
    }

    public int[] resetCenter() {
        return center(reset);
    }

    public int[] backCenter() {
        return center(back);
    }

    public int[] resetBox() {
        return new int[] {reset.getX(), reset.getY(), reset.getWidth(), reset.getHeight()};
    }

    public int[] backBox() {
        return new int[] {back.getX(), back.getY(), back.getWidth(), back.getHeight()};
    }
}
