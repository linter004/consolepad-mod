package dev.consolepad.split;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Chat de un jugador extra: una linea de texto sobre el teclado en pantalla. Intro envia el mensaje (o el comando si empieza por /) con la
 * conexion de este jugador; los mensajes recibidos salen en su region como avisos (SplitNotices). El ChatScreen de vanilla escribe en el chat de P1.
 */
public final class ExtraChatScreen extends Screen implements PlayerScreen {
    private EditBox input;
    private String initial = "";

    public ExtraChatScreen() {
        super(Component.translatable("chat_screen.title"));
    }

    @Override
    protected void init() {
        String keep = input == null ? initial : input.getValue();
        input = new EditBox(this.font, 4, inputY(false), this.width - 8, 12, Component.translatable("chat.editBox"));
        input.setMaxLength(256);
        input.setBordered(true);
        input.setValue(keep);
        this.addWidget(input);
        this.setInitialFocus(input);
    }

    /** Fila del campo: sobre el teclado en pantalla si esta abierto, y sobre la barra de ayuda si no. */
    private int inputY(boolean keyboardOpen) {
        return Math.max(2, this.height - (PadIcons.H + 3) - (keyboardOpen ? PadKeyboard.HEIGHT + 16 : 18));
    }

    /** Centro del campo de texto (para el autotest). */
    public int[] inputCenter() {
        return new int[] {input.getX() + input.getWidth() / 2, input.getY() + input.getHeight() / 2};
    }

    @Override
    public boolean wantsKeyboard() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Texto escrito hasta ahora. */
    public String text() {
        return input == null ? "" : input.getValue();
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            send();
            return true;
        }
        return super.keyPressed(key, scan, mods);
    }

    private void send() {
        String text = input.getValue().trim();
        if (!text.isEmpty() && this.minecraft.player != null) {
            if (text.startsWith("/")) this.minecraft.player.connection.sendCommand(text.substring(1));
            else this.minecraft.player.connection.sendChat(text);
        }
        this.minecraft.setScreen(null);
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, this.width, this.height, 0x60000000);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        var ctx = ContextSwitcher.current();
        input.setY(inputY(ctx != null && PadKeyboard.of(ctx.slotIndex).active()));
        super.render(graphics, mouseX, mouseY, partialTick);
        input.render(graphics, mouseX, mouseY, partialTick);
    }
}
