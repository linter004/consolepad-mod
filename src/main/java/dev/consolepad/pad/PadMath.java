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

    /**
     * Direcciones de navegacion (0 arriba, 1 abajo, 2 izquierda, 3 derecha) pulsadas ahora: el D-pad y, con la navegacion por casillas,
     * el stick izquierdo (la direccion dominante pasado el 55 % de su recorrido).
     */
    public static void navDirections(PadState s, boolean snapNav, boolean[] out) {
        out[0] = s.isDown(PadState.DPAD_UP);
        out[1] = s.isDown(PadState.DPAD_DOWN);
        out[2] = s.isDown(PadState.DPAD_LEFT);
        out[3] = s.isDown(PadState.DPAD_RIGHT);
        if (snapNav && Math.max(Math.abs(s.lx), Math.abs(s.ly)) >= 0.55f) {
            if (Math.abs(s.ly) >= Math.abs(s.lx)) out[s.ly < 0 ? 0 : 1] = true;
            else out[s.lx < 0 ? 2 : 3] = true;
        }
    }
}
