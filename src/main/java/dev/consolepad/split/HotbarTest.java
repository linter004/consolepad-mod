package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

/**
 * Casilla anterior (casilla 49, con P2 dentro): RB cambia de casilla, y LB y RB a la vez saltan a la casilla que se usaba antes de la actual (y otra vez,
 * vuelven). Con P2 (tick) y con P1 (fotograma).
 */
public final class HotbarTest {
    private static int t, pass, s1, s2;

    private HotbarTest() {}

    private static void sim(int slot, int bits) {
        PadManager.Device d = ConsolePad.PADS.slot(slot).device;
        if (d != null) d.state.simDown = bits;
    }

    private static Player player(Minecraft mc, int slot) {
        return slot == 0 ? mc.player : SecondaryPlayers.context(slot).player;
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        int slot = pass == 0 ? 1 : 0;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null || ConsolePad.PADS.slot(slot).device == null) {
            if (pass == 0) {
                pass = 1;
                t = 0;
                return false;
            }
            pass = 0;
            return true;
        }
        String who = "P" + (slot + 1);
        Player p = player(mc, slot);
        int rb = 1 << PadState.RB, lb = 1 << PadState.LB;
        if (t == 1) {
            mc.setScreen(null);
            SecondaryTick.scriptActive = false;
            p.getInventory().selected = 0;
        }
        if (t == 40) sim(slot, rb);
        if (t == 44) sim(slot, 0);
        if (t == 90) {
            s1 = p.getInventory().selected;
            check(who + ": RB cambia a otra casilla (de 0 a " + s1 + ")", s1 != 0);
            sim(slot, rb);
        }
        if (t == 94) sim(slot, 0);
        if (t == 140) {
            s2 = p.getInventory().selected;
            check(who + ": otro RB cambia otra vez (de " + s1 + " a " + s2 + ")", s2 != s1 && s2 != 0);
            sim(slot, lb | rb); // los dos a la vez: la casilla anterior
        }
        if (t == 144) sim(slot, 0);
        if (t == 190) {
            int now = p.getInventory().selected;
            check(who + ": LB y RB a la vez van a la casilla anterior (" + s1 + "; esta en " + now + ")", now == s1);
            sim(slot, lb | rb); // otra vez: vuelve
        }
        if (t == 194) sim(slot, 0);
        if (t == 240) {
            int now = p.getInventory().selected;
            check(who + ": y otra vez vuelven a la de antes (" + s2 + "; esta en " + now + ")", now == s2);
            p.getInventory().selected = 0;
            t = 0;
            if (pass == 0) {
                pass = 1;
                return false;
            }
            pass = 0;
            return true;
        }
        return false;
    }

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }
}
