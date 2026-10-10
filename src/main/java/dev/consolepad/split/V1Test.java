package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.client.PadDriver;
import dev.consolepad.mixin.MouseHandlerInvoker;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Cierre de la v1 (casilla 49 del autotest): Y en pantallas de contenedor, chat largo sobre el inventario, particulas y animaciones de los extra
 * y cierre del mundo sin errores. Reutiliza el ejecutor de secuencias por ticks de {@link Settings2Test}.
 */
public final class V1Test {
    private static int phase;
    private static int calls;

    private V1Test() {}

    static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Lo llama la casilla 49 una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        calls++;
        switch (phase) {
            case 0 -> {
                if (!AutoTest.joinTo(mc, 2)) return false;
                Settings2Test.seq = new ArrayList<>();
                yButtonSetup(mc, 0);
                phase = 1;
            }
            case 1 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    yButtonSetup(mc, 1);
                    phase = 2;
                }
            }
            case 2 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 99;
                }
            }
            default -> {
                ConsolePad.LOGGER.info("[autotest] RESULTADO cierre de la v1 en conjunto -> {}", Settings2Test.allOk ? "OK" : "FALLO");
                phase = 0;
                calls = 0;
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tarea 1: Y en pantallas de contenedor

    private static GameType yPrevMode;

    private static int hotbarDirt(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < 9; i++) if (sp.getInventory().getItem(i).is(Items.DIRT)) n += sp.getInventory().getItem(i).getCount();
        return n;
    }

    /** Lleva el cursor de P1 (el raton real, que ya lo sigue el mando) a un punto de su pantalla en unidades de GUI. */
    private static void p1CursorTo(Minecraft mc, double gx, double gy) {
        double[] gc = PadDriver.guiCursor(mc);
        double nx = gx * gc[2], ny = gy * gc[3];
        long window = mc.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, nx, ny);
        ((MouseHandlerInvoker) mc.mouseHandler).consolepad$onMove(window, nx, ny);
    }

    private static void yButtonSetup(Minecraft mc, int slot) {
        PadManager pads = ConsolePad.PADS;
        PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(slot == 0 ? mc.player.getUUID() : c.player.getUUID());
        String who = "P" + (slot + 1);
        PadManager.Device dev = pads.slot(slot).device;
        IntConsumer sim = bits -> {
            if (dev != null) dev.state.simDown = bits;
        };
        Supplier<Screen> screen = () -> slot == 0 ? mc.screen : c.screen;
        Runnable open = () -> {
            if (slot == 0) mc.setScreen(new InventoryScreen(mc.player));
            else ContextSwitcher.run(c, () -> mc.setScreen(new InventoryScreen(mc.player)));
        };
        Runnable onSlot20 = () -> {
            if (!(screen.get() instanceof AbstractContainerScreen<?> s)) return;
            var sl = s.getMenu().slots.get(20);
            double gx = s.getGuiLeft() + sl.x + 8, gy = s.getGuiTop() + sl.y + 8;
            if (slot == 0) p1CursorTo(mc, gx, gy);
            else {
                c.cursorX = gx;
                c.cursorY = gy;
            }
        };
        Runnable nowhere = () -> {
            if (slot == 0) p1CursorTo(mc, 1, 1);
            else {
                c.cursorX = 1;
                c.cursorY = 1;
            }
        };
        String[] digest = new String[1];
        ItemStack[] saved = new ItemStack[36];
        Settings2Test.startSeq(
                Settings2Test.step(who + " prepara: supervivencia y 10 de tierra en el hueco 20", 12, () -> {
                    if (slot == 0 && mc.screen != null) mc.setScreen(null);
                    if (Settings2Test.seqTick == 0) {
                        yPrevMode = sp.gameMode.getGameModeForPlayer();
                        for (int i = 0; i < 36; i++) saved[i] = sp.getInventory().getItem(i).copy();
                        server.execute(() -> {
                            sp.setGameMode(GameType.SURVIVAL);
                            for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, ItemStack.EMPTY);
                            sp.getInventory().setItem(20, new ItemStack(Items.DIRT, 10));
                        });
                    }
                }, null),
                Settings2Test.step(who + " abre el inventario", 10, () -> {
                    if (Settings2Test.seqTick == 0) open.run();
                }, () -> screen.get() instanceof InventoryScreen),
                Settings2Test.step(who + " cursor sobre el hueco", 6, onSlot20, null),
                Settings2Test.step(who + " pulsa Y", 4, () -> {
                    onSlot20.run();
                    sim.accept(1 << PadState.Y);
                }, null),
                Settings2Test.step(who + " Y sobre un hueco: mueve el objeto rapido (hueco 20 vacio, 10 de tierra en la barra) y NO cierra el inventario", 12, () -> {
                    onSlot20.run();
                    sim.accept(0);
                }, () -> screen.get() instanceof InventoryScreen && sp.getInventory().getItem(20).isEmpty() && hotbarDirt(sp) == 10),
                Settings2Test.step(who + " cursor fuera de todo hueco", 6, () -> {
                    nowhere.run();
                    if (Settings2Test.seqTick == 0) digest[0] = sp.getInventory().items.toString();
                }, null),
                Settings2Test.step(who + " pulsa Y sin hueco", 4, () -> {
                    nowhere.run();
                    sim.accept(1 << PadState.Y);
                }, null),
                Settings2Test.step(who + " Y sin hueco bajo el cursor no hace nada (inventario igual y pantalla abierta)", 12, () -> {
                    nowhere.run();
                    sim.accept(0);
                }, () -> screen.get() instanceof InventoryScreen && sp.getInventory().items.toString().equals(digest[0]) && hotbarDirt(sp) == 10),
                Settings2Test.step(who + " pulsa B", 4, () -> sim.accept(1 << PadState.B), null),
                Settings2Test.step(who + " B cierra el inventario", 10, () -> {
                    sim.accept(0);
                    if (Settings2Test.seqTick == 9) {
                        server.execute(() -> {
                            sp.setGameMode(yPrevMode);
                            for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, saved[i]);
                        });
                    }
                }, () -> screen.get() == null));
    }
}
