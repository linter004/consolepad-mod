package dev.consolepad.split;

import dev.consolepad.mixin.CreativeScreenAccessor;
import dev.consolepad.pad.PadState;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;

import java.util.List;

/**
 * LB/RB para cambiar de pestana en las pantallas que tienen (inventario creativo, paginas de los ajustes) y una flecha que marca la categoria actual
 * (pestanas del creativo y categorias del libro de recetas del modo supervivencia).
 */
public final class TabNav {
    private static final int ARROW = 0xFFFFD84A, EDGE = 0xFF3A2A00;

    /** Ultima vez que LB/RB cambio la pestana del creativo (para no abrir el teclado al pasar por la de busqueda). */
    public static volatile long lastPadTabNanos;

    private TabNav() {}

    /** Pestanas del creativo visibles en la pagina actual. */
    public static List<CreativeModeTab> creativeTabs(Screen screen) {
        return screen instanceof CreativeModeInventoryScreen c ? ((CreativeScreenAccessor) c).consolepad$currentPage().getVisibleTabs() : List.of();
    }

    public static int creativeIndex(Screen screen) {
        return creativeTabs(screen).indexOf(CreativeScreenAccessor.consolepad$selectedTab());
    }

    /** Pasa a la pestana anterior (-1) o siguiente (+1) dando la vuelta. */
    public static void changeCreativeTab(Screen screen, int dir) {
        List<CreativeModeTab> tabs = creativeTabs(screen);
        if (tabs.size() < 2) return;
        int idx = Math.max(0, tabs.indexOf(CreativeScreenAccessor.consolepad$selectedTab()));
        ((CreativeScreenAccessor) screen).consolepad$selectTab(tabs.get(Math.floorMod(idx + dir, tabs.size())));
    }

    /** LB/RB de una pantalla con pestanas; devuelve true si la pantalla las tiene. */
    public static boolean handle(Screen screen, PadState s) {
        int dir = s.pressed(PadState.LB) ? -1 : s.pressed(PadState.RB) ? 1 : 0;
        if (screen instanceof CreativeModeInventoryScreen) {
            if (dir != 0) {
                changeCreativeTab(screen, dir);
                lastPadTabNanos = System.nanoTime();
            }
            // LT/RT: pagina anterior/siguiente de la lista de objetos (5 filas)
            if (s.pressed(PadState.LT)) screen.mouseScrolled(0, 0, 0, 5.0);
            if (s.pressed(PadState.RT)) screen.mouseScrolled(0, 0, 0, -5.0);
            return true;
        }
        if (screen instanceof PlayerScreen ps && ps.hintTabs()) {
            if (dir != 0) ps.tab(dir);
            return true;
        }
        return false;
    }

    /** Pestana marcada (x, y, ancho, alto) y donde va la flecha: encima (true), debajo (false) o a la izquierda apuntando a la derecha (null). */
    public record Mark(int x, int y, int w, int h, Boolean above) {}

    public static Mark mark(Screen screen) {
        if (screen instanceof CreativeModeInventoryScreen c) {
            CreativeScreenAccessor a = (CreativeScreenAccessor) c;
            CreativeModeTab t = CreativeScreenAccessor.consolepad$selectedTab();
            if (!a.consolepad$currentPage().getVisibleTabs().contains(t)) return null;
            return new Mark(c.getGuiLeft() + a.consolepad$tabX(t), c.getGuiTop() + a.consolepad$tabY(t), 26, 32, a.consolepad$currentPage().isTop(t));
        }
        if (ConsoleCraft.bookVisible(screen)) {
            var tabs = ConsoleCraft.visibleTabs(screen);
            int i = ConsoleCraft.selectedTabIndex(screen);
            if (i < 0) return null;
            AbstractWidget w = tabs.get(i);
            return new Mark(w.getX(), w.getY(), w.getWidth(), w.getHeight(), null);
        }
        return null;
    }

