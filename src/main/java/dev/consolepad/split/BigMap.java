package dev.consolepad.split;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapa grande del cuarto cuadrante (ajuste "mapQuadrant"): con 3 jugadores la pantalla se reparte en cuadrantes y el cuarto, que queda libre, muestra un mapa
 * del tamano del mayor mapa de Minecraft (escala 4: 128 x 128 casillas de 16 bloques, 2048 x 2048 bloques) con todos los jugadores que caen dentro. Va
 * alineado a la misma cuadricula que los mapas de vanilla; el terreno se va descubriendo con los trozos que cargan los jugadores (como un mapa sin explorar).
 */
public final class BigMap {
    public static final int CELLS = 128, BLOCKS_PER_CELL = 16, COVER = CELLS * BLOCKS_PER_CELL;
    /** Casillas exploradas a cada lado de un jugador en cada refresco (mas que la distancia de dibujo de la mayoria). */
    private static final int SCAN = 14;
    private static final int UNEXPLORED = 0xFFC9B58A;
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("consolepad", "bigmap");

    private static DynamicTexture texture;
    private static final int[] PIXELS = new int[CELLS * CELLS];
    private static int tileX = Integer.MIN_VALUE, tileZ = Integer.MIN_VALUE, frame;
    private static ResourceKey<Level> tileDim;
    private static boolean dirty;
    /** Pruebas: fotogramas dibujados, casillas descubiertas y jugadores dentro del mapa en el ultimo dibujado. */
    public static volatile int drawn, lastExplored, lastPlayers;

    private BigMap() {}

    /** Verdadero cuando el reparto de 3 jugadores deja libre el cuarto cuadrante para el mapa. */
    public static boolean active(int players) {
        return players == 3 && SplitSettings.mapQuadrant();
    }

    private record Who(int slot, Player player, ClientLevel level) {}

    private static List<Who> locals(Minecraft mc) {
        List<Who> out = new ArrayList<>();
        if (mc.player != null && mc.level != null) out.add(new Who(0, mc.player, mc.level));
        for (PlayerContext c : SecondaryPlayers.contexts()) {
            if (c.player != null && c.level != null) out.add(new Who(c.slotIndex, c.player, c.level));
        }
        return out;
    }

