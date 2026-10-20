package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Asignar mandos: una fila por jugador con el mando que lleva. Pulsar una fila pide "pulsa un boton en el mando que quieres para el jugador N": el primer
 * mando que pulse algo pasa a ese jugador y los dos mandos se intercambian (el que ya llevaba el jugador pasa al jugador que tenia el nuevo). Pulsar un
 * boton en el mando que ya lleva ese jugador cancela. Los ajustes pertenecen al jugador, no al mando. Se abre desde los ajustes de cada jugador.
 */
public final class PadAssignScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final Button[] rows = new Button[PadManager.MAX_PLAYERS];
    private Button back;
    private int target = -1;
    /** Se espera a que todos los botones esten sueltos antes de oir: el clic que abre la espera (A) sigue pulsado ese fotograma. */
    private boolean armed;

    public PadAssignScreen(int slot, Screen parent) {
        super(slot, I18n.lit("Asignar mandos"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int n = PadManager.MAX_PLAYERS + 1;
        for (int i = 0; i < PadManager.MAX_PLAYERS; i++) {
            final int p = i;
            rows[i] = addRow(i, n, I18n.lit(""), b -> startListening(p));
        }
        back = addRow(PadManager.MAX_PLAYERS, n, I18n.lit("Atrás"), b -> this.onClose());
        refreshLabels();
    }

    /** Empieza a esperar un boton para el jugador p (como pulsar su fila). */
    public void startListening(int p) {
        target = p;
        armed = false;
        refreshLabels();
    }

    private String describe(int p) {
        PadManager.Device d = ConsolePad.PADS.slot(p).device;
        String who = I18n.fmt(p == slot ? "Jugador {0} (tú)" : "Jugador {0}", p + 1);
        String pad = d == null ? I18n.tr("sin mando") : d.name + " [" + d.type + "]";
        String text = who + ": " + pad;
        return this.font.plainSubstrByWidth(text, ROW_W - 2 * (thumbW() + 8));
    }

    /** Ancho de la miniatura de la silueta que lleva cada fila. */
    private static int thumbW() {
        return (int) Math.round((ROW_H - 4) * 1.35);
    }

    private void refreshLabels() {
        for (int i = 0; i < rows.length; i++) {
            if (rows[i] == null) continue;
            rows[i].setMessage(I18n.lit(i == target ? I18n.fmt("Jugador {0}: pulsa un botón...", i + 1) : describe(i)));
        }
    }

    @Override
    public boolean listeningForPad() {
        return target >= 0;
    }

    /** El boton pulsado por el mando de quien tiene abierta la pantalla mientras se escucha: no hace nada (la asignacion la decide render con todos los mandos). */
    @Override
    public void padButton(int code) {}

    /** Jugador del que se espera mando (-1 = ninguno). */
    public int listening() {
        return target;
    }

    public int[] rowCenter(int p) {
        return center(rows[p]);
    }

    /** Una vez por fotograma: con la espera armada, el primer mando que pulse algo se asigna al jugador esperado. */
    private void listen() {
        if (target < 0) return;
        PadManager pads = ConsolePad.PADS;
        if (!armed) {
            if (!pads.anyDown()) armed = true;
            return;
        }
        PadManager.Device d = pads.firstPressedDevice();
        if (d == null) return;
        pads.assign(d, target);
        target = -1;
        refreshLabels();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        listen();
        refreshLabels();
        super.render(graphics, mouseX, mouseY, partialTick);
        for (int i = 0; i < rows.length; i++) { // la silueta del mando de cada jugador, en su fila (apagada si no tiene)
            if (rows[i] == null) continue;
            PadManager.Slot sl = ConsolePad.PADS.slot(i);
            ControlsLayout.Kind kind = ControlsLayout.kindFor(sl.device, sl.settings.iconStyle);
            int h = ROW_H - 4;
            double aspect = PadSilhouette.hasImage(kind) ? PadSilhouette.imageAspect(kind) : 1.4;
            int w = (int) Math.round(h * aspect);
            if (sl.device == null) graphics.setColor(.35f, .35f, .35f, 1f);
            PadSilhouette.draw(graphics, kind, rows[i].getX() + 3, rows[i].getY() + 2, w, h);
            graphics.setColor(1f, 1f, 1f, 1f);
        }
        int free = 0;
        for (PadManager.Device d : ConsolePad.PADS.allDevices()) if (ConsolePad.PADS.slotOfDevice(d) == null) free++;
        String hint = target >= 0 ? I18n.fmt("Pulsa un botón en el mando que quieres para el jugador {0} (el mismo mando cancela)", target + 1)
                : free > 0 ? I18n.fmt(free == 1 ? "{0} mando sin jugador" : "{0} mandos sin jugador", free) : I18n.tr("Elige un jugador y pulsa un botón en su nuevo mando");
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(hint, this.width - 8), this.width / 2, 20, 0xFFFFD84A);
    }
}
