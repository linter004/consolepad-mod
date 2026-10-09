package dev.consolepad.pad;

import net.minecraft.world.item.BowItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.entity.player.Player;

/**
 * Giro de camara de un jugador a partir del stick derecho: zona muerta, curva de respuesta (lineal, suave o con aceleracion), inversion del eje Y
 * y sensibilidad propia al apuntar con arco, ballesta o catalejo. Lo usan P1 (PadDriver) y los extra (SecondaryTick.frameLook), una vez por fotograma.
 */
public final class LookControl {
    public enum Curve {
        LINEAR("lineal"), SMOOTH("suave"), ACCEL("con aceleración");

        public final String label;

        Curve(String label) {
            this.label = label;
        }
    }

    /** Estado que necesita la aceleracion: cuanto tiempo lleva el stick casi a fondo. */
    public static final class State {
        public float held;
    }

    /** Con la aceleracion, a partir de esta inclinacion cuenta como "a fondo" y la velocidad sube hasta ACCEL_MAX en ACCEL_RAMP segundos. */
    public static final float ACCEL_STICK = 0.85f;
    public static final float ACCEL_MAX = 1.8f;
    public static final float ACCEL_RAMP = 0.6f;

    private LookControl() {}

    /** Multiplicador de velocidad de la aceleracion tras {@code held} segundos con el stick a fondo (1 a ACCEL_MAX). */
    public static float accelFactor(float held) {
        return 1f + (ACCEL_MAX - 1f) * Math.min(1f, Math.max(0f, held) / ACCEL_RAMP);
    }

    /** Exponente de la curva: la lineal no curva (1); la suave y la de aceleracion usan el exponente del jugador. */
    public static float exponent(PadSettings cfg) {
        return cfg.lookCurve == Curve.LINEAR ? 1f : cfg.lookExponent;
    }

    /**
     * Giro de este fotograma en las unidades de {@code Entity.turn} (out[0] = guinada, out[1] = cabeceo, ya con el eje Y invertido si toca).
     * {@code sens} es el factor de la sensibilidad global del juego y {@code dt} los segundos reales desde el fotograma anterior.
     */
    public static void compute(PadSettings cfg, float rx, float ry, boolean aiming, float sens, float dt, State st, float[] out) {
        float stick = (float) Math.sqrt(rx * rx + ry * ry);
        st.held = stick >= ACCEL_STICK ? st.held + dt : 0f;
        PadMath.radial(rx, ry, cfg.lookDeadzone, exponent(cfg), out);
        if (out[0] == 0f && out[1] == 0f) return;
        float k = sens * dt / 0.15f; // Entity.turn multiplica por 0.15
        if (cfg.lookCurve == Curve.ACCEL) k *= accelFactor(st.held);
        if (aiming) k *= cfg.aimSensitivity;
        float pitch = cfg.invertY ? -out[1] : out[1];
        out[0] = out[0] * cfg.lookSpeed * k;
        out[1] = pitch * cfg.lookSpeedY() * k;
    }

    /** Apuntando: tensando un arco o una ballesta, o mirando por el catalejo. */
    public static boolean isAiming(Player p) {
        if (p == null || !p.isUsingItem()) return false;
        var item = p.getUseItem().getItem();
        return p.isScoping() || item instanceof BowItem || item instanceof CrossbowItem;
    }
}
