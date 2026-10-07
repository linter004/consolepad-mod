package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mientras se ejecuta la logica de P2, ninguna tecla esta pulsada: LocalPlayer.aiStep consulta options.keySprint
 * (teclas de P1) y P2 correria cuando P1 corre. P2 solo recibe entrada por su PadInput.
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingGuardMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void consolepad$noKeysForSecondary(CallbackInfoReturnable<Boolean> cir) {
        if (ContextSwitcher.isSecondaryActive()) cir.setReturnValue(false);
    }
}
