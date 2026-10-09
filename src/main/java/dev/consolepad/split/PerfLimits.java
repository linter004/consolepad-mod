package dev.consolepad.split;

/**
 * Limites de rendimiento que dependen del numero de jugadores dibujados. La distancia de vision se reduce al repartir la pantalla (cada pasada
 * de mundo cuesta ~2-3 ms de CPU y la region de cada jugador es mas pequena): 2 jugadores = 75 %, 3 = 60 %, 4 = 50 % de la distancia que el
 * usuario tiene en las opciones, con un minimo de 6 chunks (o la propia si es menor). No cambia las opciones guardadas. Se apaga con
 * "autoViewDistance": false en config/consolepad/split.json.
 */
public final class PerfLimits {
    /** Jugadores que se dibujan este fotograma (1 = sin pantalla dividida); lo fija {@link SplitRender#renderLevels}. */
    public static volatile int playerCount = 1;
    private static final int MIN_CHUNKS = 6;

    private PerfLimits() {}

    public static double factor(int players) {
        return switch (players) {
            case 2 -> 0.75;
            case 3 -> 0.6;
            case 4 -> 0.5;
            default -> 1.0;
        };
    }

    /** Distancia de vision efectiva (en chunks) para la distancia pedida por el usuario. */
    public static int viewDistance(int requested) {
        return scaled(requested, MIN_CHUNKS);
    }

    /**
     * Distancia de vision (chunks) del jugador del slot: la que elija en sus ajustes (0 = la de las opciones) y, con la distancia automatica activa,
     * nunca mas que el tope por numero de jugadores. Sin la automatica manda su eleccion (puede pasar de la de las opciones).
     */
    public static int viewDistanceFor(int slot, int requested) {
        int chosen = dev.consolepad.ConsolePad.PADS == null ? 0 : dev.consolepad.ConsolePad.PADS.slot(slot).settings.viewDistance;
        int cap = viewDistance(requested);
        if (chosen <= 0) return cap;
        return SplitSettings.autoViewDistance() ? Math.min(chosen, cap) : chosen;
    }

    /** La mayor distancia que necesita algun jugador dentro (la publica ViewDistances cada fotograma); 0 = sin datos. */
    public static volatile int serverMax = 0;

    /** Distancia de vision del servidor integrado: la mayor de los jugadores; cada uno recibe la suya (ClientInformation.viewDistance). */
    public static int serverViewDistance(int requested) {
        int m = serverMax;
        return m > 0 ? Math.max(2, m) : viewDistance(requested);
    }

    /** Distancia de simulacion del servidor integrado (en chunks). */
    public static int simulationDistance(int requested) {
        return scaled(requested, 4);
    }

    private static int scaled(int requested, int min) {
        double f = factor(playerCount);
        if (f >= 1.0 || !SplitSettings.autoViewDistance()) return requested;
        return Math.min(requested, Math.max(min, (int) Math.round(requested * f)));
    }

    /**
     * Factor de la distancia de dibujo de entidades (se multiplica por la escala de vanilla, que ya baja con la distancia de vision):
     * 2 jugadores = 1, 3 = 0,8, 4 = 0,65. Apagado con "playerLimits": false en split.json.
     */
    public static double entityScale() {
        if (!SplitSettings.playerLimits()) return 1.0;
        return switch (playerCount) {
            case 3 -> 0.8;
            case 4 -> 0.65;
            default -> 1.0;
        };
    }

    /**
     * Lluvia y nieve: con 3 o 4 jugadores se usa la version "rapida" (radio 5 en vez de 10 al dibujar y la cuarta parte de gotas al crearlas),
     * como con los graficos en rapido. Con 1 o 2 jugadores sigue segun los graficos del usuario.
     */
    public static boolean fancyWeather() {
        boolean fancy = net.minecraft.client.Minecraft.useFancyGraphics();
        return fancy && !(SplitSettings.playerLimits() && playerCount >= 3);
    }
}
