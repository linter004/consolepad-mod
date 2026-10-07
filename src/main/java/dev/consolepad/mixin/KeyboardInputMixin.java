package dev.consolepad.mixin;

import dev.consolepad.client.PadDriver;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Movimiento analogico: el stick izquierdo sustituye al teclado si da mas inclinacion. */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin extends Input {
    @Inject(method = "tick", at = @At("RETURN"))
    private void consolepad$analogMove(boolean isMovingSlowly, float sneakingSpeedBonus, CallbackInfo ci) {
        if (!PadDriver.analogActive) return;
        float forward = PadDriver.analogForward;
        float left = PadDriver.analogLeft;
        if (isMovingSlowly) {
            forward *= sneakingSpeedBonus;
            left *= sneakingSpeedBonus;
        }
        if (Math.abs(forward) > Math.abs(this.forwardImpulse)) this.forwardImpulse = forward;
        if (Math.abs(left) > Math.abs(this.leftImpulse)) this.leftImpulse = left;
    }
}
