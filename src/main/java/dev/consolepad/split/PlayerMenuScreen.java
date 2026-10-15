package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Base de los menus propios de cada jugador (pausa, ajustes, controles): una columna de botones centrada en la region del jugador. */
public abstract class PlayerMenuScreen extends Screen implements PlayerScreen {
    protected static final int ROW_W = 180, ROW_H = 20, PITCH = 24;

    protected final int slot;

    protected PlayerMenuScreen(int slot, Component title) {
        super(title);
        this.slot = slot;
    }

    protected PadSettings settings() {
        return ConsolePad.PADS.slot(slot).settings;
    }

    /** Y de la fila de botones de abajo (Atras, paginas): por encima de la barra de ayuda de botones, que se dibuja pegada al borde inferior. */
    protected int footerY() {
        var ps = ConsolePad.PADS.slot(slot);
        int bar = ps.device != null && ps.settings.showHints ? Math.round((PadIcons.H + 3) * (ps.settings.bigHints ? PadHints.BIG : 1f)) : 0;
        return this.height - ROW_H - Math.max(4, bar + 2);
    }

    /** Y del primer boton de una columna de n botones, centrada pero sin tapar el titulo. */
    protected int firstRowY(int rows) {
        return Math.max(24, (this.height - rows * PITCH) / 2 + 8);
    }

    protected Button addRow(int index, int rows, Component text, Button.OnPress press) {
        return addRenderableWidget(Button.builder(text, press).bounds(this.width / 2 - ROW_W / 2, firstRowY(rows) + index * PITCH, ROW_W, ROW_H).build());
    }

    /** Momento en que se abrio el menu, para la animacion de entrada. */
    private final long openedNanos = System.nanoTime();
    private static final double ANIM_SECONDS = 0.18;

    /** Avance de la animacion de entrada (0 a 1, con frenado al final). */
    protected float openProgress() {
        double t = Math.min(1.0, (System.nanoTime() - openedNanos) / 1e9 / ANIM_SECONDS);
        return (float) (1 - Math.pow(1 - t, 3));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float e = openProgress();
        graphics.pose().pushPose();
        graphics.pose().translate(0, (1 - e) * 10, 0); // el menu sube unos pixeles al abrirse
        try {
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
        } finally {
            graphics.pose().popPose();
        }
    }

    public static int[] center(Button b) {
        return new int[] {b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
    }
}
