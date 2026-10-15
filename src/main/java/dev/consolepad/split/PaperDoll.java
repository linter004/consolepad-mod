package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.player.Player;

/**
 * Muñeco del jugador en el HUD, como el "paper doll" de Bedrock: mientras el jugador corre, se agacha, nada, gatea o planea (y un par de segundos despues)
 * se dibuja su personaje con su skin en la esquina de arriba a la izquierda de su region, debajo de las lineas de informacion. Se apaga en los ajustes del jugador.
 */
public final class PaperDoll {
    private static final int HOLD_TICKS = 40;
    private static final int[] lastActive = {-10000, -10000, -10000, -10000};
    private static int tickCounter;
    /** Ultima caja {x, y, ancho, alto} dibujada por jugador (vacia si no se dibujo); para las pruebas. */
    public static final int[][] lastBox = new int[4][4];

    private PaperDoll() {}

    /** Slot (0 a 3) de un jugador de cliente: el de su contexto si es un extra, P1 en cualquier otro caso. */
    public static int slotOf(Object player) {
        for (PlayerContext c : SecondaryPlayers.contexts()) if (c.player == player) return c.slotIndex;
        return 0;
    }

    /** Si la postura del jugador pide el muñeco. */
    public static boolean posing(Player p) {
        return p.isSprinting() || p.isCrouching() || p.isSwimming() || p.isFallFlying() || p.isVisuallyCrawling();
    }

    /** Una vez por tick de cliente, por jugador: recuerda cuando fue la ultima vez que hizo algo que lo muestra. */
    public static void note(int slot, Player p) {
        if (posing(p)) lastActive[slot] = tickCounter;
    }

    public static void tick(Minecraft mc) {
        tickCounter++;
        if (mc.player == null) return;
        note(0, mc.player);
        for (PlayerContext c : SecondaryPlayers.contexts()) if (c.player != null) note(c.slotIndex, c.player);
    }

    /** Pruebas: como si el jugador acabara de correr. */
    public static void forceForTest(int slot) {
        lastActive[slot] = tickCounter;
    }

    public static boolean visible(int slot) {
        return tickCounter - lastActive[slot] <= HOLD_TICKS;
    }

    public static void render(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        lastBox[slot] = new int[4];
        PadSettings cfg = ConsolePad.PADS.slot(slot).settings;
        if (!cfg.paperDoll || !cfg.showHud || mc.options.hideGui || mc.screen != null || mc.player == null || !visible(slot)) return;
        if (h < 100 || w < 120) return; // en una region diminuta tapa la vista
        int lines = PlayerInfo.lastText[slot].isEmpty() ? 0 : PlayerInfo.lastText[slot].split("\\|").length;
        int x1 = 4, y1 = 6 + lines * (mc.font.lineHeight + 2), x2 = x1 + 28, y2 = y1 + 46;
        if (y2 > h - 40) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        InventoryScreen.renderEntityInInventoryFollowsAngle(g, x1, y1, x2, y2, 19, 0.0625f, 0.45f, 0f, mc.player);
        g.pose().popPose();
        lastBox[slot] = new int[] {x1, y1, x2 - x1, y2 - y1};
    }
}
