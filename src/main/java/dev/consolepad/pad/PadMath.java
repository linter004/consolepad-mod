package dev.consolepad.pad;

public final class PadMath {
    private PadMath() {}

    /** Zona muerta radial con reescalado y curva de respuesta. Escribe (x, y) en out. */
    public static void radial(float x, float y, float deadzone, float exponent, float[] out) {
        float mag = (float) Math.sqrt(x * x + y * y);
        if (mag < deadzone || mag == 0f) {
            out[0] = 0f;
            out[1] = 0f;
            return;
        }
        float scaled = Math.min(1f, (mag - deadzone) / (1f - deadzone));
        float curved = (float) Math.pow(scaled, exponent);
        float k = curved / mag;
        out[0] = x * k;
        out[1] = y * k;
    }
}
