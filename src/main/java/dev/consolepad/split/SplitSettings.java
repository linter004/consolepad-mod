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
 * Ajustes de la pantalla dividida que son de todos los jugadores (no de uno): la orientacion del reparto. Se guardan en
 * config/consolepad/split.json y se leen al primer uso; al cambiarlos se recalcula todo en el siguiente fotograma
 * ({@link SplitLayout#update} corre cada fotograma).
 */
public final class SplitSettings {
    /** Lado a lado (columnas) o apilado (filas). Con 4 jugadores son cuadrantes en ambos casos. */
    public enum Orientation {
        SIDE_BY_SIDE, STACKED
    }

    private static final class Data {
        String orientation = Orientation.SIDE_BY_SIDE.name();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile Orientation orientation;

    private SplitSettings() {}

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("split.json");
    }

    public static Orientation orientation() {
        Orientation o = orientation;
        if (o == null) {
            o = Orientation.SIDE_BY_SIDE;
            Path path = file();
            if (Files.isRegularFile(path)) {
                try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                    Data d = GSON.fromJson(reader, Data.class);
                    if (d != null && d.orientation != null) o = Orientation.valueOf(d.orientation);
                } catch (IOException | RuntimeException e) {
                    ConsolePad.LOGGER.warn("No se pudo leer {} ({}); reparto lado a lado", path, e.toString());
                }
            }
            orientation = o;
        }
        return o;
    }

    public static void setOrientation(Orientation o) {
        orientation = o;
        Data d = new Data();
        d.orientation = o.name();
        Path path = file();
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(d, writer);
            }
        } catch (IOException | RuntimeException e) {
            ConsolePad.LOGGER.warn("No se pudo guardar {}: {}", path, e.toString());
        }
    }

    public static void toggleOrientation() {
        setOrientation(orientation() == Orientation.SIDE_BY_SIDE ? Orientation.STACKED : Orientation.SIDE_BY_SIDE);
    }
}
