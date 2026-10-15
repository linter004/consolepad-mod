package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.client.PadDriver;
import dev.consolepad.mixin.MouseHandlerInvoker;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Soltar mantenido y cursor de P1 (se ejecuta dentro de la casilla 49, con P2 dentro): un toque corto de B suelta un objeto; B mantenido suelta varios
 * seguidos (P1 y P2) y se detiene al soltar el boton; con el mando moviendo el cursor de P1 se dibuja la flecha virtual y se oculta el del sistema, y un
 * movimiento real del raton lo devuelve.
 */
public final class DropCursorTest {
    private static int t, phase;
    private static int start;
    private static final List<ItemStack>[] SAVED = new List[2];
    private static GameType[] prevMode = new GameType[2];
    private static boolean pad0Own;

    private DropCursorTest() {}

    private static ServerPlayer sp(Minecraft mc, int slot) {
        var list = mc.getSingleplayerServer().getPlayerList();
        return list.getPlayer(slot == 0 ? mc.player.getUUID() : SecondaryPlayers.context(slot).player.getUUID());
    }

    private static int dirt(Minecraft mc, int slot) {
        return sp(mc, slot).getInventory().countItem(Items.DIRT);
    }

    private static void sim(int slot, int bits) {
        PadManager.Device d = ConsolePad.PADS.slot(slot).device;
        if (d != null) d.state.simDown = bits;
    }

    private static int repeats(int slot) {
        return slot == 0 ? PadDriver.dropRepeats : SecondaryTick.dbgDropRepeat;
    }

