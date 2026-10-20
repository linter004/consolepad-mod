package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Perfiles locales (desde los ajustes de un jugador): la lista de perfiles guardados, "Ninguno" para soltar el del jugador y "Nuevo perfil...". Elegir un
 * perfil abre su pantalla (usarlo, copiarle los ajustes del jugador, borrarlo). Se pasa por paginas si no caben en la region del jugador.
 */
public final class ProfilesScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final List<Button> rows = new ArrayList<>();
    private Button back, prev, next;
    private int page;
    private String message = "";

    public ProfilesScreen(int slot, Screen parent) {
        super(slot, I18n.lit("Perfiles del jugador " + (slot + 1)));
        this.parent = parent;
    }

    @Override
    protected void init() {
        rows.clear();
        List<Profiles.Profile> list = Profiles.all();
        // Filas: Ninguno, cada perfil, Nuevo.
        int total = list.size() + 2;
        int footerY = footerY();
        int perPage = Math.max(3, (footerY - 34) / PITCH);
        int pages = Math.max(1, (total + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int x = this.width / 2 - ROW_W / 2;
        for (int k = 0; k < perPage; k++) {
            int idx = page * perPage + k;
            if (idx >= total) break;
            Button b;
            int y = 34 + k * PITCH;
            if (idx == 0) {
                b = Button.builder(I18n.lit((Profiles.of(slot) == null ? "• " : "") + I18n.tr("Ninguno")), btn -> {
                    String err = Profiles.clear(slot);
                    message = err == null ? I18n.tr("Perfil quitado") : err;
                    this.rebuildWidgets();
                }).bounds(x, y, ROW_W, ROW_H).build();
            } else if (idx == total - 1) {
                final boolean full = list.size() >= Profiles.MAX;
                b = Button.builder(I18n.lit(full ? I18n.fmt("Nuevo perfil (máximo {0})", Profiles.MAX) : I18n.tr("Nuevo perfil...")), btn -> {
                    if (!full) Minecraft.getInstance().setScreen(new NewProfileScreen(slot, this));
                }).bounds(x, y, ROW_W, ROW_H).build();
            } else {
                Profiles.Profile p = list.get(idx - 1);
                boolean mine = p.id.equals(Profiles.of(slot) == null ? "" : Profiles.of(slot).id);
                int user = Profiles.slotUsing(p);
                String tag = mine ? "• " : "";
                String suffix = user >= 0 && !mine ? " (J" + (user + 1) + ")" : "";
                String text = this.font.plainSubstrByWidth(tag + p.name + suffix + " - " + (p.customSkin != null ? p.customSkin : Profiles.SKIN_NAMES[p.skin % 9]), ROW_W - 8);
                b = Button.builder(I18n.lit(text), btn -> Minecraft.getInstance().setScreen(new ProfileScreen(slot, p, this))).bounds(x, y, ROW_W, ROW_H).build();
            }
            rows.add(addRenderableWidget(b));
        }
        if (pages > 1) {
            prev = addRenderableWidget(Button.builder(I18n.lit("<"), b -> {
                page = (page + pages - 1) % pages;
                this.rebuildWidgets();
            }).bounds(x, footerY, 30, ROW_H).build());
            back = addRenderableWidget(Button.builder(I18n.lit("Atrás"), b -> this.onClose()).bounds(x + 34, footerY, ROW_W - 68, ROW_H).build());
            next = addRenderableWidget(Button.builder(I18n.lit(">"), b -> {
                page = (page + 1) % pages;
                this.rebuildWidgets();
            }).bounds(x + ROW_W - 30, footerY, 30, ROW_H).build());
        } else {
            prev = next = null;
            back = addRenderableWidget(Button.builder(I18n.lit("Atrás"), b -> this.onClose()).bounds(x, footerY, ROW_W, ROW_H).build());
        }
    }

    @Override
    public boolean hintTabs() {
        return prev != null;
    }

    @Override
    public void tab(int dir) {
        if (prev == null) return;
        int footerY = footerY();
        int perPage = Math.max(3, (footerY - 34) / PITCH);
        int pages = Math.max(1, (Profiles.all().size() + 2 + perPage - 1) / perPage);
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
        if (!message.isEmpty()) graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(message, this.width - 8), this.width / 2, 20, 0xFFFFD84A);
    }

    /** Pruebas: filas dibujadas y su centro. */
    public int rowCount() {
        return rows.size();
    }

    public int[] rowCenter(int i) {
        return center(rows.get(i));
    }
}
