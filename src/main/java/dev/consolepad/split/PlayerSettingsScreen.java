package dev.consolepad.split;

import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * Ajustes de un jugador (se guardan en el archivo de su slot): escala de interfaz, sensibilidad, campo de vision, zonas muertas, cursor,
 * ayuda de botones... Una lista de opciones que se pasa por paginas cuando no caben en su region; cada opcion cicla entre sus valores.
 */
public final class PlayerSettingsScreen extends PlayerMenuScreen {
    public static final int[] GUI_SCALES = {0, 1, 2, 3, 4};
    public static final int[] FOVS = {0, 60, 70, 80, 90, 100, 110};
    public static final float[] SPEEDS = {90f, 135f, 180f, 225f, 270f, 315f};
    public static final float[] DEADZONES = {0.05f, 0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.35f};
    public static final int[] HUD_MARGINS = {0, 2, 4, 6, 8, 10};
    public static final float[] HUD_OPACITIES = {1f, 0.75f, 0.5f, 0.25f};
    public static final float[] CURSOR_SPEEDS = {250f, 350f, 450f, 550f, 700f, 850f, 1000f};

    private record Item(String id, Supplier<String> label, Runnable next) {}

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
    private Button back, prev, nextPage;
    private int page;

    public PlayerSettingsScreen(int slot, Screen parent) {
        super(slot, Component.literal("Ajustes del jugador " + (slot + 1)));
        this.parent = parent;
    }

