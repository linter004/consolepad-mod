package dev.consolepad.split;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.LoomScreen;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Destinos de la cruceta en las estaciones de trabajo cuyas opciones se dibujan a mano y no son widgets: las recetas de la mesa de corte y los patrones del
 * telar (vanilla las pulsa calculando la posicion del clic). Cada destino es el centro del recuadro que vanilla comprueba en {@code mouseClicked}, asi que A
 * sobre el funciona igual que el raton. Los campos privados (pagina visible y si hay opciones) se leen por reflexion.
 */
public final class WorkstationTargets {
    private static final Map<String, Field> FIELDS = new HashMap<>();

    private WorkstationTargets() {}

    private static Object read(Class<?> type, Object target, String name) {
        try {
            Field f = FIELDS.computeIfAbsent(type.getName() + "#" + name, k -> {
                try {
                    Field field = type.getDeclaredField(name);
                    field.setAccessible(true);
                    return field;
                } catch (ReflectiveOperationException e) {
                    return null;
                }
            });
            return f == null ? null : f.get(target);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    /** Anade a out los destinos propios de la pantalla (mesa de corte, telar); no hace nada en las demas. */
    public static void add(AbstractContainerScreen<?> screen, List<double[]> out) {
        int left = screen.getGuiLeft(), top = screen.getGuiTop();
        if (screen instanceof StonecutterScreen sc) {
            Object display = read(StonecutterScreen.class, sc, "displayRecipes"), start = read(StonecutterScreen.class, sc, "startIndex");
            if (!(display instanceof Boolean b) || !b || !(start instanceof Integer first)) return;
            int n = sc.getMenu().getNumRecipes();
            for (int k = 0; k < 12; k++) {
                if (first + k >= n) break;
                out.add(new double[] {left + 52 + (k % 4) * 16 + 8, top + 14 + (k / 4) * 18 + 9, 16, 18});
            }
        } else if (screen instanceof LoomScreen lm) {
            Object display = read(LoomScreen.class, lm, "displayPatterns"), start = read(LoomScreen.class, lm, "startRow");
            if (!(display instanceof Boolean b) || !b || !(start instanceof Integer firstRow)) return;
            int n = lm.getMenu().getSelectablePatterns().size();
            for (int row = 0; row < 4; row++) {
                for (int col = 0; col < 4; col++) {
                    if ((row + firstRow) * 4 + col >= n) return;
                    out.add(new double[] {left + 60 + col * 14 + 7, top + 13 + row * 14 + 7, 14, 14});
                }
            }
        }
    }
}
