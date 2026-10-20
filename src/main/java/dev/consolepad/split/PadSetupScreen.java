package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asignar mandos antes de entrar en un mundo (boton "Mandos" del titulo), como la pantalla de inicio de sesion de una consola: cuatro cuadrantes, uno por
 * jugador. Cada mando conectado aparece en su jugador; el que pulse A "se une" al primer cuadrante que nadie haya reclamado todavia (si estaba en otro, se
 * intercambian), con lo que el orden lo decide quien pulsa primero y no el orden en que Windows los lista. Un mando que ya se ha unido y pulsa A otra vez
 * continua (cierra la pantalla); B suelta su cuadrante, y B de un mando sin unir vuelve. La pantalla se queda con todos los botones de todos los mandos
 * (listeningForPad), el raton sigue pudiendo pulsar "Listo".
 */
public final class PadSetupScreen extends Screen implements PlayerScreen {
    private final Screen parent;
    private final boolean[] claimed = new boolean[PadManager.MAX_PLAYERS];
    /** Se espera a que todos los botones esten sueltos: el que abre la pantalla (A sobre el boton) sigue pulsado ese fotograma. */
    private boolean armed;
    private int[][] cell = new int[PadManager.MAX_PLAYERS][];

    public PadSetupScreen(Screen parent) {
        super(I18n.lit("Asignar mandos"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(I18n.lit("Listo"), b -> this.onClose()).bounds(this.width / 2 - 50, this.height - 24, 100, 20).build());
    }

    @Override
    public boolean listeningForPad() {
        return true;
    }

    /** Cada boton que llega ya se trata en {@link #render} con todos los mandos a la vez. */
    @Override
    public void padButton(int code) {}

    public boolean claimed(int slot) {
        return claimed[slot];
    }

    /** A en un mando: se une al primer cuadrante sin reclamar; si ya estaba unido, continua. Publico para las pruebas. */
    public void pressA(PadManager.Device d) {
        PadManager pads = ConsolePad.PADS;
        PadManager.Slot own = pads.slotOfDevice(d);
        if (own != null && claimed[own.index]) {
            this.onClose();
            return;
        }
        int q = -1;
        for (int i = 0; i < claimed.length; i++) if (!claimed[i]) { q = i; break; }
        if (q < 0) return;
        if (own == null || own.index != q) pads.assign(d, q);
        claimed[q] = true;
    }

    /** B en un mando: suelta su cuadrante si estaba unido; si no, vuelve. */
    public void pressB(PadManager.Device d) {
        PadManager.Slot own = ConsolePad.PADS.slotOfDevice(d);
        if (own != null && claimed[own.index]) claimed[own.index] = false;
        else this.onClose();
    }

    private void listen() {
        PadManager pads = ConsolePad.PADS;
        if (!armed) {
            if (!pads.anyDown()) armed = true;
            return;
        }
        for (PadManager.Device d : pads.allDevices()) {
            if (Minecraft.getInstance().screen != this) return;
            if (d.state.pressed(PadState.START)) {
                this.onClose();
                return;
            }
            if (d.state.pressed(PadState.A)) pressA(d);
            else if (d.state.pressed(PadState.B)) pressB(d);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Fondo liso, como el de la bienvenida: el difuminado del titulo dejaba el texto ilegible. */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0C0C14);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        listen();
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFD84A);
        String hint = I18n.tr("Pulsa A en cada mando, por orden, para unirte · B sale · A otra vez continúa");
        g.drawCenteredString(this.font, this.font.plainSubstrByWidth(hint, this.width - 8), this.width / 2, 20, 0xFFB0B0B0);
        int top = 34, bottom = this.height - 30, gap = 6;
        int qw = (this.width - 3 * gap) / 2, qh = (bottom - top - gap) / 2;
        for (int i = 0; i < PadManager.MAX_PLAYERS; i++) {
            int x = gap + (i % 2) * (qw + gap), y = top + (i / 2) * (qh + gap);
            cell[i] = new int[] {x, y, qw, qh};
            drawCell(g, i, x, y, qw, qh);
        }
        int free = 0;
        for (PadManager.Device d : ConsolePad.PADS.allDevices()) if (ConsolePad.PADS.slotOfDevice(d) == null) free++;
        if (free > 0) {
            String extra = I18n.fmt(free == 1 ? "{0} mando sin jugador (todos los cuadrantes ocupados)" : "{0} mandos sin jugador", free);
            g.drawCenteredString(this.font, extra, this.width / 2, this.height - 38, 0xFFFFD84A);
        }
    }

    private void drawCell(GuiGraphics g, int i, int x, int y, int w, int h) {
        PadManager.Slot s = ConsolePad.PADS.slot(i);
        PadManager.Device d = s.device;
        boolean on = d != null;
        int border = claimed[i] ? 0xFF55FF55 : on ? 0xFF9A9A9A : 0xFF4A4A4A;
        g.fill(x, y, x + w, y + h, border);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, claimed[i] ? 0xFF1C2C1C : 0xFF1C1C2C);
        String who = I18n.fmt("Jugador {0}", i + 1);
        g.drawString(this.font, who, x + 6, y + 5, claimed[i] ? 0xFF55FF55 : 0xFFFFFFFF, false);
        ControlsLayout.Kind kind = ControlsLayout.kindFor(d, s.settings.iconStyle);
        int footer = 14;
        int availH = h - 18 - footer - 4, availW = w - 12;
        double aspect = PadSilhouette.hasImage(kind) ? PadSilhouette.imageAspect(kind) : 1.4;
        int ph = Math.min(availH, (int) (availW / aspect)), pw = (int) (ph * aspect);
        int px = x + (w - pw) / 2, py = y + 16 + (availH - ph) / 2;
        if (ph > 8) {
            if (!on) g.setColor(.35f, .35f, .35f, 1f); // sin mando: la silueta apagada
            else if (!claimed[i]) g.setColor(.7f, .7f, .7f, 1f);
            PadSilhouette.draw(g, kind, px, py, pw, ph);
            g.setColor(1f, 1f, 1f, 1f);
        }
        String line;
        int color;
        if (!on) {
            line = I18n.tr("Sin mando");
            color = 0xFF808080;
        } else if (claimed[i]) {
            line = d.name;
            color = 0xFFFFFFFF;
        } else {
            line = I18n.tr("Pulsa A");
            color = 0xFFFFD84A;
        }
        String shown = this.font.plainSubstrByWidth(line, w - 14);
        g.drawCenteredString(this.font, shown, x + w / 2, y + h - footer + 2, color);
        if (on && !claimed[i]) PadIcons.drawCentered(g, x + w / 2 - this.font.width(shown) / 2 - 8, y + h - footer + 6, 11, PadState.A, d.type);
    }

    /** Pruebas: caja de un cuadrante {x, y, w, h} del ultimo dibujo (null si aun no se ha dibujado). */
    public int[] cell(int i) {
        return cell[i];
    }
}
