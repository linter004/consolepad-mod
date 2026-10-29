package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Indicador de companero, como la barra de localizacion de Bedrock: sobre la barra de experiencia de cada jugador (la de siempre se sigue viendo debajo)
 * hay un punto del color de cada uno de los demas jugadores locales (en el mismo mundo y dimension) que se mueve a izquierda y derecha segun hacia donde
 * esta el companero respecto a donde mira el jugador: en el centro, delante; en un extremo, a ese lado o detras (con una marca extra). Con la opcion
 * "Distancia del companero" (apagada por defecto), encima de los corazones tambien su nombre y la distancia. Solo aparece cuando el companero esta lejos (no estorba si ya se le ve) y se apaga en los ajustes de cada
 * jugador. Las posiciones salen del servidor integrado, asi que valen aunque el companero este fuera del alcance de dibujado.
 */
public final class Companions {
    /** Altura (z) a la que se dibuja lo que va encima de todo el HUD: el HUD de P1 lo dibujan las capas de NeoForge, que se separan 200 unidades cada una, asi que a 300 la barra de experiencia tapaba el punto. */
    public static final float OVERLAY_Z = 1200f;
    /** Ultimo centro del primer punto dibujado por jugador en pixeles de la ventana {x, y}; para las pruebas de pixeles. */
    public static final int[][] lastDotPx = new int[4][];
    /** Distancia minima (bloques) para dibujar el punto. */
    public static final double MIN_DISTANCE = 10;
    /** Ancho de la barra de experiencia (y de la barra de objetos) de vanilla. */
    public static final int BAR_W = 182;
    /** Pruebas: sustituye la distancia minima (negativo = la normal). */
    public static double minDistanceOverride = -1;
    /** Ultimo texto dibujado por jugador (nombres y distancias separados por "|"), para las comprobaciones del autotest. */
    public static final String[] lastText = {"", "", "", ""};
    /** Ultima posicion {x, y} de cada punto dibujado por jugador (en unidades de su region); para las pruebas. */
    public static final int[][][] lastDots = new int[4][][];
    /** Cuantas etiquetas de nombre y distancia se dibujaron la ultima vez por jugador (0 si la opcion de distancia esta apagada); para las pruebas. */
    public static final int[] lastLabels = new int[4];

    /** Vida (en puntos, 2 = un corazon) por debajo de la cual el marcador del companero parpadea en rojo: menos de 4 corazones. */
    public static final float LOW_HEALTH = 8f;
    /** Cuantos marcadores de companero con poca vida se dibujaron la ultima vez por jugador; para las pruebas. */
    public static final int[] lastLow = new int[4];

    /** Un marcador: un companero (slot 0 a 3, con su vida) o la cama propia (slot -1, vida -1). */
    public record Mark(int slot, String name, int rgb, double distance, float relAngle, float health) {
        public boolean bed() {
            return slot == -1;
        }

        /** Una marca de lugar puesta por un jugador (rueda de acciones). */
        public boolean waypoint() {
            return slot == -2;
        }
    }

    public static boolean isLow(float health) {
        return health > 0f && health <= LOW_HEALTH;
    }


    private Companions() {}

    /** Angulo (grados) al que esta el objetivo respecto a donde mira el jugador: 0 = delante, positivo = a la derecha, +-180 = detras. */
    public static float relativeAngle(float viewerYaw, double vx, double vz, double tx, double tz) {
        double dx = tx - vx, dz = tz - vz;
        float targetYaw = (float) (Math.atan2(-dx, dz) * 180.0 / Math.PI); // misma convencion que el giro de Minecraft (0 = sur, 90 = oeste)
        return Mth.wrapDegrees(targetYaw - viewerYaw);
    }

    /** Posicion horizontal (0 a barW) de un punto de la barra para un angulo relativo: +-90 grados son los extremos, mas alla se queda en el extremo. */
    public static int barX(float rel, int barW) {
        float k = Math.max(-1f, Math.min(1f, rel / 90f));
        return Math.round(barW / 2f + k * barW / 2f);
    }

    /** Fila de la barra de experiencia de vanilla en una region de alto h (guiHeight - 32 + 3). */
    public static int barY(int h) {
        return h - 29;
    }