    /** Dibuja el mapa en el rectangulo {x, y, ancho, alto} (unidades de GUI de la ventana). */
    public static void render(Minecraft mc, GuiGraphics g, int[] r) {
        List<Who> all = locals(mc);
        if (all.isEmpty()) return;
        Who first = all.get(0);
        ResourceKey<Level> dim = first.level().dimension();
        // la casilla del mapa sale de la posicion de P1, con la misma cuadricula que los mapas de vanilla
        int cx = (int) Math.floor((first.player().getX() + 64.0) / COVER) * COVER + COVER / 2 - 64;
        int cz = (int) Math.floor((first.player().getZ() + 64.0) / COVER) * COVER + COVER / 2 - 64;
        if (cx != tileX || cz != tileZ || dim != tileDim || texture == null) {
            tileX = cx;
            tileZ = cz;
            tileDim = dim;
            java.util.Arrays.fill(PIXELS, UNEXPLORED);
            frame = 0;
            dirty = true;
            if (texture == null) {
                texture = new DynamicTexture(new NativeImage(NativeImage.Format.RGBA, CELLS, CELLS, true));
                mc.getTextureManager().register(TEXTURE, texture);
            }
        }
        int minX = cx - COVER / 2, minZ = cz - COVER / 2;
        if (frame++ % 20 == 0) {
            for (Who w : all) {
                if (w.level().dimension() == dim) explore(w, minX, minZ);
            }
        }
        if (dirty) {
            NativeImage img = texture.getPixels();
            for (int j = 0; j < CELLS; j++) {
                for (int i = 0; i < CELLS; i++) {
                    int argb = PIXELS[j * CELLS + i];
                    img.setPixelRGBA(i, j, (argb & 0xFF00FF00) | ((argb >> 16) & 0xFF) | ((argb & 0xFF) << 16)); // ARGB -> ABGR
                }
            }
            texture.upload();
            dirty = false;
        }
        int size = Math.min(r[2], r[3]) - 10;
        if (size < 32) return;
        int x0 = r[0] + (r[2] - size) / 2, y0 = r[1] + (r[3] - size) / 2;
        g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], 0xFF101010);
        g.fill(x0 - 2, y0 - 2, x0 + size + 2, y0 + size + 2, 0xFF000000);
        g.blit(TEXTURE, x0, y0, size, size, 0f, 0f, CELLS, CELLS, CELLS, CELLS);
        int explored = 0;
        for (int p : PIXELS) if (p != UNEXPLORED) explored++;
        lastExplored = explored;
        int inside = 0;
        for (Who w : all) {
            if (w.level().dimension() != dim) continue;
            double fx = (w.player().getX() - minX) / COVER, fz = (w.player().getZ() - minZ) / COVER;
            if (fx < 0 || fx >= 1 || fz < 0 || fz >= 1) continue; // fuera de lo que abarca el mapa
            int px = x0 + (int) (fx * size), py = y0 + (int) (fz * size);
            int rgb = 0xFF000000 | PlayerLabels.themeRgb(w.slot());
            g.fill(px - 3, py - 3, px + 4, py + 4, 0xFF000000);
            g.fill(px - 2, py - 2, px + 3, py + 3, rgb);
            if (size >= 100) g.drawString(mc.font, PlayerLabels.labelOf(w.slot()), px + 6, py - 4, rgb, true);
            inside++;
        }
        for (Coop.Waypoint wp : Coop.waypoints()) {
            if (wp.dimension() != dim) continue;
            double fx = (wp.x() - minX) / COVER, fz = (wp.z() - minZ) / COVER;
            if (fx < 0 || fx >= 1 || fz < 0 || fz >= 1) continue;
            PixelArt.draw(g, PixelArt.PIN, x0 + (int) (fx * size) - 3, y0 + (int) (fz * size) - 6, 1, PlayerLabels.themeRgb(wp.slot()));
        }
        g.drawCenteredString(mc.font, "N", x0 + size / 2, y0 + 2, 0xFFFFFFFF);
        lastPlayers = inside;
        drawn++;
    }

    /** Colorea las casillas que rodean a un jugador con el color de la superficie de su centro (solo si el trozo esta cargado en su mundo). */
    private static void explore(Who w, int minX, int minZ) {
        int pcx = (int) Math.floor((w.player().getX() - minX) / BLOCKS_PER_CELL), pcz = (int) Math.floor((w.player().getZ() - minZ) / BLOCKS_PER_CELL);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean ceiling = w.level().dimensionType().hasCeiling();
        for (int j = Math.max(0, pcz - SCAN); j <= Math.min(CELLS - 1, pcz + SCAN); j++) {
            for (int i = Math.max(0, pcx - SCAN); i <= Math.min(CELLS - 1, pcx + SCAN); i++) {
                int x = minX + i * BLOCKS_PER_CELL + BLOCKS_PER_CELL / 2, z = minZ + j * BLOCKS_PER_CELL + BLOCKS_PER_CELL / 2;
                if (w.level().getChunkSource().getChunkNow(x >> 4, z >> 4) == null) continue;
                int y;
                if (ceiling) {
                    y = (int) w.player().getY(); // sin cielo: la altura del jugador (el techo del Nether tapa el mapa)
                    for (int k = 0; k < 40 && y > w.level().getMinBuildHeight() + 1; k++, y--) {
                        pos.set(x, y, z);
                        if (!w.level().getBlockState(pos).isAir()) break;
                    }
                } else {
                    y = w.level().getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                }
                pos.set(x, y, z);
                int rgb = w.level().getBlockState(pos).getMapColor(w.level(), pos).col;
                if (rgb == 0) continue;
                int c = 0xFF000000 | (shade(rgb >> 16) << 16) | (shade(rgb >> 8) << 8) | shade(rgb);
                if (PIXELS[j * CELLS + i] != c) {
                    PIXELS[j * CELLS + i] = c;
                    dirty = true;
                }
            }
        }
    }

    private static int shade(int v) {
        return Math.max(0, Math.min(255, (int) ((v & 255) * 0.86f)));
    }
}
