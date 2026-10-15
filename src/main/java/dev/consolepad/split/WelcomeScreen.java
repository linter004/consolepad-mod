package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Bienvenida de la primera vez (sobre el titulo): un dibujo del mando con Start y Atras marcados (los botones que meten y sacan a los jugadores extra),
 * como entrar, lo que ConsolePad detecto de otros mods, un acceso a los ajustes y un boton para no volver a verla. Esta en espanol o en ingles segun el
 * idioma del juego. Se maneja con el cursor del mando como cualquier pantalla. No sale durante el autotest ni la generacion de capturas.
 */
public final class WelcomeScreen extends Screen {
    private static final int LINE = 10, SIL_W = 110;
    private final Screen parent;
    private Button ok, settings, close;
    private ControlsLayout.Kind kind = ControlsLayout.Kind.XBOX;
    private int silX, silY, silW, silH;
    private final int[][] anchor = new int[2][2]; // Start y Atras: centro del boton en la silueta

    public WelcomeScreen(Screen parent) {
        super(Component.literal(t("Bienvenido a ConsolePad", "Welcome to ConsolePad")));
        this.parent = parent;
    }

    /** Idioma de las bienvenida: espanol si el juego esta en espanol, ingles en cualquier otro caso. */
    public static boolean spanish() {
        Minecraft mc = Minecraft.getInstance();
        String code = mc == null || mc.options == null ? "es_es" : mc.options.languageCode;
        return code == null || code.toLowerCase(java.util.Locale.ROOT).startsWith("es");
    }

    private static String t(String es, String en) {
        return spanish() ? es : en;
    }

    /** Nombre del boton segun el mando de P1 (Menu/Vista en Xbox, Options/Create en PlayStation...), en el idioma de la bienvenida. */
    private static String button(int code) {
        String n = PadNames.button(code, ConsolePad.PADS.slot(0).iconType());
        if (!spanish()) n = n.replace("Menú", "Menu").replace("Vista", "View").replace("Opciones", "Options").replace("Crear", "Create").replace("Atrás", "Back");
        return n;
    }

    /** Las lineas del cuerpo: dependen de los mods detectados. */
    public static List<String> lines() {
        List<String> l = new ArrayList<>();
        String start = button(PadState.START), back = button(PadState.BACK);
        l.add(t("Dentro de un mundo, pulsa " + start + " en el mando de cada jugador extra", "In a world, press " + start + " on each extra player's controller"));
        l.add(t("para que entre (o " + start + " + " + back + "). Mantenlo 2 s para que salga.", "to let them in (or " + start + " + " + back + "). Hold it 2 s to leave."));
        l.add(t("Cada jugador tiene su menú con ajustes y controles propios.", "Each player has their own menu with settings and controls."));
        if (Compat.controlify()) {
            l.add(t("Controlify detectado: P1 va con Controlify; ConsolePad lleva a P2-P4.", "Controlify detected: P1 uses Controlify; ConsolePad handles P2-P4."));
        }
        if (Compat.sodium() || Compat.iris()) {
            String mods = (Compat.sodium() ? "Sodium" : "") + (Compat.sodium() && Compat.iris() ? " + " : "") + (Compat.iris() ? "Iris" : "");
            l.add(mods + t(": compatible (Sodium pide NeoForge 21.1.219+).", ": compatible (Sodium needs NeoForge 21.1.219+)."));
        }
        if (Compat.iris()) {
            l.add(t("Con varios jugadores los shaders se apagan solos y vuelven después.", "With several players, shaders turn off by themselves and come back later."));
        }
        return l;
    }

