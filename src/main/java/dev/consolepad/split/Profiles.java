package dev.consolepad.split;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.core.UUIDUtil;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Perfiles locales: un nombre, un color de etiqueta, una skin y los ajustes de un jugador, guardados en config/consolepad/profiles/&lt;id&gt;.json.
 * <p>
 * El id del perfil es el nombre de cuenta con el que ese jugador entra en el mundo (un jugador extra con perfil entra con ese nombre en vez de "Nombre_2"),
 * asi que el servidor le guarda el inventario, la posicion y el punto de reaparicion POR PERFIL, vaya en el slot que vaya. La skin es una de las 18 por
 * defecto de Minecraft (9 personajes, brazos finos o anchos): se elige al crear el perfil y se consigue buscando un id cuyo UUID de cuenta sin conexion
 * caiga en esa skin (DefaultPlayerSkin.get usa el hash del UUID), por eso no se puede cambiar despues sin cambiar de cuenta. Un perfil se usa en un solo
 * jugador a la vez. P1 entra siempre con su cuenta real: su perfil solo trae nombre, color y ajustes.
 */
public final class Profiles {
    public static final int MAX = 8;
    public static final String[] SKIN_NAMES = {"Alex", "Ari", "Efe", "Kai", "Makena", "Noor", "Steve", "Sunny", "Zuri"};

    public static final class Profile {
        public String id = "";
        public String name = "";
        public int labelColor = 0;
        public int skin = 6;
        /** Skin propia: nombre del PNG de config/consolepad/skins (null = una de las 18 por defecto) y si lleva brazos finos. */
        public String customSkin = null;
        public boolean slim = false;
        public PadSettings settings = new PadSettings();
    }

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile List<Profile> loaded;

    private Profiles() {}