    /** Los companeros que ve el jugador del contexto activo (mc.player), ordenados del slot menor al mayor. */
    public static List<Mark> marks(Minecraft mc, int viewerSlot) {
        List<Mark> out = new ArrayList<>();
        var server = mc.getSingleplayerServer();
        if (server == null || mc.player == null || mc.level == null) return out;
        double min = minDistanceOverride >= 0 ? minDistanceOverride : MIN_DISTANCE;
        List<ServerPlayer> players;
        try {
            players = new ArrayList<>(server.getPlayerList().getPlayers());
            players.removeIf(java.util.Objects::isNull); // el servidor cambia la lista (entra o sale un jugador) mientras se copia desde el hilo de dibujo: la copia puede traer un hueco
        } catch (RuntimeException e) {
            return out; // la lista cambio mientras se copiaba: se dibuja en el siguiente fotograma
        }
        ServerPlayer viewer = null;
        for (ServerPlayer sp : players) {
            if (sp.getUUID().equals(mc.player.getUUID())) {
                viewer = sp;
                continue;
            }
            int slot = PlayerLabels.slotOf(mc, sp.getGameProfile().getName());
            if (slot < 0 || (slot > 0 && !SecondaryPlayers.isActive(slot))) continue;
            if (sp.level().dimension() != mc.level.dimension()) continue;
            double dx = sp.getX() - mc.player.getX(), dz = sp.getZ() - mc.player.getZ(), dy = sp.getY() - mc.player.getY();
            double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (dist < min) continue;
            out.add(new Mark(slot, PlayerLabels.labelOf(slot), PlayerLabels.themeRgb(slot), dist,
                    relativeAngle(mc.player.getYRot(), mc.player.getX(), mc.player.getZ(), sp.getX(), sp.getZ()), sp.getHealth()));
        }
        out.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        for (Coop.Waypoint w : Coop.waypoints()) { // marcas de lugar de todos (tambien las propias)
            if (w.dimension() != mc.level.dimension()) continue;
            double wdx = w.x() - mc.player.getX(), wdy = w.y() - mc.player.getY(), wdz = w.z() - mc.player.getZ();
            double wdist = Math.sqrt(wdx * wdx + wdy * wdy + wdz * wdz);
            if (wdist < 4.0) continue;
            out.add(new Mark(-2, I18n.tr("Marca") + " " + PlayerLabels.labelOf(w.slot()), PlayerLabels.themeRgb(w.slot()), wdist,
                    relativeAngle(mc.player.getYRot(), mc.player.getX(), mc.player.getZ(), w.x(), w.z()), -1f));
        }
        if (viewer != null && ConsolePad.PADS.slot(viewerSlot).settings.showBedMarker) { // segundo marcador: la cama o el punto de reaparicion de este jugador
            var bed = viewer.getRespawnPosition();
            if (bed != null && viewer.getRespawnDimension() == mc.level.dimension()) {
                double bx = bed.getX() + 0.5, by = bed.getY(), bz = bed.getZ() + 0.5;
                double ddx = bx - mc.player.getX(), ddy = by - mc.player.getY(), ddz = bz - mc.player.getZ();
                double bdist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                if (bdist >= min) out.add(new Mark(-1, I18n.tr("Cama"), 0xE8E8FF, bdist, relativeAngle(mc.player.getYRot(), mc.player.getX(), mc.player.getZ(), bx, bz), -1f));
            }
        }
        return out;
    }

