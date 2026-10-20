package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor de la rueda de acciones rapidas de un jugador: la lista de entradas (hasta 8), "Añadir" y "Usar la de todos" (borra la propia). Elegir una entrada la
 * abre para cambiar su nombre y su comando. Mientras el jugador no la toque usa la de config/consolepad/wheel.json, comun a todos.
 */
public final class WheelEditorScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final List<Button> rows = new ArrayList<>();
    private int page;
    private Button prev, next;

    public WheelEditorScreen(int slot, Screen parent) {
        super(slot, I18n.lit("Rueda del jugador " + (slot + 1)));
        this.parent = parent;
    }

    private int total() {
        List<QuickWheel.Entry> list = QuickWheel.listFor(settings());
        return list.size() + (list.size() < QuickWheel.MAX_ENTRIES ? 1 : 0) + (settings().wheel != null ? 1 : 0);
    }

    private int perPage() {
        return Math.max(3, (footerY() - 34) / PITCH);
    }

    @Override
    protected void init() {
        rows.clear();
        List<QuickWheel.Entry> list = QuickWheel.listFor(settings());
        // Filas: cada entrada, Añadir (si caben mas) y Usar la de todos (si tiene la suya).
        int total = total();
        int footerY = footerY();
        int perPage = perPage();
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int x = this.width / 2 - ROW_W / 2;
        for (int k = 0; k < perPage; k++) {
            int idx = page * perPage + k;
            if (idx >= total) break;
            int y = 28 + k * PITCH;
            Button b;
            if (idx < list.size()) {
                final int e = idx;
                QuickWheel.Entry en = list.get(idx);
                b = Button.builder(I18n.lit(this.font.plainSubstrByWidth(en.label() + " - /" + en.command(), ROW_W - 8)),
                        btn -> Minecraft.getInstance().setScreen(new WheelEntryScreen(slot, e, this))).bounds(x, y, ROW_W, ROW_H).build();
            } else if (idx == list.size() && list.size() < QuickWheel.MAX_ENTRIES) {
                b = Button.builder(I18n.lit("Añadir entrada..."), btn -> Minecraft.getInstance().setScreen(new WheelEntryScreen(slot, -1, this))).bounds(x, y, ROW_W, ROW_H).build();
            } else {
                b = Button.builder(I18n.lit("Usar la rueda de todos"), btn -> {
                    settings().wheel = null;
                    dev.consolepad.ConsolePad.PADS.saveSettings();
                    this.rebuildWidgets();
                }).bounds(x, y, ROW_W, ROW_H).build();
            }
            rows.add(addRenderableWidget(b));
        }
        if (pages > 1) {
            prev = addRenderableWidget(Button.builder(I18n.lit("<"), b -> tab(-1)).bounds(x, footerY, 30, ROW_H).build());
            addRenderableWidget(Button.builder(I18n.lit("Atrás"), b -> this.onClose()).bounds(x + 34, footerY, ROW_W - 68, ROW_H).build());
            next = addRenderableWidget(Button.builder(I18n.lit(">"), b -> tab(1)).bounds(x + ROW_W - 30, footerY, 30, ROW_H).build());
        } else {
            prev = next = null;
            addRenderableWidget(Button.builder(I18n.lit("Atrás"), b -> this.onClose()).bounds(x, footerY, ROW_W, ROW_H).build());
        }
    }

    @Override
    public boolean hintTabs() {
        return prev != null;
    }

    @Override
    public void tab(int dir) {
        if (prev == null) return;
        int pages = Math.max(1, (total() + perPage() - 1) / perPage());
        page = Math.floorMod(page + dir, pages);
        this.rebuildWidgets();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        String hint = settings().wheel == null ? I18n.tr("Es la rueda de todos; al cambiarla será solo tuya") : I18n.fmt("Rueda propia del jugador {0}", slot + 1);
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(hint, this.width - 8), this.width / 2, 18, 0xFFA0A0A0);
    }

    public int rowCount() {
        return rows.size();
    }

    public int[] rowCenter(int i) {
        return center(rows.get(i));
    }
}
