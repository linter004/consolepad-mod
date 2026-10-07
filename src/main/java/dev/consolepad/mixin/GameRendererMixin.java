package dev.consolepad.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SplitRender;
import dev.consolepad.split.SplitView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Pantalla dividida: una pasada de mundo por jugador y proyeccion con el aspecto de cada region. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** En render(), el mundo se dibuja una vez por jugador (SplitRender decide si hay uno o dos). */
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void consolepad$splitRenderLevel(GameRenderer self, DeltaTracker deltaTracker) {
        SplitRender.renderLevels(self, deltaTracker);
    }

    /** Durante la pasada de P2, renderLevel usa el LevelRenderer de P2, no el de P1. */
    @Redirect(method = "renderLevel",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/Minecraft;levelRenderer:Lnet/minecraft/client/renderer/LevelRenderer;"))
    private LevelRenderer consolepad$renderLevelRenderer(Minecraft mc) {
        return ContextSwitcher.rendererOr(mc, mc.levelRenderer);
    }

    /** El aspecto de la proyeccion es el de la region que se dibuja, no el de la ventana entera. */
    @Redirect(method = "getProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getWidth()I"))
    private int consolepad$projectionWidth(Window window) {
        int w = SplitView.width;
        return w > 0 ? w : window.getWidth();
    }

    @Redirect(method = "getProjectionMatrix",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getHeight()I"))
    private int consolepad$projectionHeight(Window window) {
        int h = SplitView.height;
        return h > 0 ? h : window.getHeight();
    }
}