    @Override
    protected void init() {
        int w = Math.min(200, this.width - 16);
        int half = (w - 4) / 2;
        int x = this.width / 2 - w / 2;
        ok = addRenderableWidget(Button.builder(Component.literal(t("Entendido, no volver a mostrar", "Got it, don't show again")), b -> {
            SplitSettings.setWelcomeSeen(true);
            onClose();
        }).bounds(x, this.height - 52, w, 20).build());
        settings = addRenderableWidget(Button.builder(Component.literal(t("Ajustes", "Settings")),
                b -> Minecraft.getInstance().setScreen(new PlayerSettingsScreen(0, this))).bounds(x, this.height - 28, half, 20).build());
        close = addRenderableWidget(Button.builder(Component.literal(t("Cerrar", "Close")), b -> onClose()).bounds(x + half + 4, this.height - 28, half, 20).build());
        // La silueta del mando del jugador 1 (o Xbox) arriba, con la geometria de la pantalla de controles escalada.
        kind = ControlsLayout.kindFor(ConsolePad.PADS.slot(0).device, ConsolePad.PADS.slot(0).settings.iconStyle);
        ControlsLayout.Result r = ControlsLayout.compute(kind, 300, 190);
        float s = (float) SIL_W / r.sw;
        silW = SIL_W;
        silH = Math.round(r.sh * s);
        silX = this.width / 2 - silW / 2;
        silY = 22;
        int[] codes = {PadState.START, PadState.BACK};
        for (int k = 0; k < 2; k++) {
            int i = ControlsLayout.index(codes[k]);
            anchor[k][0] = silX + Math.round((r.ax[i] - r.sx) * s);
            anchor[k][1] = silY + Math.round((r.ay[i] - r.sy) * s);
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Sin el fondo difuminado de vanilla (el texto se dibuja sobre un panel liso). */
    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {}

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xFF0C0C14); // fondo liso: el difuminado del titulo hacia ilegible el texto
        int pw = 40;
        for (String l : lines()) pw = Math.max(pw, this.font.width(l));
        pw = Math.min(this.width - 8, Math.max(pw, silW + 150) + 24);
        g.fill(this.width / 2 - pw / 2, 6, this.width / 2 + pw / 2, this.height - 56, 0xFF1C1C2C);
        g.drawCenteredString(this.font, this.title, this.width / 2, 10, 0xFFFFD84A);
        PadSilhouette.draw(g, kind, silX, silY, silW, silH);
        var type = ConsolePad.PADS.slot(0).iconType();
        int[] codes = {PadState.START, PadState.BACK};
        for (int k = 0; k < 2; k++) {
            int ax = anchor[k][0], ay = anchor[k][1];
            boolean right = k == 0; // Start a la derecha, Atras a la izquierda
            int lx = right ? silX + silW + 14 : silX - 14;
            g.fill(Math.min(ax, lx), ay, Math.max(ax, lx) + 1, ay + 1, 0xFFFFD84A); // linea hasta la etiqueta
            PadIcons.drawCentered(g, ax, ay, 11, codes[k], type);
            String name = button(codes[k]);
            int tw = this.font.width(name);
            g.drawString(this.font, name, right ? lx + 3 : lx - 3 - tw, ay - 4, 0xFFFFD84A, false);
        }
        int y = silY + silH + 8;
        for (String s : lines()) {
            g.drawCenteredString(this.font, s, this.width / 2, y, 0xFFFFFFFF);
            y += LINE;
        }
        super.render(g, mouseX, mouseY, partialTick);
    }

    /** Pruebas: el dibujo y el texto caben entre el titulo y los botones. */
    public boolean textFits() {
        return silY + silH + 8 + LINE * lines().size() <= ok.getY() - 4;
    }

    public int[] okCenter() {
        return new int[] {ok.getX() + ok.getWidth() / 2, ok.getY() + ok.getHeight() / 2};
    }

    public int[] settingsCenter() {
        return new int[] {settings.getX() + settings.getWidth() / 2, settings.getY() + settings.getHeight() / 2};
    }

    /** Pruebas: Start y Atras marcados dentro de la pantalla y el boton de ajustes abre los ajustes de P1. */
    public int[][] anchors() {
        return anchor;
    }

    public void pressSettings() {
        settings.onPress();
    }
}
