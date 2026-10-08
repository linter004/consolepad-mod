package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SecondaryPlayers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import org.objectweb.asm.Opcodes;
import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    /** "Usar" anima la mano del jugador que usa el objeto (la de P2 es propia, no la de GameRenderer). */
    @Redirect(method = "startUseItem",
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/renderer/GameRenderer;itemInHandRenderer:Lnet/minecraft/client/renderer/ItemInHandRenderer;"))
    private ItemInHandRenderer consolepad$ownHandOnUse(GameRenderer renderer) {
        return ContextSwitcher.handOr(renderer.itemInHandRenderer);
    }

    /**
     * Sin pausa global: con P2 dentro, un menu de pausa de P1 no congela el mundo (ni el servidor integrado, ni el tick de P2).
     * Vanilla ya hace esto con una partida abierta a LAN (isPublished); aqui se finge lo mismo mientras P2 esta conectado.
     */
    @Redirect(method = "runTick",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;isPublished()Z"))
    private boolean consolepad$noGlobalPause(IntegratedServer server) {
        return SecondaryPlayers.isActive() || server.isPublished();
    }
}
