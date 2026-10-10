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
    /** Diagnostico: scissor real al dibujar un objeto (el del cursor no debe salirse de la region de su jugador). */
    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;III)V", at = @At("HEAD"))
    private void consolepad$diagItem(net.minecraft.world.entity.LivingEntity entity, net.minecraft.world.level.Level level,
                                     net.minecraft.world.item.ItemStack stack, int x, int y, int seed,
                                     org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.consolepad.split.SplitDiag.onItem(stack, x, y);
    }

    /** Diagnostico: tooltips dibujados por jugador (el del contexto activo). */
    @Inject(method = "renderTooltipInternal", at = @At("HEAD"))
    private void consolepad$diagTooltip(net.minecraft.client.gui.Font font, java.util.List<net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent> components,
                                        int x, int y, net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner positioner,
                                        org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        if (!components.isEmpty()) dev.consolepad.split.SplitDiag.tooltipsDrawn[dev.consolepad.split.ReduceEffects.contextSlot()]++;
    }

    @org.spongepowered.asm.mixin.Shadow
    public abstract com.mojang.blaze3d.vertex.PoseStack pose();

    /**
     * enableScissor trabaja con coordenadas de la ventana y no mira la pose. Las pantallas de cada jugador se dibujan trasladadas y
     * escaladas a su region (el maniqui del inventario, listas con scroll...): el recorte se lleva con esa pose, si no queda en otro sitio
     * (el maniqui de P2-P4 salia vacio porque su recorte caia en la region de P1).
     */
    private int consolepad$poseX(int v) {
        if (!SplitView.p1HalfScreen) return v;
        org.joml.Matrix4f m = pose().last().pose();
        return Math.round(m.m00() * v + m.m30());
    }

    private int consolepad$poseY(int v) {
        if (!SplitView.p1HalfScreen) return v;
        org.joml.Matrix4f m = pose().last().pose();
        return Math.round(m.m11() * v + m.m31());
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int consolepad$scissorMinX(int v) {
        return consolepad$poseX(v);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int consolepad$scissorMinY(int v) {
        return consolepad$poseY(v);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 2)
    private int consolepad$scissorMaxX(int v) {
        return consolepad$poseX(v);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "enableScissor", at = @At("HEAD"), argsOnly = true, ordinal = 3)
    private int consolepad$scissorMaxY(int v) {
        return consolepad$poseY(v);
    }

    /**
     * AbstractWidget.render decide si el raton esta encima con containsPointInScissor, que compara el raton (en unidades de la pantalla del
     * jugador) con el recorte de su region (en unidades de la ventana): fuera de la esquina superior izquierda ningun boton de P2-P4 se
     * marcaba como "encima" (ni el campo de texto reconocia el cursor). El punto se lleva a coordenadas de ventana con la pose.
     */
    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "containsPointInScissor", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private int consolepad$hoverX(int v) {
        return consolepad$poseX(v);
    }

    @org.spongepowered.asm.mixin.injection.ModifyVariable(method = "containsPointInScissor", at = @At("HEAD"), argsOnly = true, ordinal = 1)
    private int consolepad$hoverY(int v) {
        return consolepad$poseY(v);
    }

    /** Diagnostico: scissor GL real tras cada enableScissor (el del maniqui del inventario debe caer dentro de la region de su jugador). */
    @Inject(method = "enableScissor", at = @At("RETURN"))
    private void consolepad$diagScissor(int minX, int minY, int maxX, int maxY, org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
        dev.consolepad.split.SplitDiag.onScissor();
    }

    @Inject(method = "guiWidth", at = @At("HEAD"), cancellable = true)
    private void consolepad$regionWidth(CallbackInfoReturnable<Integer> cir) {
        int w = SplitView.guiWidth;
        if (w > 0) cir.setReturnValue(w);
    }

    @Inject(method = "guiHeight", at = @At("HEAD"), cancellable = true)
    private void consolepad$regionHeight(CallbackInfoReturnable<Integer> cir) {
        int h = SplitView.guiHeight;
        if (h > 0) cir.setReturnValue(h);
    }
}
