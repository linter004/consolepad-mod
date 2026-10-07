package dev.consolepad.pad;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.consolepad.ConsolePad;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Guarda y carga los ajustes de cada slot en config/consolepad/slotN.json. */
public final class PadSettingsStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private PadSettingsStore() {}

    private static Path file(int slot) {
        return FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("slot" + slot + ".json");
    }

    public static boolean exists(int slot) {
        return Files.isRegularFile(file(slot));
    }

    /**
     * Devuelve los ajustes guardados. Si el archivo falta o esta mal formado, devuelve {@code seed}. Si el archivo existe
     * pero no trae "bindings", se usan los de {@code seed}. Los bindings guardados en el archivo siempre se respetan.
     */
    public static PadSettings load(int slot, PadSettings seed) {
        Path path = file(slot);
        if (!Files.isRegularFile(path)) return seed;
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            JsonElement tree = JsonParser.parseReader(reader);
            if (tree == null || !tree.isJsonObject()) return seed;
            PadSettings loaded = GSON.fromJson(tree, PadSettings.class);
            if (loaded == null) return seed;
            if (!tree.getAsJsonObject().has("bindings")) {
                loaded.bindings.clear();
                loaded.bindings.putAll(seed.bindings);
            }
            loaded.bindings.values().removeIf(a -> a == null); // acciones desconocidas
            return loaded;
        } catch (Exception e) {
            ConsolePad.LOGGER.warn("No se pudo leer {} ({}); se usan los valores por defecto", path, e.toString());
            return seed;
        }
    }

    public static void save(int slot, PadSettings settings) {
        Path path = file(slot);
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                GSON.toJson(settings, writer);
            }
        } catch (IOException | RuntimeException e) {
            ConsolePad.LOGGER.warn("No se pudo guardar {}: {}", path, e.toString());
        }
    }
}
