package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.neoforged.fml.ModList;

/**
 * Compatibilidad con otros mods que tocan el mando o el dibujo: Controlify (mandos), Sodium e Iris (render). Se detectan por id y se registran en el log.
 * <ul>
 *   <li>Sodium e Iris: ConsolePad no usa mixins sobre lo que reescriben (ver LevelRendererMixin); aqui solo se informa, y la bienvenida avisa de la
 *       version de NeoForge que pide Sodium.</li>
 *   <li>Controlify: lleva su propio mando para el jugador 1. En modo compatibilidad ConsolePad no maneja el mando del slot 0 (lo deja a Controlify) y se
 *       queda con los mandos de los jugadores 2 a 4. Ajuste "compat" de split.json: "auto" (por defecto: activo si Controlify esta instalado), "on", "off".</li>
 * </ul>
 */
public final class Compat {
    private Compat() {}

    /** Pruebas: simula que Controlify esta (null = lo que diga la lista de mods). */
    public static volatile Boolean controlifyOverride;

    private static boolean loaded(String id) {
        try {
            ModList list = ModList.get();
            return list != null && list.isLoaded(id);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static boolean controlify() {
        Boolean o = controlifyOverride;
        return o != null ? o : loaded("controlify");
    }

    public static boolean sodium() {
        return loaded("sodium");
    }

    public static boolean iris() {
        return loaded("iris");
    }

    /** Verdadero si ConsolePad debe dejar el mando del jugador 1 a Controlify. */
    public static boolean yieldP1() {
        String m = SplitSettings.compat();
        if (m.equals("on")) return true;
        if (m.equals("off")) return false;
        return controlify();
    }

    /** Una linea con lo detectado y lo que se hace, para el log y la bienvenida. */
    public static String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Controlify: ").append(controlify() ? "si" : "no");
        sb.append(", Sodium: ").append(sodium() ? "si" : "no");
        sb.append(", Iris: ").append(iris() ? "si" : "no");
        sb.append(" -> modo compatibilidad ").append(SplitSettings.compat()).append(yieldP1() ? " (el mando del jugador 1 lo maneja Controlify)" : " (ConsolePad maneja todos los mandos)");
        return sb.toString();
    }

    public static void logOnce() {
        ConsolePad.LOGGER.info("[compat] {}", summary());
    }
}
