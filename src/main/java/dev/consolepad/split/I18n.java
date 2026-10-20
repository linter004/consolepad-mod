package dev.consolepad.split;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Idiomas de los textos del mod (menus, ayudas, avisos). Los textos del codigo estan escritos en espanol y la tabla {@code assets/consolepad/i18n/en.json}
 * (clave = el texto en espanol) da el ingles: con el juego en espanol no cambia nada, y con cualquier otro idioma se usa el ingles. El ajuste
 * {@code "language"} de split.json ("auto", "es" o "en") lo fuerza. Un texto sin traduccion se deja en espanol y se apunta en {@link #missing} (la prueba
 * de idiomas recorre las pantallas en ingles y falla si queda alguno). Los textos compuestos ("Etiqueta: valor", "A · B", "Texto (P2)", "Jugador 2") se
 * traducen por partes.
 */
public final class I18n {
    private static final Pattern NUMBER_TAIL = Pattern.compile("^(.*?) (\\d+)$");
    private static final Pattern PAREN_TAIL = Pattern.compile("^(.*?) (\\([^()]*\\))$");
    private static final Pattern SPANISH_LOOK = Pattern.compile("[áéíóúñÁÉÍÓÚÑ¿¡]|\\b(jugador|jugadores|mando|mandos|ajustes|volver|salir|guardar|partida|pausa|botón|boton|botones|menú|pantalla|reparto|opciones|elige|pulsa|mantén|atrás|cerrar|aceptar|restablecer|cancelar|nuevo|borrar|editar|perfil|nombre|sensibilidad|distancia|velocidad|fuerza|vibración|marcar|cama|compañero|sí|rápido|suave|lento|fuerte|barra|mostrar|ayuda)\\b", Pattern.CASE_INSENSITIVE);

    private static volatile Map<String, String> en;
    private static volatile String forced;
    /** Textos que se pidieron en ingles y no estaban en la tabla (y se veian como espanol). */
    public static final Set<String> missing = ConcurrentHashMap.newKeySet();

    private I18n() {}

    /** Pruebas: fuerza el idioma ("es", "en") o null para volver al normal. */
    public static void force(String lang) {
        forced = lang;
    }

    public static String forced() {
        return forced;
    }

    public static boolean spanish() {
        String f = forced != null ? forced : SplitSettings.language();
        if ("es".equals(f)) return true;
        if ("en".equals(f)) return false;
        Minecraft mc = Minecraft.getInstance();
        String code = mc == null || mc.options == null ? "es_es" : mc.options.languageCode;
        return code == null || code.toLowerCase(java.util.Locale.ROOT).startsWith("es");
    }

    private static Map<String, String> table() {
        Map<String, String> t = en;
        if (t != null) return t;
        Map<String, String> loaded = new HashMap<>();
        try (InputStream in = I18n.class.getResourceAsStream("/assets/consolepad/i18n/en.json")) {
            if (in != null) {
                Map<String, String> read = new Gson().fromJson(new InputStreamReader(in, StandardCharsets.UTF_8), new TypeToken<Map<String, String>>() {}.getType());
                if (read != null) loaded.putAll(read);
            }
        } catch (Exception e) {
            dev.consolepad.ConsolePad.LOGGER.warn("No se pudo leer la tabla de ingles: {}", e.toString());
        }
        en = Collections.unmodifiableMap(loaded);
        return en;
    }

    /** Numero de textos de la tabla de ingles (pruebas). */
    public static int tableSize() {
        return table().size();
    }

    public static boolean looksSpanish(String s) {
        return s != null && SPANISH_LOOK.matcher(s).find();
    }

    /** El texto (escrito en espanol) en el idioma activo. */
    public static String tr(String es) {
        if (es == null || es.isEmpty() || spanish()) return es;
        String r = translate(es);
        if (r == null) {
            if (looksSpanish(es)) missing.add(es);
            return es;
        }
        return r;
    }

    /** Traduccion exacta o por partes; null si no hay. */
    private static String lookup(String s) {
        Map<String, String> t = table();
        String r = t.get(s);
        if (r == null) r = t.get(s + " "); // las claves con un espacio al final ("Sacar a ") tambien valen sin el
        if (r == null) r = t.get(s.trim());
        return r;
    }

    private static String translate(String es) {
        String exact = lookup(es);
        if (exact != null) return exact;
        int c = es.indexOf(": ");
        if (c > 0) { // "Etiqueta: valor"
            String label = es.substring(0, c);
            String a = translate(label);
            if (a == null) { // la clave puede llevar los dos puntos ("Reparto: ")
                String withColon = lookup(label + ": ");
                if (withColon != null) a = withColon.endsWith(": ") ? withColon.substring(0, withColon.length() - 2) : withColon.endsWith(":") ? withColon.substring(0, withColon.length() - 1) : withColon;
            }
            String b = translatePart(es.substring(c + 2));
            if (a != null || b != null) return (a != null ? a : es.substring(0, c)) + ": " + (b != null ? b : es.substring(c + 2));
        }
        int dot = es.indexOf(" · ");
        if (dot > 0) { // "A · B"
            String a = translatePart(es.substring(0, dot)), b = translatePart(es.substring(dot + 3));
            if (a != null || b != null) return (a != null ? a : es.substring(0, dot)) + " · " + (b != null ? b : es.substring(dot + 3));
        }
        var m = PAREN_TAIL.matcher(es); // "Texto (P2)"
        if (m.matches()) {
            String a = translate(m.group(1));
            if (a != null) return a + " " + m.group(2);
        }
        m = NUMBER_TAIL.matcher(es); // "Jugador 2"
        if (m.matches()) {
            String a = translate(m.group(1));
            if (a != null) return a + " " + m.group(2);
        }
        return null;
    }

    private static String translatePart(String s) {
        String r = translate(s);
        if (r != null) return r;
        return s.matches("[\\d.,]+( ?[%a-z]{0,3})?") ? s : null; // numeros y unidades se quedan como estan
    }

    /** Plantilla con huecos {0}, {1}...: se traduce la frase entera y luego se rellenan (con el juego en espanol, tal cual). */
    public static String fmt(String esTemplate, Object... args) {
        String t = tr(esTemplate);
        for (int i = 0; i < args.length; i++) t = t.replace("{" + i + "}", String.valueOf(args[i]));
        return t;
    }

    public static MutableComponent lit(String es) {
        return Component.literal(tr(es));
    }
}
