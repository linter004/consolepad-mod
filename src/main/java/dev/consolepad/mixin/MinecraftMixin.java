package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Guardas: mientras se procesa un paquete de P2, no se toca lo que pertenece a P1 (pantalla, motores de nivel). */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Shadow public ClientLevel level;

    /** setLevel de vanilla cambia pantalla, LevelRenderer, ParticleEngine... compartidos con P1. P2 solo fija su nivel. */
    @Inject(method = "setLevel", at = @At("HEAD"), cancellable = true)
    private void consolepad$secondarySetLevel(ClientLevel newLevel, ReceivingLevelScreen.Reason reason, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) {
            this.level = newLevel;
            ci.cancel();
        }
    }

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void consolepad$secondarySetScreen(Screen screen, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
