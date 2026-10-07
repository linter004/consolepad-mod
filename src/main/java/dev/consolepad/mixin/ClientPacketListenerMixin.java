package dev.consolepad.mixin;

import dev.consolepad.split.ContextSwitcher;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ReceivingLevelScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

/** Guardas de login/respawn para el jugador local extra (ver SecondaryPlayers). */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
    /** El ClientLevel de P2 se crea con su renderer latente, no con el LevelRenderer de P1. */
    @Redirect(method = {"handleLogin", "handleRespawn"},
            at = @At(value = "FIELD", opcode = Opcodes.GETFIELD,
                    target = "Lnet/minecraft/client/Minecraft;levelRenderer:Lnet/minecraft/client/renderer/LevelRenderer;"))
    private LevelRenderer consolepad$levelRenderer(Minecraft mc) {
        return ContextSwitcher.rendererOr(mc, mc.levelRenderer);
    }

    /** handleLogin fija el UUID del dueno del servidor al del primer jugador que entra sin player: no debe ser P2. */
    @Redirect(method = "handleLogin",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/server/IntegratedServer;setUUID(Ljava/util/UUID;)V"))
    private void consolepad$ownerUuid(IntegratedServer server, UUID uuid) {
        if (!ContextSwitcher.isSecondaryActive()) server.setUUID(uuid);
    }

    /** Muestra la pantalla de carga de P1 y crea el LevelLoadStatusManager con su renderer. */
    @Inject(method = "startWaitingForNewLevel(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/client/multiplayer/ClientLevel;Lnet/minecraft/client/gui/screens/ReceivingLevelScreen$Reason;Lnet/minecraft/resources/ResourceKey;Lnet/minecraft/resources/ResourceKey;)V",
            at = @At("HEAD"), cancellable = true)
    private void consolepad$noLoadingScreen(LocalPlayer player, ClientLevel level, ReceivingLevelScreen.Reason reason,
                                            ResourceKey<Level> to, ResourceKey<Level> from, CallbackInfo ci) {
        if (ContextSwitcher.isSecondaryActive()) ci.cancel();
    }
}
