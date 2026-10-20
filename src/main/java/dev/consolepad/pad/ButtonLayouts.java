package dev.consolepad.pad;

import java.util.Map;

/**
 * Disposiciones de botones preestablecidas: cambian todos los botones de un jugador de golpe (el remapeo individual de PlayerControlsScreen sigue
 * disponible encima). BEDROCK = la de fabrica; JAVA = acciones de teclado (agacharse en B, soltar en R3); ZURDO = la de fabrica con gatillos,
 * bumpers y clics de stick intercambiados y los sticks intercambiados (PadSettings.swapSticks); NINTENDO = la de fabrica con A y B, y X e Y, intercambiados
 * al leer el mando (PadSettings.swapFace).
 */
public final class ButtonLayouts {
    public enum Layout {
        BEDROCK("Bedrock"), JAVA("Java"), LEFTY("Zurdo (sticks cambiados)"), NINTENDO("Nintendo (A↔B, X↔Y)");

        public final String label;

        Layout(String label) {
            this.label = label;
        }
    }

    private ButtonLayouts() {}

    public static Map<Integer, PadAction> bindings(Layout l) {
        Map<Integer, PadAction> m = PadSettings.defaultBindings();
        switch (l) {
            case JAVA -> {
                m.put(PadState.B, PadAction.SNEAK_TOGGLE);
                m.put(PadState.R3, PadAction.DROP);
            }
            case LEFTY -> {
                swap(m, PadState.LT, PadState.RT);
                swap(m, PadState.LB, PadState.RB);
                swap(m, PadState.L3, PadState.R3);
            }
            default -> { }
        }
        return m;
    }

    private static void swap(Map<Integer, PadAction> m, int a, int b) {
        PadAction t = m.get(a);
        m.put(a, m.get(b));
        m.put(b, t);
    }

    public static void apply(PadSettings s, Layout l) {
        s.bindings.clear();
        s.bindings.putAll(bindings(l));
        s.swapSticks = l == Layout.LEFTY;
        s.swapFace = l == Layout.NINTENDO; // los botones se intercambian al leer el mando, asi vale tambien en los menus
    }

    /** La disposicion que coincide exactamente con los botones y los sticks de estos ajustes, o null si es personalizada. */
    public static Layout detect(PadSettings s) {
        for (Layout l : Layout.values()) {
            if (s.swapSticks == (l == Layout.LEFTY) && s.swapFace == (l == Layout.NINTENDO) && s.bindings.equals(bindings(l))) return l;
        }
        return null;
    }

    public static String name(PadSettings s) {
        Layout l = detect(s);
        return dev.consolepad.split.I18n.tr(l == null ? "personalizada" : l.label);
    }

    /** Siguiente del ciclo; desde una personalizada se vuelve a Bedrock. */
    public static Layout next(PadSettings s) {
        Layout cur = detect(s);
        if (cur == null) return Layout.BEDROCK;
        Layout[] all = Layout.values();
        return all[(cur.ordinal() + 1) % all.length];
    }
}
