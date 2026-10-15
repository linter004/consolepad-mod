package dev.consolepad.split;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Aviso de mando desconectado de un jugador local (PadLost): ocupa su region mientras el mando no vuelva; el jugador esta quieto y no recibe dano.
 * Se cierra solo al reconectar el mando. P1 puede seguir con teclado y raton (boton o Esc); los extra no tienen otra forma de control.
 */
public final class PadLostScreen extends Screen implements PlayerScreen {
    private final int slot;

    public PadLostScreen(int slot) {
        super(Component.literal("Mando desconectado"));
        this.slot = slot;
    }

    @Override
    protected void init() {
        if (slot == 0) {
            addRenderableWidget(Button.builder(Component.literal("Seguir con teclado y ratón"), b -> this.onClose())
                    .bounds(this.width / 2 - 90, this.height / 2 + 22, 180, 20).build());
        }
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, this.width, this.height, 0xB0000000);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(this.font, "Mando del jugador " + (slot + 1) + " desconectado", this.width / 2, this.height / 2 - 24, 0xFFFFAA33);
        g.drawCenteredString(this.font, "Jugador en pausa: no se mueve ni recibe daño", this.width / 2, this.height / 2 - 10, 0xFFFFFFFF);
        g.drawCenteredString(this.font, "Vuelve a conectar el mando para seguir jugando", this.width / 2, this.height / 2 + 2, 0xFFFFFFFF);
        int left = PadLost.secondsLeft(slot);
        if (left >= 0) g.drawCenteredString(this.font, "Si no vuelve, sale de la partida en " + left + " s", this.width / 2, this.height / 2 + 16, 0xFFFFD84A);
    }

    @Override
    public boolean isPauseScreen() {
        return slot == 0; // como el menu de pausa de P1: con extras dentro Minecraft no pausa de todos modos
    }

    public int slot() {
        return slot;
    }
}
