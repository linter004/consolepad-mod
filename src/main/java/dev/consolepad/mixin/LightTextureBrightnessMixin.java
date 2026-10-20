package dev.consolepad.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.consolepad.ConsolePad;
import dev.consolepad.split.SplitDiag;
import dev.consolepad.split.SplitRender;
import net.minecraft.client.Minecraft;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.renderer.LightTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Brillo por jugador. Cada jugador tiene su propio mapa de luz (ContextSwitcher/GameRendererMixin); al calcularlo, la opcion global "Brillo" se sustituye por
 * la del jugador de la pasada que se dibuja (SplitRender.passSlot) si ha elegido una (valor negativo = la del juego).
 */
@Mixin(LightTexture.class)
public abstract class LightTextureBrightnessMixin {
    @WrapOperation(method = "updateLightTexture",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/OptionInstance;get()Ljava/lang/Object;"))
    private Object consolepad$ownGamma(OptionInstance<?> instance, Operation<Object> original) {
        Object value = original.call(instance);
        if (ConsolePad.PADS != null && instance == Minecraft.getInstance().options.gamma()) {
            int slot = SplitRender.passSlot;
            float own = ConsolePad.PADS.slot(slot).settings.brightness;
            if (own >= 0f) {
                SplitDiag.gammaOverrides[slot]++;
                SplitDiag.lastGamma[slot] = own;
                return (double) own;
            }
        }
        return value;
    }
}
