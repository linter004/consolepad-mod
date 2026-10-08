package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menu de pausa de un jugador extra. No pausa nada (con extras dentro el mundo no se pausa nunca). "Salir" saca solo a ese jugador (el
 * PauseScreen de vanilla desconectaria a P1 y pararia el servidor). Tambien guarda sus ajustes propios: campo de vision y sensibilidad
 * de la camara, que se guardan en el archivo de su slot.
 */
public final class P2PauseScreen extends Screen {
    private static final int[] FOVS = {0, 60, 70, 80, 90, 100, 110};
    private static final float[] SPEEDS = {90f, 135f, 180f, 225f, 270f, 315f};

    private final int slot;
    private Button resume, fov, sens, quit;

    public P2PauseScreen(int slot) {
        super(Component.literal("Jugador " + (slot + 1)));
        this.slot = slot;
    }

    private PadSettings settings() {
        return ConsolePad.PADS.slot(slot).settings;
    }

    private Component fovLabel() {
        int v = settings().fov;
        return Component.literal("Campo de visión: " + (v <= 0 ? "global" : String.valueOf(v)));
    }

    private Component sensLabel() {
        return Component.literal("Sensibilidad: " + Math.round(settings().lookSpeed));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 80;
        int y = this.height / 4 + 28;
        resume = addRenderableWidget(Button.builder(Component.literal("Volver a la partida"), b -> this.onClose())
                .bounds(x, y, 160, 20).build());
        fov = addRenderableWidget(Button.builder(fovLabel(), b -> {
            PadSettings s = settings();
            int i = 0;
            for (int k = 0; k < FOVS.length; k++) if (FOVS[k] == s.fov) i = k;
            s.fov = FOVS[(i + 1) % FOVS.length];
            b.setMessage(fovLabel());
        }).bounds(x, y + 24, 160, 20).build());
        sens = addRenderableWidget(Button.builder(sensLabel(), b -> {
            PadSettings s = settings();
            int i = 0;
            for (int k = 0; k < SPEEDS.length; k++) if (Math.abs(SPEEDS[k] - s.lookSpeed) < 1f) i = k;
            s.lookSpeed = SPEEDS[(i + 1) % SPEEDS.length];
            b.setMessage(sensLabel());
        }).bounds(x, y + 48, 160, 20).build());
        quit = addRenderableWidget(Button.builder(Component.literal("Salir de la partida (P" + (slot + 1) + ")"),
                b -> SecondaryPlayers.leave(Minecraft.getInstance(), slot)).bounds(x, y + 72, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 + 10, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static int[] center(Button b) {
        return new int[] {b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
    }

    public int[] resumeCenter() {
        return center(resume);
    }

    public int[] fovCenter() {
        return center(fov);
    }

    public int[] sensCenter() {
        return center(sens);
    }

    public int[] quitCenter() {
        return center(quit);
    }
}
