package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Remapeo de los controles de un jugador: lista de acciones con su boton, en paginas si la region es baja. Se elige una accion, se pulsa el
 * boton nuevo del mando de ESE jugador y se guarda en su slotN.json. Si el boton ya lo usa otra accion avisa y pide pulsarlo otra vez
 * para intercambiarlos; "Restablecer" vuelve a los botones de fabrica.
 */
public final class PlayerControlsScreen extends PlayerMenuScreen {
    /** Ticks que se espera un boton antes de cancelar (5 s). */
    private static final int LISTEN_TICKS = 100;

    private final Screen parent;
    private int page;
    private PadAction listening;
    private int listenTicks;
    private int pendingConfirm = -1;
    private Component warning = Component.empty();
    private final List<Button> rows = new ArrayList<>();
    private final List<PadAction> rowActions = new ArrayList<>();
    private Button prev, next, reset, back;
    private int perPage = 5;

    public PlayerControlsScreen(int slot, Screen parent) {
        super(slot, Component.literal("Controles del jugador " + (slot + 1)));
        this.parent = parent;
    }

    private PadType padType() {
        PadManager.Device d = ConsolePad.PADS.slot(slot).device;
        return d == null ? PadType.XBOX : d.type;
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

    private Component rowLabel(PadAction a) {
        if (a == listening) return Component.literal(PadNames.action(a) + ": > pulsa un botón <");
        return Component.literal(PadNames.action(a));
    }

    /** Iconos de los botones de cada accion, pegados al borde derecho de su fila (el texto de la fila es solo la accion). */
    private void renderRowIcons(GuiGraphics g) {
        PadType type = padType();
        for (int i = 0; i < rows.size(); i++) {
            PadAction a = rowActions.get(i);
            if (a == listening) continue;
            Button b = rows.get(i);
            List<Integer> codes = codesOf(a);
            int total = 0;
            for (int c : codes) total += PadIcons.width(this.font, c, type) + 2;
            int x = b.getX() + b.getWidth() - 4 - total;
            int y = b.getY() + (b.getHeight() - PadIcons.H) / 2;
            for (int c : codes) x += PadIcons.draw(g, this.font, x, y, c, type) + 2;
        }
    }

    @Override
    protected void init() {
        rows.clear();
        rowActions.clear();
        PadAction[] all = PadAction.values();
        int rowH = 20;
        int top = 20, bottomReserve = 62;
        perPage = Math.max(3, Math.min(all.length, (this.height - top - bottomReserve) / rowH));
        int pages = (all.length + perPage - 1) / perPage;
        page = Math.max(0, Math.min(page, pages - 1));
        int x = this.width / 2 - ROW_W / 2;
        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= all.length) break;
            PadAction a = all[idx];
            Button b = addRenderableWidget(Button.builder(rowLabel(a), btn -> startListening(a)).bounds(x, top + i * rowH, ROW_W, 18).build());
            rows.add(b);
            rowActions.add(a);
        }
        int navY = this.height - 44;
        prev = null;
        next = null;
        if (pages > 1) {
            prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> changePage(-1)).bounds(x, navY, 40, 18).build());
            next = addRenderableWidget(Button.builder(Component.literal(">"), b -> changePage(1)).bounds(x + ROW_W - 40, navY, 40, 18).build());
        }
        reset = addRenderableWidget(Button.builder(Component.literal("Restablecer"), b -> resetDefaults()).bounds(x, this.height - 22, 88, 18).build());
        back = addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> this.onClose()).bounds(x + ROW_W - 88, this.height - 22, 88, 18).build());
    }

    private void changePage(int d) {
        cancelListening();
        page += d;
        rebuildWidgets();
    }

    private void refreshLabels() {
        for (int i = 0; i < rows.size(); i++) rows.get(i).setMessage(rowLabel(rowActions.get(i)));
    }

    private void startListening(PadAction a) {
        listening = a;
        listenTicks = LISTEN_TICKS;
        pendingConfirm = -1;
        warning = Component.literal("Pulsa el botón nuevo para «" + PadNames.action(a) + "»");
        refreshLabels();
    }

    private void cancelListening() {
        listening = null;
        pendingConfirm = -1;
        warning = Component.empty();
        refreshLabels();
    }

    private void resetDefaults() {
        PadSettings s = settings();
        s.bindings.clear();
        s.bindings.putAll(PadSettings.defaultBindings());
        ConsolePad.PADS.saveSettings();
        listening = null;
        pendingConfirm = -1;
        warning = Component.literal("Controles de fábrica restablecidos");
        refreshLabels();
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
        pendingConfirm = -1;
        refreshLabels();
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

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderRowIcons(graphics);
        if (!warning.getString().isEmpty()) {
            graphics.drawCenteredString(this.font, warning, this.width / 2, this.height - 58, 0xFFFF55);
        }
        if (prev != null) {
            int pages = (PadAction.values().length + perPage - 1) / perPage;
            graphics.drawCenteredString(this.font, "Página " + (page + 1) + "/" + pages, this.width / 2, this.height - 39, 0xFFFFFF);
        }
    }

    public List<Button> rowButtons() {
        return rows;
    }

    public int[] rowCenter(PadAction a) {
        int i = rowActions.indexOf(a);
        return i < 0 ? null : center(rows.get(i));
    }

    public int[] resetCenter() {
        return center(reset);
    }

    public int[] backCenter() {
        return center(back);
    }

    public int[] nextCenter() {
        return next == null ? null : center(next);
    }

    public int perPage() {
        return perPage;
    }
}
