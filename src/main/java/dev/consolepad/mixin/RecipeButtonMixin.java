package dev.consolepad.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Fallo raro de vanilla: si la lista de recetas de un boton del libro se vacia justo antes de dibujarlo (el filtro de "solo fabricables" o la llegada de
 * recetas del servidor mientras se pinta), {@code renderWidget} divide por el tamano de la lista (0) y cierra el juego con ArithmeticException. Con
 * varios jugadores, cada uno con su libro, pasa mas a menudo. Un boton sin recetas simplemente no se dibuja ese fotograma.
 */
@Mixin(RecipeButton.class)
public abstract class RecipeButtonMixin {
    @Shadow
    protected abstract List<RecipeHolder<?>> getOrderedRecipes();

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true)
    private void consolepad$skipEmpty(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.getOrderedRecipes().isEmpty()) ci.cancel();
    }
}
