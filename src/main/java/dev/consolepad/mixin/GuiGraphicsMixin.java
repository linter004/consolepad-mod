package dev.consolepad.mixin;

import dev.consolepad.split.SplitView;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mientras se dibuja el HUD de una region, el ancho de la interfaz es el de esa region (el HUD se centra en ella). */
@Mixin(GuiGraphics.class)
public abstract class GuiGraphicsMixin {
    @Inject(method = "guiWidth", at = @At("HEAD"), cancellable = true)
    private void consolepad$regionWidth(CallbackInfoReturnable<Integer> cir) {
        int w = SplitView.guiWidth;
        if (w > 0) cir.setReturnValue(w);
    }
}
