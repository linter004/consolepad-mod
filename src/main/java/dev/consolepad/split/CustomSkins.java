package dev.consolepad.split;

import com.mojang.blaze3d.platform.NativeImage;
import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Skins propias para los perfiles: los PNG de 64x64 de config/consolepad/skins/ (se elige el archivo y si los brazos son anchos o finos al crear el perfil).
 * Se cargan como textura dinamica la primera vez que se dibujan y {@code PlayerInfoMixin} las sirve en lugar de la skin de la cuenta. Un archivo que falta o no
 * es de 64x64 deja la skin por defecto de la cuenta.
 */
public final class CustomSkins {
    private static final Map<String, PlayerSkin> CACHE = new HashMap<>();
    private static final Map<String, Boolean> FAILED = new HashMap<>();

    private CustomSkins() {}

    public static Path dir() {
        return FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("skins");
    }

    /** Nombres de los PNG disponibles, ordenados (crea la carpeta si no existe para que se sepa donde ponerlos). */
    public static List<String> files() {
        List<String> out = new ArrayList<>();
        Path d = dir();
        try {
            Files.createDirectories(d);
            try (var stream = Files.list(d)) {
                stream.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png")).forEach(p -> out.add(p.getFileName().toString()));
            }
        } catch (IOException | RuntimeException e) {
            ConsolePad.LOGGER.warn("No se pudo leer {} ({})", d, e.toString());
        }
        java.util.Collections.sort(out, String.CASE_INSENSITIVE_ORDER);
        return out;
    }

    /** Skin del perfil con su textura (la carga si hace falta, en el hilo de dibujo); null si no tiene skin propia o no se puede usar. */
    public static PlayerSkin skinOf(Profiles.Profile p) {
        if (p == null || p.customSkin == null || p.customSkin.isEmpty()) return null;
        String key = p.customSkin + (p.slim ? "|slim" : "|wide");
        PlayerSkin cached = CACHE.get(key);
        if (cached != null) return cached;
        if (FAILED.containsKey(key)) return null;
        Path file = dir().resolve(p.customSkin).normalize();
        if (!file.startsWith(dir().normalize()) || !Files.isRegularFile(file)) {
            FAILED.put(key, true);
            return null;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage img = NativeImage.read(in);
            if (img.getWidth() != 64 || img.getHeight() != 64) {
                ConsolePad.LOGGER.warn("La skin {} no es de 64x64 ({}x{}): se usa la de la cuenta", file, img.getWidth(), img.getHeight());
                img.close();
                FAILED.put(key, true);
                return null;
            }
            ResourceLocation rl = ResourceLocation.fromNamespaceAndPath("consolepad", "custom_skin/" + sanitize(key));
            Minecraft.getInstance().getTextureManager().register(rl, new DynamicTexture(img));
            PlayerSkin skin = new PlayerSkin(rl, null, null, null, p.slim ? PlayerSkin.Model.SLIM : PlayerSkin.Model.WIDE, false);
            CACHE.put(key, skin);
            return skin;
        } catch (IOException | RuntimeException e) {
            ConsolePad.LOGGER.warn("No se pudo cargar la skin {} ({})", file, e.toString());
            FAILED.put(key, true);
            return null;
        }
    }

    private static String sanitize(String s) {
        StringBuilder b = new StringBuilder();
        for (char c : s.toLowerCase(Locale.ROOT).toCharArray()) b.append((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_' || c == '-' || c == '.' ? c : '_');
        return b.toString();
    }

    /** Pruebas: olvida las skins cargadas (por si el archivo ha cambiado). */
    public static void clearCache() {
        CACHE.clear();
        FAILED.clear();
    }
}
