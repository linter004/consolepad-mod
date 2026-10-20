package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;

/**
 * El teclado en pantalla se abre con un boton (casilla 49, con P2 dentro): con "Teclado automatico" apagado (el de fabrica) el chat se abre sin teclado, Y lo abre,
 * B lo cierra y otra B cierra el chat; igual en P1 (chat de vanilla) y en P2 (ExtraChatScreen).
 */
public final class KeyboardButtonTest {
    private static int t;
    private static boolean prev0, prev1;

    private KeyboardButtonTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    private static void pad(int slot, int bits) {
        var d = ConsolePad.PADS.slot(slot).device;
        if (d != null) d.state.simDown = bits;
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null) return true;
        var s0 = ConsolePad.PADS.slot(0).settings;
        var s1 = ConsolePad.PADS.slot(1).settings;
        if (t == 1) {
            mc.setScreen(null);
            prev0 = s0.autoKeyboard;
            prev1 = s1.autoKeyboard;
            s0.autoKeyboard = false;
            s1.autoKeyboard = false;
            SecondaryTick.scriptActive = true;
            SecondaryTick.scriptForward = 0f;
            SecondaryTick.scriptActions = java.util.EnumSet.noneOf(PadAction.class);
        }
        // ---- P2: su chat
        if (t == 3) SecondaryTick.scriptActions = java.util.EnumSet.of(PadAction.CHAT);
        if (t == 6) SecondaryTick.scriptActions = java.util.EnumSet.noneOf(PadAction.class);
        if (t == 15) check("teclado por boton (P2): el chat se abre sin teclado (pantalla " + c.screen + ", teclado=" + PadKeyboard.of(1).active() + ")", c.screen instanceof ExtraChatScreen && !PadKeyboard.of(1).active());
        if (t == 16) pad(1, 1 << PadState.Y);
        if (t == 19) pad(1, 0);
        if (t == 25) check("teclado por boton (P2): Y abre el teclado (teclado=" + PadKeyboard.of(1).active() + ")", PadKeyboard.of(1).active());
        if (t == 26) pad(1, 1 << PadState.B);
        if (t == 29) pad(1, 0);
        if (t == 35) check("teclado por boton (P2): B cierra el teclado y el chat sigue (teclado=" + PadKeyboard.of(1).active() + ", pantalla " + c.screen + ")", !PadKeyboard.of(1).active() && c.screen instanceof ExtraChatScreen);
        if (t == 36) pad(1, 1 << PadState.B);
        if (t == 39) pad(1, 0);
        if (t == 45) check("teclado por boton (P2): otra B cierra el chat (pantalla " + c.screen + ")", c.screen == null);
        // ---- P1: el chat de vanilla
        if (t == 50) mc.setScreen(new ChatScreen(""));
        if (t == 60) check("teclado por boton (P1): el chat se abre sin teclado (teclado=" + PadKeyboard.of(0).active() + ")", mc.screen instanceof ChatScreen && !PadKeyboard.of(0).active());
        if (t == 61) pad(0, 1 << PadState.Y);
        if (t == 64) pad(0, 0);
        if (t == 70) check("teclado por boton (P1): Y abre el teclado (teclado=" + PadKeyboard.of(0).active() + ")", PadKeyboard.of(0).active());
        if (t == 71) pad(0, 1 << PadState.B);
        if (t == 74) pad(0, 0);
        if (t == 78) check("teclado por boton (P1): B cierra el teclado y el chat sigue (teclado=" + PadKeyboard.of(0).active() + ", pantalla " + mc.screen + ")", !PadKeyboard.of(0).active() && mc.screen instanceof ChatScreen);
        if (t == 79) pad(0, 1 << PadState.B);
        if (t == 82) pad(0, 0);
        if (t == 90) {
            check("teclado por boton (P1): otra B cierra el chat (pantalla " + mc.screen + ")", mc.screen == null);
            if (mc.screen != null) mc.setScreen(null);
            s0.autoKeyboard = prev0;
            s1.autoKeyboard = prev1;
            SecondaryTick.scriptActive = false;
            t = 0;
            return true;
        }
        return false;
    }
}
