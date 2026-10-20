package dev.consolepad.split;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;

/**
 * Pantalla de un jugador extra que esta en la cama: un boton "Salir de la cama" (como el InBedChatScreen de vanilla, que solo abre Minecraft para P1).
 * B, Escape o el boton mandan al servidor que se levante con la conexion de este jugador; al despertar la pantalla se cierra sola.
 */
public final class ExtraBedScreen extends Screen implements PlayerScreen {
    private Button leave;

    public ExtraBedScreen() {
        super(Component.translatable("multiplayer.stopSleeping"));
    }

    @Override
    protected void init() {
        leave = Button.builder(Component.translatable("multiplayer.stopSleeping"), b -> wake()).bounds(this.width / 2 - 100, this.height - (PadIcons.H + 3) - 28, 200, 20).build();
        this.addRenderableWidget(leave);
    }

    /** Centro del boton (para el autotest). */
    public int[] buttonCenter() {
        return new int[] {leave.getX() + leave.getWidth() / 2, leave.getY() + leave.getHeight() / 2};
    }

    private void wake() {
        if (this.minecraft != null && this.minecraft.player != null) {
            this.minecraft.player.connection.send(new ServerboundPlayerCommandPacket(this.minecraft.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
        }
    }

    @Override
    public void onClose() {
        wake();
    }

    @Override
    public void tick() {
        if (this.minecraft != null && this.minecraft.player != null && !this.minecraft.player.isSleeping()) this.minecraft.setScreen(null);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // sin fondo: se sigue viendo el techo y la cama
    }
}
