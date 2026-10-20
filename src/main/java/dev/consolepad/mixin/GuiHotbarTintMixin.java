package dev.consolepad.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.consolepad.ConsolePad;
import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.PlayerLabels;
import dev.consolepad.split.SplitDiag;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** El marco de la casilla seleccionada de la barra de objetos se tine con el color del jugador (opcion "Colores del jugador"). */
@Mixin(Gui.class)
public abstract class GuiHotbarTintMixin {
    @WrapOperation(method = "renderItemHotbar",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphics;blitSprite(Lnet/minecraft/resources/ResourceLocation;IIII)V", ordinal = 1))
    private void consolepad$tintSelection(GuiGraphics g, ResourceLocation sprite, int x, int y, int w, int h, Operation<Void> original) {
        var ctx = ContextSwitcher.current();
        int slot = ctx == null ? 0 : ctx.slotIndex;
        boolean tint = ConsolePad.PADS != null && ConsolePad.PADS.slot(slot).settings.playerColors;
        if (tint) {
            int rgb = PlayerLabels.themeRgb(slot);
            g.setColor(((rgb >> 16) & 255) / 255f, ((rgb >> 8) & 255) / 255f, (rgb & 255) / 255f, 1f);
            SplitDiag.hotbarTinted[slot]++;
        }
        try {
            original.call(g, sprite, x, y, w, h);
        } finally {
            if (tint) g.setColor(1f, 1f, 1f, 1f);
        }
    }
}
