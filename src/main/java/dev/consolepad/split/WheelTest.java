package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

import java.util.ArrayList;
import java.util.List;

/**
 * Rueda de acciones rapidas (dentro de la casilla 49, con P2 dentro): para P1 y P2, mantener el boton de la rueda la abre, el stick derecho elige la entrada
 * de la derecha, la camara no gira mientras esta abierta, soltar ejecuta el comando como ese jugador (llega al servidor: cambia su modo de juego) y soltar sin
 * elegir nada no ejecuta nada.
 */
public final class WheelTest {
    private static int t, phase;
    private static float yaw0;
    private static int exec0;
    private static GameType prevMode;

    private WheelTest() {}

    private static PadManager.Device dev(int slot) {
        return ConsolePad.PADS.slot(slot).device;
    }

    private static ServerPlayer sp(Minecraft mc, int slot) {
        var list = mc.getSingleplayerServer().getPlayerList();
        return list.getPlayer(slot == 0 ? mc.player.getUUID() : SecondaryPlayers.context(slot).player.getUUID());
    }

    private static float yaw(Minecraft mc, int slot) {
        return slot == 0 ? mc.player.getYRot() : SecondaryPlayers.context(slot).player.getYRot();
    }

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Editor de la rueda: la lista propia de un jugador no toca la de otro, anadir/editar/borrar y volver a la de todos. */
    private static void editorChecks(Minecraft mc) {
        var s0 = ConsolePad.PADS.slot(0).settings;
        var s1 = ConsolePad.PADS.slot(1).settings;
        var before = mc.screen;
        List<QuickWheel.Entry> common = new ArrayList<>();
        common.add(new QuickWheel.Entry("Uno", "time set day"));
        common.add(new QuickWheel.Entry("Dos", "weather clear"));
        common.add(new QuickWheel.Entry("Tres", "time set night"));
        QuickWheel.setEntriesForTest(common);
        s0.wheel = null;
        s1.wheel = null;
        check("rueda: sin rueda propia se usa la de todos (" + QuickWheel.listFor(s0).size() + " entradas)", QuickWheel.listFor(s0).size() == 3);
        var ed = new WheelEditorScreen(0, null);
        ed.init(mc, 427, 240);
        check("rueda: el editor lista las entradas y Anadir (" + ed.rowCount() + " filas)", ed.rowCount() == 4);
        var add = new WheelEntryScreen(0, -1, null);
        add.init(mc, 427, 240);
        add.setLabel("Mediodia");
        add.setCommand("/time set noon");
        String err = add.save();
        check("rueda: una entrada nueva se guarda como rueda propia del jugador 1 (error=" + err + ", " + QuickWheel.listFor(s0).size() + " entradas, ultima '" + QuickWheel.listFor(s0).get(QuickWheel.listFor(s0).size() - 1).command() + "')",
                err == null && s0.wheel != null && QuickWheel.listFor(s0).size() == 4 && QuickWheel.listFor(s0).get(3).command().equals("time set noon") && QuickWheel.listFor(s0).get(3).label().equals("Mediodia"));
        check("rueda: la rueda de otro jugador no cambia (" + QuickWheel.listFor(s1).size() + " entradas, y la comun sigue con 3)", QuickWheel.listFor(s1).size() == 3 && common.size() == 3 && s1.wheel == null);
        var edit = new WheelEntryScreen(0, 0, null);
        edit.init(mc, 427, 240);
        edit.setLabel("Amanecer");
        err = edit.save();
        check("rueda: editar una entrada cambia su nombre (" + QuickWheel.listFor(s0).get(0).label() + ")", err == null && QuickWheel.listFor(s0).get(0).label().equals("Amanecer") && QuickWheel.listFor(s0).get(0).command().equals("time set day"));
        var bad = new WheelEntryScreen(0, -1, null);
        bad.init(mc, 427, 240);
        bad.setLabel("Vacia");
        bad.setCommand("  ");
        check("rueda: un comando vacio no se guarda", bad.save() != null && QuickWheel.listFor(s0).size() == 4);
        var del = new WheelEntryScreen(0, 3, null);
        del.init(mc, 427, 240);
        del.pressDelete();
        check("rueda: borrar quita la entrada (" + QuickWheel.listFor(s0).size() + " entradas)", QuickWheel.listFor(s0).size() == 3);
        s0.wheel = null;
        check("rueda: volver a la de todos (" + QuickWheel.listFor(s0).size() + " entradas, " + QuickWheel.listFor(s0).get(0).label() + ")", QuickWheel.listFor(s0).get(0).label().equals("Uno"));
        ConsolePad.PADS.saveSettings();
        mc.setScreen(before);
    }

