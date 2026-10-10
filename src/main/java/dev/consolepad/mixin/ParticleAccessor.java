package dev.consolepad.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** El mundo al que pertenece cada particula: cada jugador solo dibuja las de su nivel. */
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("level")
    ClientLevel consolepad$getLevel();
}
