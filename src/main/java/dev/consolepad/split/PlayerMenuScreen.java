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

    /** Y del primer boton de una columna de n botones, centrada pero sin tapar el titulo. */
    protected int firstRowY(int rows) {
        return Math.max(24, (this.height - rows * PITCH) / 2 + 8);
    }

    protected Button addRow(int index, int rows, Component text, Button.OnPress press) {
        return addRenderableWidget(Button.builder(text, press).bounds(this.width / 2 - ROW_W / 2, firstRowY(rows) + index * PITCH, ROW_W, ROW_H).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 8, 0xFFFFFF);
    }

    public static int[] center(Button b) {
        return new int[] {b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2};
    }
}
