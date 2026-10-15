package dev.consolepad.split;

import dev.consolepad.mixin.ContainerScreenInvoker;
import dev.consolepad.mixin.RecipeBookComponentAccessor;
import dev.consolepad.mixin.RecipeBookPageAccessor;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Crafteo estilo consola sobre el libro de recetas de vanilla (inventario, mesa de crafteo, hornos): L3 abre o cierra el libro, LB/RB cambian de
 * categoria, el D-pad salta entre recetas, A fabrica la receta bajo el cursor (la coloca con el paquete de vanilla {@code ServerboundPlaceRecipePacket}, que ya
 * rellena la cuadricula en el servidor, y recoge el resultado con un mover rapido) y un panel muestra los ingredientes con los que faltan en rojo.
 * No toca el servidor. Una instancia de estado por jugador (slot 0 a 3).
 */
public final class ConsoleCraft {
    private static final class State {
        long pendingUntil; // nanoTime hasta el que se espera el resultado de una receta recien colocada
    }

    private static final State[] STATE = {new State(), new State(), new State(), new State()};
    /** Ultimo dibujo por slot (para el autotest y la ayuda de botones). */
    public static final boolean[] hoverRecipe = new boolean[4];
    public static final String[] lastPanel = {"", "", "", ""};
    /** Recetas fabricadas con A desde el arranque, por slot (pruebas). */
    public static final int[] crafted = new int[4];
    /** Si la ultima receta pulsada con A estaba marcada como fabricable (diagnostico). */
    /** Veces que Y ha fabricado "todo" en cada slot (diagnostico y pruebas). */
    public static final int[] craftedAll = new int[4];
    public static final boolean[] lastCraftable = new boolean[4];

    private ConsoleCraft() {}

    public static boolean applies(Screen screen) {
        return screen instanceof AbstractContainerScreen<?> && screen instanceof RecipeUpdateListener;
    }

    private static RecipeBookComponent book(Screen screen) {
        return ((RecipeUpdateListener) screen).getRecipeBookComponent();
    }

    public static boolean bookVisible(Screen screen) {
        return applies(screen) && book(screen).isVisible();
    }

    private static List<RecipeButton> recipeButtons(RecipeBookComponent book) {
        List<RecipeButton> out = new ArrayList<>();
        for (RecipeButton b : ((RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book).consolepad$page()).consolepad$buttons()) {
            if (b.visible) out.add(b);
        }
        return out;
    }

    private static boolean inside(AbstractWidget w, double x, double y) {
        return x >= w.getX() && x < w.getX() + w.getWidth() && y >= w.getY() && y < w.getY() + w.getHeight();
    }

    /** Botones de receta visibles de la pagina actual del libro (vacia si el libro esta cerrado). */
    public static List<RecipeButton> visibleRecipes(Screen screen) {
        return bookVisible(screen) ? recipeButtons(book(screen)) : new ArrayList<>();
    }

    /** Receta bajo el cursor (en unidades de la pantalla), o null. */
    public static RecipeButton recipeAt(Screen screen, double x, double y) {
        if (!bookVisible(screen)) return null;
        for (RecipeButton b : recipeButtons(book(screen))) {
            if (inside(b, x, y)) return b;
        }
        return null;
    }

    /** Destinos extra del D-pad cuando el libro esta abierto: recetas, pagina anterior/siguiente y el filtro de "solo fabricables". Centro y tamano. */
    public static List<double[]> extraTargets(Screen screen) {
        List<double[]> out = new ArrayList<>();
        if (!bookVisible(screen)) return out;
        RecipeBookComponent book = book(screen);
        RecipeBookComponentAccessor acc = (RecipeBookComponentAccessor) book;
        for (RecipeButton b : recipeButtons(book)) out.add(center(b));
        RecipeBookPageAccessor page = (RecipeBookPageAccessor) acc.consolepad$page();
        if (page.consolepad$forward().visible) out.add(center(page.consolepad$forward()));
        if (page.consolepad$back().visible) out.add(center(page.consolepad$back()));
        if (acc.consolepad$filter() != null && acc.consolepad$filter().visible) out.add(center(acc.consolepad$filter()));
        return out;
    }

