package dev.consolepad.mixin;

import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Estado de FOV suavizado de GameRenderer (efecto de correr, arco...): cada jugador tiene el suyo. */
@Mixin(GameRenderer.class)
public interface GameRendererAccessor {
    @Accessor("fov")
    float consolepad$getFov();

    @Accessor("fov")
    void consolepad$setFov(float fov);

    @Accessor("oldFov")
    float consolepad$getOldFov();

    @Accessor("oldFov")
    void consolepad$setOldFov(float oldFov);

    @Invoker("tickFov")
    void consolepad$tickFov();
}