    /** Dibuja el punto de cada companero sobre la barra de experiencia del jugador, en su region de w x h (dentro de su contexto). */
    public static void render(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        lastText[slot] = "";
        lastDots[slot] = new int[0][];
        lastLabels[slot] = 0;
        lastLow[slot] = 0;
        lastDotPx[slot] = null;
        PadSettings cfg = ConsolePad.PADS.slot(slot).settings;
        if (!cfg.showCompanions || !cfg.showHud || mc.options.hideGui || mc.screen != null) return;
        List<Mark> marks = marks(mc, slot);
        if (marks.isEmpty()) return;
        int barW = Math.min(BAR_W, Math.max(60, w - 4)), x0 = (w - barW) / 2, y = barY(h);
        g.pose().pushPose();
        g.pose().translate(0, 0, OVERLAY_Z);
        StringBuilder text = new StringBuilder();
        int[][] dots = new int[marks.size()][];
        int idx = 0, row = 0;
        for (Mark m : marks) {
            if (text.length() > 0) text.append('|');
            String dist = String.format(Locale.ROOT, "%.0f m", m.distance());
            text.append(m.name()).append(' ').append(dist);
            int x = x0 + barX(m.relAngle(), barW);
            int color = 0xFF000000 | m.rgb();
            if (isLow(m.health())) { // poca vida: el cuadrado parpadea en rojo (sin texto)
                lastLow[slot]++;
                if ((System.nanoTime() / 350_000_000L) % 2 == 0) color = 0xFFFF3030;
            }
            // Un cuadrado de 5x5 (la barra de experiencia mide 5 de alto) dentro de la barra, con un borde oscuro de 1 pixel para que se distinga del relleno verde.
            if (m.waypoint()) {
                PixelArt.draw(g, PixelArt.PIN, x - 3, y - 2, 1, m.rgb()); // la marca de lugar es una chincheta, no un cuadrado
            } else {
            g.fill(x - 3, y - 1, x + 4, y + 6, 0xFF000000);
            g.fill(x - 2, y, x + 3, y + 5, color);
            }
            if (m.bed()) g.fill(x - 1, y + 1, x + 2, y + 4, 0xFF303030); // la cama es un anillo: el hueco la distingue de un companero
            if (Math.abs(m.relAngle()) > 90f) { // detras o muy de lado: una marca de 2x3 junto al cuadrado, hacia ese lado
                int side = m.relAngle() > 0 ? 1 : -1;
                g.fill(x + side * 4, y, x + side * 7, y + 5, 0xFF000000);
                g.fill(x + side * 4, y + 1, x + side * 6, y + 4, color);
            }
            if (idx == 0) {
                var v = new org.joml.Vector4f(x, y + 2, 0f, 1f).mul(g.pose().last().pose());
                double k = mc.getWindow().getGuiScale();
                lastDotPx[slot] = new int[] {(int) Math.round(v.x() * k), (int) Math.round(v.y() * k)};
            }
            dots[idx++] = new int[] {x, y};
            if (cfg.showCompanionDistance) { // por defecto solo se ve la direccion; con la opcion, tambien el nombre y la distancia
                String label = m.name() + " " + dist;
                int tw = mc.font.width(label);
                int lx = Mth.clamp(x - tw / 2, Math.max(2, x0), Math.max(2, Math.min(w - tw - 2, x0 + barW - tw)));
                int ly = y - 20 - row * (mc.font.lineHeight + 1); // encima de los corazones y la comida (que acaban 10 por encima de la barra)
                if (ly >= 0) {
                    g.fill(lx - 2, ly - 1, lx + tw + 2, ly + mc.font.lineHeight, 0x80000000);
                    g.drawString(mc.font, label, lx, ly, color, true);
                    lastLabels[slot]++;
                }
                row++;
            }
        }
        g.pose().popPose();
        lastText[slot] = text.toString();
        lastDots[slot] = dots;
    }

    /** Prueba numerica de las funciones puras: null si bien. */
    public static String selfTest() {
        float ahead = relativeAngle(0, 0, 0, 0, 10), west = relativeAngle(0, 0, 0, -10, 0), east = relativeAngle(0, 0, 0, 10, 0), back = Math.abs(relativeAngle(0, 0, 0, 0, -10));
        float turned = relativeAngle(90, 0, 0, -10, 0); // mirando al oeste, el oeste esta delante
        if (Math.abs(ahead) > 0.5f || Math.abs(west - 90) > 0.5f || Math.abs(east + 90) > 0.5f || Math.abs(back - 180) > 0.5f || Math.abs(turned) > 0.5f) {
            return "angulos " + ahead + ", " + west + ", " + east + ", " + back + ", " + turned;
        }
        if (barX(0, 100) != 50 || barX(90, 100) != 100 || barX(-90, 100) != 0 || barX(180, 100) != 100 || barX(-45, 100) != 25) {
            return "barra " + barX(0, 100) + ", " + barX(90, 100) + ", " + barX(-90, 100) + ", " + barX(-45, 100);
        }
        if (barY(240) != 211 || barY(135) != 106) return "fila de la barra de experiencia " + barY(240) + ", " + barY(135);
        return null;
    }
}
