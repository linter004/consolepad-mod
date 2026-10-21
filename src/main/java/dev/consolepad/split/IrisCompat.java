package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/**
 * Iris con shaders y pantalla dividida: Iris compone cada pasada de mundo sobre la ventana entera (sus fotogramas de posproceso no conocen las regiones),
 * asi que con 2 o mas jugadores solo la ultima pasada se ve bien y las demas salen lavadas o rotas (medido con Sodium 0.8.13 + Iris 1.8.14 + Complementary
 * Reimagined: 3 de 4 vistas en blanco). Mientras haya jugadores extra, los shaders se apagan y al quedar un solo jugador se vuelven a encender.
 * Excepcion: un pack "seguro para pantalla dividida" se queda encendido. Lo es el que lleva el archivo marcador {@link #SAFE_MARKER} (en su carpeta o dentro de su zip):
 * quien lo escribe se compromete a que no guarda estado entre fotogramas (sin exposicion automatica, TAA ni sombras) y a sacar las coordenadas de gl_FragCoord entre el
 * tamano de la ventana (ver tools/shaderpack/ConsolePadLite). Se usa la API publica de Iris por reflexion (Iris es opcional). Ajuste "keepShaders": true en split.json
 * para no tocar ningun pack.
 */
public final class IrisCompat {
    /** Archivo que declara un pack seguro para la pantalla dividida, dentro de su carpeta shaders. */
    public static final String SAFE_MARKER = "shaders/consolepad-splitscreen-safe";
    /** Pack seguro que se dejo encendido con jugadores extra (vacio si ninguno); para el diagnostico y las pruebas. */
    public static volatile String safePackKept = "";
    private static boolean disabledByUs;
    private static Method currentPackName;
    private static String lastPack;
    private static boolean lastSafe;
    private static int checkedAt;
    private static Object config;
    private static Method areEnabled, setEnabled;
    private static boolean resolved, available;

    private IrisCompat() {}

    private static boolean resolve() {
        if (resolved) return available;
        resolved = true;
        if (!Compat.iris()) return false;
        try {
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object inst = api.getMethod("getInstance").invoke(null);
            config = api.getMethod("getConfig").invoke(inst);
            Class<?> cfg = Class.forName("net.irisshaders.iris.api.v0.IrisApiConfig");
            areEnabled = cfg.getMethod("areShadersEnabled");
            setEnabled = cfg.getMethod("setShadersEnabledAndApply", boolean.class);
            available = true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            ConsolePad.LOGGER.warn("[compat] no se pudo usar la API de Iris ({}); los shaders no se apagaran solos", e.toString());
        }
        return available;
    }

