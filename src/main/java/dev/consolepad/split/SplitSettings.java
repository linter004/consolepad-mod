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
        Boolean autoViewDistance = Boolean.TRUE;
        Boolean playerLimits = Boolean.TRUE;
        String compat = "auto";
        Boolean welcomeSeen = Boolean.FALSE;
        Boolean keepShaders = Boolean.FALSE;
        Boolean soundOcclusion = Boolean.TRUE;
        Boolean soundPerPlayer = Boolean.TRUE;
        Boolean adaptivePerformance = Boolean.TRUE;
        Integer targetFps = 40;
        Integer padLostLeaveSeconds = 30;
        Boolean askPlayers = Boolean.TRUE;
        java.util.Map<String, String> worldOrientation = new java.util.HashMap<>();
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
                        if (read.autoViewDistance != null) d.autoViewDistance = read.autoViewDistance;
                        else complete = false;
                        if (read.playerLimits != null) d.playerLimits = read.playerLimits;
                        else complete = false;
                        if (read.compat != null && java.util.List.of("auto", "on", "off").contains(read.compat)) d.compat = read.compat;
                        else complete = false;
                        if (read.welcomeSeen != null) d.welcomeSeen = read.welcomeSeen;
                        else complete = false;
                        if (read.keepShaders != null) d.keepShaders = read.keepShaders;
                        else complete = false;
                        if (read.soundOcclusion != null) d.soundOcclusion = read.soundOcclusion;
                        else complete = false;
                        if (read.soundPerPlayer != null) d.soundPerPlayer = read.soundPerPlayer;
                        else complete = false;
                        if (read.adaptivePerformance != null) d.adaptivePerformance = read.adaptivePerformance;
                        else complete = false;
                        if (read.targetFps != null) d.targetFps = Math.max(15, Math.min(240, read.targetFps));
                        else complete = false;
                        if (read.padLostLeaveSeconds != null) d.padLostLeaveSeconds = Math.max(0, read.padLostLeaveSeconds);
                        else complete = false;
                        if (read.askPlayers != null) d.askPlayers = read.askPlayers;
                        else complete = false;
                        if (read.worldOrientation != null) d.worldOrientation = read.worldOrientation;
                        else complete = false;
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

    /** Pruebas: fuerza la distancia de vision automatica sin tocar el archivo (null = lo que diga split.json). */
    public static volatile Boolean autoViewDistanceOverride;

    /** Distancia de vision automatica segun los jugadores ({@link PerfLimits}); se apaga con "autoViewDistance": false en split.json. */
    public static boolean autoViewDistance() {
        Boolean o = autoViewDistanceOverride;
        return o != null ? o : data().autoViewDistance;
    }

    /** Pruebas: fuerza los limites por jugador sin tocar el archivo (null = lo que diga split.json). */
    public static volatile Boolean playerLimitsOverride;

    /** Limites por jugador (distancia de entidades, lluvia) con 3 o 4 jugadores ({@link PerfLimits}); se apagan con "playerLimits": false en split.json. */
    public static boolean playerLimits() {
        Boolean o = playerLimitsOverride;
        return o != null ? o : data().playerLimits;
    }

    /** Modo compatibilidad con Controlify: "auto" (activo si esta instalado), "on" o "off" (editando "compat" en split.json). */
    public static String compat() {
        return data().compat;
    }

    /** Verdadero si no se deben apagar los shaders de Iris al jugar varios (por defecto falso: con shaders la pantalla dividida sale rota). */
    /** Los mobs suenan mas bajo si hay bloques entre ellos y el jugador 1 (opcion "soundOcclusion" de split.json). */
    public static boolean soundOcclusion() {
        return data().soundOcclusion;
    }

    /** Cada sonido lo oye el jugador local mas cercano (los de los extra se mueven al oyente de P1; opcion "soundPerPlayer" de split.json). */
    public static boolean soundPerPlayer() {
        return data().soundPerPlayer;
    }

    /** Regulador de rendimiento automatico con varios jugadores (opcion "adaptivePerformance" de split.json). */
    public static boolean adaptivePerformance() {
        return data().adaptivePerformance;
    }

    /** FPS objetivo del regulador automatico ("targetFps" de split.json, entre 15 y 240). */
    public static int targetFps() {
        return data().targetFps;
    }

    /** Segundos que un jugador extra puede estar sin mando antes de salir de la partida (0 = nunca; "padLostLeaveSeconds" de split.json). */
    public static int padLostLeaveSeconds() {
        return data().padLostLeaveSeconds;
    }

    /** Preguntar cuantos jugadores son al entrar en un mundo con varios mandos ("askPlayers" de split.json). */
    public static boolean askPlayers() {
        return data().askPlayers;
    }

    public static void setAskPlayers(boolean on) {
        data().askPlayers = on;
        save();
    }

    /** Reparto de pantalla guardado para un mundo (null si no se ha tocado en ese mundo). */
    public static Orientation worldOrientation(String level) {
        String v = level == null ? null : data().worldOrientation.get(level);
        try {
            return v == null ? null : Orientation.valueOf(v);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public static void forgetWorldOrientation(String level) {
        if (data().worldOrientation.remove(level) != null) save();
    }

    public static void rememberWorldOrientation(String level, Orientation o) {
        if (level == null || level.isEmpty()) return;
        data().worldOrientation.put(level, o.name());
        save();
    }

    public static boolean keepShaders() {
        return data().keepShaders;
    }

    /** Pruebas: la bienvenida ya se mostro. */
    public static boolean welcomeSeen() {
        return data().welcomeSeen;
    }

    public static void setWelcomeSeen(boolean seen) {
        data().welcomeSeen = seen;
        save();
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
