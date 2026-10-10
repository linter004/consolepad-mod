package dev.consolepad.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleRenderType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;
import java.util.Queue;

/** Nivel con el que el ParticleEngine crea las particulas (lo cambia ContextSwitcher al entrar en el contexto de un jugador) y sus listas (para las pruebas). */
@Mixin(ParticleEngine.class)
public interface ParticleEngineAccessor {
    @Accessor("level")
    ClientLevel consolepad$getLevel();

    @Accessor("level")
    void consolepad$setLevel(ClientLevel level);

    @Accessor("particles")
    Map<ParticleRenderType, Queue<Particle>> consolepad$getParticles();
}
