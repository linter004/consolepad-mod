package dev.consolepad.split;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.neoforged.fml.loading.FMLPaths;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Rueda de acciones rapidas de cada jugador: mantener el boton de la rueda (cruceta izquierda por defecto) la abre en su region, el stick derecho elige una
 * entrada (la camara no gira mientras esta abierta) y soltar el boton la ejecuta como un comando del propio jugador ("/time set day"...). Si no se eligio
 * nada, soltar la cierra sin hacer nada. Las entradas son las de config/consolepad/wheel.json ([{"label": "Dia", "command": "time set day"}, ...], hasta 8):
 * se crea con unas por defecto y se puede editar a mano. Los comandos necesitan permiso, como en el chat (en un mundo de un jugador, trucos activados).
 */
public final class QuickWheel {
    public record Entry(String label, String command) {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    public static final int MAX_ENTRIES = 8;
    private static volatile List<Entry> entries;

    private static final boolean[] OPEN = new boolean[4];
    private static final int[] SELECTED = {-1, -1, -1, -1};
    private static final float[] HELD = new float[4];
    /** Cuantos comandos ha ejecutado la rueda de cada jugador y el ultimo (diagnostico y pruebas). */
    public static final int[] executed = new int[4];
    public static final String[] lastCommand = {"", "", "", ""};

    private QuickWheel() {}

    private static List<Entry> defaults() {
        List<Entry> l = new ArrayList<>();
        l.add(new Entry("Día", "time set day"));
        l.add(new Entry("Despejar", "weather clear"));
        l.add(new Entry("Noche", "time set night"));
        l.add(new Entry("Ir con alguien", "tp @s @a[sort=furthest,limit=1,distance=0.01..]"));
        l.add(new Entry("Supervivencia", "gamemode survival"));
        l.add(new Entry("Curarme", "effect give @s minecraft:instant_health 1 10 true"));
        l.add(new Entry("Creativo", "gamemode creative"));
        l.add(new Entry("Traer a alguien", "tp @a[sort=furthest,limit=1,distance=0.01..] @s"));
        return l;
    }

    private static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("wheel.json");
    }

    /** Las entradas de la rueda (se leen una vez; se crea el archivo con las de fabrica si no existe). */
    public static List<Entry> entries() {
        List<Entry> e = entries;
        if (e != null) return e;
        Path path = file();
        List<Entry> loaded = null;
        if (Files.isRegularFile(path)) {
            try (Reader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                List<Entry> read = GSON.fromJson(r, new TypeToken<List<Entry>>() {}.getType());
                if (read != null) {
                    loaded = new ArrayList<>();
                    for (Entry en : read) {
                        if (en != null && en.label() != null && en.command() != null && !en.command().isBlank() && loaded.size() < MAX_ENTRIES) loaded.add(en);
                    }
                    if (loaded.isEmpty()) loaded = null;
                }
            } catch (IOException | RuntimeException ex) {
                ConsolePad.LOGGER.warn("No se pudo leer {} ({}); se usan las entradas de fabrica", path, ex.toString());
            }
        }
        boolean fresh = loaded == null;
        if (fresh) loaded = defaults();
        entries = loaded;
        if (fresh && !Files.isRegularFile(path)) {
            try {
                Files.createDirectories(path.getParent());
                try (Writer w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
                    GSON.toJson(loaded, w);
                }
            } catch (IOException | RuntimeException ex) {
                ConsolePad.LOGGER.warn("No se pudo guardar {}: {}", path, ex.toString());
            }
        }
        return loaded;
    }

    /** Las entradas que usa un jugador: las suyas si las ha cambiado, si no las comunes. */
    public static List<Entry> listFor(PadSettings cfg) {
        if (cfg != null && cfg.wheel != null && !cfg.wheel.isEmpty()) {
            List<Entry> out = new ArrayList<>();
            for (PadSettings.WheelEntry e : cfg.wheel) {
                if (e != null && e.label != null && e.command != null && !e.command.isBlank() && out.size() < MAX_ENTRIES) out.add(new Entry(e.label, e.command));
            }
            if (!out.isEmpty()) return out;
        }
        return entries();
    }

    /** Pruebas: sustituye las entradas sin tocar el archivo (null = volver a leerlas). */
    public static void setEntriesForTest(List<Entry> list) {
        entries = list;
    }

    public static boolean isOpen(int slot) {
        return slot >= 0 && slot < 4 && OPEN[slot];
    }

    public static int selected(int slot) {
        return SELECTED[slot];
    }

    /** Boton de la rueda segun los bindings del jugador, o -1 si no tiene. */
    public static int buttonOf(PadSettings cfg) {
        for (var e : cfg.bindings.entrySet()) if (e.getValue() == PadAction.QUICK_WHEEL) return e.getKey();
        return -1;
    }

