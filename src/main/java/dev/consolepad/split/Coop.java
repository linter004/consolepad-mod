package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.resources.ResourceKey;

import java.util.ArrayList;
import java.util.List;

/**
 * Cooperativo entre los jugadores locales: marcas de lugar que ven los demas, dar
 * el objeto de la mano al companero mas cercano, y las reglas de equipo (sin dano entre jugadores, aviso de muerte, aviso de companero lejos).
 * La marca y el reparto salen de la rueda de acciones (entradas "consolepad:..." de {@link QuickWheel}); todo corre en el servidor integrado.
 */
public final class Coop {
    public static final String PREFIX = "consolepad:";

    /** Una marca de lugar de un jugador (una por jugador): mundo, posicion y cuando se puso. */
    public record Waypoint(int slot, ResourceKey<Level> dimension, double x, double y, double z) {}

    private static final Waypoint[] WAYPOINTS = new Waypoint[4];
    /** Contadores por jugador (pruebas): gestos hechos, marcas puestas, objetos dados, y el ultimo gesto. */
    public static final int[] marksSet = new int[4], gives = new int[4];
    /** Avisos de companero lejos enviados (pruebas). */
    public static volatile int farWarnings, deathNotices, blockedHits;
    /** Pruebas: distancia de aviso (0 = la de split.json). */
    public static volatile int farOverride = -1;

    private static final boolean[][] FAR_FLAG = new boolean[4][4];

    private Coop() {}

    /** Las entradas fijas de la segunda pagina de la rueda: marcar un lugar, quitar la marca y dar el objeto de la mano. */
    public static List<QuickWheel.Entry> wheelEntries() {
        List<QuickWheel.Entry> l = new ArrayList<>();
        l.add(new QuickWheel.Entry(I18n.tr("Marcar lugar"), PREFIX + "mark"));
        l.add(new QuickWheel.Entry(I18n.tr("Quitar marca"), PREFIX + "unmark"));
        l.add(new QuickWheel.Entry(I18n.tr("Dar objeto"), PREFIX + "give"));
        return l;
    }