    private static double[] center(AbstractWidget w) {
        return new double[] {w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, w.getWidth(), w.getHeight()};
    }

    /** Categorias visibles del libro, en orden. */
    public static List<RecipeBookTabButton> visibleTabs(Screen screen) {
        List<RecipeBookTabButton> out = new ArrayList<>();
        if (!bookVisible(screen)) return out;
        for (RecipeBookTabButton t : ((RecipeBookComponentAccessor) book(screen)).consolepad$tabs()) {
            if (t.visible) out.add(t);
        }
        return out;
    }

    /** Indice de la categoria seleccionada entre las visibles (o -1). */
    public static int selectedTabIndex(Screen screen) {
        List<RecipeBookTabButton> tabs = visibleTabs(screen);
        return tabs.indexOf(((RecipeBookComponentAccessor) book(screen)).consolepad$selectedTab());
    }

    /** Pasa a la categoria anterior (-1) o siguiente (+1), dando la vuelta. */
    public static void changeTab(Screen screen, int dir) {
        List<RecipeBookTabButton> tabs = visibleTabs(screen);
        if (tabs.size() < 2) return;
        int idx = tabs.indexOf(((RecipeBookComponentAccessor) book(screen)).consolepad$selectedTab());
        RecipeBookTabButton next = tabs.get(Math.floorMod(idx + dir, tabs.size()));
        book(screen).mouseClicked(next.getX() + next.getWidth() / 2.0, next.getY() + next.getHeight() / 2.0, 0);
    }

    /** Pasa de pagina de recetas: +1 siguiente, -1 anterior. */
    public static boolean changePage(Screen screen, int dir) {
        if (!bookVisible(screen)) return false;
        RecipeBookPageAccessor page = (RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book(screen)).consolepad$page();
        AbstractWidget w = dir > 0 ? page.consolepad$forward() : page.consolepad$back();
        if (!w.visible) return false;
        book(screen).mouseClicked(w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, 0);
        return true;
    }

    /** Abre o cierra el libro con el mismo boton que usa el raton (recoloca la pantalla). */
    public static boolean toggleBook(Screen screen) {
        if (!applies(screen)) return false;
        for (var child : screen.children()) {
            if (child instanceof ImageButton b) {
                b.onPress();
                return true;
            }
        }
        return false;
    }

    /**
     * Entrada del mando en una pantalla con libro de recetas, una vez por fotograma, con el cursor en unidades de la pantalla. Devuelve true si A
     * se ha gastado en fabricar (el llamador no debe hacer ademas el clic normal).
     */
    public static boolean handle(Minecraft mc, Screen screen, PadState s, int slot, double cx, double cy) {
        if (!applies(screen)) return false;
        State st = STATE[slot];
        finishPending(screen, st);
        if (s.pressed(PadState.L3)) {
            toggleBook(screen);
            return false;
        }
        if (!bookVisible(screen)) return false;
        if (s.pressed(PadState.LT)) changePage(screen, -1); // LT/RT: pagina de recetas
        if (s.pressed(PadState.RT)) changePage(screen, 1);
        if (s.pressed(PadState.LB)) changeTab(screen, -1);
        if (s.pressed(PadState.RB)) changeTab(screen, 1);
        if (s.pressed(PadState.Y)) { // Y sobre una receta: fabrica todo lo que dan los ingredientes (como mayus + clic en vanilla)
            RecipeButton b = recipeAt(screen, cx, cy);
            if (b != null) {
                craftAll(mc, screen, st, b, cx, cy);
                craftedAll[slot]++;
                return true;
            }
        }
        if (s.pressed(PadState.A)) {
            RecipeButton b = recipeAt(screen, cx, cy);
            if (b != null) {
                craft(screen, st, b, cx, cy);
                crafted[slot]++;
                return true;
            }
        }
        return false;
    }

    /** Fila del stick derecho: pasa de pagina del libro. Devuelve true si lo ha usado. */
    public static boolean scroll(Screen screen, int steps) {
        if (steps == 0 || !bookVisible(screen)) return false;
        return changePage(screen, steps < 0 ? 1 : -1);
    }

    private static int slotOf(State st) {
        for (int i = 0; i < STATE.length; i++) {
            if (STATE[i] == st) return i;
        }
        return 0;
    }

