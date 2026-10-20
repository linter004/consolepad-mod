package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.lang.reflect.Method;

/**
 * Iris con shaders y pantalla dividida: Iris compone cada pasada de mundo sobre la ventana entera (sus fotogramas de posproceso no conocen las regiones),
 * asi que con 2 o mas jugadores solo la ultima pasada se ve bien y las demas salen lavadas o rotas (medido con Sodium 0.8.13 + Iris 1.8.14 + Complementary
 * Reimagined: 3 de 4 vistas en blanco). Mientras haya jugadores extra, los shaders se apagan y al quedar un solo jugador se vuelven a encender.
 * Se usa la API publica de Iris por reflexion (Iris es opcional). Ajuste "keepShaders": true en split.json para no tocarlos.
 */
public final class IrisCompat {
    private static boolean disabledByUs;
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

    /** True si los shaders los apagamos nosotros y siguen apagados. */
    public static boolean disabledByUs() {
        return disabledByUs;
    }

    /** Una vez por fotograma. */
    public static void frame(Minecraft mc) {
        if (!Compat.iris() || SplitSettings.keepShaders() || !resolve()) return;
        boolean split = SecondaryPlayers.anyActive();
        if (split && !disabledByUs && shadersOn()) {
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