    /** Ejecuta una entrada interna de la rueda del jugador del slot. Devuelve false si no era interna. */
    public static boolean run(int slot, String command) {
        if (!command.startsWith(PREFIX)) return false;
        String[] parts = command.substring(PREFIX.length()).split(" ");
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server == null) return true;
        switch (parts[0]) {
            case "mark" -> server.execute(() -> mark(server, slot));
            case "unmark" -> server.execute(() -> unmark(server, slot));
            case "give" -> server.execute(() -> give(server, slot));
            default -> { }
        }
        return true;
    }

    /** El jugador del servidor de un slot local (0 a 3), o null. */
    public static ServerPlayer serverPlayer(MinecraftServer server, int slot) {
        Minecraft mc = Minecraft.getInstance();
        List<ServerPlayer> all;
        try {
            all = new ArrayList<>(server.getPlayerList().getPlayers());
        } catch (RuntimeException e) {
            return null;
        }
        for (ServerPlayer sp : all) {
            if (sp != null && PlayerLabels.slotOf(mc, sp.getGameProfile().getName()) == slot) return sp;
        }
        return null;
    }

    private static Component said(int slot, String text) {
        return Component.empty().append(PlayerLabels.styled(slot, PlayerLabels.labelOf(slot))).append(Component.literal(": " + text));
    }

    private static void mark(MinecraftServer server, int slot) {
        ServerPlayer sp = serverPlayer(server, slot);
        if (sp == null) return;
        WAYPOINTS[slot] = new Waypoint(slot, sp.level().dimension(), sp.getX(), sp.getY(), sp.getZ());
        marksSet[slot]++;
    }

    private static void unmark(MinecraftServer server, int slot) {
        if (WAYPOINTS[slot] == null) return;
        WAYPOINTS[slot] = null;
    }

    /** El objeto de la mano pasa al inventario del jugador local mas cercano (hasta 8 bloques, en el mismo mundo); lo que no cabe vuelve a la mano. */
    private static void give(MinecraftServer server, int slot) {
        ServerPlayer from = serverPlayer(server, slot);
        if (from == null) return;
        ItemStack held = from.getMainHandItem();
        if (held.isEmpty()) {
            from.sendSystemMessage(Component.literal(I18n.tr("No llevas nada en la mano.")), true);
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        ServerPlayer best = null;
        double bestD = 64.0; // 8 bloques al cuadrado
        List<ServerPlayer> all = new ArrayList<>(server.getPlayerList().getPlayers());
        for (ServerPlayer sp : all) {
            if (sp == null || sp == from || sp.level() != from.level()) continue;
            if (PlayerLabels.slotOf(mc, sp.getGameProfile().getName()) < 0) continue;
            double d = sp.distanceToSqr(from);
            if (d < bestD) {
                bestD = d;
                best = sp;
            }
        }
        if (best == null) {
            from.sendSystemMessage(Component.literal(I18n.tr("No hay nadie cerca (hasta 8 bloques).")), true);
            return;
        }
        ItemStack giving = held.copy();
        int before = giving.getCount();
        best.getInventory().add(giving); // lo que no cabe se queda en `giving`
        int given = before - giving.getCount();
        if (given <= 0) {
            from.sendSystemMessage(Component.literal(I18n.fmt("A {0} no le caben más objetos.", PlayerLabels.labelOf(PlayerLabels.slotOf(mc, best.getGameProfile().getName())))), true);
            return;
        }
        held.shrink(given);
        gives[slot]++;
        int toSlot = PlayerLabels.slotOf(mc, best.getGameProfile().getName());
        Component what = Component.literal(given + " ").append(held.getItem().getName(held.isEmpty() ? new ItemStack(giving.getItem()) : held));
        from.sendSystemMessage(Component.literal(I18n.fmt("Le diste a {0}: ", PlayerLabels.labelOf(toSlot))).append(what), true);
        best.sendSystemMessage(Component.literal(I18n.fmt("{0} te dio: ", PlayerLabels.labelOf(slot))).append(what), true);
        ((ServerLevel) best.level()).playSound(null, best.blockPosition(), SoundEvents.ITEM_PICKUP, SoundSource.PLAYERS, 0.4f, 1.1f);
    }

    // ---- Marcas de lugar

    /** Marcas activas (para dibujarlas y probarlas). */
    public static List<Waypoint> waypoints() {
        List<Waypoint> out = new ArrayList<>();
        for (Waypoint w : WAYPOINTS) if (w != null) out.add(w);
        return out;
    }

    public static void clearWaypoints() {
        java.util.Arrays.fill(WAYPOINTS, null);
        for (boolean[] row : FAR_FLAG) java.util.Arrays.fill(row, false);
    }

    // ---- Reglas de equipo

    /** Si los jugadores locales se hacen dano entre si (el ajuste "friendlyFire"; apagado por defecto). */
    public static boolean friendlyFire() {
        return SplitSettings.friendlyFire();
    }

    /** True si este dano entre jugadores locales debe cancelarse. */
    public static boolean blocksDamage(Object attacker, Object target) {
        if (friendlyFire()) return false;
        if (!(attacker instanceof Player a) || !(target instanceof Player t) || a == t) return false;
        if (!(a instanceof ServerPlayer) || !(t instanceof ServerPlayer)) return false;
        Minecraft mc = Minecraft.getInstance();
        if (PlayerLabels.slotOf(mc, a.getGameProfile().getName()) < 0 || PlayerLabels.slotOf(mc, t.getGameProfile().getName()) < 0) return false;
        blockedHits++;
        return true;
    }

    /** Aviso de muerte para todos: quien murio y donde. */
    public static void onDeath(ServerPlayer dead) {
        if (!SplitSettings.deathNotice()) return;
        Minecraft mc = Minecraft.getInstance();
        int slot = PlayerLabels.slotOf(mc, dead.getGameProfile().getName());
        if (slot < 0 || !SecondaryPlayers.anyActive()) return;
        MinecraftServer server = dead.getServer();
        if (server == null) return;
        deathNotices++;
        server.getPlayerList().broadcastSystemMessage(said(slot, I18n.fmt("murió en {0}, {1}, {2}", (int) dead.getX(), (int) dead.getY(), (int) dead.getZ())), false);
    }

    /** Una vez por tick de servidor: las marcas se ven en el mundo (una columna de luz) y se avisa a quien tiene un companero muy lejos. */
    public static void serverTick(MinecraftServer server) {
        int tick = server.getTickCount();
        if (tick % 10 == 0) {
            for (Waypoint w : WAYPOINTS) {
                if (w == null) continue;
                ServerLevel level = server.getLevel(w.dimension());
                if (level == null) continue;
                for (int k = 0; k < 6; k++) level.sendParticles(ParticleTypes.END_ROD, w.x(), w.y() + 1 + k * 0.8, w.z(), 1, 0.05, 0.0, 0.05, 0.0);
            }
        }
        if (tick % 40 != 0) return;
        int limit = farOverride >= 0 ? farOverride : SplitSettings.farWarning();
        if (limit <= 0) return;
        Minecraft mc = Minecraft.getInstance();
        List<ServerPlayer> all;
        try {
            all = new ArrayList<>(server.getPlayerList().getPlayers());
            all.removeIf(java.util.Objects::isNull);
        } catch (RuntimeException e) {
            return;
        }
        for (ServerPlayer v : all) {
            int vs = PlayerLabels.slotOf(mc, v.getGameProfile().getName());
            if (vs < 0 || vs > 3) continue;
            for (ServerPlayer o : all) {
                int os = PlayerLabels.slotOf(mc, o.getGameProfile().getName());
                if (o == v || os < 0 || os > 3) continue;
                double d = o.level() == v.level() ? Math.sqrt(o.distanceToSqr(v)) : 0;
                boolean far = o.level() == v.level() && d > limit;
                if (far && !FAR_FLAG[vs][os]) {
                    FAR_FLAG[vs][os] = true;
                    farWarnings++;
                    v.sendSystemMessage(Component.literal(I18n.fmt("{0} está lejos ({1} bloques)", PlayerLabels.labelOf(os), (int) d)), true);
                } else if (FAR_FLAG[vs][os] && (o.level() != v.level() || d < limit * 0.7)) {
                    FAR_FLAG[vs][os] = false;
                }
            }
        }
    }
}
