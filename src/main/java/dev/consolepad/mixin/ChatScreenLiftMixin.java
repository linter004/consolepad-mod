package dev.consolepad.mixin;

import dev.consolepad.split.ChatLift;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * El chat de P1 con mando sube lo justo para que el campo de texto quede encima del teclado en pantalla y de la barra de ayuda (como el de P2 a P4).
 * Se dibuja todo trasladado hacia arriba y el raton se lleva la misma distancia hacia abajo, asi los clics y el cursor siguen coincidiendo.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenLiftMixin extends Screen {
    protected ChatScreenLiftMixin() {
        super(null);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void consolepad$liftStart(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        graphics.pose().pushPose();
        graphics.pose().translate(0f, -ChatLift.lift(), 0f);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void consolepad$liftEnd(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        graphics.pose().popPose();
        ChatLift.lastInput = new int[] {4, this.height - 12 - ChatLift.lift(), this.width - 4, 12};
    }

    @ModifyVariable(method = "render", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int consolepad$renderMouseY(int mouseY) {
        return mouseY + ChatLift.lift();
    }

    @ModifyVariable(method = "mouseClicked", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double consolepad$clickMouseY(double mouseY) {
        return mouseY + ChatLift.lift();
    }

    @ModifyVariable(method = "mouseScrolled", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private double consolepad$scrollMouseY(double mouseY) {
        return mouseY + ChatLift.lift();
    }
}
