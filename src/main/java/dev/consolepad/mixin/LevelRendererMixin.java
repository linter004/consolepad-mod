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
    @Redirect(method = "renderLevel", require = 0,
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/pipeline/RenderTarget;bindWrite(Z)V"))
    private void consolepad$keepRegionViewport(RenderTarget target, boolean setViewport) {
        target.bindWrite(setViewport);
        SplitView.restoreViewport();
    }

    // La escala de dibujo de entidades con 3 y 4 jugadores ya no es un mixin sobre setupRender (Sodium reescribe ese metodo y el mixin tumbaba el
    // arranque): la aplica PerfLimits.applyEntityScale desde RenderLevelStageEvent.

    /**
     * Lluvia rapida con 3 y 4 jugadores (PerfLimits.fancyWeather). Antes era un @Redirect de Minecraft.useFancyGraphics() dentro de estos metodos, y Sodium
     * redirige esa misma llamada (dos @Redirect no caben y el de Sodium es critico: el juego no arrancaba). Ahora solo se marca que se esta en el clima y
     * Minecraft.useFancyGraphics() (MinecraftMixin) responde con el valor rapido mientras la marca esta puesta; si Sodium redirige la llamada, manda su opcion.
     */
    @org.spongepowered.asm.mixin.injection.Inject(method = {"renderSnowAndRain", "tickRain"}, at = @At("HEAD"), require = 0)
    private void consolepad$weatherStart(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.consolepad.split.PerfLimits.inWeather = true;
    }

    @org.spongepowered.asm.mixin.injection.Inject(method = {"renderSnowAndRain", "tickRain"}, at = @At("RETURN"), require = 0)
    private void consolepad$weatherEnd(org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.consolepad.split.PerfLimits.inWeather = false;
    }
}
