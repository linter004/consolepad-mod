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

    /** La camara y el mapa de luz de P1 (los de GameRenderer), sin pasar por la redireccion de GameRendererMixin. */
    @Accessor("mainCamera")
    net.minecraft.client.Camera consolepad$getVanillaCamera();

    @Accessor("lightTexture")
    net.minecraft.client.renderer.LightTexture consolepad$getVanillaLight();

    @Invoker("tickFov")
    void consolepad$tickFov();
}