    /** True si el filtro "solo fabricables" del libro esta activado. */
    public static boolean filtering(Screen screen) {
        var f = ((RecipeBookComponentAccessor) book(screen)).consolepad$filter();
        return f != null && f.isStateTriggered();
    }

    /** Pulsa el filtro "solo fabricables" del libro. */
    public static void toggleFilter(Screen screen) {
        var f = ((RecipeBookComponentAccessor) book(screen)).consolepad$filter();
        if (f != null) book(screen).mouseClicked(f.getX() + f.getWidth() / 2.0, f.getY() + f.getHeight() / 2.0, 0);
    }

    /** Coloca la receta con "mayus" (reparte los ingredientes por toda la cuadricula) y el mover rapido del resultado fabrica todas las veces que se pueda. */
    private static void craftAll(Minecraft mc, Screen screen, State st, RecipeButton b, double cx, double cy) {
        if (!b.getCollection().isCraftable(b.getRecipe()) || mc.gameMode == null || mc.player == null) {
            craft(screen, st, b, cx, cy); // sin ingredientes: como A (receta fantasma)
            return;
        }
        mc.gameMode.handlePlaceRecipe(mc.player.containerMenu.containerId, b.getRecipe(), true);
        lastCraftable[slotOf(st)] = true;
        st.pendingUntil = System.nanoTime() + 1_500_000_000L;
    }

    private static void craft(Screen screen, State st, RecipeButton b, double cx, double cy) {
        RecipeCollection col = b.getCollection();
        boolean craftable = col.isCraftable(b.getRecipe());
        boolean wasVisible = book(screen).isVisible();
        screen.mouseClicked(cx, cy, 0); // vanilla coloca la receta (ServerboundPlaceRecipePacket); si falta algo, deja la receta fantasma
        screen.mouseReleased(cx, cy, 0);
        if (wasVisible && !book(screen).isVisible()) toggleBook(screen); // en pantallas estrechas vanilla cierra el libro tras colocar
        lastCraftable[slotOf(st)] = craftable;
        st.pendingUntil = System.nanoTime() + 1_500_000_000L; // si no se puede fabricar el resultado nunca aparece y la espera caduca sola
    }

    /** Cuando el servidor ya ha rellenado la cuadricula, recoge el resultado con un mover rapido (equivale a fabricar una vez). */
    private static void finishPending(Screen screen, State st) {
        if (st.pendingUntil == 0L) return;
        if (System.nanoTime() > st.pendingUntil) {
            st.pendingUntil = 0L;
            return;
        }
        if (!(screen instanceof AbstractContainerScreen<?> c) || !(c.getMenu() instanceof RecipeBookMenu<?, ?> menu)) {
            st.pendingUntil = 0L;
            return;
        }
        Slot result = menu.getSlot(menu.getResultSlotIndex());
        if (result.hasItem()) {
            st.pendingUntil = 0L;
            ((ContainerScreenInvoker) c).consolepad$slotClicked(result, result.index, 0, ClickType.QUICK_MOVE);
        }
    }

    // ------------------------------------------------------------------ panel de ingredientes

    /** Ingrediente agrupado: cuantos hacen falta y cuantos hay. */
    public record Need(ItemStack shown, int need, int have) {
        public boolean missing() {
            return have < need;
        }
    }

    /** Ingredientes de una receta con lo que lleva el jugador (inventario, otra mano y cuadricula de crafteo). */
    public static List<Need> needs(Minecraft mc, RecipeHolder<?> holder) {
        record Group(Ingredient ing, ItemStack shown, int[] count) {}
        Map<String, Group> groups = new LinkedHashMap<>();
        for (Ingredient ing : holder.value().getIngredients()) {
            if (ing.isEmpty()) continue;
            StringBuilder key = new StringBuilder();
            for (ItemStack st : ing.getItems()) key.append(BuiltInRegistries.ITEM.getKey(st.getItem())).append(',');
            groups.computeIfAbsent(key.toString(), k -> new Group(ing, ing.getItems()[(int) ((System.currentTimeMillis() / 900) % ing.getItems().length)], new int[1])).count()[0]++;
        }
        List<ItemStack> owned = new ArrayList<>();
        Inventory inv = mc.player.getInventory();
        owned.addAll(inv.items);
        owned.addAll(inv.offhand);
        if (mc.player.containerMenu instanceof RecipeBookMenu<?, ?> menu) {
            for (int i = 0; i < menu.getSize(); i++) {
                if (i != menu.getResultSlotIndex()) owned.add(menu.getSlot(i).getItem());
            }
        }
        List<Need> out = new ArrayList<>();
        for (Group g : groups.values()) {
            int have = 0;
            for (ItemStack st : owned) {
                if (!st.isEmpty() && g.ing().test(st)) have += st.getCount();
            }
            ItemStack shown = g.shown().copy();
            shown.setCount(1);
            out.add(new Need(shown, g.count()[0], have));
        }
        return out;
    }

