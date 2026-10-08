package dev.consolepad.mixin;

import com.mojang.blaze3d.platform.Window;
import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SplitDiag;
import dev.consolepad.split.SplitHud;
import dev.consolepad.split.SplitRender;
import dev.consolepad.split.SplitView;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import org.joml.Matrix4f;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Pantalla dividida: una pasada de mundo por jugador y proyeccion con el aspecto de cada region. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    /** En render(), el mundo se dibuja una vez por jugador (SplitRender decide si hay uno o dos). */
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;renderLevel(Lnet/minecraft/client/DeltaTracker;)V"))
    private void consolepad$splitRenderLevel(GameRenderer self, DeltaTracker deltaTracker) {
        SplitRender.renderLevels(self, deltaTracker);
    }

    /** El HUD se divide igual que el mundo (SplitHud). */
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Gui;render(Lnet/minecraft/client/gui/GuiGraphics;Lnet/minecraft/client/DeltaTracker;)V"))
    private void consolepad$splitHud(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker) {
        SplitHud.render(gui, graphics, deltaTracker);
    }

    /** Durante la pasada de P2, renderLevel usa el LevelRenderer de P2, no el de P1. */
    @Redirect(method = "renderLevel",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/Minecraft;levelRenderer:Lnet/minecraft/client/renderer/LevelRenderer;"))
    private LevelRenderer consolepad$renderLevelRenderer(Minecraft mc) {
        return ContextSwitcher.rendererOr(mc, mc.levelRenderer);
    }

    /** La mano en primera persona es la del jugador que se dibuja (objeto y animaciones propios). */
    @Redirect(method = "renderItemInHand",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/renderer/GameRenderer;itemInHandRenderer:Lnet/minecraft/client/renderer/ItemInHandRenderer;"))
    private ItemInHandRenderer consolepad$ownHand(GameRenderer self) {
        return ContextSwitcher.handOr(self.itemInHandRenderer);
    }

    /** La pantalla de P1 se dibuja a la escala de su region (con 4 jugadores < 1) y con la posicion del raton en unidades logicas. */
    @Redirect(method = "render",
            at = @At(value = "INVOKE", target = "Lnet/neoforged/neoforge/client/ClientHooks;drawScreen(Lnet/minecraft/client/gui/screens/Screen;Lnet/minecraft/client/gui/GuiGraphics;IIF)V"))
    private void consolepad$scaledP1Screen(net.minecraft.client.gui.screens.Screen screen, net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        float k = SplitView.p1Scale();
        if (!SplitView.p1HalfScreen) {
            net.neoforged.neoforge.client.ClientHooks.drawScreen(screen, graphics, mouseX, mouseY, partial);
            return;
        }
        int oldW = SplitView.guiWidth, oldH = SplitView.guiHeight;
        int[] r = SplitView.p1Gui;
        graphics.enableScissor(r[0], r[1], r[0] + r[2], r[1] + r[3]); // el objeto del cursor, el fondo difuminado y las ayudas no salen de la region de P1
        graphics.pose().pushPose();
        graphics.pose().scale(k, k, 1f);
        SplitView.guiWidth = screen.width;
        SplitView.guiHeight = screen.height;
        try {
            net.neoforged.neoforge.client.ClientHooks.drawScreen(screen, graphics, Math.round(mouseX / k), Math.round(mouseY / k), partial);
        } finally {
            SplitView.guiWidth = oldW;
            SplitView.guiHeight = oldH;
            graphics.pose().popPose();
            graphics.disableScissor();
        }
    }

    /** Campo de vision propio de cada jugador: se escala por (FOV del jugador / FOV global); la mano (useFOVSetting = false) no cambia. */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void consolepad$ownFov(net.minecraft.client.Camera camera, float partialTicks, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        if (!useFovSetting || dev.consolepad.ConsolePad.PADS == null) return;
        int slot = SplitRender.passSlot;
        int own = dev.consolepad.ConsolePad.PADS.slot(slot).settings.fov;
        if (own <= 0) return;
        int global = Minecraft.getInstance().options.fov().get();
        if (global > 0) cir.setReturnValue(cir.getReturnValue() * own / global);
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

    /** Diagnostico: el desenfoque de fondo de una pantalla se aplica con el scissor de la region de quien lo pide (solo con el autotest grabando). */
    @Inject(method = "processBlurEffect", at = @At("HEAD"))
    private void consolepad$diagBlur(float partialTick, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        SplitDiag.onBlur();
    }

    /** Diagnostico: anota la matriz de proyeccion que devuelve cada llamada (solo con el autotest grabando). */
    @Inject(method = "getProjectionMatrix", at = @At("RETURN"))
    private void consolepad$diagProjection(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        SplitDiag.onProjection(cir.getReturnValue());
    }
}
