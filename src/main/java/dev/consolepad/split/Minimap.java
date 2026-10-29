package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Minimapa opcional de cada jugador (ajuste "showMinimap", apagado por defecto): el terreno que rodea al jugador visto desde arriba (norte arriba), con
 * los companeros como puntos de su color y las marcas de lugar como chinchetas. Los colores salen de los colores de mapa de los bloques y se refrescan
 * unas dos veces por segundo, no en cada fotograma.
 */
public final class Minimap {
    /** Bloques que se ven a cada lado del jugador y lado de la cuadricula. */
    public static final int RADIUS = 20, SIDE = RADIUS * 2;
    private static final int REFRESH_FRAMES = 30;

    private static final int[][] CELLS = new int[4][SIDE * SIDE];
    private static final int[] FRAME = new int[4], CX = new int[4], CZ = new int[4];
    private static final boolean[] READY = new boolean[4];
    /** Pruebas: fotogramas dibujados y recuentos de celdas con color por jugador. */
    public static final int[] drawn = new int[4], lastColored = new int[4], lastDots = new int[4];

    private Minimap() {}

    /** Dibuja el minimapa del jugador del slot en la esquina superior derecha de su region w x h (dentro de su contexto). */
    public static void render(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        PadSettings cfg = ConsolePad.PADS.slot(slot).settings;
        if (!cfg.showMinimap || !cfg.showHud || mc.options.hideGui || mc.screen != null || mc.player == null || mc.level == null) return;
        ClientLevel level = mc.level;
        Player me = mc.player;
        int px = (int) Math.floor(me.getX()), pz = (int) Math.floor(me.getZ());
        boolean stale = !READY[slot] || ++FRAME[slot] >= REFRESH_FRAMES || Math.abs(px - CX[slot]) > 2 || Math.abs(pz - CZ[slot]) > 2;
        if (stale) {
            FRAME[slot] = 0;
            CX[slot] = px;
            CZ[slot] = pz;
            sample(level, me, slot);
            READY[slot] = true;
        }
        int cell = h < 240 ? 1 : 2, size = SIDE * cell;
        int x0 = w - size - 6, y0 = 6;
        if (x0 < 2 || size + 12 > h) return; // la region es demasiado pequena
        int[] cells = CELLS[slot];
        g.pose().pushPose();
        g.pose().translate(0, 0, Companions.OVERLAY_Z);
        g.fill(x0 - 2, y0 - 2, x0 + size + 2, y0 + size + 2, 0xFF000000);
        int colored = 0;
        for (int j = 0; j < SIDE; j++) {
            int i = 0;
            while (i < SIDE) { // tramos horizontales del mismo color en un solo rectangulo
                int c = cells[j * SIDE + i], k = i + 1;
                while (k < SIDE && cells[j * SIDE + k] == c) k++;
                g.fill(x0 + i * cell, y0 + j * cell, x0 + k * cell, y0 + (j + 1) * cell, c);
                if (c != 0xFF303030) colored += k - i;
                i = k;
            }
        }
        lastColored[slot] = colored;
        // companeros (puntos de su color; si estan fuera, pegados al borde) y marcas de lugar
        int dots = 0;
        for (Player other : level.players()) {
            if (other == me) continue;
            int os = PlayerLabels.slotOf(mc, other.getGameProfile().getName());
            if (os < 0 || (os > 0 && !SecondaryPlayers.isActive(os))) continue;
            dot(g, x0, y0, cell, size, other.getX() - me.getX(), other.getZ() - me.getZ(), 0xFF000000 | PlayerLabels.themeRgb(os));
            dots++;
        }
        for (Coop.Waypoint wp : Coop.waypoints()) {
            if (wp.dimension() != level.dimension()) continue;
            double dx = (wp.x() - me.getX()) * cell, dz = (wp.z() - me.getZ()) * cell;
            double lim = size / 2.0 - 4;
            int mx = x0 + size / 2 + (int) Math.max(-lim, Math.min(lim, dx)), my = y0 + size / 2 + (int) Math.max(-lim, Math.min(lim, dz));
            PixelArt.draw(g, PixelArt.PIN, mx - 3, my - 6, 1, PlayerLabels.themeRgb(wp.slot()));
            dots++;
        }
        // el propio jugador: un cuadrado blanco con una raya hacia donde mira (el norte esta arriba; yaw 0 mira al sur)
        int cx = x0 + size / 2, cy = y0 + size / 2;
        double yaw = Math.toRadians(me.getYRot());
        int fx = cx + (int) Math.round(-Math.sin(yaw) * 5), fy = cy + (int) Math.round(Math.cos(yaw) * 5);
        g.fill(fx - 1, fy - 1, fx + 1, fy + 1, 0xFFFFD84A);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, 0xFF000000);
        g.fill(cx, cy, cx + 1, cy + 1, 0xFFFFFFFF);
        g.pose().popPose();
        lastDots[slot] = dots;
        drawn[slot]++;
    }

    private static void dot(GuiGraphics g, int x0, int y0, int cell, int size, double dx, double dz, int color) {
        double lim = size / 2.0 - 3;
        int mx = x0 + size / 2 + (int) Math.max(-lim, Math.min(lim, dx * cell)), my = y0 + size / 2 + (int) Math.max(-lim, Math.min(lim, dz * cell));
        g.fill(mx - 2, my - 2, mx + 3, my + 3, 0xFF000000);
        g.fill(mx - 1, my - 1, mx + 2, my + 2, color);
    }

    /** Rellena la cuadricula del slot con el color de la superficie de cada columna (gris oscuro si el trozo no esta cargado). */
    private static void sample(ClientLevel level, Player me, int slot) {
        int[] cells = CELLS[slot];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean ceiling = level.dimensionType().hasCeiling();
        int cx = CX[slot], cz = CZ[slot];
        for (int j = 0; j < SIDE; j++) {
            int prev = Integer.MIN_VALUE;
            for (int i = 0; i < SIDE; i++) {
                int x = cx - RADIUS + i, z = cz - RADIUS + j;
                int c = 0xFF303030;
                if (level.getChunkSource().getChunkNow(x >> 4, z >> 4) != null) {
                    int y = ceiling ? scanDown(level, x, z, (int) me.getY() + 3, pos) : level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    pos.set(x, y, z);
                    BlockState st = level.getBlockState(pos);
                    int rgb = st.getMapColor(level, pos).col;
                    float shade = prev == Integer.MIN_VALUE || y == prev ? 0.9f : y > prev ? 1.1f : 0.75f; // relieve: sube = mas claro, baja = mas oscuro
                    prev = y;
                    c = 0xFF000000 | (clamp(((rgb >> 16) & 255) * shade) << 16) | (clamp(((rgb >> 8) & 255) * shade) << 8) | clamp((rgb & 255) * shade);
                    if (rgb == 0) c = 0xFF303030; // aire o sin color
                }
                cells[j * SIDE + i] = c;
            }
        }
    }

    private static int clamp(float v) {
        return Math.max(0, Math.min(255, (int) v));
    }

    /** Dimensiones con techo (Nether): el primer bloque con color por debajo del jugador. */
    private static int scanDown(ClientLevel level, int x, int z, int fromY, BlockPos.MutableBlockPos pos) {
        int min = level.getMinBuildHeight();
        for (int y = Math.min(fromY, level.getMaxBuildHeight() - 1); y > min; y--) {
            pos.set(x, y, z);
            BlockState st = level.getBlockState(pos);
            if (!st.isAir() && st.getMapColor(level, pos).col != 0) return y;
        }
        return min;
    }
}
