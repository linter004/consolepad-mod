package dev.consolepad.mixin;

import net.minecraft.client.CameraType;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Acceso directo al tipo de camara: Options.setCameraType tiene efectos secundarios (post-efectos de la camara de P1). */
@Mixin(Options.class)
public interface OptionsAccessor {
    @Accessor("cameraType")
    CameraType consolepad$getCameraType();

    @Accessor("cameraType")
    void consolepad$setCameraType(CameraType type);
}
