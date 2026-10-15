package dev.consolepad.split;

/**
 * Compensacion del campo de vision al dividir la pantalla. Minecraft fija el FOV vertical, asi que en una region estrecha (dos jugadores lado a lado,
 * 427x540) el campo horizontal baja de unos 102 a unos 57 grados y se ve "como por un tubo". Con la compensacion el FOV vertical sube lo justo para
 * recuperar parte (media) o todo (total) del campo horizontal que tendria el mismo FOV en una pantalla 16:9, con un tope de 110 grados verticales.
 * Solo cuando la region es mas estrecha que 16:9; en regiones anchas (apilado) no cambia nada. Ajuste por jugador ({@code PadSettings.fovComp}).
 */
public final class FovComp {
    public static final double WIDE = 16.0 / 9.0, MAX_VERTICAL = 110.0;

    private FovComp() {}

    /** FOV vertical (grados) a usar en una region de ese aspecto, para el FOV vertical {@code v} y la compensacion {@code comp} (0 = ninguna, 1 = total). */
    public static double adjust(double v, double aspect, float comp) {
        if (comp <= 0f || aspect <= 0.0 || aspect >= WIDE || v <= 0.0 || v >= 170.0) return v;
        double t = Math.tan(Math.toRadians(v) / 2.0);
        double hNat = 2.0 * Math.atan(t * aspect), hWide = 2.0 * Math.atan(t * WIDE);
        double h = hNat + Math.min(1f, comp) * (hWide - hNat);
        double out = Math.toDegrees(2.0 * Math.atan(Math.tan(h / 2.0) / aspect));
        return Math.max(v, Math.min(MAX_VERTICAL, out));
    }

    /** Campo horizontal (grados) de un FOV vertical en una region de ese aspecto. */
    public static double horizontal(double v, double aspect) {
        return Math.toDegrees(2.0 * Math.atan(Math.tan(Math.toRadians(v) / 2.0) * aspect));
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        double aspect = 427.0 / 540.0;
        double none = adjust(70, aspect, 0f), half = adjust(70, aspect, 0.5f), full = adjust(70, aspect, 1f);
        double hNat = horizontal(70, aspect), hHalf = horizontal(half, aspect), hFull = horizontal(full, aspect), hWide = horizontal(70, WIDE);
        if (none != 70) return "sin compensacion debe dejar 70, dio " + none;
        if (!(half > 70 && full > half)) return "la compensacion debe subir el FOV vertical: " + half + ", " + full;
        if (Math.abs(hHalf - (hNat + hWide) / 2) > 3.0) return "media debe estar a mitad de camino: " + hHalf + " entre " + hNat + " y " + hWide;
        if (full > MAX_VERTICAL + 0.001 || hFull <= hHalf) return "total: " + full + " vertical, " + hFull + " horizontal";
        if (adjust(70, WIDE, 1f) != 70 || adjust(70, 3.1, 1f) != 70) return "una region ancha no debe cambiar";
        if (adjust(70, 0.2, 1f) > MAX_VERTICAL + 0.001) return "el tope vertical debe respetarse: " + adjust(70, 0.2, 1f);
        return null;
    }
}