    private static String dz(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    public static float nextFloat(float[] options, float current) {
        int idx = 0;
        float best = Float.MAX_VALUE;
        for (int k = 0; k < options.length; k++) {
            float d = Math.abs(options[k] - current);
            if (d < best) {
                best = d;
                idx = k;
            }
        }
        return options[(idx + 1) % options.length];
    }

    private static int nextInt(int[] options, int current) {
        int idx = 0;
        for (int k = 0; k < options.length; k++) if (options[k] == current) idx = k;
        return options[(idx + 1) % options.length];
    }

    private static String yesNo(boolean v) {
        return v ? "sí" : "no";
    }

    private void buildItems() {
        items.clear();
        PadSettings s = settings();
        items.add(new Item("guiScale", () -> "Escala de interfaz: " + (s.guiScale <= 0 ? "automática" : String.valueOf(s.guiScale)),
                () -> s.guiScale = nextInt(GUI_SCALES, s.guiScale)));
        items.add(new Item("sens", () -> "Sensibilidad horizontal: " + Math.round(s.lookSpeed), () -> s.lookSpeed = nextFloat(SPEEDS, s.lookSpeed)));
        items.add(new Item("fov", () -> "Campo de visión: " + (s.fov <= 0 ? "global" : String.valueOf(s.fov)), () -> s.fov = nextInt(FOVS, s.fov)));
        items.add(new Item("moveDead", () -> "Zona muerta del movimiento: " + dz(s.moveDeadzone), () -> s.moveDeadzone = nextFloat(DEADZONES, s.moveDeadzone)));
        items.add(new Item("lookDead", () -> "Zona muerta de la cámara: " + dz(s.lookDeadzone), () -> s.lookDeadzone = nextFloat(DEADZONES, s.lookDeadzone)));
        items.add(new Item("sensY", () -> "Sensibilidad vertical: " + Math.round(s.lookSpeedY()), () -> s.lookSpeedY = nextFloat(SPEEDS, s.lookSpeedY())));
        items.add(new Item("invertY", () -> "Invertir eje Y: " + yesNo(s.invertY), () -> s.invertY = !s.invertY));
        items.add(new Item("doubleTap", () -> "Correr con doble toque: " + yesNo(s.doubleTapSprint), () -> s.doubleTapSprint = !s.doubleTapSprint));
        items.add(new Item("cursorSpeed", () -> "Velocidad del cursor: " + Math.round(s.cursorSpeed), () -> s.cursorSpeed = nextFloat(CURSOR_SPEEDS, s.cursorSpeed)));
        items.add(new Item("snapNav", () -> "Cursor en menús: " + (s.snapNav ? "por casillas" : "libre"), () -> s.snapNav = !s.snapNav));
        items.add(new Item("name", () -> "Nombre: " + PlayerLabels.labelOf(slot), () -> Minecraft.getInstance().setScreen(new PlayerNameScreen(slot, this))));
        items.add(new Item("hudMargin", () -> "Zona segura del HUD: " + s.hudMargin + " %", () -> s.hudMargin = nextInt(HUD_MARGINS, s.hudMargin)));
        items.add(new Item("hudOpacity", () -> "Opacidad del HUD: " + Math.round(s.hudOpacity * 100) + " %", () -> s.hudOpacity = nextFloat(HUD_OPACITIES, s.hudOpacity)));
        items.add(new Item("showHud", () -> "Mostrar HUD: " + yesNo(s.showHud), () -> s.showHud = !s.showHud));
        items.add(new Item("showHand", () -> "Mostrar mano: " + yesNo(s.showHand), () -> s.showHand = !s.showHand));
        items.add(new Item("hints", () -> "Ayuda de botones: " + yesNo(s.showHints), () -> s.showHints = !s.showHints));
    }

    @Override
    protected void init() {
        buildItems();
        buttons.clear();
        int footerY = this.height - 24;
        int perPage = Math.max(3, (footerY - 28) / PITCH);
        int pages = Math.max(1, (items.size() + perPage - 1) / perPage);
        page = Math.min(page, pages - 1);
        int x = this.width / 2 - ROW_W / 2;
        for (int k = 0; k < perPage; k++) {
            int idx = page * perPage + k;
            if (idx >= items.size()) break;
            Item it = items.get(idx);
            Button b = addRenderableWidget(Button.builder(Component.literal(it.label().get()), btn -> {
                it.next().run();
                if (Minecraft.getInstance().screen != this) return; // la opcion abrio otra pantalla
                btn.setMessage(Component.literal(it.label().get()));
            }).bounds(x, 24 + k * PITCH, ROW_W, ROW_H).build());
            buttons.put(it.id(), b);
        }
        if (pages > 1) {
            prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> {
                page = (page + pages - 1) % pages;
                this.rebuildWidgets();
            }).bounds(x, footerY, 30, ROW_H).build());
            back = addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> this.onClose()).bounds(x + 34, footerY, ROW_W - 68, ROW_H).build());
            nextPage = addRenderableWidget(Button.builder(Component.literal(">"), b -> {
                page = (page + 1) % pages;
                this.rebuildWidgets();
            }).bounds(x + ROW_W - 30, footerY, 30, ROW_H).build());
        } else {
            prev = nextPage = null;
            back = addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> this.onClose()).bounds(x, footerY, ROW_W, ROW_H).build());
        }
    }

    @Override
    public void onClose() {
        dev.consolepad.ConsolePad.PADS.saveSettings();
        Minecraft.getInstance().setScreen(parent);
    }

    /** Centro del boton de una opcion de la pagina actual, o {0, 0} si no esta en ella. */
    public int[] centerOf(String id) {
        Button b = buttons.get(id);
        return b == null ? new int[] {0, 0} : center(b);
    }

    public int[] guiScaleCenter() {
        return centerOf("guiScale");
    }

    public int[] sensCenter() {
        return centerOf("sens");
    }

    public int[] fovCenter() {
        return centerOf("fov");
    }

    public int[] moveDeadCenter() {
        return centerOf("moveDead");
    }

    public int[] lookDeadCenter() {
        return centerOf("lookDead");
    }

    public int[] cursorSpeedCenter() {
        return centerOf("cursorSpeed");
    }

    public int[] snapNavCenter() {
        return centerOf("snapNav");
    }

    public int[] backCenter() {
        return center(back);
    }
}
