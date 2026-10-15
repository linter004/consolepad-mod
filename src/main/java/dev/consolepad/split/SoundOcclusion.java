package dev.consolepad.split;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Oclusion simple del sonido de los mobs: vanilla los oye a traves del suelo y las paredes sin atenuar. El volumen se multiplica por 0.55 por cada bloque
 * solido entre el oyente y el sonido (minimo 0.12). Opcion "soundOcclusion" de split.json (activa por defecto).
 */
public final class SoundOcclusion {
    private static final double PER_BLOCK = 0.55, MIN = 0.12;

    private SoundOcclusion() {}

    /** Factor de volumen (entre MIN y 1) del sonido en `to` oido desde `from`; `solid` dice si un bloque tapa el sonido. Los bloques de los extremos no cuentan. */
    public static float factor(Predicate<BlockPos> solid, Vec3 from, Vec3 to) {
        double len = from.distanceTo(to);
        if (len < 1.5) return 1f;
        BlockPos a = BlockPos.containing(from), b = BlockPos.containing(to);
        Set<BlockPos> seen = new HashSet<>();
        int steps = (int) Math.min(64, Math.ceil(len * 2));
        for (int i = 1; i < steps; i++) {
            Vec3 p = from.lerp(to, i / (double) steps);
            BlockPos bp = BlockPos.containing(p);
            if (!bp.equals(a) && !bp.equals(b)) seen.add(bp);
        }
        int blocked = 0;
        for (BlockPos bp : seen) if (solid.test(bp)) blocked++;
        return (float) Math.max(MIN, Math.pow(PER_BLOCK, blocked));
    }

    /** Autocomprobacion sin mundo: suelo de piedra entre el oyente y un sonido debajo, y aire libre. */
    public static String selfTest() {
        Predicate<BlockPos> floor = p -> p.getY() <= 60 && p.getY() >= 59; // dos bloques de suelo
        Vec3 up = new Vec3(0.5, 64.5, 0.5);
        float through = factor(floor, up, new Vec3(0.5, 55.5, 0.5));
        float open = factor(p -> false, up, new Vec3(0.5, 55.5, 0.5));
        float near = factor(floor, up, new Vec3(0.5, 64.5, 1.5));
        float sameSide = factor(floor, new Vec3(0.5, 62.5, 0.5), new Vec3(8.5, 62.5, 0.5));
        boolean ok = Math.abs(through - 0.3025f) < 0.001f && open == 1f && near == 1f && sameSide == 1f;
        return (ok ? "OK" : "FALLO") + " (suelo de 2 bloques " + through + ", aire " + open + ", cerca " + near + ", mismo lado " + sameSide + ")";
    }
}
