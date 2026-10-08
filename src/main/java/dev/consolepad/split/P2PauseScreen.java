package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Menu de pausa de P2. No pausa nada (con P2 dentro el mundo no se pausa nunca). "Salir" saca solo a P2; el PauseScreen de vanilla
 * desconectaria a P1 y pararia el servidor, asi que no se usa para P2.
 */
public final class P2PauseScreen extends Screen {
    private Button resume, quit;

    public P2PauseScreen() {
        super(Component.literal("Jugador 2"));
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 80;
        int y = this.height / 4 + 40;
        resume = addRenderableWidget(Button.builder(Component.literal("Volver a la partida"), b -> this.onClose())
                .bounds(x, y, 160, 20).build());
        quit = addRenderableWidget(Button.builder(Component.literal("Salir de la partida (P2)"), b -> SecondaryPlayers.leave(Minecraft.getInstance()))
                .bounds(x, y + 28, 160, 20).build());
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, this.height / 4 + 16, 0xFFFFFF);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    public int[] resumeCenter() {
        return new int[] {resume.getX() + resume.getWidth() / 2, resume.getY() + resume.getHeight() / 2};
    }

    public int[] quitCenter() {
        return new int[] {quit.getX() + quit.getWidth() / 2, quit.getY() + quit.getHeight() / 2};
    }
}
