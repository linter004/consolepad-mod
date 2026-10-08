package dev.consolepad.mixin;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Altura de ojos suavizada de la camara (agacharse, elitros): cada jugador tiene su camara y la suya. */
@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("eyeHeight")
    float consolepad$getEyeHeight();

    @Accessor("eyeHeight")
    void consolepad$setEyeHeight(float v);

    @Accessor("eyeHeightOld")
    void consolepad$setEyeHeightOld(float v);
}
