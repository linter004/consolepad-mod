package dev.consolepad.split;

/**
 * Regulador de rendimiento automatico para "mi PC va justo". Con varios jugadores mide los FPS reales en ventanas de 3 s: si la media baja del objetivo
 * (40 FPS por defecto) sube un nivel de presion (maximo 3) que reduce un poco mas la distancia de vision (x0,85, x0,7, x0,6) y el dibujo de entidades
 * (x0,9, x0,8, x0,7) por encima de lo que ya baja el numero de jugadores; con la media muy por encima del objetivo (1,6 veces durante 3 ventanas seguidas)
 * baja un nivel. Con un solo jugador siempre es 0. Se apaga con "adaptivePerformance": false en split.json; el objetivo se cambia con "targetFps". No actua
 * durante el autotest ni la generacion de capturas (sus medidas parten de los limites fijos).
 */
public final class AdaptivePerf {
    public static final int MAX_LEVEL = 3;
    private static final double WINDOW_SECONDS = 3.0;
    private static final double[] VIEW = {1.0, 0.85, 0.7, 0.6};
    private static final double[] ENTITIES = {1.0, 0.9, 0.8, 0.7};

    private static volatile int level;
    private static double windowTime;
    private static int windowFrames, calmWindows, cooldown;
    private static long lastNanos;

    private AdaptivePerf() {}

    /** Nivel efectivo: el del regulador, y como minimo 2 con el modo ahorro (con varios jugadores). */
    public static int level() {
        return Math.max(level, SplitSettings.savingMode() ? 2 : 0);
    }

    public static double viewFactor() {
        return VIEW[level()];
    }

    public static double entityFactor() {
        return ENTITIES[level()];
    }

    /** Adelgaza las particulas con presion alta: nivel 2 dibuja 3 de cada 4 y nivel 3 la mitad (segun el hash de cada particula, sin parpadeos). */
    public static boolean drawParticle(int hash, int level) {
        if (level >= 3) return (hash & 1) == 0;
        if (level == 2) return (hash & 3) != 0;
        return true;
    }

    private static boolean enabled() {
        return SplitSettings.adaptivePerformance() && !AutoTest.running() && !ShotsTest.ENABLED && PerfLimits.playerCount > 1;
    }

    /** Una vez por fotograma. */
    public static void frame() {
        long now = System.nanoTime();
        double dt = lastNanos == 0 ? 0 : (now - lastNanos) / 1e9;
        lastNanos = now;
        if (!enabled()) {
            level = 0;
            windowTime = 0;
            windowFrames = 0;
            calmWindows = 0;
            cooldown = 0;
            return;
        }
        if (dt <= 0 || dt > 1.0) return; // un tirón de carga no cuenta
        windowTime += dt;
        windowFrames++;
        if (windowTime >= WINDOW_SECONDS) {
            step(windowFrames / windowTime, SplitSettings.targetFps());
            windowTime = 0;
            windowFrames = 0;
        }
    }

    /** Una ventana ya medida: sube o baja el nivel segun la media de FPS y el objetivo. Publico para las pruebas. */
    public static void step(double avgFps, int target) {
        if (cooldown > 0) {
            cooldown--;
            return; // tras un cambio se deja una ventana para que el efecto se note
        }
        if (avgFps < target && level < MAX_LEVEL) {
            level++;
            calmWindows = 0;
            cooldown = 1;
            dev.consolepad.ConsolePad.LOGGER.info("[perf] rendimiento automatico: {} FPS de media (objetivo {}), nivel {}", Math.round(avgFps), target, level);
        } else if (avgFps > target * 1.6 && level > 0) {
            if (++calmWindows >= 3) {
                level--;
                calmWindows = 0;
                cooldown = 1;
                dev.consolepad.ConsolePad.LOGGER.info("[perf] rendimiento automatico: {} FPS de media, baja la presion al nivel {}", Math.round(avgFps), level);
            }
        } else {
            calmWindows = 0;
        }
    }

    /** Solo pruebas: vuelve al estado inicial. */
    public static void resetForTest() {
        level = 0;
        windowTime = 0;
        windowFrames = 0;
        calmWindows = 0;
        cooldown = 0;
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        resetForTest();
        step(20, 40);
        if (level != 1) return "con 20 FPS deberia subir a 1: " + level;
        step(20, 40); // la ventana de enfriamiento no cuenta
        if (level != 1) return "tras un cambio hay una ventana de espera: " + level;
        step(20, 40);
        step(20, 40);
        step(20, 40);
        step(20, 40);
        step(20, 40);
        if (level != MAX_LEVEL) return "con FPS bajos sostenidos deberia llegar al maximo " + MAX_LEVEL + ": " + level;
        step(20, 40);
        if (level != MAX_LEVEL) return "no debe pasar del maximo: " + level;
        if (Math.abs(viewFactor() - 0.6) > 1e-9 || Math.abs(entityFactor() - 0.7) > 1e-9) return "factores del nivel 3: " + viewFactor() + ", " + entityFactor();
        step(100, 40); // cooldown
        step(100, 40);
        step(100, 40);
        step(100, 40);
        if (level != MAX_LEVEL - 1) return "con 3 ventanas muy por encima del objetivo debe bajar un nivel: " + level;
        step(50, 40); // bien pero sin margen (50 < 64): no cambia
        step(50, 40);
        step(50, 40);
        step(50, 40);
        if (level != MAX_LEVEL - 1) return "sin margen no debe bajar: " + level;
        resetForTest();
        return null;
    }
}
