package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Informacion extra de un jugador (coordenadas, direccion, FPS) dibujada solo en SU region, arriba a la izquierda y dentro de su contexto
 * (mc.player es el suyo). Cada una se activa en los ajustes de ese jugador; no tiene nada que ver con la pantalla de depuracion (F3), que sigue
 * siendo solo de P1.
 */
public final class PlayerInfo {
    /** Ultimo texto dibujado por jugador (lineas separadas por "|"), para las comprobaciones del autotest. */
    public static final String[] lastText = {"", "", "", ""};

    private PlayerInfo() {}

    public static String directionName(Direction d) {
        return switch (d) {
            case NORTH -> "norte (-Z)";
            case SOUTH -> "sur (+Z)";
            case WEST -> "oeste (-X)";
            case EAST -> "este (+X)";
            default -> d.getName();
        };
    }

    /** Lineas que debe mostrar este jugador segun sus ajustes (vacio si no tiene ninguna activada). */
    public static List<String> lines(Minecraft mc, PadSettings cfg) {
        List<String> out = new ArrayList<>();
        if (mc.player == null) return out;
        if (cfg.showCoords) {
            out.add(String.format(Locale.ROOT, "XYZ: %.1f / %.1f / %.1f", mc.player.getX(), mc.player.getY(), mc.player.getZ()));
        }
        if (cfg.showDirection) {
            out.add(String.format(Locale.ROOT, "Mira al %s  %.0f°", directionName(mc.player.getDirection()), Mth.wrapDegrees(mc.player.getYRot())));
        }
        if (cfg.showFps) {
            out.add(mc.getFps() + " FPS");
        }
        return out;
    }

    public static void render(Minecraft mc, GuiGraphics g, int slot) {
        lastText[slot] = "";
        PadSettings cfg = ConsolePad.PADS.slot(slot).settings;
        if (!cfg.showHud || mc.options.hideGui || mc.screen != null) return;
        List<String> lines = lines(mc, cfg);
        if (lines.isEmpty()) return;
        lastText[slot] = String.join("|", lines);
        int y = 4;
        for (String line : lines) {
            int w = mc.font.width(line);
            g.fill(2, y - 1, 6 + w, y + mc.font.lineHeight, 0x70000000);
            g.drawString(mc.font, line, 4, y, 0xFFFFFFFF, true);
            y += mc.font.lineHeight + 2;
        }
    }
}
