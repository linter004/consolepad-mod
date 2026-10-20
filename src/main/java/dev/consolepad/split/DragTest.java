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
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Repartir objetos como en Bedrock (casilla 49, con P2 dentro): el jugador coge una pila de 8, mantiene X (clic derecho) y se mueve con la cruceta de
 * hueco en hueco; al soltar X queda un objeto en cada hueco recorrido, el de partida incluido, y el resto sigue en el cursor. Se hace con P2 y con P1.
 */
public final class DragTest {
    private static int t, pass;
    private static GameType prevMode;
    private static List<ItemStack> saved;

    private DragTest() {}

    private static void sim(int slot, int bits) {
        PadManager.Device d = ConsolePad.PADS.slot(slot).device;
        if (d != null) d.state.simDown = bits;
    }

    private static Screen screenOf(Minecraft mc, int slot) {
        return slot == 0 ? mc.screen : SecondaryPlayers.context(slot).screen;
    }

    private static Player playerOf(Minecraft mc, int slot) {
        return slot == 0 ? mc.player : SecondaryPlayers.context(slot).player;
    }

    private static void cursorTo(Minecraft mc, int slot, int menuSlot) {
        var screen = (AbstractContainerScreen<?>) screenOf(mc, slot);
        Slot sl = screen.getMenu().slots.get(menuSlot);
        double x = screen.getGuiLeft() + sl.x + 8, y = screen.getGuiTop() + sl.y + 8;
        if (slot == 0) {
            double[] gc = PadDriver.guiCursor(mc);
            long window = mc.getWindow().getWindow();
            GLFW.glfwSetCursorPos(window, x * gc[2], y * gc[3]);
            ((MouseHandlerInvoker) mc.mouseHandler).consolepad$onMove(window, x * gc[2], y * gc[3]);
        } else {
            PlayerContext c = SecondaryPlayers.context(slot);
            c.cursorX = x;
            c.cursorY = y;
        }
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        int slot = pass == 0 ? 1 : 0;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null || ConsolePad.PADS.slot(slot).device == null) {
            if (pass == 0) pass = 1;
            else {
                pass = 0;
                return true;
            }
            t = 0;
            return false;
        }
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(playerOf(mc, slot).getUUID());
        String who = "P" + (slot + 1);
        if (t == 1) {
            mc.setScreen(null);
            SecondaryTick.scriptActive = false;
            server.execute(() -> {
                saved = new ArrayList<>();
                for (ItemStack st : sp.getInventory().items) saved.add(st.copy());
                prevMode = sp.gameMode.getGameModeForPlayer();
                sp.setGameMode(GameType.SURVIVAL);
                for (int i = 0; i < sp.getInventory().items.size(); i++) sp.getInventory().items.set(i, ItemStack.EMPTY);
                sp.getInventory().items.set(0, new ItemStack(Items.DIRT, 8));
            });
        }
        if (t == 10) {
            if (slot == 0) mc.setScreen(new InventoryScreen(mc.player));
            else ContextSwitcher.run(c, () -> mc.setScreen(new InventoryScreen(c.player)));
        }
        if (t == 30 && screenOf(mc, slot) instanceof InventoryScreen) cursorTo(mc, slot, 36); // hueco 0 de la barra
        if (t == 36) sim(slot, 1 << PadState.A); // coger la pila
        if (t == 40) sim(slot, 0);
        if (t == 50) {
            Settings2Test.check(who + ": el cursor del mando se dibuja con el color del jugador (" + Integer.toHexString(VirtualCursor.lastRgb[slot]) + ")", VirtualCursor.lastRgb[slot] == PlayerLabels.accent(slot));
            Settings2Test.check(who + ": rereparto: coge la pila de 8 con A (en el cursor " + playerOf(mc, slot).containerMenu.getCarried().getCount() + ")",
                    screenOf(mc, slot) instanceof InventoryScreen && playerOf(mc, slot).containerMenu.getCarried().getCount() == 8);
            sim(slot, 1 << PadState.DPAD_RIGHT); // al hueco 1
        }
        if (t == 54) sim(slot, 0);
        if (t == 60) sim(slot, 1 << PadState.X); // empezar a repartir con X mantenido
        if (t == 66) sim(slot, (1 << PadState.X) | (1 << PadState.DPAD_RIGHT)); // hueco 2
        if (t == 70) sim(slot, 1 << PadState.X);
        if (t == 76) sim(slot, (1 << PadState.X) | (1 << PadState.DPAD_RIGHT)); // hueco 3
        if (t == 80) sim(slot, 1 << PadState.X);
        if (t == 86) sim(slot, 0); // soltar: se reparte
        if (t == 110) {
            int[] counts = new int[4];
            for (int i = 0; i < 4; i++) counts[i] = sp.getInventory().items.get(i).getCount();
            int carried = playerOf(mc, slot).containerMenu.getCarried().getCount();
            Settings2Test.check(who + ": reparto: X mantenido y cruceta dejan 1 objeto en cada hueco recorrido, el de partida incluido (huecos 1 a 3: " + counts[1] + ", " + counts[2] + ", " + counts[3] + "; en el cursor " + carried + ")",
                    counts[1] == 1 && counts[2] == 1 && counts[3] == 1 && carried == 5);
            if (slot == 0) {
                if (mc.screen != null) mc.screen.onClose();
            } else {
                ContextSwitcher.run(c, () -> {
                    if (mc.screen != null) mc.screen.onClose();
                });
            }
        }
        if (t == 125) {
            server.execute(() -> {
                for (int i = 0; i < sp.getInventory().items.size(); i++) sp.getInventory().items.set(i, saved.get(i).copy());
                sp.setGameMode(prevMode);
                for (ItemEntity e : sp.serverLevel().getEntitiesOfClass(ItemEntity.class, sp.getBoundingBox().inflate(30))) e.discard();
            });
        }
        if (t == 140) {
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
}
