package dev.consolepad.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import dev.consolepad.split.SplitView;
import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Pantalla dividida: tras volver a enlazar el framebuffer principal dentro de renderLevel, se restaura el viewport de la
 * region. RenderTarget.clear() (entityTarget, itemEntityTarget...) lo deja al tamano de la ventana y sin esto el resto de
 * la pasada (entidades, bloques-entidad, particulas, clima, mano) saldria ensanchado.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Redirect(method = "renderLevel",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void consolepad$keepRegionViewport(RenderTarget target, boolean setViewport) {
        target.bindWrite(setViewport);
        SplitView.restoreViewport();
    }

    /** Dibujo de entidades mas corto con 3 y 4 jugadores (PerfLimits.entityScale). */
    @org.spongepowered.asm.mixin.injection.ModifyArg(method = "setupRender", index = 0,
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/Entity;setViewScale(D)V"))
    private double consolepad$entityScale(double scale) {
        return scale * dev.consolepad.split.PerfLimits.entityScale();
    }

    /** Lluvia rapida con 3 y 4 jugadores (PerfLimits.fancyWeather). */
    @Redirect(method = {"renderSnowAndRain", "tickRain"},
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Minecraft;useFancyGraphics()Z"))
    private boolean consolepad$weatherQuality() {
        return dev.consolepad.split.PerfLimits.fancyWeather();
    }
}
