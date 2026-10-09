package dev.consolepad.split;

import net.minecraft.client.player.Input;

/** Entrada del LocalPlayer de P2: la rellena SecondaryTick desde su mando, en lugar de las teclas de P1. */
public final class PadInput extends Input {
    /** Inclinacion del stick, -1..1. forward > 0 = adelante; left > 0 = izquierda. */
    float rawForward;
    float rawLeft;
    /**
     * Salto pedido por el mando. Se copia a {@code jumping} dentro de {@link #tick}, no antes: LocalPlayer.aiStep lee {@code jumping} (valor del tick
     * anterior) ANTES de llamar a tick() y compara con el nuevo para detectar el flanco (abrir los elitros en el aire, volar en creativo con doble
     * toque). Si SecondaryTick ya lo hubiera puesto, aiStep veria siempre "mantenido" y nunca un toque nuevo.
     */
    boolean rawJumping;

    /** Lo llama LocalPlayer.aiStep en cada tick del jugador. */
    @Override
    public void tick(boolean isSneaking, float sneakingSpeedMultiplier) {
        this.jumping = rawJumping;
        this.up = rawForward > 0f;
        this.down = rawForward < 0f;
        this.left = rawLeft > 0f;
        this.right = rawLeft < 0f;
        this.forwardImpulse = rawForward;
        this.leftImpulse = rawLeft;
        if (isSneaking) {
            this.forwardImpulse *= sneakingSpeedMultiplier;
            this.leftImpulse *= sneakingSpeedMultiplier;
        }
    }
}
