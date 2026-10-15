package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.Rumble;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * Vibracion de acciones (dentro de la casilla 49, con P2 dentro): una explosion a unos 10 bloques, romper un bloque, golpear a un animal y comer piden un
 * impulso al mando de P2 (los eventos llegan del servidor); la fuerza de las explosiones baja con la distancia y el arco solo zumba al tensarse.
 */
public final class RumbleTest {
    private static int t, before;
    private static float vib;
    private static net.minecraft.world.entity.animal.Cow cow;

    private RumbleTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    private static int pulses() {
        return Rumble.pulses[1];
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null) return true;
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(c.player.getUUID());
        var cfg = ConsolePad.PADS.slot(1).settings;
        if (t == 1) {
            vib = cfg.vibration;
            cfg.vibration = 1f;
            mc.setScreen(null);
            before = pulses();
            server.execute(() -> {
                ServerLevel level = (ServerLevel) sp.level();
                level.explode(null, sp.getX() + 10, sp.getY(), sp.getZ(), 2f, Level.ExplosionInteraction.NONE);
            });
        }
        if (t == 12) {
            check("vibracion: una explosion a 10 bloques de P2 pide un impulso a su mando (" + before + " -> " + pulses() + ")", pulses() > before);
            check("vibracion: la fuerza de una explosion baja con la distancia (0 -> " + RumbleEvents.explosionStrength(0) + ", 12 -> " + RumbleEvents.explosionStrength(12) + ", 24 -> " + RumbleEvents.explosionStrength(24) + ")",
                    RumbleEvents.explosionStrength(0) == 1f && Math.abs(RumbleEvents.explosionStrength(12) - 0.5f) < 0.01f && RumbleEvents.explosionStrength(24) == 0f);
            before = pulses();
            server.execute(() -> {
                ServerLevel level = (ServerLevel) sp.level();
                BlockPos pos = sp.blockPosition().east(2).above(2);
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), 3);
                sp.gameMode.destroyBlock(pos);
            });
        }
        if (t == 24) {
            check("vibracion: romper un bloque pide un impulso al mando de P2 (" + before + " -> " + pulses() + ")", pulses() > before);
            before = pulses();
            server.execute(() -> {
                ServerLevel level = (ServerLevel) sp.level();
                cow = EntityType.COW.create(level);
                cow.moveTo(sp.getX() + 1, sp.getY(), sp.getZ(), 0f, 0f);
                level.addFreshEntity(cow);
                sp.attack(cow);
            });
        }
        if (t == 36) {
            check("vibracion: golpear a un animal pide un impulso al mando de P2 (" + before + " -> " + pulses() + ")", pulses() > before);
            before = pulses();
            server.execute(() -> {
                cow.discard();
                net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(sp, new ItemStack(Items.BREAD), 0, ItemStack.EMPTY));
            });
        }
        if (t == 46) {
            check("vibracion: comer pide un impulso suave al mando de P2 (" + before + " -> " + pulses() + ")", pulses() > before);
            check("vibracion: el arco solo zumba al tensarse y sube con la carga (" + RumbleEvents.bowRumble(net.minecraft.world.item.UseAnim.BOW, 20) + ", sin carga "
                    + RumbleEvents.bowRumble(net.minecraft.world.item.UseAnim.BOW, 0) + ", comiendo " + RumbleEvents.bowRumble(net.minecraft.world.item.UseAnim.EAT, 20) + ")",
                    RumbleEvents.bowRumble(net.minecraft.world.item.UseAnim.BOW, 20) == 0.5f && RumbleEvents.bowRumble(net.minecraft.world.item.UseAnim.BOW, 0) == 0f
                            && RumbleEvents.bowRumble(net.minecraft.world.item.UseAnim.EAT, 20) == 0f);
            cfg.vibration = vib;
            Rumble.stopAll();
            t = 0;
            return true;
        }
        return false;
    }
}
