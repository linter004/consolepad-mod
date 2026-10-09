package dev.consolepad.split;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.consolepad.ConsolePad;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Ajustes de la pantalla dividida que son de todos los jugadores (no de uno): la orientacion del reparto y el estilo de los iconos de botones. Se guardan en
 * config/consolepad/split.json y se leen al primer uso; al cambiarlos se recalcula todo en el siguiente fotograma
 * ({@link SplitLayout#update} corre cada fotograma).
 */
public final class SplitSettings {
    /** Lado a lado (columnas) o apilado (filas). Con 4 jugadores son cuadrantes en ambos casos. */
    public enum Orientation {
        SIDE_BY_SIDE, STACKED
    }

    /** Estilos de iconos de botones (atlas en textures/gui/prompts/<estilo>.png, ver tools/PromptAtlas.java). */
    public static final String[] PROMPT_STYLES = {"default_color", "default_white", "retro_color", "retro_white", "stylized"};
    public static final String DEFAULT_PROMPT_STYLE = "default_color";

    private static final class Data {
        String orientation = Orientation.SIDE_BY_SIDE.name();
        String promptStyle = DEFAULT_PROMPT_STYLE;
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Data data;

    private SplitSettings() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("split.json");
    }

    private static Data data() {
        Data d = data;
        if (d == null) {
            d = new Data();
            Path path = file();
            boolean complete = false;
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Data read = GSON.fromJson(reader, Data.class);
                    if (read != null) {
                        complete = read.orientation != null && read.promptStyle != null;
                        if (read.orientation != null) Orientation.valueOf(read.orientation);
                        d.orientation = read.orientation != null ? read.orientation : d.orientation;
                        if (read.promptStyle != null && java.util.Arrays.asList(PROMPT_STYLES).contains(read.promptStyle)) d.promptStyle = read.promptStyle;
                    }
                } catch (IOException | RuntimeException e) {
                    ConsolePad.LOGGER.warn("No se pudo leer {} ({}); valores por defecto", path, e.toString());
                    d = new Data();
                }
            }
            data = d;
            if (!complete) save(); // deja el archivo con todas las claves para poder editarlo a mano
        }
        return d;
    }

    private static void save() {
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(data(), writer);
            }
        } catch (IOException | RuntimeException e) {
            ConsolePad.LOGGER.warn("No se pudo guardar {}: {}", path, e.toString());
        }
    }

    public static Orientation orientation() {
        return Orientation.valueOf(data().orientation);
    }

    public static void setOrientation(Orientation o) {
        data().orientation = o.name();
        save();
    }

    public static void toggleOrientation() {
        setOrientation(orientation() == Orientation.SIDE_BY_SIDE ? Orientation.STACKED : Orientation.SIDE_BY_SIDE);
    }

    /** Estilo de los iconos de botones, igual para todos los jugadores; se cambia editando "promptStyle" en split.json. */
    public static String promptStyle() {
        return data().promptStyle;
    }
}
