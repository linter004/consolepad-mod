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
                    phase = 3;
                }
            }
            case 3 -> { // chat sobre el inventario con 1 jugador
                if (!AutoTest.leaveTo(mc, 1)) return false;
                chatOverContainerSetup(mc, 1);
                phase = 4;
            }
            case 4 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 5;
                }
            }
            case 5 -> { // y con 4
                if (!AutoTest.joinTo(mc, 4)) return false;
                chatOverContainerSetup(mc, 4);
                phase = 6;
            }
            case 6 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 7;
                }
            }
            case 7 -> { // la casilla acaba con P2 dentro (leaveTest lo necesita)
                if (!AutoTest.leaveTo(mc, 2)) return false;
                if (!AutoTest.joinTo(mc, 2)) return false;
                phase = 99;
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

    // ---------------------------------------------------------------- tarea 2: chat largo sobre el inventario

    private static void sayAll(Minecraft mc, String tag) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            for (int i = 0; i < 6; i++) server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(tag + " linea " + i + " de un chat largo"), false);
        });
    }

    private static void forEachPlayer(Minecraft mc, int n, java.util.function.IntConsumer body) {
        for (int slot = 0; slot < n; slot++) body.accept(slot);
    }

    private static int extrasLines(int n) {
        int min = Integer.MAX_VALUE, max = 0;
        for (int slot = 1; slot < n; slot++) {
            PlayerContext c = SecondaryPlayers.context(slot);
            int v = c == null ? -1 : c.chatLinesDrawn;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return n == 1 ? 0 : (max == 0 ? 0 : min);
    }

    private static void chatOverContainerSetup(Minecraft mc, int n) {
        String who = n + " jugador" + (n > 1 ? "es" : "");
        int[] base = new int[2];
        Settings2Test.startSeq(
                Settings2Test.step(who + ": prepara (sin pantallas) y escribe 6 lineas de chat", 25, () -> {
                    if (Settings2Test.seqTick == 0) {
                        if (mc.screen != null) mc.setScreen(null);
                        forEachPlayer(mc, n, slot -> {
                            PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
                            if (c != null && c.screen != null) ContextSwitcher.run(c, () -> mc.setScreen(null));
                        });
                        sayAll(mc, "A");
                    }
                    if (Settings2Test.seqTick == 12) {
                        base[0] = SplitDiag.p1ChatShown;
                        base[1] = SplitDiag.p1ChatHidden;
                    }
                }, () -> SplitDiag.p1ChatShown > base[0] && SplitDiag.p1ChatHidden == base[1] && (n == 1 || extrasLines(n) > 0)),
                Settings2Test.step(who + ": abre el inventario de todos", 4, () -> {
                    if (Settings2Test.seqTick == 0) {
                        forEachPlayer(mc, n, slot -> {
                            PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
                            if (slot == 0) mc.setScreen(new InventoryScreen(mc.player));
                            else ContextSwitcher.run(c, () -> mc.setScreen(new InventoryScreen(mc.player)));
                        });
                        sayAll(mc, "B");
                    }
                }, null),
                Settings2Test.step(who + ": con un contenedor abierto nadie dibuja el historial", 4, () -> {
                    if (Settings2Test.seqTick == 0) {
                        base[0] = SplitDiag.p1ChatShown;
                        base[1] = SplitDiag.p1ChatHidden;
                    }
                }, null),
                Settings2Test.step(who + ": con un contenedor abierto el chat de cada jugador se oculta (P1 oculto en varios fotogramas, 0 dibujado; extras 0 lineas)", 20, () -> { },
                        () -> SplitDiag.p1ChatShown == base[0] && SplitDiag.p1ChatHidden > base[1] + 5
                                && (n == 1 || (allExtrasHaveScreen(n) && extrasLinesAll(n, 0)))),
                Settings2Test.step(who + ": cierra los inventarios", 4, () -> {
                    if (Settings2Test.seqTick == 0) {
                        forEachPlayer(mc, n, slot -> {
                            PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
                            if (slot == 0) mc.setScreen(null);
                            else ContextSwitcher.run(c, () -> mc.setScreen(null));
                        });
                        sayAll(mc, "C");
                        base[0] = SplitDiag.p1ChatShown;
                    }
                }, null),
                Settings2Test.step(who + ": sin pantallas el chat vuelve en todos", 20, () -> { },
                        () -> SplitDiag.p1ChatShown > base[0] + 5 && (n == 1 || extrasLines(n) > 0)));
    }

    private static boolean allExtrasHaveScreen(int n) {
        for (int slot = 1; slot < n; slot++) {
            PlayerContext c = SecondaryPlayers.context(slot);
            if (c == null || !(c.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return false;
        }
        return true;
    }

    private static boolean extrasLinesAll(int n, int expected) {
        for (int slot = 1; slot < n; slot++) {
            PlayerContext c = SecondaryPlayers.context(slot);
            if (c == null || c.chatLinesDrawn != expected) return false;
        }
        return true;
    }
}