    /** Una vuelta por tick; true al terminar. */
    public static boolean run(Minecraft mc) {
        t++;
        int slot = phase == 0 ? 0 : 1;
        if (phase > 1) {
            editorChecks(mc);
            QuickWheel.setEntriesForTest(null);
            ConsolePad.LOGGER.info("[autotest] RESULTADO rueda de acciones rapidas -> fin");
            phase = 0;
            t = 0;
            return true;
        }
        PadManager.Device d = dev(slot);
        if (d == null) { // sin mando en el slot (la prueba solo se hace con los que hay)
            phase++;
            t = 0;
            return false;
        }
        int wheelBtn = QuickWheel.buttonOf(ConsolePad.PADS.slot(slot).settings);
        String who = "P" + (slot + 1);
        if (t == 1) {
            List<QuickWheel.Entry> list = new ArrayList<>();
            for (int i = 0; i < 8; i++) list.add(new QuickWheel.Entry("Entrada " + i, i == 2 ? "gamemode creative" : "list"));
            QuickWheel.setEntriesForTest(list);
            ServerPlayer p = sp(mc, slot);
            prevMode = p.gameMode.getGameModeForPlayer();
            mc.getSingleplayerServer().execute(() -> p.setGameMode(GameType.SURVIVAL));
            if (slot == 0 && mc.screen != null) mc.setScreen(null);
            SecondaryTick.scriptActive = false;
        }
        if (t == 20) {
            yaw0 = yaw(mc, slot);
            exec0 = QuickWheel.executed[slot];
            d.state.simDown = 1 << wheelBtn;
        }
        if (t == 24) {
            d.state.simRx = 1f; // stick derecho a la derecha: la entrada 2 (90 grados)
            d.state.simRy = 0.001f;
        }
        if (t == 40) {
            int sel = QuickWheel.selected(slot);
            int[] b = QuickWheel.lastBounds[slot];
            Settings2Test.check(who + ": mantener el boton de la rueda la abre y el stick derecho elige la entrada de la derecha (abierta=" + QuickWheel.isOpen(slot) + ", elegida " + sel
                    + ", dibujada en " + b[0] + "," + b[1] + " " + b[2] + "x" + b[3] + ")", wheelBtn >= 0 && QuickWheel.isOpen(slot) && sel == 2 && b[2] > 0 && b[0] >= 0 && b[1] >= 0);
            Settings2Test.check(who + ": con la rueda abierta la camara no gira (giro " + Math.abs(yaw(mc, slot) - yaw0) + ")", Math.abs(yaw(mc, slot) - yaw0) < 0.01f);
        }
        if (t == 42) {
            d.state.simRx = 0f;
            d.state.simRy = 0f;
            d.state.simDown = 0; // soltar: ejecuta
        }
        if (t == 90) {
            Settings2Test.check(who + ": soltar ejecuta el comando de la entrada ('" + QuickWheel.lastCommand[slot] + "', ejecutados " + (QuickWheel.executed[slot] - exec0) + ") y se cierra",
                    QuickWheel.executed[slot] == exec0 + 1 && QuickWheel.lastCommand[slot].equals("gamemode creative") && !QuickWheel.isOpen(slot));
            GameType gm = sp(mc, slot).gameMode.getGameModeForPlayer();
            Settings2Test.check(who + ": el comando llega al servidor como ese jugador (modo de juego " + gm + ")", gm == GameType.CREATIVE);
            ServerPlayer p = sp(mc, slot);
            mc.getSingleplayerServer().execute(() -> p.setGameMode(prevMode));
        }
        if (t == 100) {
            exec0 = QuickWheel.executed[slot];
            d.state.simDown = 1 << wheelBtn; // abrir y soltar sin elegir
        }
        if (t == 110) d.state.simDown = 0;
        if (t == 125) {
            Settings2Test.check(who + ": soltar sin elegir nada no ejecuta nada (ejecutados " + (QuickWheel.executed[slot] - exec0) + ")", QuickWheel.executed[slot] == exec0 && !QuickWheel.isOpen(slot));
            phase++;
            t = 0;
        }
        return false;
    }
}
