package dev.consolepad.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.consolepad.split.SplitView;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Con la pantalla dividida la pantalla de P1 se dibuja a escala k (la de su region) para caber en su cuadrante: sus coordenadas logicas son las de la
 * ventana divididas por k. Mientras eso ocurre, el ancho y alto de GUI que usa MouseHandler para convertir la posicion del raton
 * son los logicos (clic, scroll, arrastre), como si la interfaz fuera mayor.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Redirect(method = {"onPress", "onScroll", "handleAccumulatedMovement"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private int consolepad$logicalWidth(Window window) {
        float k = SplitView.p1Scale();
        return k != 1f ? Math.round(window.getGuiScaledWidth() / k) : window.getGuiScaledWidth();
    }

    @Redirect(method = {"onPress", "onScroll", "handleAccumulatedMovement"},
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledHeight()I"))
    private int consolepad$logicalHeight(Window window) {
        float k = SplitView.p1Scale();
        return k != 1f ? Math.round(window.getGuiScaledHeight() / k) : window.getGuiScaledHeight();
    }
}