    private static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("profiles");
    }

    /** Nombre legible de una de las 18 skins: personaje y modelo. */
    public static String skinLabel(int skin) {
        int s = Math.floorMod(skin, 18);
        return SKIN_NAMES[s % 9] + (s < 9 ? " (brazos finos)" : " (brazos anchos)");
    }

    /** Opciones de skin al crear un perfil: las 18 por defecto y, por cada PNG de la carpeta de skins, una con brazos anchos y otra con finos. */
    public static List<String> skinChoices() {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < 18; i++) out.add(skinLabel(i));
        for (String f : CustomSkins.files()) {
            out.add(f + " (brazos anchos)");
            out.add(f + " (brazos finos)");
        }
        return out;
    }

    /** Texto de la skin de un perfil. */
    public static String labelOf(Profile p) {
        return p.customSkin != null ? p.customSkin + (p.slim ? " (finos)" : " (anchos)") : skinLabel(p.skin);
    }

    /** Skin por defecto (0 a 17) que le toca a una cuenta sin conexion con ese nombre. */
    public static int skinOf(String account) {
        return Math.floorMod(UUIDUtil.createOfflinePlayerUUID(account).hashCode(), 18);
    }

    /** Los perfiles guardados, por nombre. */
    public static synchronized List<Profile> all() {
        if (loaded == null) {
            List<Profile> list = new ArrayList<>();
            Path d = dir();
            if (Files.isDirectory(d)) {
                try (var stream = Files.list(d)) {
                    for (Path f : (Iterable<Path>) stream.filter(p -> p.toString().endsWith(".json"))::iterator) {
                        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                            Profile p = GSON.fromJson(r, Profile.class);
                            if (p != null && validId(p.id)) {
                                if (p.settings == null) p.settings = new PadSettings();
                                p.settings.bindings.values().removeIf(a -> a == null);
                                list.add(p);
                            }
                        } catch (IOException | RuntimeException e) {
                            ConsolePad.LOGGER.warn("No se pudo leer el perfil {} ({})", f, e.toString());
                        }
                    }
                } catch (IOException e) {
                    ConsolePad.LOGGER.warn("No se pudo leer {} ({})", d, e.toString());
                }
            }
            list.sort(Comparator.comparing((Profile p) -> p.name.toLowerCase(java.util.Locale.ROOT)));
            loaded = list;
        }
        return new ArrayList<>(loaded);
    }

    public static Profile byId(String id) {
        if (id == null || id.isEmpty()) return null;
        if (loaded == null) all();
        for (Profile p : loaded) if (p.id.equals(id)) return p; // sin copiar la lista: se llama por cada etiqueta de nombre
        return null;
    }

    private static boolean validId(String id) {
        return id != null && id.matches("[A-Za-z0-9_]{3,16}");
    }

    private static void write(Profile p) {
        Path f = dir().resolve(p.id + ".json");
        try {
            Files.createDirectories(f.getParent());
            try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                GSON.toJson(p, w);
            }
        } catch (IOException | RuntimeException e) {
            ConsolePad.LOGGER.warn("No se pudo guardar el perfil {}: {}", f, e.toString());
        }
    }

    /** Un id (nombre de cuenta, 3 a 16 caracteres) libre cuya cuenta sin conexion tenga la skin pedida. */
    public static String accountFor(String name, int skin) {
        StringBuilder b = new StringBuilder();
        for (char c : name.toCharArray()) if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) b.append(c);
        String base = b.length() == 0 ? "Jugador" : b.substring(0, Math.min(11, b.length()));
        String host = Minecraft.getInstance().getUser() == null ? "" : Minecraft.getInstance().getUser().getName();
        for (int n = 0; n < 100000; n++) {
            String id = base + "_" + n;
            if (id.length() > 16) break;
            if (skinOf(id) != Math.floorMod(skin, 18) || byId(id) != null || id.equalsIgnoreCase(host)) continue;
            return id;
        }
        return null;
    }

    /** Crea y guarda un perfil nuevo con los ajustes de partida dados (los de un jugador, o los de fabrica si es null); null si no cabe. */
    public static synchronized Profile create(String name, int skin, int labelColor, PadSettings from) {
        if (all().size() >= MAX || name == null || name.isBlank()) return null;
        String custom = null;
        boolean slim = false;
        if (skin >= 18) { // opcion de skin propia: 18 + 2 * archivo (+ 1 si los brazos son finos)
            List<String> files = CustomSkins.files();
            int f = (skin - 18) / 2;
            if (f >= files.size()) return null;
            custom = files.get(f);
            slim = (skin - 18) % 2 == 1;
            skin = 6;
        }
        String id = accountFor(name.trim(), skin);
        if (id == null) return null;
        Profile p = new Profile();
        p.id = id;
        p.name = name.trim();
        p.skin = Math.floorMod(skin, 18);
        p.customSkin = custom;
        p.slim = slim;
        p.labelColor = labelColor;
        if (from != null) p.settings.copyFrom(from);
        p.settings.playerName = p.name;
        p.settings.labelColor = labelColor;
        List<Profile> list = all();
        list.add(p);
        list.sort(Comparator.comparing((Profile q) -> q.name.toLowerCase(java.util.Locale.ROOT)));
        loaded = list;
        write(p);
        return p;
    }

    /** Borra un perfil (y lo suelta del jugador que lo tuviera). Su inventario guardado en el mundo se queda: sigue siendo de esa cuenta. */
    public static synchronized void delete(Profile p) {
        List<Profile> list = all();
        list.removeIf(q -> q.id.equals(p.id));
        loaded = list;
        for (int i = 0; i < PadManager.MAX_PLAYERS; i++) {
            PadSettings s = ConsolePad.PADS.slot(i).settings;
            if (p.id.equals(s.profileId)) s.profileId = "";
        }
        try {
            Files.deleteIfExists(dir().resolve(p.id + ".json"));
        } catch (IOException e) {
            ConsolePad.LOGGER.warn("No se pudo borrar el perfil {} ({})", p.id, e.toString());
        }
        ConsolePad.PADS.saveSettings();
    }

    /** El jugador (slot) que usa este perfil, o -1. */
    public static int slotUsing(Profile p) {
        for (int i = 0; i < PadManager.MAX_PLAYERS; i++) if (p.id.equals(ConsolePad.PADS.slot(i).settings.profileId)) return i;
        return -1;
    }

    /**
     * Pone un perfil a un jugador: sus ajustes, nombre y color pasan al jugador y, si es un jugador extra, la proxima vez que entre lo hara con la cuenta del
     * perfil. Devuelve null si va bien, o por que no se puede (el perfil ya lo usa otro jugador, o el jugador extra esta dentro y no puede cambiar de cuenta).
     */
    public static String use(int slot, Profile p) {
        int other = slotUsing(p);
        if (other >= 0 && other != slot) return "Lo usa el jugador " + (other + 1);
        if (slot >= 1 && SecondaryPlayers.isActive(slot)) return "Saca al jugador " + (slot + 1) + " primero (Start + Atrás 2 s)";
        PadSettings s = ConsolePad.PADS.slot(slot).settings;
        s.copyFrom(p.settings);
        s.playerName = p.name;
        s.labelColor = p.labelColor;
        s.profileId = p.id;
        ConsolePad.PADS.saveSettings();
        PlayerLabels.refreshAll(Minecraft.getInstance());
        return null;
    }

    /** Quita el perfil de un jugador (vuelve a su nombre de cuenta de siempre, con los ajustes que tenga). */
    public static String clear(int slot) {
        if (slot >= 1 && SecondaryPlayers.isActive(slot)) return "Saca al jugador " + (slot + 1) + " primero (Start + Atrás 2 s)";
        ConsolePad.PADS.slot(slot).settings.profileId = "";
        ConsolePad.PADS.saveSettings();
        return null;
    }

    /** El perfil que usa un jugador (o null). */
    public static Profile of(int slot) {
        return byId(ConsolePad.PADS.slot(slot).settings.profileId);
    }

    /** Guarda en el perfil los ajustes actuales del jugador que lo usa (se llama al guardar ajustes y al salir). */
    public static void sync(int slot) {
        Profile p = of(slot);
        if (p == null) return;
        PadSettings s = ConsolePad.PADS.slot(slot).settings;
        p.settings.copyFrom(s);
        p.settings.playerName = s.playerName;
        p.settings.labelColor = s.labelColor;
        if (s.playerName != null && !s.playerName.isBlank()) p.name = s.playerName.trim();
        p.labelColor = s.labelColor;
        write(p);
    }

    /** Pruebas: olvidar lo cargado y releer del disco. */
    public static synchronized void reload() {
        loaded = null;
    }
}