    /** Flecha de la categoria actual; se dibuja sobre la pantalla (misma pose que la pantalla). */
    public static void render(GuiGraphics g, Screen screen, int w, int h, dev.consolepad.pad.PadType type) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        slotFrame(g, container);
        Mark m = mark(screen);
        if (m == null) {
            g.pose().popPose();
            return;
        }
        chips(g, screen, w, h, type);
        int cx = m.x() + m.w() / 2, cy = m.y() + m.h() / 2;
        if (m.above() == null) arrow(g, Math.max(1, m.x() - 8), cy, 2); // apunta a la derecha
        else if (m.above()) arrow(g, cx, Math.max(1, m.y() - 8), 1); // encima, apunta abajo
        else arrow(g, cx, Math.min(h - 8, m.y() + m.h() + 1), 3); // debajo, apunta arriba
        g.pose().popPose();
    }

    /** Marco blanco alrededor de la ranura bajo el cursor, como la seleccion de Bedrock (vanilla solo la aclara). */
    private static void slotFrame(GuiGraphics g, AbstractContainerScreen<?> screen) {
        net.minecraft.world.inventory.Slot slot = screen.getSlotUnderMouse();
        if (slot == null) return;
        int x = screen.getGuiLeft() + slot.x - 1, y = screen.getGuiTop() + slot.y - 1;
        var cur = ContextSwitcher.current();
        int c = 0xFF000000 | PlayerLabels.accent(cur == null ? 0 : cur.slotIndex), e = 0xFF000000; // anillo del color del jugador (blanco si apago los colores)
        g.fill(x - 1, y - 1, x + 19, y, e); // anillo oscuro por fuera
        g.fill(x - 1, y + 18, x + 19, y + 19, e);
        g.fill(x - 1, y, x, y + 18, e);
        g.fill(x + 18, y, x + 19, y + 18, e);
        g.fill(x, y, x + 18, y + 1, c); // anillo blanco
        g.fill(x, y + 17, x + 18, y + 18, c);
        g.fill(x, y + 1, x + 1, y + 17, c);
        g.fill(x + 17, y + 1, x + 18, y + 17, c);
    }

    /** Ficha con el boton del mando, como en Bedrock: solo el icono del boton, sin recuadro. */
    private static void chip(GuiGraphics g, int x, int y, int code, dev.consolepad.pad.PadType type) {
        int cw = 22, ch = 14; // sin rectangulo: solo el boton
        PadIcons.drawCentered(g, x + cw / 2, y + ch / 2, 14, code, type);
    }

    /** LB y RB a los lados de las pestanas de categoria (arriba en el creativo; encima y debajo de la columna del libro de recetas). */
    private static void chips(GuiGraphics g, Screen screen, int w, int h, dev.consolepad.pad.PadType type) {
        if (screen instanceof CreativeModeInventoryScreen c) {
            CreativeScreenAccessor a = (CreativeScreenAccessor) c;
            int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, y = 0;
            for (CreativeModeTab t : a.consolepad$currentPage().getVisibleTabs()) {
                if (!a.consolepad$currentPage().isTop(t)) continue;
                int x = c.getGuiLeft() + a.consolepad$tabX(t);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x + 26);
                y = c.getGuiTop() + a.consolepad$tabY(t);
            }
            if (minX == Integer.MAX_VALUE) return;
            int cy = y + 8;
            chip(g, Math.max(1, minX - 25), cy, PadState.LB, type);
            chip(g, Math.min(w - 23, maxX + 3), cy, PadState.RB, type);
            // LT/RT pasan de pagina de la lista: a la derecha del panel, arriba y abajo de la lista de objetos
            int px = Math.min(w - 23, c.getGuiLeft() + 195 + 3);
            chip(g, px, c.getGuiTop() + 18, PadState.LT, type);
            chip(g, px, c.getGuiTop() + 127 - 14, PadState.RT, type);
        } else if (ConsoleCraft.bookVisible(screen)) {
            var tabs = ConsoleCraft.visibleTabs(screen);
            if (tabs.size() < 2) return;
            AbstractWidget first = tabs.get(0), last = tabs.get(tabs.size() - 1);
            int x = Math.max(1, first.getX() + first.getWidth() / 2 - 11);
            chip(g, x, Math.max(1, first.getY() - 17), PadState.LB, type);
            chip(g, x, Math.min(h - 15, last.getY() + last.getHeight() + 3), PadState.RB, type);
        }
    }

    /** Triangulo de 7 px con borde: dir 1 = mira abajo, 2 = mira a la derecha, 3 = mira arriba. */
    private static void arrow(GuiGraphics g, int x, int y, int dir) {
        for (int pass = 0; pass < 2; pass++) {
            int grow = pass == 0 ? 1 : 0;
            int color = pass == 0 ? EDGE : ARROW;
            for (int r = 0; r < 4; r++) {
                int half = 3 - r + grow;
                if (dir == 1) g.fill(x - half, y + r - grow, x + half + 1, y + r + 1, color);
                else if (dir == 3) g.fill(x - half, y + 6 - r, x + half + 1, y + 7 - r + grow, color);
                else g.fill(x + r - grow, y - half, x + r + 1, y + half + 1, color);
            }
        }
    }
}
