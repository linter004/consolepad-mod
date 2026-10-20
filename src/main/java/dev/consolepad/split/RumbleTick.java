package dev.consolepad.split;

import dev.consolepad.pad.Rumble;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Cuando vibra el mando de cada jugador: al recibir dano (mas fuerte y largo cuanto mas dano) y al morir. Una vez por tick de cliente mira la vida de P1 y de cada
 * jugador extra dentro; la fuerza final la pone cada jugador en sus ajustes. Sin mundo, o con la ventana sin foco, los motores se apagan.
 */
public final class RumbleTick {
    private static final float[] lastHealth = new float[4];
    private static final boolean[] have = new boolean[4];

    private RumbleTick() {}

    public static void tick(Minecraft mc) {
        tickCounter++;
        if (mc.level == null || mc.player == null) {
            java.util.Arrays.fill(have, false);
            Rumble.stopAll();
            return;
        }
        check(0, mc.player);
        bow(0, mc.player);
        boolean[] seen = new boolean[4];
        seen[0] = true;
        for (PlayerContext c : SecondaryPlayers.contexts()) {
            if (c.player != null && c.slotIndex > 0 && c.slotIndex < 4) {
                check(c.slotIndex, c.player);
                bow(c.slotIndex, c.player);
                seen[c.slotIndex] = true;
            }
        }
        for (int i = 1; i < 4; i++) if (!seen[i]) have[i] = false; // un jugador que sale no deja una vida vieja para cuando vuelva
        Rumble.tick(mc.isWindowActive() || AutoTest.running());
    }

    /** Tensar un arco o una ballesta: zumbido que sube con la carga. */
    private static void bow(int slot, Player p) {
        if (!p.isUsingItem()) return;
        float k = RumbleEvents.bowRumble(p.getUseItem().getUseAnimation(), p.getTicksUsingItem());
        if (k > 0f) Rumble.pulse(slot, 0f, k, 70, Rumble.Kind.ACTION);
    }

    private static int tickCounter;
    /** Cuantos latidos se han disparado por jugador (pruebas). */
    public static final int[] heartbeats = new int[4];

    /** Latido con poca vida (por debajo de 3 corazones): un pulso suave cada 24 ticks. */
    public static boolean heartbeatDue(float hp, int tick) {
        return hp > 0f && hp <= 6f && tick % 24 == 0;
    }

    private static void check(int slot, Player p) {
        onHealth(slot, p.getHealth());
        if (heartbeatDue(p.getHealth(), tickCounter)) {
            heartbeats[slot]++;
            Rumble.pulse(slot, 0.3f, 0f, 90, Rumble.Kind.DAMAGE);
        }
    }

    /** La vida actual del jugador del slot; vibra si bajo desde la vez anterior. */
    public static void onHealth(int slot, float hp) {
        if (have[slot] && hp < lastHealth[slot] - 0.01f) {
            float lost = lastHealth[slot] - hp;
            if (hp <= 0f) Rumble.pulse(slot, 1f, 1f, 700, Rumble.Kind.DAMAGE);
            else Rumble.pulse(slot, Math.min(1f, 0.35f + lost / 10f), Math.min(1f, 0.2f + lost / 12f), 180 + Math.min(400, (int) (lost * 40)), Rumble.Kind.DAMAGE);
        }
        lastHealth[slot] = hp;
        have[slot] = true;
    }
}
