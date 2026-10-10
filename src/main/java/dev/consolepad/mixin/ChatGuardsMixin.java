package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;

/** El chat de un extra no entra en el ChatComponent de P1: se guarda en el contexto de ese jugador (SplitNotices) y se dibuja en su region. */
@Mixin(ChatComponent.class)
public abstract class ChatGuardsMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void consolepad$noDuplicateChat(Component message, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag,
                                            CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) {
            ci.cancel();
            dev.consolepad.split.SplitNotices.addChat(message); // lo ve el jugador en su propia region
        } else {
            dev.consolepad.split.SplitNotices.logP1(message);
        }
    }

    /**
     * El historial del chat de P1 no se dibuja mientras tiene abierta una pantalla de contenedor (inventario, cofre...): se cruzaba con ella. El chat
     * propio (ChatScreen) y los menus siguen como en vanilla. Los extra hacen lo mismo en SplitNotices.
     */
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void consolepad$hideOverContainer(net.minecraft.client.gui.GuiGraphics graphics, int tickCount, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) return;
        if (net.minecraft.client.Minecraft.getInstance().screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) {
            dev.consolepad.split.SplitDiag.p1ChatHidden++;
            ci.cancel();
        } else {
            dev.consolepad.split.SplitDiag.p1ChatShown++;
        }
    }

    /** Con la pantalla dividida el chat de P1 no puede ser mas ancho que su region (320 unidades se saldrian a la region de otro jugador). */
    @Inject(method = "getWidth()I", at = @At("RETURN"), cancellable = true)
    private void consolepad$regionWidth(org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Integer> cir) {
        if (!dev.consolepad.split.SplitView.p1HalfScreen || ContextSwitcher.isSecondaryActive()) return;
        int cap = Math.max(60, dev.consolepad.split.SplitView.p1HudW - 12);
        if (cir.getReturnValueI() > cap) cir.setReturnValue(cap);
    }
}