    /**
     * Panel bajo el libro con la receta sobre la que esta el cursor: nombre del resultado y sus ingredientes (los que faltan, en rojo con "tiene/necesita").
     * Se dibuja tras la pantalla, antes de la barra de ayuda, en unidades de la pantalla (w x h).
     */
    public static void renderPanel(Minecraft mc, GuiGraphics g, int slot, Screen screen, double cx, double cy, int w, int h) {
        hoverRecipe[slot] = false;
        lastPanel[slot] = "";
        RecipeButton b = recipeAt(screen, cx, cy);
        if (b == null) return;
        hoverRecipe[slot] = true;
        RecipeHolder<?> holder = b.getRecipe();
        List<Need> needs = needs(mc, holder);
        Font font = mc.font;
        String title = holder.value().getResultItem(b.getCollection().registryAccess()).getHoverName().getString();
        List<String> labels = new ArrayList<>();
        int[] cellW = new int[needs.size()];
        int total = 0;
        for (int i = 0; i < needs.size(); i++) {
            Need n = needs.get(i);
            String label = n.missing() ? Math.min(n.have(), 99) + "/" + n.need() : "" + n.need();
            labels.add(label);
            cellW[i] = Math.max(18, font.width(label) + 4);
            total += cellW[i];
        }
        int panelW = Math.max(Math.max(total, font.width(title)) + 8, 60);
        RecipeBookPageAccessor page = (RecipeBookPageAccessor) ((RecipeBookComponentAccessor) book(screen)).consolepad$page();
        int bookLeft = page.consolepad$back().getX() - 38, bookBottom = page.consolepad$back().getY() - 137 + 166;
        int panelH = 3 + 9 + 2 + 16 + 1 + 9 + 3;
        int x = Math.max(1, Math.min(bookLeft, w - panelW - 1));
        int y = Math.min(bookBottom + 1, h - 16 - panelH); // por encima de la barra de ayuda
        y = Math.max(1, y);
        g.pose().pushPose();
        g.pose().translate(0, 0, 450);
        g.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, 0xFF000000);
        g.fill(x, y, x + panelW, y + panelH, 0xF0201028);
        g.drawString(font, title, x + 4, y + 3, 0xFFFFFFFF, false);
        int cellX = x + 4;
        StringBuilder log = new StringBuilder(title).append(':');
        for (int i = 0; i < needs.size(); i++) {
            Need n = needs.get(i);
            if (n.missing()) g.fill(cellX, y + 14, cellX + cellW[i] - 2, y + 14 + 16 + 11, 0x66FF3030);
            g.renderFakeItem(n.shown(), cellX + (cellW[i] - 2 - 16) / 2, y + 14);
            g.drawString(font, labels.get(i), cellX + (cellW[i] - 2 - font.width(labels.get(i))) / 2, y + 14 + 17, n.missing() ? 0xFFFF6060 : 0xFFA0FFA0, false);
            cellX += cellW[i];
            log.append(' ').append(BuiltInRegistries.ITEM.getKey(n.shown().getItem()).getPath()).append('=').append(n.have()).append('/').append(n.need());
        }
        g.pose().popPose();
        lastPanel[slot] = log.toString();
    }

    /** True si el boton del mando tiene que dibujarse como texto "Fabricar" en la barra de ayuda (cursor sobre una receta). */
    public static boolean hovering(int slot) {
        return slot >= 0 && slot < 4 && hoverRecipe[slot];
    }
}
