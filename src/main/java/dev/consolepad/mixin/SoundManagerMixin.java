package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Los sonidos de P2 los oiria P1 duplicados (el servidor ya se los manda a ambos). */
@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
    @Inject(method = "play(Lnet/minecraft/client/resources/sounds/SoundInstance;)V", at = @At("HEAD"), cancellable = true)
    private void consolepad$mutePlay(SoundInstance sound, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }

    @Inject(method = "playDelayed", at = @At("HEAD"), cancellable = true)
    private void consolepad$mutePlayDelayed(SoundInstance sound, int delay, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