    public static void closeWheel(int slot) {
        close(slot);
    }

    private static void close(int slot) {
        OPEN[slot] = false;
        SELECTED[slot] = -1;
        HELD[slot] = 0f;
    }

    /** Entrada de la rueda del jugador una vez por fotograma. {@code send} ejecuta un comando como ese jugador. */
    public static void frame(int slot, PadState s, PadSettings cfg, boolean inWorld, float dt, Consumer<String> send) {
        int code = buttonOf(cfg);
        if (code < 0 || !inWorld) {
            close(slot);
            return;
        }
        List<Entry> list = listFor(cfg);
        if (!OPEN[slot]) {
            if (s.pressed(code) && !list.isEmpty()) {
                OPEN[slot] = true;
                SELECTED[slot] = -1;
                HELD[slot] = 0f;
            }
            return;
        }
        HELD[slot] += dt;
        if (!s.isDown(code)) { // soltar: ejecuta lo elegido
            int sel = SELECTED[slot];
            boolean run = sel >= 0 && sel < list.size() && HELD[slot] >= 0.08f;
            close(slot);
            if (run) {
                String cmd = list.get(sel).command();
                if (cmd.startsWith("/")) cmd = cmd.substring(1);
                executed[slot]++;
                lastCommand[slot] = cmd;
                send.accept(cmd);
            }
            return;
        }
        float rx = s.rx, ry = s.ry;
        if (Math.hypot(rx, ry) > 0.55) {
            double angle = Math.atan2(rx, -ry); // 0 = arriba, en el sentido de las agujas del reloj
            if (angle < 0) angle += Math.PI * 2;
            SELECTED[slot] = (int) Math.round(angle / (Math.PI * 2 / list.size())) % list.size();
        }
    }

    /** Posicion del centro de la entrada i en una rueda de n entradas con radio r alrededor de (cx, cy). */
    public static int[] slice(int i, int n, int cx, int cy, int r) {
        double a = i * Math.PI * 2 / n;
        return new int[] {cx + (int) Math.round(Math.sin(a) * r), cy - (int) Math.round(Math.cos(a) * r)};
    }

    /** Radio de la rueda en una region de w x h (unidades de GUI de su interfaz). */
    public static int radius(int w, int h) {
        return Math.max(36, Math.min(78, Math.min(w, h) * 32 / 100));
    }

    /** Ultima caja dibujada por jugador {x, y, ancho, alto} de la entrada elegida o de la rueda entera (pruebas). */
    public static final int[][] lastBounds = new int[4][4];

    /** Dibuja la rueda abierta del jugador en el centro de su region. */
    public static void render(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        if (!OPEN[slot]) return;
        Font font = mc.font;
        List<Entry> list = listFor(ConsolePad.PADS.slot(slot).settings);
        int n = list.size();
        int cx = w / 2, cy = h / 2, r = radius(w, h);
        int minX = cx, minY = cy, maxX = cx, maxY = cy;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        int sel = SELECTED[slot];
        for (int i = 0; i < n; i++) {
            int[] p = slice(i, n, cx, cy, r);
            String label = list.get(i).label();
            int tw = font.width(label);
            int bw = tw + 8, bh = 13;
            int x0 = p[0] - bw / 2, y0 = p[1] - bh / 2;
            boolean on = i == sel;
            g.fill(x0 - 1, y0 - 1, x0 + bw + 1, y0 + bh + 1, on ? 0xFFFFD84A : 0xFF000000);
            g.fill(x0, y0, x0 + bw, y0 + bh, on ? 0xFF4A3A10 : 0xE0181820);
            g.drawString(font, label, x0 + 4, y0 + 3, on ? 0xFFFFD84A : 0xFFFFFFFF, false);
            minX = Math.min(minX, x0 - 1);
            minY = Math.min(minY, y0 - 1);
            maxX = Math.max(maxX, x0 + bw + 1);
            maxY = Math.max(maxY, y0 + bh + 1);
        }
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        g.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
        if (sel >= 0 && sel < n) {
            int[] p = slice(sel, n, cx, cy, r - 14);
            int steps = 12;
            for (int k = 1; k <= steps; k++) { // aguja punteada hacia la entrada
                int x = cx + (p[0] - cx) * k / steps, y = cy + (p[1] - cy) * k / steps;
                if (k % 2 == 0) g.fill(x - 1, y - 1, x + 1, y + 1, 0xFFFFD84A);
            }
            String cmd = "/" + list.get(sel).command();
            String shown = font.plainSubstrByWidth(cmd, Math.max(40, w - 10));
            g.drawCenteredString(font, shown, cx, cy + r + 18, 0xFFA0A0A0);
        }
        g.pose().popPose();
        lastBounds[slot] = new int[] {minX, minY, maxX - minX, maxY - minY};
    }
}
