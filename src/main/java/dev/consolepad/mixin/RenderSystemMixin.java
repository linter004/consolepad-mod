package dev.consolepad.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.consolepad.split.SplitView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Opacidad del HUD de un jugador: mientras se dibuja, el alfa de todo color de sombreador se multiplica por la suya (SplitView.hudAlpha). */
@Mixin(RenderSystem.class)
public abstract class RenderSystemMixin {
    @ModifyVariable(method = "setShaderColor", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private static float consolepad$hudAlpha(float alpha) {
        float k = SplitView.hudAlpha;
        return k >= 0.999f ? alpha : alpha * k;
    }
}