    /** Una vuelta por tick; true al terminar. */
    public static boolean run(Minecraft mc) {
        t++;
        int slot = phase >= 10 && phase < 20 ? 1 : 0;
        switch (phase) {
            case 0 -> {
                if (t == 1) {
                    SecondaryTick.scriptActive = false;
                    for (int s = 0; s < 2; s++) {
                        ServerPlayer p = sp(mc, s);
                        final int fs = s;
                        mc.getSingleplayerServer().execute(() -> {
                            SAVED[fs] = new ArrayList<>();
                            for (ItemStack st : p.getInventory().items) SAVED[fs].add(st.copy());
                            prevMode[fs] = p.gameMode.getGameModeForPlayer();
                            p.setGameMode(GameType.SURVIVAL);
                            for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, ItemStack.EMPTY);
                            p.getInventory().items.set(p.getInventory().selected, new ItemStack(Items.DIRT, 64));
                        });
                    }
                }
                if (t == 40) {
                    phase = 1;
                    t = 0;
                }
            }
            case 1, 10 -> { // toque corto: suelta exactamente 1
                int sl = phase == 1 ? 0 : 1;
                if (t == 5) start = dirt(mc, sl);
                if (t == 8) sim(sl, 1 << PadState.B);
                if (t == 12) sim(sl, 0);
                if (t == 40) {
                    int used = start - dirt(mc, sl);
                    Settings2Test.check("P" + (sl + 1) + ": un toque corto de B suelta 1 objeto (soltados " + used + ")", used == 1);
                    phase = phase == 1 ? 2 : 11;
                    t = 0;
                }
            }
            case 2, 11 -> { // mantenido 2 s: varios seguidos
                int sl = phase == 2 ? 0 : 1;
                if (t == 5) {
                    start = dirt(mc, sl);
                    holdStartRepeats = repeats(sl);
                }
                if (t == 8) sim(sl, 1 << PadState.B);
                if (t == 48) sim(sl, 0); // 40 ticks = 2 s
                if (t == 60) {
                    int used = start - dirt(mc, sl);
                    int reps = repeats(sl) - holdStartRepeats;
                    Settings2Test.check("P" + (sl + 1) + ": B mantenida 2 s suelta varios objetos seguidos (soltados " + used + ", repeticiones " + reps + ")", used >= 8 && used <= 26 && reps >= 6);
                    afterReps = repeats(sl); // el jugador recoge de nuevo lo que tira (esta encima), asi que se cuentan las repeticiones y no los objetos
                }
                if (t == 100) {
                    Settings2Test.check("P" + (sl + 1) + ": al soltar B se detiene (repeticiones " + afterReps + " -> " + repeats(sl) + ")", repeats(sl) == afterReps);
                    phase = phase == 2 ? 10 : 20;
                    t = 0;
                    if (phase == 20) { // fin de los objetos: restaurar
                        for (int s = 0; s < 2; s++) {
                            ServerPlayer p = sp(mc, s);
                            final int fs = s;
                            mc.getSingleplayerServer().execute(() -> {
                                for (int i = 0; i < p.getInventory().items.size(); i++) p.getInventory().items.set(i, SAVED[fs].get(i).copy());
                                p.setGameMode(prevMode[fs]);
                                for (ItemEntity e : p.serverLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(30))) e.discard();
                            });
                        }
                        phase = 20;
                    }
                }
            }
            case 20 -> { // cursor de P1 movido por el mando
                if (t == 30) {
                    PadManager.Device d = ConsolePad.PADS.slot(0).device;
                    pad0Own = d != null;
                    mc.setScreen(new InventoryScreen(mc.player));
                }
                if (t == 45) sim(0, 1 << PadState.A);
                if (t == 50) sim(0, 0);
                if (t == 60) {
                    int mode = GLFW.glfwGetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR);
                    Settings2Test.check("P1: con el mando manejando la pantalla se usa la flecha virtual y se oculta el cursor del sistema (padCursor=" + PadDriver.padCursor + ", modo "
                            + mode + ")", pad0Own && PadDriver.padCursor && mode == GLFW.GLFW_CURSOR_HIDDEN);
                    long window = mc.getWindow().getWindow();
                    double nx = mc.mouseHandler.xpos() + 40, ny = mc.mouseHandler.ypos() + 25;
                    GLFW.glfwSetCursorPos(window, nx, ny);
                    ((MouseHandlerInvoker) mc.mouseHandler).consolepad$onMove(window, nx, ny); // un movimiento del raton real
                }
                if (t == 75) {
                    int mode = GLFW.glfwGetInputMode(mc.getWindow().getWindow(), GLFW.GLFW_CURSOR);
                    Settings2Test.check("P1: mover el raton real devuelve el cursor del sistema (padCursor=" + PadDriver.padCursor + ", modo " + mode + ")",
                            !PadDriver.padCursor && mode == GLFW.GLFW_CURSOR_NORMAL);
                    mc.setScreen(null);
                }
                if (t == 90) {
                    phase = 30;
                    t = 0;
                }
            }
            case 30 -> { // sonidos: el servidor se los manda a P1 y a P2, solo suena el de P1; los bucles de las abejas (queueTickingSound) igual
                if (t == 5) {
                    sndPlayed0 = SplitDiag.soundsPlayed;
                    sndMuted0 = SplitDiag.soundsMuted;
                    sndQ0 = SplitDiag.tickingQueued;
                    sndQM0 = SplitDiag.tickingMuted;
                    ServerPlayer p = sp(mc, 0);
                    mc.getSingleplayerServer().execute(() -> {
                        var lv = p.serverLevel();
                        var p2 = sp(mc, 1);
                        var mid = p.position().add(p2.position()).scale(0.5); // a menos de 16 bloques de los dos (si no, el servidor no se lo manda al otro)
                        lv.playSound(null, mid.x, mid.y, mid.z, net.minecraft.sounds.SoundEvents.COW_AMBIENT, net.minecraft.sounds.SoundSource.NEUTRAL, 1f, 1f);
                        var bee = net.minecraft.world.entity.EntityType.BEE.create(lv);
                        if (bee != null) {
                            bee.moveTo(p.getX() + 3, p.getY() + 1, p.getZ(), 0f, 0f);
                            bee.setNoAi(true);
                            lv.addFreshEntity(bee);
                            beeId = bee.getId();
                        }
                    });
                }
                if (t == 60) {
                    int played = SplitDiag.soundsPlayed - sndPlayed0, muted = SplitDiag.soundsMuted - sndMuted0;
                    int queued = SplitDiag.tickingQueued - sndQ0, qmuted = SplitDiag.tickingMuted - sndQM0;
                    Settings2Test.check("sonido: un sonido del servidor suena una vez en P1 y se silencia en P2 (suenan " + played + ", silenciados " + muted + ")", played >= 1 && muted >= 1);
                    Settings2Test.check("sonido: el bucle de una abeja se encola una vez en P1 y se silencia en P2 (encolados " + queued + ", silenciados " + qmuted + ")", queued == 1 && qmuted >= 1);
                    mc.getSingleplayerServer().execute(() -> {
                        var e = sp(mc, 0).serverLevel().getEntity(beeId);
                        if (e != null) e.discard();
                    });
                }
                if (t == 80) {
                    ConsolePad.LOGGER.info("[autotest] RESULTADO soltar mantenido y cursor de P1 -> fin");
                    return true;
                }
            }
            default -> { }
        }
        return false;
    }

    private static int holdStartRepeats, afterReps, sndPlayed0, sndMuted0, sndQ0, sndQM0, beeId;
}
