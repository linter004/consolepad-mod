package dev.consolepad.mixin;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Volumen de un sonido ya creado (la oclusion lo baja antes de reproducirlo). */
@Mixin(AbstractSoundInstance.class)
public interface AbstractSoundInstanceAccessor {
    @Accessor("volume")
    float consolepad$volume();

    @Accessor("volume")
    void consolepad$setVolume(float volume);

    @Accessor("x")
    void consolepad$setX(double x);

    @Accessor("y")
    void consolepad$setY(double y);

    @Accessor("z")
    void consolepad$setZ(double z);
}
