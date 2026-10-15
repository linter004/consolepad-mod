package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Sonido por jugador. OpenAL tiene un solo oyente (la camara de P1), asi que antes solo se oia lo de cerca de P1 y el resto se silenciaba. Ahora cada sonido
 * lo reproduce la copia que recibe el jugador local MAS CERCANO (en la misma dimension; si hay empate, el de menor numero) y los de un jugador extra se
 * mueven al marco de P1: la misma distancia al oyente y la misma direccion respecto a hacia donde mira ese jugador. Las posiciones salen del servidor
 * integrado, como en {@link Companions}. Se apaga con "soundPerPlayer": false en split.json.
 */
public final class SoundRouting {
    public record Listener(int slot, Vec3 eye, float yaw, ResourceKey<Level> dimension) {}

    private SoundRouting() {}

    /** Los jugadores locales dentro del mundo (P1 y los extra activos) con su posicion de oyente. */
    public static List<Listener> listeners(Minecraft mc) {
        List<Listener> out = new ArrayList<>();
        var server = mc.getSingleplayerServer();
        if (server == null) return out;
        List<ServerPlayer> players;
        try {
            players = new ArrayList<>(server.getPlayerList().getPlayers());
        } catch (RuntimeException e) {
            return out;
        }
        for (ServerPlayer sp : players) {
            int slot = PlayerLabels.slotOf(mc, sp.getGameProfile().getName());
            if (slot < 0 || (slot > 0 && !SecondaryPlayers.isActive(slot))) continue;
            out.add(new Listener(slot, sp.getEyePosition(), sp.getYRot(), sp.level().dimension()));
        }
        return out;
    }

    /** Slot del oyente mas cercano a pos en esa dimension (empate dentro de 5 cm: el menor), o -1 si no hay ninguno. */
    public static int nearest(List<Listener> all, Vec3 pos, ResourceKey<Level> dimension) {
        int best = -1;
        double bestD = Double.MAX_VALUE;
        for (Listener l : all) {
            if (dimension != null && !l.dimension().equals(dimension)) continue;
            double d = l.eye().distanceTo(pos);
            if (d < bestD - 0.05 || (Math.abs(d - bestD) <= 0.05 && l.slot() < best)) {
                bestD = d;
                best = l.slot();
            }
        }
        return best;
    }

    /** Mueve un sonido del marco del oyente `from` al del oyente `to`: mismo alejamiento y misma direccion respecto a donde mira cada uno. */
    public static Vec3 translate(Vec3 pos, Listener from, Listener to) {
        double dx = pos.x - from.eye().x, dy = pos.y - from.eye().y, dz = pos.z - from.eye().z;
        double r = Math.hypot(dx, dz);
        double a = Math.atan2(-dx, dz) + Math.toRadians(to.yaw() - from.yaw()); // el angulo de Minecraft: 0 = sur, 90 = oeste
        return new Vec3(to.eye().x - r * Math.sin(a), to.eye().y + dy, to.eye().z + r * Math.cos(a));
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        ResourceKey<Level> ow = Level.OVERWORLD, nether = Level.NETHER;
        Listener p1 = new Listener(0, new Vec3(0, 64, 0), 0f, ow), p2 = new Listener(1, new Vec3(100, 64, 0), 90f, ow), far = new Listener(2, new Vec3(0, 64, 5), 0f, nether);
        List<Listener> all = List.of(p1, p2, far);
        if (nearest(all, new Vec3(3, 64, 0), ow) != 0 || nearest(all, new Vec3(97, 64, 0), ow) != 1) return "el mas cercano no es el esperado";
        if (nearest(all, new Vec3(50, 64, 0), ow) != 0) return "un empate debe ser del menor numero";
        if (nearest(all, new Vec3(0, 64, 4), nether) != 2) return "debe contar la dimension";
        if (nearest(List.of(), new Vec3(0, 64, 0), ow) != -1) return "sin oyentes debe dar -1";
        // Un sonido a 10 bloques delante de P2 (mira al oeste: yaw 90, delante es -x) debe quedar a 10 bloques delante de P1 (mira al sur: delante es +z).
        Vec3 t = translate(new Vec3(90, 64, 0), p2, p1);
        if (Math.abs(t.x - 0) > 0.001 || Math.abs(t.z - 10) > 0.001 || Math.abs(t.y - 64) > 0.001) return "delante de P2 deberia ser delante de P1: " + t;
        // A la izquierda de P2 (mirando al oeste la izquierda es el sur, +z) y 6 mas arriba: debe quedar a la izquierda de P1 (mirando al sur la izquierda es +x).
        Vec3 side = translate(new Vec3(100, 70, 10), p2, p1);
        if (Math.abs(side.x - 10) > 0.001 || Math.abs(side.z - 0) > 0.001 || Math.abs(side.y - 70) > 0.001) return "la izquierda de P2 deberia ser la izquierda de P1: " + side;
        return null;
    }
}
