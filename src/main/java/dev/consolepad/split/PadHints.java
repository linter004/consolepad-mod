package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Ayuda de botones de cada jugador, como en las consolas: en el HUD abajo a la izquierda (tirar objeto, inventario) y abajo a la derecha
 * (atacar o romper, usar o colocar, segun a donde apunta y que lleva en la mano) y una barra con los botones de la pantalla abierta.
 * Los iconos son los del tipo de mando del jugador y los botones, sus bindings reales; una accion sin boton no muestra nada.
 */
public final class PadHints {
    public record Hint(int code, String text) {}

    /** Ultimo dibujo por slot (para el autotest): lineas de cada esquina y la caja de la ayuda, en unidades de GUI de su region. */
    public static final String[] lastLeft = {"", "", "", ""};
    public static final String[] lastRight = {"", "", "", ""};
    public static final String[] lastBar = {"", "", "", ""};
    /** Cajas {x, y, ancho, alto} de la columna izquierda y de la derecha. */
    public static final int[][] lastBoxL = new int[4][4], lastBoxR = new int[4][4];

    private PadHints() {}

    /** Boton del mando de esa configuracion que dispara la accion (el de codigo mas bajo si hay varios), o -1. */
    public static int buttonFor(PadSettings cfg, PadAction action) {
        int best = -1;
        for (var e : cfg.bindings.entrySet()) {
            if (e.getValue() == action && (best < 0 || e.getKey() < best)) best = e.getKey();
        }
        return best;
    }

    private static PadType typeOf(int slot) {
        PadManager.Device d = ConsolePad.PADS.slot(slot).device;
        return d == null ? PadType.XBOX : d.type;
    }

    // ------------------------------------------------------------------ HUD

    /** Texto del boton de atacar segun a donde apunta (null = nada que mostrar). */
    public static String attackLabel(HitResult hit) {
        if (hit == null) return null;
        return switch (hit.getType()) {
            case BLOCK -> "Romper";
            case ENTITY -> "Atacar";
            default -> null;
        };
    }

    /** Texto del boton de usar: colocar bloque si lleva un bloque en la mano, usar objeto en el resto. */
    public static String useLabel(net.minecraft.world.entity.player.Player player) {
        if (player != null && player.getMainHandItem().getItem() instanceof BlockItem) return "Colocar bloque";
        return "Usar objeto";
    }

    /** Ayuda del HUD de un jugador en su region de w x h (unidades de GUI de su interfaz). Dentro de su contexto: mc.player y mc.hitResult son los suyos. */
    public static void renderHud(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        lastLeft[slot] = "";
        lastRight[slot] = "";
        lastBoxL[slot] = new int[] {0, 0, 0, 0};
        lastBoxR[slot] = new int[] {0, 0, 0, 0};
        PadManager.Slot ps = ConsolePad.PADS.slot(slot);
        if (ps.device == null || !ps.settings.showHints || mc.player == null || mc.options.hideGui || mc.screen != null) return;
        if (mc.player.isSpectator()) return;
        PadSettings cfg = ps.settings;
        PadType type = ps.device.type;

        List<Hint> left = new ArrayList<>();
        int drop = buttonFor(cfg, PadAction.DROP);
        if (drop >= 0) left.add(new Hint(drop, "Tirar objeto"));
        int inv = buttonFor(cfg, PadAction.INVENTORY);
        if (inv >= 0) left.add(new Hint(inv, "Inventario"));

        List<Hint> right = new ArrayList<>();
        int attack = buttonFor(cfg, PadAction.ATTACK);
        String attackText = attackLabel(mc.hitResult);
        if (attack >= 0 && attackText != null) right.add(new Hint(attack, attackText));
        int use = buttonFor(cfg, PadAction.USE);
        if (use >= 0) right.add(new Hint(use, useLabel(mc.player)));

        Font font = mc.font;
        // La barra de objetos ocupa 182 de ancho en el centro: la ayuda de cada lado se encoge si no cabe en lo que queda.
        int margin = Math.max(24, (w - 182) / 2 - 8);
        int[] boxL = {Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0}, boxR = {Integer.MAX_VALUE, Integer.MAX_VALUE, 0, 0};
        drawColumn(g, font, left, type, 4, h - 4, margin, false, boxL);
        drawColumn(g, font, right, type, w - 4, h - 4, margin, true, boxR);
        lastLeft[slot] = text(left);
        lastRight[slot] = text(right);
        if (boxL[0] != Integer.MAX_VALUE) lastBoxL[slot] = new int[] {boxL[0], boxL[1], boxL[2] - boxL[0], boxL[3] - boxL[1]};
        if (boxR[0] != Integer.MAX_VALUE) lastBoxR[slot] = new int[] {boxR[0], boxR[1], boxR[2] - boxR[0], boxR[3] - boxR[1]};
    }

