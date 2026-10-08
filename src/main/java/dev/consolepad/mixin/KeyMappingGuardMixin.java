package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SecondaryTick;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mientras se ejecuta la logica de P2, ninguna tecla esta pulsada: LocalPlayer.aiStep consulta options.keySprint
 * (teclas de P1) y P2 correria cuando P1 corre. P2 solo recibe entrada por su PadInput.
 */
// ToggleKeyMapping (keySprint, keyShift) redefine isDown en NeoForge sin llamar a super: hay que guardarlo tambien.
@Mixin({KeyMapping.class, net.minecraft.client.ToggleKeyMapping.class})
public abstract class KeyMappingGuardMixin {
    @Inject(method = "isDown", at = @At("HEAD"), cancellable = true)
    private void consolepad$noKeysForSecondary(CallbackInfoReturnable<Boolean> cir) {
        if (!ContextSwitcher.isSecondaryActive()) return;
        // Solo el sprint de P2 (conmutador de su mando) llega a LocalPlayer.aiStep; el resto de teclas son de P1.
        KeyMapping self = (KeyMapping) (Object) this;
        cir.setReturnValue(self == Minecraft.getInstance().options.keySprint && SecondaryTick.sprintHeld);
    }
}
