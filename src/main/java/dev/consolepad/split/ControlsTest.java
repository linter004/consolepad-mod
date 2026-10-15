package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Pantalla de controles con diagrama (casilla 21 del autotest): a 854x480 y 1920x1080, con 1 y con 4 jugadores y con las 4 siluetas (Xbox, PlayStation,
 * Switch Pro, Joy-Con), abre la pantalla de cada jugador y mide: ninguna etiqueta se solapa con otra, ni con la silueta, ni con los botones inferiores;
 * todas dentro de la zona libre; iconos de los botones sin solapes; posiciones fisicas y simbolos propios de cada mando (A/B intercambiados en
 * Nintendo, cruz/circulo/cuadrado/triangulo en PlayStation); y el D-pad llega desde una etiqueta a todas las demas y a los botones inferiores.
 */
public final class ControlsTest {
    private static final int[][] WINDOWS = {{854, 480}, {1920, 1080}};
    private static final int[] PLAYERS = {1, 4};
    private static final ControlsLayout.Kind[] KINDS = ControlsLayout.Kind.values();

    private static int win, pl, kind, step, t;
    private static boolean ok = true;

    private ControlsTest() {}

    private static void verdict(String name, boolean good) {
        ok &= good;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, good ? "OK" : "FALLO");
    }

    private static Screen screenOf(Minecraft mc, int slot) {
        if (slot == 0) return mc.screen;
        PlayerContext c = SecondaryPlayers.context(slot);
        return c == null ? null : c.screen;
    }

    private static void setScreen(Minecraft mc, int slot, java.util.function.Supplier<Screen> s) {
        if (slot == 0) mc.setScreen(s.get());
        else {
            PlayerContext c = SecondaryPlayers.context(slot);
            if (c != null) ContextSwitcher.run(c, () -> mc.setScreen(s.get()));
        }
    }

    private static boolean overlap(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    private static boolean inside(int[] a, int w, int top, int bottom) {
        return a[0] >= 0 && a[0] + a[2] <= w && a[1] >= top && a[1] + a[3] <= bottom;
    }

    /** Lo que se mide de la pantalla abierta de un jugador. */
    private static void measure(Minecraft mc, int slot, String label) {
        String who = label + " P" + (slot + 1);
        Screen sc = screenOf(mc, slot);
        if (!(sc instanceof PlayerControlsScreen cs)) {
            verdict(who + ": se abre la pantalla de controles (hay " + (sc == null ? "ninguna" : sc.getClass().getSimpleName()) + ")", false);
            return;
        }
        ControlsLayout.Kind kind = cs.kind();
        var lay = cs.layout();
        int n = ControlsLayout.CODES.length;
        int top = ControlsLayout.TOP, bottom = cs.height - ControlsLayout.BOTTOM_RESERVE;
        List<String> bad = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int[] a = lay.label[i];
            if (a == null || a[2] <= 0 || a[3] <= 0) {
                bad.add("sin caja " + i);
                continue;
            }
            if (!inside(a, cs.width, top, bottom)) bad.add("fuera de la zona libre " + java.util.Arrays.toString(a));
            for (int j = i + 1; j < n; j++) if (lay.label[j] != null && overlap(a, lay.label[j])) bad.add("solape " + i + "/" + j);
            if (overlap(a, new int[] {lay.sx, lay.sy, lay.sw, lay.sh})) bad.add("pisa la silueta " + i);
            if (overlap(a, cs.resetBox()) || overlap(a, cs.backBox())) bad.add("pisa Restablecer/Atras " + i);
        }
        verdict(who + " (" + cs.width + "x" + cs.height + "): 16 etiquetas sin solapes entre si, con la silueta " + java.util.Arrays.toString(new int[] {lay.sx, lay.sy, lay.sw, lay.sh})
                + " ni con los botones inferiores, dentro de la zona libre [" + top + "," + bottom + "]: " + (bad.isEmpty() ? "sin problemas" : bad.toString()), bad.isEmpty());

        // Iconos de los botones: ninguno pisa a otro.
        List<String> ic = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (Math.abs(lay.ax[i] - lay.ax[j]) < lay.icon && Math.abs(lay.ay[i] - lay.ay[j]) < lay.icon) ic.add(i + "/" + j);
            }
        }
        verdict(who + ": iconos de " + n + " botones (tamano " + lay.icon + ") sin solapes: " + (ic.isEmpty() ? "sin problemas" : ic.toString()), ic.isEmpty());

        // Posiciones fisicas y simbolos propios del mando.
        int a = ControlsLayout.index(PadState.A), b = ControlsLayout.index(PadState.B), x = ControlsLayout.index(PadState.X), y = ControlsLayout.index(PadState.Y);
        boolean pos = lay.ay[a] > lay.ay[b] && lay.ay[y] < lay.ay[b] && lay.ax[b] > lay.ax[a] && lay.ax[x] < lay.ax[a];
        var type = kind.family;
        String gA = PadIcons.glyph(PadState.A, type), gB = PadIcons.glyph(PadState.B, type), gX = PadIcons.glyph(PadState.X, type), gY = PadIcons.glyph(PadState.Y, type);
        boolean sym = switch (kind) {
            case PLAYSTATION -> gA.equals("×") && gB.equals("○") && gX.equals("□") && gY.equals("△");
            case SWITCH_PRO, JOY_CON -> gA.equals("B") && gB.equals("A") && gX.equals("Y") && gY.equals("X");
            default -> gA.equals("A") && gB.equals("B") && gX.equals("X") && gY.equals("Y");
        };
        boolean sides = true;
        for (int i = 0; i < n; i++) {
            boolean left = ControlsLayout.leftSide(ControlsLayout.CODES[i]);
            if (ControlsLayout.topLabel(ControlsLayout.CODES[i])) sides &= lay.label[i][1] + lay.label[i][3] <= lay.sy; // menu y pausa: encima de la silueta
            else sides &= left ? lay.label[i][0] + lay.label[i][2] <= lay.sx : lay.label[i][0] >= lay.sx + lay.sw;
        }
        verdict(who + ": " + kind.label + ": posiciones (abajo/derecha/izquierda/arriba = botones " + gA + "/" + gB + "/" + gX + "/" + gY + ")=" + pos + ", simbolos de su familia=" + sym
                + ", etiquetas de cada lado fuera de la silueta=" + sides, pos && sym && sides);

        // Cada etiqueta lleva el nombre de la accion del boton (o "Sin accion").
        boolean names = true;
        for (int code : ControlsLayout.CODES) {
            var act = ConsolePad.PADS.slot(slot).settings.bindings.get(code);
            names &= cs.labelText(code).equals(act == null ? "Sin acción" : PadNames.action(act));
        }
        verdict(who + ": cada etiqueta muestra la funcion actual de su boton=" + names, names);

        // Iconos a eleccion del jugador: automatico = los de su mando; 1 Xbox, 2 PlayStation, 3 Nintendo (el simbolo de A cambia: A, cruz, B).
        if (kind == ControlsLayout.Kind.XBOX) {
            var st = ConsolePad.PADS.slot(slot).settings;
            int prev = st.iconStyle;
            String[] want = {"A", "×", "B"};
            boolean icons = true;
            StringBuilder sb = new StringBuilder();
            for (int v = 0; v <= 3; v++) {
                st.iconStyle = v;
                var want2 = v == 0 ? kind.family : dev.consolepad.pad.PadType.values()[v - 1];
                boolean this1 = cs.iconType() == want2 && ConsolePad.PADS.slot(slot).iconType() == want2;
                if (v >= 1) this1 &= PadIcons.glyph(PadState.A, cs.iconType()).equals(want[v - 1]);
                icons &= this1;
                sb.append(v).append('=').append(cs.iconType()).append(' ');
            }
            st.iconStyle = prev;
            verdict(who + ": ajuste de iconos (auto/Xbox/PlayStation/Nintendo) cambia la familia de iconos del jugador: " + sb.toString().trim(), icons);
        }

        // El D-pad (con el cursor) llega de una etiqueta a todas las demas y a Restablecer y Atras.
        if (kind == ControlsLayout.Kind.XBOX) {
            var targets = SecondaryScreens.targets(cs);
            double[] start = {cs.labelCenter(PadState.A)[0], cs.labelCenter(PadState.A)[1]};
            java.util.Set<String> seen = new java.util.HashSet<>();
            java.util.ArrayDeque<double[]> q = new java.util.ArrayDeque<>();
            q.add(start);
            seen.add((int) start[0] + "," + (int) start[1]);
            while (!q.isEmpty()) {
                double[] cur = q.poll();
                for (int d = 0; d < 4; d++) {
                    double[] to = SecondaryScreens.pick(targets, cur[0], cur[1], d);
                    if (to != null && seen.add((int) to[0] + "," + (int) to[1])) q.add(new double[] {to[0], to[1]});
                }
            }
            verdict(who + ": con el D-pad se llega a " + seen.size() + " de " + targets.size() + " destinos (16 etiquetas + Restablecer + Atras)", targets.size() == n + 2 && seen.size() == targets.size());
        }
    }

    /** true cuando termina. */
    public static boolean run(Minecraft mc) {
        t++;
        var window = mc.getWindow();
        switch (step) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (t == 2) GLFW.glfwSetWindowSize(window.getWindow(), WINDOWS[win][0], WINDOWS[win][1]);
                if (t > 70) {
                    step = 1;
                    t = 0;
                }
            }
            case 1 -> {
                int n = PLAYERS[pl];
                int now = 1 + SecondaryPlayers.contexts().size();
                boolean done = n >= now ? AutoTest.joinTo(mc, n) : AutoTest.leaveTo(mc, n);
                if (done && t > 10) {
                    step = 2;
                    t = 0;
                } else if (t > 2400) {
                    verdict("pasar a " + n + " jugadores", false);
                    step = 9;
                    t = 0;
                }
            }
            case 2 -> {
                int n = PLAYERS[pl];
                if (t == 1) {
                    PlayerControlsScreen.forcedKind = KINDS[kind];
                    for (int s = 0; s < n; s++) {
                        int slot = s;
                        setScreen(mc, slot, () -> new PlayerControlsScreen(slot, null));
                    }
                }
                if (t == 12) {
                    String label = WINDOWS[win][0] + "x" + WINDOWS[win][1] + "/" + n + "j/" + KINDS[kind].label;
                    for (int s = 0; s < n; s++) measure(mc, s, label);
                    if (n == 1 && win == 1) AutoTest.requestShot("controls-" + KINDS[kind].name().toLowerCase() + ".png"); // para ver el diagrama
                }
                if (t == 15) {
                    for (int s = 0; s < n; s++) setScreen(mc, s, () -> null);
                    kind++;
                    t = 0;
                    if (kind >= KINDS.length) {
                        kind = 0;
                        pl++;
                        step = 1;
                        if (pl >= PLAYERS.length) {
                            pl = 0;
                            win++;
                            step = win >= WINDOWS.length ? 8 : 0;
                        }
                    }
                }
            }
            case 8 -> {
                PlayerControlsScreen.forcedKind = null;
                if (t == 1) ConsolePad.LOGGER.info("[autotest] RESULTADO pantalla de controles con diagrama en conjunto -> {}", ok ? "OK" : "FALLO");
                if (t == 2) GLFW.glfwSetWindowSize(window.getWindow(), 1920, 1080);
                if (t > 70 && (AutoTest.leaveTo(mc, 2) || t > 800)) {
                    step = 0;
                    win = 0;
                    pl = 0;
                    kind = 0;
                    t = 0;
                    return true;
                }
            }
            default -> {
                PlayerControlsScreen.forcedKind = null;
                step = 8;
                t = 0;
            }
        }
        return false;
    }
}
