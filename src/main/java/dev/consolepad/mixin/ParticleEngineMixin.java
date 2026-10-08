package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** El ParticleEngine es de P1: las particulas que genera el cliente de P2 no deben entrar en el. */
@Mixin(ParticleEngine.class)
public abstract class ParticleEngineMixin {
    @Inject(method = "add(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void consolepad$noAdd(Particle particle, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive() && !dev.consolepad.split.SplitView.particleBypass) ci.cancel();
    }

    @Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
    private void consolepad$noCreate(ParticleOptions options, double x, double y, double z, double xs, double ys, double zs,
                                     CallbackInfoReturnable<Particle> cir) {
        if (ContextSwitcher.isSecondaryActive() && !dev.consolepad.split.SplitView.particleBypass) cir.setReturnValue(null);
    }

    @Inject(method = "createTrackingEmitter", at = @At("HEAD"), cancellable = true)
    private void consolepad$noEmitter(CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
