package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Pantalla de muerte de un jugador extra (la DeathScreen de vanilla, con "volver al titulo", desconectaria a P1): dentro de su region,
 * con Reaparecer y Salir de la partida local. Se cierra sola cuando el jugador vuelve a estar vivo.
 */
public final class ExtraDeathScreen extends Screen implements PlayerScreen {
    private final int slot;
    private final Component cause;
    private int delay;
    private boolean sawDead;
    private Button respawn, quit;

    public ExtraDeathScreen(int slot, Component cause) {
        super(Component.translatable("deathScreen.title"));
        this.slot = slot;
        this.cause = cause;
    }

    @Override
    protected void init() {
        delay = 0;
        int x = this.width / 2 - 100;
        int y = Math.max(60, this.height / 4 + 56);
        respawn = addRenderableWidget(Button.builder(Component.translatable("deathScreen.respawn"), b -> {
            this.minecraft.player.respawn();
            b.active = false;
        }).bounds(x, y, 200, 20).build());
        quit = addRenderableWidget(Button.builder(Component.literal("Salir de la partida (P" + (slot + 1) + ")"),
                b -> SecondaryPlayers.leave(Minecraft.getInstance(), slot)).bounds(x, y + 24, 200, 20).build());
        respawn.active = false;
        quit.active = false;
    }

    @Override
    public void tick() {
        var player = this.minecraft.player;
        if (player != null) {
            // El paquete de muerte puede llegar antes que la vida a 0: solo cuenta como reaparecido quien antes se vio muerto.
            boolean dead = player.isDeadOrDying() || player.getHealth() <= 0f;
            if (dead) {
                sawDead = true;
            } else if (sawDead) {
                this.minecraft.setScreen(null); // reaparecio: fuera la pantalla
                return;
            }
        }
        if (++delay == 20) { // como la de vanilla: los botones se activan tras un segundo
            respawn.active = true;
            quit.active = true;
        }
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fillGradient(0, 0, this.width, this.height, 1615855616, -1602211792); // el rojo de la pantalla de muerte de vanilla
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.pose().pushPose();
        graphics.pose().scale(2.0F, 2.0F, 2.0F);
        graphics.drawCenteredString(this.font, this.title, this.width / 4, Math.max(10, this.height / 8), 0xFFFFFF);
        graphics.pose().popPose();
        int y = Math.max(10, this.height / 8) * 2 + 24;
        if (cause != null) graphics.drawCenteredString(this.font, cause, this.width / 2, y, 0xFFFFFF);
        if (this.minecraft.player != null) {
            graphics.drawCenteredString(this.font, Component.translatable("deathScreen.score.value",
                    Component.literal(Integer.toString(this.minecraft.player.getScore())).withStyle(net.minecraft.ChatFormatting.YELLOW)),
                    this.width / 2, y + 14, 0xFFFFFF);
        }
    }

    public int[] respawnCenter() {
        return PlayerMenuScreen.center(respawn);
    }

    public int[] quitCenter() {
        return PlayerMenuScreen.center(quit);
    }

    public boolean respawnActive() {
        return respawn.active;
    }
}
