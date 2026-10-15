package dev.consolepad.split;

import dev.consolepad.pad.Rumble;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;

/**
 * Mas vibracion por jugador (ademas del dano y la muerte de {@link RumbleTick}): romper un bloque (leve), golpear (media), comer o beber (suave) y las
 * explosiones cercanas (segun la distancia). Los eventos llegan en el hilo del servidor integrado; solo escriben en la cola de impulsos de {@link Rumble}.
 */
public final class RumbleEvents {
    /** Distancia (bloques) a la que una explosion deja de notarse. */
    public static final float EXPLOSION_RANGE = 24f;

    private RumbleEvents() {}

    /** Slot local (0 a 3) del jugador, o -1 si no es un jugador local. */
    private static int slotOf(Player p) {
        if (!(p instanceof ServerPlayer)) return -1;
        return PlayerLabels.slotOf(Minecraft.getInstance(), p.getGameProfile().getName());
    }

    public static void onBreak(BlockEvent.BreakEvent e) {
        int slot = slotOf(e.getPlayer());
        if (slot >= 0) Rumble.pulse(slot, 0.25f, 0.45f, 90);
    }

    public static void onAttack(AttackEntityEvent e) {
        int slot = slotOf(e.getEntity());
        if (slot >= 0) Rumble.pulse(slot, 0.3f, 0.65f, 80);
    }

    public static void onUseFinish(LivingEntityUseItemEvent.Finish e) {
        LivingEntity who = e.getEntity();
        if (!(who instanceof Player p)) return;
        ItemStack item = e.getItem();
        if (item.getUseAnimation() != net.minecraft.world.item.UseAnim.EAT && item.getUseAnimation() != net.minecraft.world.item.UseAnim.DRINK) return;
        int slot = slotOf(p);
        if (slot >= 0) Rumble.pulse(slot, 0.15f, 0.25f, 70);
    }

    /** Fuerza (0 a 1) con que un jugador a esa distancia nota una explosion. */
    public static float explosionStrength(double distance) {
        return (float) Math.max(0.0, 1.0 - distance / EXPLOSION_RANGE);
    }

    public static void onExplosion(ExplosionEvent.Detonate e) {
        if (!(e.getLevel() instanceof ServerLevel level)) return;
        var center = e.getExplosion().center();
        for (ServerPlayer p : level.players()) {
            int slot = slotOf(p);
            if (slot < 0) continue;
            float k = explosionStrength(Math.sqrt(p.distanceToSqr(center)));
            if (k > 0.05f) Rumble.pulse(slot, 0.4f + 0.6f * k, 0.3f + 0.7f * k, 150 + (int) (350 * k));
        }
    }

    /** Arco y ballesta tensandose: un zumbido agudo que sube con la carga (una vez por tick de cliente; dentro del tick del jugador). */
    public static float bowRumble(net.minecraft.world.item.UseAnim anim, int ticksUsing) {
        if (anim != net.minecraft.world.item.UseAnim.BOW && anim != net.minecraft.world.item.UseAnim.CROSSBOW) return 0f;
        return Math.min(0.5f, ticksUsing / 20f * 0.5f);
    }
}
