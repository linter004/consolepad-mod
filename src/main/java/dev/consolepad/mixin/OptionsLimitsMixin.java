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

    /** La informacion de cliente que se manda al servidor lleva la distancia del jugador (no la de las opciones): cada jugador recibe solo sus chunks. */
    @Inject(method = "buildPlayerInformation", at = @At("RETURN"), cancellable = true)
    private void consolepad$playerViewDistance(CallbackInfoReturnable<net.minecraft.server.level.ClientInformation> cir) {
        var in = cir.getReturnValue();
        if (dev.consolepad.ConsolePad.PADS == null) return;
        var ctx = dev.consolepad.split.ContextSwitcher.current();
        cir.setReturnValue(dev.consolepad.split.ViewDistances.withDistance(in, PerfLimits.viewDistanceFor(ctx == null ? 0 : ctx.slotIndex, in.viewDistance())));
    }

    @Inject(method = "getEffectiveRenderDistance", at = @At("RETURN"), cancellable = true)
    private void consolepad$autoViewDistance(CallbackInfoReturnable<Integer> cir) {
        var ctx = dev.consolepad.split.ContextSwitcher.current(); // el jugador cuya region se dibuja ahora (P1 si no hay contexto extra)
        int own = PerfLimits.viewDistanceFor(ctx == null ? 0 : ctx.slotIndex, renderDistance.get());
        cir.setReturnValue(serverRenderDistance > 0 ? Math.min(own, serverRenderDistance) : own);
    }
}
