package dev.consolepad.mixin;

import dev.consolepad.ConsolePad;
import dev.consolepad.split.PaperDoll;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Salto automatico por jugador: la opcion del juego es global, cada jugador elige la suya ("como el juego", si o no). */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerMixin {
    @Inject(method = "isAutoJumpEnabled", at = @At("HEAD"), cancellable = true)
    private void consolepad$autoJump(CallbackInfoReturnable<Boolean> cir) {
        if (ConsolePad.PADS == null) return;
        int mode = ConsolePad.PADS.slot(PaperDoll.slotOf(this)).settings.autoJump;
        if (mode == 1) cir.setReturnValue(true);
        else if (mode == 2) cir.setReturnValue(false);
    }
}
