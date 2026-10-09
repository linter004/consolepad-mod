package dev.consolepad.mixin;

import dev.consolepad.split.PerfLimits;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Distancia de vision automatica segun los jugadores (PerfLimits): todo lo que usa la distancia efectiva (mallas, niebla, entidades) la respeta.
 * Se aplica a la distancia que pide el usuario y despues se limita con la del servidor (que ya viene reducida por el mismo motivo), sin reducir dos veces.
 */
@Mixin(Options.class)
public abstract class OptionsLimitsMixin {
    @Shadow private OptionInstance<Integer> renderDistance;
    @Shadow private int serverRenderDistance;

    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void consolepad$autoViewDistance(CallbackInfoReturnable<Integer> cir) {
        int own = PerfLimits.viewDistance(renderDistance.get());
        cir.setReturnValue(serverRenderDistance > 0 ? Math.min(own, serverRenderDistance) : own);
    }
}
