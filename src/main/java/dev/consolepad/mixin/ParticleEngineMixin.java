package dev.consolepad.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Particulas por jugador. El ParticleEngine es uno solo (sus texturas y sus listas son de P1), pero cada particula recuerda el nivel en que se creo:
 * ContextSwitcher le pone al motor el nivel del jugador activo mientras corre su contexto (asi rotura, golpes, comer, pociones o recoger objetos de
 * P2 a P4 nacen en SU mundo) y cada pasada de dibujo solo pinta las particulas del nivel que se esta dibujando. Antes se descartaban todas las de los
 * extra y no se veia ninguna.
 */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Redirect(method = "render(Lnet/minecraft/client/renderer/LightTexture;Lnet/minecraft/client/Camera;FLnet/minecraft/client/renderer/culling/Frustum;Ljava/util/function/Predicate;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/particle/Particle;render(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lnet/minecraft/client/Camera;F)V"))
    private void consolepad$ownWorldOnly(Particle particle, com.mojang.blaze3d.vertex.VertexConsumer consumer, Camera camera, float partialTick) {
        int slot = dev.consolepad.split.SplitRender.passSlot;
        if (((ParticleAccessor) particle).consolepad$getLevel() == Minecraft.getInstance().level) {
            if (dev.consolepad.split.PerfLimits.playerCount > 1 && !dev.consolepad.split.AdaptivePerf.drawParticle(System.identityHashCode(particle), dev.consolepad.split.AdaptivePerf.level())) {
                dev.consolepad.split.SplitDiag.particlesThinned++; // presion alta: se adelgazan
                return;
            }
            if (slot >= 0 && slot < 4) dev.consolepad.split.SplitDiag.particlesDrawn[slot]++;
            particle.render(consumer, camera, partialTick);
        } else if (slot >= 0 && slot < 4) {
            dev.consolepad.split.SplitDiag.particlesForeign[slot]++;
        }
    }
}
