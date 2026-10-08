package dev.consolepad.split;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.consolepad.ConsolePad;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.List;

/**
 * Diagnostico del aspecto por pasada: para cada pasada de mundo (P1/P2) anota la region declarada, el viewport y el
 * scissor reales de OpenGL, y m00/m11 de la proyeccion (aspecto = m11/m00, FOV = 2*atan(1/m11)). Solo actua mientras
 * {@link #recording} es true (autotest).
 */
public final class SplitDiag {
    /** Manos en primera persona dibujadas por jugador (la mano oculta no cuenta). */
    public static final int[] handsDrawn = new int[4];

    public static volatile boolean recording;

    /** Una muestra: de donde sale, region declarada, viewport/scissor GL reales y la proyeccion. */
    public record Sample(String pass, String where, int regionW, int regionH, int[] viewport, int[] scissor, float m00, float m11) {
        public float aspect() {
            return m11 / m00;
        }

        public double fovDeg() {
            return Math.toDegrees(2 * Math.atan(1.0 / m11));
        }
    }

    // --- HUD: por capa dibujada, el jugador cuyos datos usa y el ancho de interfaz de su region ---
    public static volatile boolean hudRecording;
    public static volatile String hudOwner = "-";
    public static volatile net.minecraft.world.entity.player.Player hudExpected;
    private static final java.util.Map<String, java.util.TreeMap<String, Integer>> hudSeen = new java.util.TreeMap<>();
    private static int hudWrong;
    private static String hudWrongWhat = "";

    public static synchronized void hudClear() {
        hudSeen.clear();
        hudWrong = 0;
        hudWrongWhat = "";
    }

    public static synchronized void onHudLayer(String layer, net.minecraft.world.entity.player.Player player, int guiWidth) {
        if (!hudRecording || hudExpected == null) return;
        hudSeen.computeIfAbsent(hudOwner, k -> new java.util.TreeMap<>()).put(layer, guiWidth);
        if (player != hudExpected) {
            hudWrong++;
            hudWrongWhat += hudOwner + ":" + layer + " ";
        }
    }

    public static synchronized java.util.Map<String, java.util.TreeMap<String, Integer>> hudSeen() {
        return new java.util.TreeMap<>(hudSeen);
    }

    public static synchronized int hudWrong() {
        return hudWrong;
    }

    public static synchronized String hudWrongWhat() {
        return hudWrongWhat;
    }

    // --- Desenfoque de fondo y objetos dibujados: scissor GL real en el momento de dibujar, y de quien es la pantalla ---
    public static volatile boolean uiRecording;
    /** Objeto que se vigila (por ejemplo el del cursor de P1); null = ninguno. */
    public static volatile net.minecraft.world.item.Item watchItem;
    public record UiDraw(String owner, String what, int[] scissor, int x, int y) {}
    private static final List<UiDraw> uiDraws = new ArrayList<>();

    private static String uiOwner() {
        PlayerContext c = ContextSwitcher.current();
        return c == null ? "P1" : c.label();
    }

    public static synchronized void uiClear() {
        uiDraws.clear();
    }

    public static synchronized List<UiDraw> uiDraws() {
        return new ArrayList<>(uiDraws);
    }

    private static int[] scissorBox() {
        int[] sc = new int[4];
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, sc);
        if (!GL11.glIsEnabled(GL11.GL_SCISSOR_TEST)) sc = new int[] {-1, -1, -1, -1};
        return sc;
    }

    /** GameRenderer.processBlurEffect: el desenfoque se aplica a lo que deje pasar el scissor de ese momento. */
    public static synchronized void onBlur() {
        if (!uiRecording) return;
        uiDraws.add(new UiDraw(uiOwner(), "desenfoque", scissorBox(), 0, 0));
    }

    /** Cada enableScissor de GuiGraphics: el scissor GL real que queda (recortes de la region, del maniqui del inventario...). */
    public static synchronized void onScissor() {
        if (!uiRecording) return;
        uiDraws.add(new UiDraw(uiOwner(), "scissor", scissorBox(), 0, 0));
    }

    public static synchronized void onItem(net.minecraft.world.item.ItemStack stack, int x, int y) {
        if (!uiRecording || watchItem == null || !stack.is(watchItem)) return;
        uiDraws.add(new UiDraw(uiOwner(), "objeto", scissorBox(), x, y));
    }

    private static final List<Sample> samples = new ArrayList<>();
    private static String currentPass = "-";
    private static int regionW, regionH;

    private SplitDiag() {}

    public static void beginPass(String pass, int w, int h) {
        currentPass = pass;
        regionW = w;
        regionH = h;
    }

    public static void endPass() {
        currentPass = "-";
    }

    public static synchronized void clear() {
        samples.clear();
    }

    public static synchronized List<Sample> samples() {
        return new ArrayList<>(samples);
    }

    /** GameRenderer.getProjectionMatrix devolvio esta matriz (todas las llamadas: nivel, culling y mano). */
    public static void onProjection(Matrix4f m) {
        if (!recording || regionW <= 0) return;
        add("getProjectionMatrix", m);
    }

    /** Etapas de renderLevel: proyeccion realmente activa en RenderSystem en ese momento. */
    public static void onStage(RenderLevelStageEvent e) {
        if (!recording || regionW <= 0) return;
        if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY || e.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES
                || e.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            add("stage:" + e.getStage(), RenderSystem.getProjectionMatrix());
        }
    }

    private static synchronized void add(String where, Matrix4f m) {
        int[] vp = new int[4];
        int[] sc = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, vp);
        GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, sc);
        samples.add(new Sample(currentPass, where, regionW, regionH, vp, sc, m.m00(), m.m11()));
    }

    /**
     * Comprueba que, en cada pasada, el aspecto de la proyeccion (getProjectionMatrix) y el viewport GL real coinciden con
     * la relacion de la region declarada, con una tolerancia relativa. Devuelve el numero de desviaciones.
     */
    public static int check(String label, double tolerance) {
        List<Sample> list = samples();
        int bad = 0;
        for (Sample s : list) {
            double region = (double) s.regionW() / s.regionH();
            double vp = (double) s.viewport()[2] / s.viewport()[3];
            double proj = s.aspect();
            boolean projChecked = s.where().equals("getProjectionMatrix");
            double devProj = Math.abs(proj / vp - 1.0);
            double devVp = Math.abs(vp / region - 1.0);
            boolean fail = devVp > tolerance || (projChecked && devProj > tolerance);
            if (fail) bad++;
            ConsolePad.LOGGER.info("[diag] {} {} {}: region {}x{} ({}), viewport GL {} (aspecto {}), scissor {}, proyeccion m00={} m11={} aspecto={} fov={} | dev(proj/vp)={}% dev(vp/region)={}%{}",
                    label, s.pass(), s.where(), s.regionW(), s.regionH(), fmt(region), java.util.Arrays.toString(s.viewport()), fmt(vp),
                    java.util.Arrays.toString(s.scissor()), fmt(s.m00()), fmt(s.m11()), fmt(proj), fmt(s.fovDeg()),
                    fmt(devProj * 100), fmt(devVp * 100), fail ? "  <== FUERA DE TOLERANCIA" : (projChecked ? "" : "  (informativo)"));
        }
        return bad;
    }

    private static String fmt(double d) {
        return String.format(java.util.Locale.ROOT, "%.4f", d);
    }
}
