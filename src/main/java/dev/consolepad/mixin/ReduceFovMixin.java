package dev.consolepad.mixin;

import dev.consolepad.split.ReduceEffects;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Accesibilidad: sin cambio de FOV por correr, volar o efectos de velocidad para el jugador que lo pida (se queda el zoom del arco y del catalejo). */
@Mixin(AbstractClientPlayer.class)
public abstract class ReduceFovMixin {
    @Inject(method = "getFieldOfViewModifier", at = @At("RETURN"), cancellable = true)
    private void consolepad$reduceFov(CallbackInfoReturnable<Float> cir) {
        if (!ReduceEffects.contextOn() || cir.getReturnValueF() == 0.1F) return; // 0.1 = mirando por el catalejo
        AbstractClientPlayer self = (AbstractClientPlayer) (Object) this;
        float bow = 1.0F;
        if (self.isUsingItem() && self.getUseItem().is(Items.BOW)) {
            float t = Math.min(1.0F, self.getTicksUsingItem() / 20.0F);
            if (t < 1.0F) t *= t;
            bow = 1.0F - t * 0.15F;
        }
        if (bow != cir.getReturnValueF()) ReduceEffects.skippedFov[ReduceEffects.contextSlot()]++;
        cir.setReturnValue(bow);
    }
}
