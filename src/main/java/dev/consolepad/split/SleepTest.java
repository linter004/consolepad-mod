package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;

/**
 * Aviso de dormir juntos (casilla 49, con P2 dentro): de noche (de dia vanilla despierta al instante), P2 se acuesta y P1 ve "Durmiendo: Jugador 2" y
 * "Faltan: Jugador 1"; al levantarse P2, el aviso desaparece.
 */
public final class SleepTest {
    private static int t;
    private static String p2Text = "";
    private static long dayTime0;
    private static net.minecraft.core.BlockPos bed;
    private static net.minecraft.world.level.block.state.BlockState before;

    private SleepTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null) return true;
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(c.player.getUUID());
        if (t == 1) {
            mc.setScreen(null);
            SecondaryTick.scriptActive = false;
            dayTime0 = server.overworld().getDayTime();
            SleepNotice.resetForTest();
            server.execute(() -> server.overworld().setDayTime(18000)); // de noche
        }
        if (t == 8) { // unos ticks despues: el cielo tarda en oscurecerse y con dia vanilla despierta al instante; y hace falta una cama de verdad bajo el jugador
            server.execute(() -> {
                bed = sp.blockPosition().east(1);
                before = sp.level().getBlockState(bed);
                sp.level().setBlock(bed, net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState(), 3);
                sp.startSleeping(bed);
            });
        }
        if (t == 35) {
            String text = SleepNotice.lastText[0];
            check("dormir juntos: con P2 en la cama, P1 ve quien duerme y quien falta ('" + text + "'; durmiendo=" + sp.isSleeping() + ", dia=" + sp.level().isDay() + ", hora=" + server.overworld().getDayTime() + ", jugadores=" + SleepNotice.who(mc) + ", pantalla P1=" + (mc.screen == null ? "-" : mc.screen.getClass().getSimpleName()) + ")",
                    text.contains(I18n.tr("Durmiendo: ") + PlayerLabels.labelOf(1)) && text.contains(I18n.tr("Faltan: ") + PlayerLabels.labelOf(0)));
            p2Text = SleepNotice.lastText[1];
            check("dormir juntos: el aviso se ve entero al principio (opacidad " + SleepNotice.lastAlpha[0] + ")", SleepNotice.lastAlpha[0] == 1f);
            SleepNotice.advanceForTest(8_000); // como si llevara unos 9 s
        }
        if (t == 40) {
            float a = SleepNotice.lastAlpha[0];
            check("dormir juntos: hacia los 9 s se esta difuminando (opacidad " + a + ", entre 0 y 1)", a > 0.2f && a < 0.8f);
            SleepNotice.advanceForTest(3_500); // pasados los 12 s
        }
        if (t == 45) {
            check("dormir juntos: a los 12 s ya no se dibuja ('" + SleepNotice.lastText[0] + "', opacidad " + SleepNotice.lastAlpha[0] + ") aunque P2 siga en la cama", SleepNotice.lastText[0].isEmpty() && SleepNotice.lastAlpha[0] == 0f);
            check("dormir juntos: P2 en la cama tiene su pantalla para levantarse (" + c.screen + ")", c.screen instanceof ExtraBedScreen);
            check("dormir juntos: P2, que duerme, tambien ve a quien falta ('" + p2Text + "')", p2Text.contains(I18n.tr("Faltan: ") + PlayerLabels.labelOf(0)));
            ConsolePad.PADS.slot(1).device.state.simDown = 1 << dev.consolepad.pad.PadState.B; // B: levantarse
        }
        if (t == 48) ConsolePad.PADS.slot(1).device.state.simDown = 0;
        if (t == 56) check("dormir juntos: B levanta a P2 de la cama (durmiendo=" + sp.isSleeping() + ", pantalla " + c.screen + ")", !sp.isSleeping() && c.screen == null);
        if (t == 60) {
            check("dormir juntos: al levantarse P2 el aviso desaparece ('" + SleepNotice.lastText[0] + "')", SleepNotice.lastText[0].isEmpty());
            server.execute(() -> {
                server.overworld().setDayTime(dayTime0);
                sp.level().setBlock(bed, before, 3);
            });
        }
        if (t == 70) {
            t = 0;
            return true;
        }
        return false;
    }
}