    private static String text(List<Hint> hints) {
        StringBuilder b = new StringBuilder();
        for (Hint hint : hints) {
            if (b.length() > 0) b.append('|');
            b.append(hint.code()).append(':').append(hint.text());
        }
        return b.toString();
    }

    /** Filas de [icono] texto, de abajo hacia arriba, pegadas a una esquina; si la mas ancha no cabe en maxW, se dibuja a menor escala. */
    private static void drawColumn(GuiGraphics g, Font font, List<Hint> hints, PadType type, int anchorX, int bottom, int maxW, boolean rightAlign, int[] box) {
        if (hints.isEmpty()) return;
        int widest = 0;
        for (Hint hint : hints) widest = Math.max(widest, PadIcons.width(font, hint.code(), type) + 3 + font.width(hint.text()));
        float k = widest > maxW ? Math.max(0.5f, (float) maxW / widest) : 1f;
        int row = 0;
        for (int i = hints.size() - 1; i >= 0; i--) { // la primera de la lista queda arriba
            Hint hint = hints.get(i);
            int iconW = PadIcons.width(font, hint.code(), type);
            int rowW = iconW + 3 + font.width(hint.text());
            int sx = rightAlign ? Math.round(anchorX - rowW * k) : anchorX;
            int sy = Math.round(bottom - (row + 1) * (PadIcons.H + 2) * k);
            g.pose().pushPose();
            g.pose().translate(sx, sy, 0);
            g.pose().scale(k, k, 1f);
            g.fill(-2, -1, rowW + 2, PadIcons.H + 1, 0x70000000);
            PadIcons.draw(g, font, 0, 0, hint.code(), type);
            g.drawString(font, hint.text(), iconW + 3, 1, 0xFFFFFFFF, true);
            g.pose().popPose();
            box[0] = Math.min(box[0], sx - 2);
            box[1] = Math.min(box[1], sy - 1);
            box[2] = Math.max(box[2], Math.round(sx + (rowW + 2) * k));
            box[3] = Math.max(box[3], Math.round(sy + (PadIcons.H + 1) * k));
            row++;
        }
    }

    // ------------------------------------------------------------------ pantallas

    /** Botones que hace cada pantalla (los fijos del cursor virtual de P1 y de los extra: A clic, X clic derecho, Y mover rapido, B salir). */
    public static List<Hint> screenHints(Screen screen) {
        List<Hint> out = new ArrayList<>();
        if (screen == null) return out;
        if (screen instanceof PlayerScreen ps && ps.listeningForPad()) return out;
        out.add(new Hint(PadState.A, "Seleccionar"));
        out.add(new Hint(PadState.B, screen instanceof PlayerMenuScreen || screen instanceof net.minecraft.client.gui.screens.PauseScreen ? "Volver" : "Salir"));
        if (screen instanceof AbstractContainerScreen<?>) {
            out.add(new Hint(PadState.X, "Mitad"));
            out.add(new Hint(PadState.Y, "Mover rápido"));
        }
        if (screen instanceof PlayerScreen ps && ps.hintTabs()) out.add(new Hint(PadState.LB, "Pestañas"));
        return out;
    }

    /**
     * Barra de ayuda abajo en la pantalla de un jugador (w x h = tamano de la pantalla en sus unidades). Dibujar tras la pantalla y antes del cursor.
     * Texto cuando la pantalla escucha un boton para el remapeo: la unica indicacion es esa.
     */
    public static void renderScreenBar(Minecraft mc, GuiGraphics g, int slot, Screen screen, int w, int h) {
        lastBar[slot] = "";
        PadManager.Slot ps = ConsolePad.PADS.slot(slot);
        if (screen == null || ps.device == null || !ps.settings.showHints) return;
        List<Hint> hints = screenHints(screen);
        if (hints.isEmpty()) return;
        Font font = mc.font;
        PadType type = ps.device.type;
        int total = 0;
        for (Hint hint : hints) total += PadIcons.width(font, hint.code(), type) + 3 + font.width(hint.text()) + 10;
        total -= 10;
        float k = total > w - 8 ? Math.max(0.5f, (float) (w - 8) / total) : 1f;
        int x = 4;
        int y = Math.round(h - (PadIcons.H + 3) * k);
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1f);
        g.fill(-4, -1, total + 4, PadIcons.H + 1, 0x90000000);
        int cx = 0;
        for (Hint hint : hints) {
            int iconW = PadIcons.draw(g, font, cx, 0, hint.code(), type);
            g.drawString(font, hint.text(), cx + iconW + 3, 1, 0xFFFFFFFF, true);
            cx += iconW + 3 + font.width(hint.text()) + 10;
        }
        g.pose().popPose();
        lastBar[slot] = text(hints);
    }
}
