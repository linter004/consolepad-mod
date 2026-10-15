package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.ButtonLayouts;
import dev.consolepad.pad.LookControl;
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
    public static final int[] VIEW_DISTANCES = {0, 4, 6, 8, 10, 12, 16, 24};
    public static final int[] FOVS = {0, 60, 70, 80, 90, 100, 110};
    public static final float[] SPEEDS = {90f, 135f, 180f, 225f, 270f, 315f};
    public static final float[] DEADZONES = {0.05f, 0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.35f};
    public static final int[] HUD_MARGINS = {0, 2, 4, 6, 8, 10};
    public static final float[] HUD_OPACITIES = {1f, 0.75f, 0.5f, 0.25f};
    public static final float[] TARGET_SLOWDOWNS = {1f, 0.8f, 0.6f, 0.45f};
    private static final String[] TARGET_NAMES = {"no", "suave", "media", "fuerte"};
    private static final String[] AUTO_JUMP_NAMES = {"como el juego", "sí", "no"};
    public static final float[] FOV_COMPS = {0f, 0.5f, 1f};
    private static final String[] FOV_COMP_NAMES = {"no", "media", "total"};
    public static final float[] VIBRATIONS = {0f, 0.4f, 0.7f, 1f};
    private static final String[] VIBRATION_NAMES = {"no", "suave", "normal", "fuerte"};
    public static final float[] AIM_SENS = {1f, 0.75f, 0.5f, 0.35f, 0.25f};
    public static final float[] CURSOR_SPEEDS = {250f, 350f, 450f, 550f, 700f, 850f, 1000f};

    private static final String[] ICON_STYLES = {"los de mi mando", "Xbox", "PlayStation", "Nintendo"};

    private record Item(String id, Supplier<String> label, Runnable next) {}

    /** Categorias de los ajustes (cada una empieza en pagina nueva) y que opciones lleva cada una. */
    private static final String[][] CATEGORIES = {
            {"Cámara y apuntado", "sens", "sensY", "lookCurve", "aimSens", "targetSlowdown", "invertY", "lookDead", "fov"},
            {"Movimiento y botones", "moveDead", "doubleTap", "layout", "sneakMode", "sprintMode", "attackMode", "toggleHolds", "autoJump"},
            {"Menús y cursor", "cursorSpeed", "snapNav", "hints", "bigHints", "iconStyle"},
            {"Pantalla e interfaz", "guiScale", "viewDist", "fovComp", "hudMargin", "hudOpacity", "showHud", "showHand", "showCoords", "showDirection", "paperDoll", "showCompanions", "showFps", "reduceEffects"},
            {"Jugador y mandos", "name", "labelColor", "profile", "assignPads", "vibration", "wheel"},
            {"Copiar y restablecer", "copySource", "copyDo", "reset"},
    };

    private record Page(String category, int part, int parts, List<Item> items) {}

    private final List<Page> pages = new ArrayList<>();
    /** Accion de "valor anterior" (X) de las opciones que ciclan entre varios valores; las demas hacen lo mismo que A. */
    private final java.util.Map<String, Runnable> prevActions = new java.util.HashMap<>();

    /** Boton de una opcion: A la avanza, X (clic derecho) la devuelve al valor anterior. */
    private static final class StepButton extends Button {
        private final Runnable back;

        StepButton(int x, int y, int w, int h, Component text, OnPress press, Runnable back) {
            super(x, y, w, h, text, press, DEFAULT_NARRATION);
            this.back = back;
        }

        @Override
        public boolean mouseClicked(double mx, double my, int button) {
            if (button == 1 && this.active && this.visible && this.clicked(mx, my)) {
                this.playDownSound(Minecraft.getInstance().getSoundManager());
                back.run();
                return true;
            }
            return super.mouseClicked(mx, my, button);
        }
    }

    private final Screen parent;
    private final List<Item> items = new ArrayList<>();
    private final java.util.Map<String, Button> buttons = new java.util.HashMap<>();
    private Button back, prev, nextPage;
    private int page;
    /** Jugador del que se copian los ajustes (por defecto el siguiente al propio). */
    private int copySource;
    private boolean resetArmed;

    public PlayerSettingsScreen(int slot, Screen parent) {
        super(slot, Component.literal("Ajustes del jugador " + (slot + 1)));
        this.parent = parent;
        this.copySource = nextOther(slot);
    }

    /** El siguiente jugador (0 a 3) distinto del propio. */
    private int nextOther(int from) {
        int n = (from + 1) % dev.consolepad.pad.PadManager.MAX_PLAYERS;
        return n == slot ? (n + 1) % dev.consolepad.pad.PadManager.MAX_PLAYERS : n;
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

    public static float prevFloat(float[] options, float current) {
        int idx = 0;
        float best = Float.MAX_VALUE;
        for (int k = 0; k < options.length; k++) {
            float d = Math.abs(options[k] - current);
            if (d < best) {
                best = d;
                idx = k;
            }
        }
        return options[(idx + options.length - 1) % options.length];
    }

    private static int prevInt(int[] options, int current) {
        int idx = 0;
        for (int k = 0; k < options.length; k++) if (options[k] == current) idx = k;
        return options[(idx + options.length - 1) % options.length];
    }

    private static int nextInt(int[] options, int current) {
        int idx = 0;
        for (int k = 0; k < options.length; k++) if (options[k] == current) idx = k;
        return options[(idx + 1) % options.length];
    }

    private static String targetName(float v) {
        int best = 0;
        for (int k = 0; k < TARGET_SLOWDOWNS.length; k++) if (Math.abs(TARGET_SLOWDOWNS[k] - v) < Math.abs(TARGET_SLOWDOWNS[best] - v)) best = k;
        return TARGET_NAMES[best];
    }

    private static String fovCompName(float v) {
        int best = 0;
        for (int k = 0; k < FOV_COMPS.length; k++) if (Math.abs(FOV_COMPS[k] - v) < Math.abs(FOV_COMPS[best] - v)) best = k;
        return FOV_COMP_NAMES[best];
    }

    private static String vibrationName(float v) {
        int best = 0;
        for (int k = 0; k < VIBRATIONS.length; k++) if (Math.abs(VIBRATIONS[k] - v) < Math.abs(VIBRATIONS[best] - v)) best = k;
        return VIBRATION_NAMES[best];
    }

    private static String yesNo(boolean v) {
        return v ? "sí" : "no";
    }

    private static String modeLabel(String name, boolean toggle, PadSettings s) {
        return name + ": " + (s.toggleHolds ? "alternar (accesibilidad)" : toggle ? "alternar" : "mantener");
    }

    private void buildItems() {
        items.clear();
        PadSettings s = settings();
        items.add(new Item("guiScale", () -> "Escala de interfaz: " + (s.guiScale <= 0 ? "automática" : String.valueOf(s.guiScale)),
                () -> s.guiScale = nextInt(GUI_SCALES, s.guiScale)));
        items.add(new Item("sens", () -> "Sensibilidad horizontal: " + Math.round(s.lookSpeed), () -> s.lookSpeed = nextFloat(SPEEDS, s.lookSpeed)));
        items.add(new Item("fov", () -> "Campo de visión: " + (s.fov <= 0 ? "global" : String.valueOf(s.fov)), () -> s.fov = nextInt(FOVS, s.fov)));
        items.add(new Item("viewDist", () -> "Distancia de chunks: " + (s.viewDistance <= 0 ? "global" : String.valueOf(s.viewDistance)) + " (activa "
                + PerfLimits.viewDistanceFor(slot, Minecraft.getInstance().options.renderDistance().get()) + ")", () -> s.viewDistance = nextInt(VIEW_DISTANCES, s.viewDistance)));
        items.add(new Item("moveDead", () -> "Zona muerta del movimiento: " + dz(s.moveDeadzone), () -> s.moveDeadzone = nextFloat(DEADZONES, s.moveDeadzone)));
        items.add(new Item("lookDead", () -> "Zona muerta de la cámara: " + dz(s.lookDeadzone), () -> s.lookDeadzone = nextFloat(DEADZONES, s.lookDeadzone)));
        items.add(new Item("sensY", () -> "Sensibilidad vertical: " + Math.round(s.lookSpeedY()), () -> s.lookSpeedY = nextFloat(SPEEDS, s.lookSpeedY())));
        items.add(new Item("lookCurve", () -> "Curva de cámara: " + s.lookCurve.label, () -> s.lookCurve = LookControl.Curve.values()[(s.lookCurve.ordinal() + 1) % LookControl.Curve.values().length]));
        items.add(new Item("aimSens", () -> "Sensibilidad al apuntar: " + Math.round(s.aimSensitivity * 100) + " %", () -> s.aimSensitivity = nextFloat(AIM_SENS, s.aimSensitivity)));
        items.add(new Item("targetSlowdown", () -> "Ayuda de apuntado: " + targetName(s.targetSlowdown), () -> s.targetSlowdown = nextFloat(TARGET_SLOWDOWNS, s.targetSlowdown)));
        items.add(new Item("vibration", () -> "Vibración del mando: " + vibrationName(s.vibration), () -> {
            s.vibration = nextFloat(VIBRATIONS, s.vibration);
            dev.consolepad.pad.Rumble.pulse(slot, 0.6f, 0.6f, 220); // se nota al elegirla
        }));
        items.add(new Item("autoJump", () -> "Salto automático: " + AUTO_JUMP_NAMES[Math.max(0, Math.min(2, s.autoJump))], () -> s.autoJump = (s.autoJump + 1) % 3));
        items.add(new Item("invertY", () -> "Invertir eje Y: " + yesNo(s.invertY), () -> s.invertY = !s.invertY));
        items.add(new Item("doubleTap", () -> "Correr con doble toque: " + yesNo(s.doubleTapSprint), () -> s.doubleTapSprint = !s.doubleTapSprint));
        items.add(new Item("cursorSpeed", () -> "Velocidad del cursor: " + Math.round(s.cursorSpeed), () -> s.cursorSpeed = nextFloat(CURSOR_SPEEDS, s.cursorSpeed)));
        items.add(new Item("snapNav", () -> "Cursor en menús: " + (s.snapNav ? "por casillas" : "libre"), () -> s.snapNav = !s.snapNav));
        items.add(new Item("assignPads", () -> "Asignar mandos...", () -> Minecraft.getInstance().setScreen(new PadAssignScreen(slot, this))));
        items.add(new Item("wheel", () -> "Rueda de acciones: " + (s.wheel == null ? "la de todos" : "propia") + "...", () -> Minecraft.getInstance().setScreen(new WheelEditorScreen(slot, this))));
        items.add(new Item("profile", () -> "Perfil: " + (Profiles.of(slot) == null ? "ninguno" : Profiles.of(slot).name) + "...", () -> Minecraft.getInstance().setScreen(new ProfilesScreen(slot, this))));
        items.add(new Item("name", () -> "Nombre: " + PlayerLabels.labelOf(slot), () -> Minecraft.getInstance().setScreen(new PlayerNameScreen(slot, this))));
        items.add(new Item("labelColor", () -> "Color de la etiqueta: " + PlayerLabels.COLOR_NAMES[s.labelColor % PlayerLabels.COLOR_NAMES.length], () -> {
            s.labelColor = (s.labelColor + 1) % PlayerLabels.COLOR_NAMES.length;
            PlayerLabels.refreshAll(Minecraft.getInstance());
        }));
        items.add(new Item("fovComp", () -> "Compensar campo de visión: " + fovCompName(s.fovComp), () -> s.fovComp = nextFloat(FOV_COMPS, s.fovComp)));
        items.add(new Item("hudMargin", () -> "Zona segura del HUD: " + s.hudMargin + " %", () -> s.hudMargin = nextInt(HUD_MARGINS, s.hudMargin)));
        items.add(new Item("hudOpacity", () -> "Opacidad del HUD: " + Math.round(s.hudOpacity * 100) + " %", () -> s.hudOpacity = nextFloat(HUD_OPACITIES, s.hudOpacity)));
        items.add(new Item("showHud", () -> "Mostrar HUD: " + yesNo(s.showHud), () -> s.showHud = !s.showHud));
        items.add(new Item("showHand", () -> "Mostrar mano: " + yesNo(s.showHand), () -> s.showHand = !s.showHand));
        items.add(new Item("layout", () -> "Disposición de botones: " + ButtonLayouts.name(s), () -> ButtonLayouts.apply(s, ButtonLayouts.next(s))));
        items.add(new Item("sneakMode", () -> modeLabel("Agacharse", s.sneakToggle, s), () -> s.sneakToggle = !s.sneakToggle));
        items.add(new Item("sprintMode", () -> modeLabel("Correr", s.sprintToggle, s), () -> s.sprintToggle = !s.sprintToggle));
        items.add(new Item("attackMode", () -> modeLabel("Atacar", s.attackToggle, s), () -> s.attackToggle = !s.attackToggle));
        items.add(new Item("copySource", () -> "Copiar ajustes de: " + PlayerLabels.labelOf(copySource), () -> copySource = nextOther(copySource)));
        items.add(new Item("copyDo", () -> "Copiar ahora a este jugador", () -> {
            s.copyFrom(ConsolePad.PADS.slot(copySource).settings);
            ConsolePad.PADS.saveSettings();
            resetArmed = false;
            this.rebuildWidgets();
        }));
        items.add(new Item("reset", () -> resetArmed ? "¿Seguro? Pulsa otra vez para restablecer" : "Restablecer ajustes de fábrica", () -> {
            if (!resetArmed) {
                resetArmed = true;
                return;
            }
            s.resetToDefaults();
            ConsolePad.PADS.saveSettings();
            resetArmed = false;
            this.rebuildWidgets();
        }));
        items.add(new Item("showCoords", () -> "Mostrar coordenadas: " + yesNo(s.showCoords), () -> s.showCoords = !s.showCoords));
        items.add(new Item("paperDoll", () -> "Muñeco del jugador: " + yesNo(s.paperDoll), () -> s.paperDoll = !s.paperDoll));
        items.add(new Item("showCompanions", () -> "Indicador del compañero: " + yesNo(s.showCompanions), () -> s.showCompanions = !s.showCompanions));
        items.add(new Item("showDirection",() -> "Mostrar dirección: " + yesNo(s.showDirection), () -> s.showDirection = !s.showDirection));
        items.add(new Item("showFps", () -> "Mostrar FPS: " + yesNo(s.showFps), () -> s.showFps = !s.showFps));
        items.add(new Item("bigHints", () -> "Ayuda de botones grande: " + yesNo(s.bigHints), () -> s.bigHints = !s.bigHints));
        items.add(new Item("reduceEffects", () -> "Menos efectos de pantalla: " + yesNo(s.reduceEffects), () -> s.reduceEffects = !s.reduceEffects));
        items.add(new Item("toggleHolds", () -> "Mantener como alternar: " + yesNo(s.toggleHolds), () -> s.toggleHolds = !s.toggleHolds));
        items.add(new Item("iconStyle", () -> "Iconos de botones: " + ICON_STYLES[Math.floorMod(s.iconStyle, ICON_STYLES.length)], () -> s.iconStyle = (s.iconStyle + 1) % ICON_STYLES.length));
        items.add(new Item("hints", () -> "Ayuda de botones: " + yesNo(s.showHints), () -> s.showHints = !s.showHints));
        prevActions.clear();
        prevActions.put("guiScale", () -> s.guiScale = prevInt(GUI_SCALES, s.guiScale));
        prevActions.put("sens", () -> s.lookSpeed = prevFloat(SPEEDS, s.lookSpeed));
        prevActions.put("fov", () -> s.fov = prevInt(FOVS, s.fov));
        prevActions.put("viewDist", () -> s.viewDistance = prevInt(VIEW_DISTANCES, s.viewDistance));
        prevActions.put("moveDead", () -> s.moveDeadzone = prevFloat(DEADZONES, s.moveDeadzone));
        prevActions.put("lookDead", () -> s.lookDeadzone = prevFloat(DEADZONES, s.lookDeadzone));
        prevActions.put("sensY", () -> s.lookSpeedY = prevFloat(SPEEDS, s.lookSpeedY()));
        prevActions.put("lookCurve", () -> s.lookCurve = LookControl.Curve.values()[(s.lookCurve.ordinal() + LookControl.Curve.values().length - 1) % LookControl.Curve.values().length]);
        prevActions.put("targetSlowdown", () -> s.targetSlowdown = prevFloat(TARGET_SLOWDOWNS, s.targetSlowdown));
        prevActions.put("vibration", () -> {
            s.vibration = prevFloat(VIBRATIONS, s.vibration);
            dev.consolepad.pad.Rumble.pulse(slot, 0.6f, 0.6f, 220);
        });
        prevActions.put("autoJump", () -> s.autoJump = (s.autoJump + 2) % 3);
        prevActions.put("fovComp", () -> s.fovComp = prevFloat(FOV_COMPS, s.fovComp));
        prevActions.put("aimSens", () -> s.aimSensitivity = prevFloat(AIM_SENS, s.aimSensitivity));
        prevActions.put("cursorSpeed", () -> s.cursorSpeed = prevFloat(CURSOR_SPEEDS, s.cursorSpeed));
        prevActions.put("hudMargin", () -> s.hudMargin = prevInt(HUD_MARGINS, s.hudMargin));
        prevActions.put("hudOpacity", () -> s.hudOpacity = prevFloat(HUD_OPACITIES, s.hudOpacity));
        prevActions.put("iconStyle", () -> s.iconStyle = (s.iconStyle + ICON_STYLES.length - 1) % ICON_STYLES.length);
        prevActions.put("labelColor", () -> {
            s.labelColor = (s.labelColor + PlayerLabels.COLOR_NAMES.length - 1) % PlayerLabels.COLOR_NAMES.length;
            PlayerLabels.refreshAll(Minecraft.getInstance());
        });
        buildPages();
    }

    private int perPage() {
        return Math.max(3, (footerY() - 32) / PITCH);
    }

    /** Reparte las opciones por categorias y cada categoria por paginas (una categoria nunca comparte pagina con otra). */
    private void buildPages() {
        pages.clear();
        int perPage = perPage();
        java.util.Set<String> placed = new java.util.HashSet<>();
        for (String[] cat : CATEGORIES) {
            List<Item> mine = new ArrayList<>();
            for (int k = 1; k < cat.length; k++) {
                for (Item it : items) {
                    if (it.id().equals(cat[k])) {
                        mine.add(it);
                        placed.add(it.id());
                    }
                }
            }
            int parts = Math.max(1, (mine.size() + perPage - 1) / perPage);
            for (int part = 0; part < parts; part++) {
                pages.add(new Page(cat[0], part, parts, new ArrayList<>(mine.subList(part * perPage, Math.min(mine.size(), (part + 1) * perPage)))));
            }
        }
        List<Item> rest = new ArrayList<>(); // una opcion sin categoria (nueva) no se pierde
        for (Item it : items) if (!placed.contains(it.id())) rest.add(it);
        if (!rest.isEmpty()) pages.add(new Page("Otras", 0, 1, rest));
    }

    @Override
    protected void init() {
        buildItems();
        buttons.clear();
        int footerY = footerY();
        int pageCount = pages.size();
        page = Math.min(page, pageCount - 1);
        int x = this.width / 2 - ROW_W / 2;
        List<Item> shown = pages.get(page).items();
        for (int k = 0; k < shown.size(); k++) {
            Item it = shown.get(k);
            Runnable back = prevActions.getOrDefault(it.id(), it.next());
            Button b = addRenderableWidget(new StepButton(x, 28 + k * PITCH, ROW_W, ROW_H, Component.literal(it.label().get()), btn -> {
                it.next().run();
                if (Minecraft.getInstance().screen != this) return; // la opcion abrio otra pantalla
                btn.setMessage(Component.literal(it.label().get()));
            }, () -> {
                back.run();
                if (Minecraft.getInstance().screen != this) return;
                Button self = buttons.get(it.id());
                if (self != null) self.setMessage(Component.literal(it.label().get()));
            }));
            buttons.put(it.id(), b);
        }
        int pages = pageCount;
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

    private int pageCount() {
        return Math.max(1, pages.size());
    }

    @Override
    public boolean hintTabs() {
        return pageCount() > 1;
    }

    /** LB/RB: pagina anterior / siguiente. */
    @Override
    public void tab(int dir) {
        int pages = pageCount();
        if (pages < 2) return;
        page = Math.floorMod(page + dir, pages);
        this.rebuildWidgets();
    }

    public int page() {
        return page;
    }

    /** Categoria de la pagina actual. */
    public String category() {
        return pages.isEmpty() ? "" : pages.get(Math.min(page, pages.size() - 1)).category();
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        if (pages.isEmpty()) return;
        Page pg = pages.get(Math.min(page, pages.size() - 1));
        String head = pg.category() + (pg.parts() > 1 ? "  " + (pg.part() + 1) + "/" + pg.parts() : "") + "  ·  " + (page + 1) + "/" + pages.size();
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(head, this.width - 8), this.width / 2, 18, 0xFFFFD84A);
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

    /** Como centerOf, pero si la opcion esta en otra pagina pasa antes a esa pagina (para pruebas que no deben depender del alto de la ventana). */
    public int[] centerOnItsPage(String id) {
        if (!buttons.containsKey(id)) {
            for (int k = 0; k < pages.size(); k++) {
                for (Item it : pages.get(k).items()) {
                    if (it.id().equals(id)) {
                        page = k;
                        this.rebuildWidgets();
                        return centerOf(id);
                    }
                }
            }
        }
        return centerOf(id);
    }

    public int[] guiScaleCenter() {
        return centerOnItsPage("guiScale");
    }

    public int[] sensCenter() {
        return centerOnItsPage("sens");
    }

    public int[] fovCenter() {
        return centerOnItsPage("fov");
    }

    public int[] moveDeadCenter() {
        return centerOnItsPage("moveDead");
    }

    public int[] lookDeadCenter() {
        return centerOnItsPage("lookDead");
    }

    public int[] cursorSpeedCenter() {
        return centerOnItsPage("cursorSpeed");
    }

    public int[] snapNavCenter() {
        return centerOnItsPage("snapNav");
    }

    public int[] layoutCenter() {
        return centerOnItsPage("layout");
    }

    public int[] backCenter() {
        return center(back);
    }
}
