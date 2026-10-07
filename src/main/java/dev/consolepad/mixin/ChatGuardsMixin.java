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

/** El chat es comun: P1 ya recibe cada mensaje del servidor; el cliente de P2 no debe duplicarlo. */
@Mixin(ChatComponent.class)
public abstract class ChatGuardsMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void consolepad$noDuplicateChat(Component message, @Nullable MessageSignature signature, @Nullable GuiMessageTag tag,
                                            CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