    private static boolean shadersOn() {
        try {
            return (boolean) areEnabled.invoke(config);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    private static void set(boolean on) {
        try {
            setEnabled.invoke(config, on);
        } catch (ReflectiveOperationException | RuntimeException e) {
            ConsolePad.LOGGER.warn("[compat] Iris: no se pudo {} los shaders ({})", on ? "encender" : "apagar", e.toString());
        }
    }

    /** Nombre del pack activo: por la API interna de Iris, o por config/iris.properties si no se puede llamar. */
    private static String currentPack(Minecraft mc) {
        try {
            if (currentPackName == null) currentPackName = Class.forName("net.irisshaders.iris.Iris").getMethod("getCurrentPackName");
            Object o = currentPackName.invoke(null);
            if (o instanceof String s && !s.isBlank()) return s;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            // sin esa funcion: se lee el archivo de ajustes de Iris
        }
        return packFromProperties(mc.gameDirectory.toPath());
    }

    /** El pack elegido en config/iris.properties de esa carpeta de juego, o null. */
    static String packFromProperties(java.nio.file.Path gameDir) {
        java.nio.file.Path f = gameDir.resolve("config").resolve("iris.properties");
        if (!java.nio.file.Files.isRegularFile(f)) return null;
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(f)) {
            java.util.Properties p = new java.util.Properties();
            p.load(in);
            String v = p.getProperty("shaderPack", "").trim();
            return v.isEmpty() ? null : v;
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** True si el pack de esa carpeta de packs (en carpeta o en zip) lleva el marcador de pack seguro. */
    static boolean isSafePack(java.nio.file.Path packsDir, String name) {
        if (name == null || name.isBlank() || name.contains("..") || name.contains("/") || name.contains("\\")) return false;
        java.nio.file.Path p = packsDir.resolve(name);
        if (java.nio.file.Files.isDirectory(p)) return java.nio.file.Files.isRegularFile(p.resolve(SAFE_MARKER));
        if (!java.nio.file.Files.isRegularFile(p)) return false;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(p.toFile())) {
            var en = z.entries();
            while (en.hasMoreElements()) {
                String n = en.nextElement().getName();
                if (n.equals(SAFE_MARKER) || n.endsWith("/" + SAFE_MARKER)) return true; // el pack puede ir dentro de una carpeta del zip
            }
        } catch (java.io.IOException | RuntimeException e) {
            return false;
        }
        return false;
    }

    /** El pack activo es seguro para la pantalla dividida (se comprueba al cambiar de pack y cada pocos segundos). */
    private static boolean activePackSafe(Minecraft mc) {
        String pack = currentPack(mc);
        if (pack == null) return false;
        if (!pack.equals(lastPack) || ++checkedAt > 600) {
            lastPack = pack;
            checkedAt = 0;
            lastSafe = isSafePack(mc.gameDirectory.toPath().resolve("shaderpacks"), pack);
        }
        return lastSafe;
    }

    /** Autocomprobacion con archivos temporales (sin Iris): null si bien. */
    public static String selfTest() {
        try {
            java.nio.file.Path d = java.nio.file.Files.createTempDirectory("consolepad-iris");
            java.nio.file.Path safeDir = d.resolve("Segura"), plainDir = d.resolve("Normal");
            java.nio.file.Files.createDirectories(safeDir.resolve("shaders"));
            java.nio.file.Files.createDirectories(plainDir.resolve("shaders"));
            java.nio.file.Files.writeString(safeDir.resolve(SAFE_MARKER), "ok");
            java.nio.file.Files.writeString(plainDir.resolve("shaders/final.fsh"), "x");
            for (String[] z : new String[][] {{"Zip.zip", SAFE_MARKER}, {"ZipCarpeta.zip", "Pack/" + SAFE_MARKER}, {"ZipNormal.zip", "shaders/final.fsh"}}) {
                try (java.util.zip.ZipOutputStream out = new java.util.zip.ZipOutputStream(java.nio.file.Files.newOutputStream(d.resolve(z[0])))) {
                    out.putNextEntry(new java.util.zip.ZipEntry(z[1]));
                    out.write(1);
                    out.closeEntry();
                }
            }
            java.nio.file.Files.createDirectories(d.resolve("config"));
            java.nio.file.Files.writeString(d.resolve("config/iris.properties"), "#x\nshaderPack=Mi Pack.zip\nenableShaders=true\n");
            boolean ok = isSafePack(d, "Segura") && !isSafePack(d, "Normal") && isSafePack(d, "Zip.zip") && isSafePack(d, "ZipCarpeta.zip") && !isSafePack(d, "ZipNormal.zip")
                    && !isSafePack(d, "NoExiste") && !isSafePack(d, null) && !isSafePack(d, "") && !isSafePack(d, "../x") && !isSafePack(d, "Segura/shaders");
            String prop = packFromProperties(d);
            String none = packFromProperties(d.resolve("nada"));
            return ok && "Mi Pack.zip".equals(prop) && none == null ? null : "packs seguros: " + ok + ", propiedad '" + prop + "', sin archivo " + none;
        } catch (java.io.IOException | RuntimeException e) {
            return e.toString();
        }
    }

    /** True si los shaders los apagamos nosotros y siguen apagados. */
    public static boolean disabledByUs() {
        return disabledByUs;
    }

    /** Una vez por fotograma. */
    public static void frame(Minecraft mc) {
        if (!Compat.iris() || SplitSettings.keepShaders() || !resolve()) return;
        boolean split = SecondaryPlayers.anyActive();
        if (!split) safePackKept = "";
        if (split && !disabledByUs && shadersOn() && activePackSafe(mc)) { // pack seguro: se queda encendido con varios jugadores
            if (!lastPack.equals(safePackKept)) {
                safePackKept = lastPack;
                ConsolePad.LOGGER.info("[compat] Iris: el pack '{}' es seguro para la pantalla dividida y se queda encendido", lastPack);
            }
        } else if (split && !disabledByUs && shadersOn()) {
            disabledByUs = true;
            set(false);
            ConsolePad.LOGGER.info("[compat] Iris: shaders apagados mientras haya jugadores extra (la pantalla dividida no los admite)");
            if (mc.player != null) mc.player.displayClientMessage(I18n.lit("Shaders apagados mientras juegan varios jugadores; se encienden al quedar uno."), true);
        } else if (!split && disabledByUs) {
            disabledByUs = false;
            set(true);
            ConsolePad.LOGGER.info("[compat] Iris: shaders encendidos otra vez");
        }
    }
}
