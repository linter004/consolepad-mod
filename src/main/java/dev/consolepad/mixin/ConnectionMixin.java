package dev.consolepad.mixin;

import dev.consolepad.split.SecondaryPlayers;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Envuelve el listener de juego de las conexiones del jugador local extra (ver SecondaryPlayers). */
@Mixin(Connection.class)
public abstract class ConnectionMixin {
    @ModifyVariable(method = "setupInboundProtocol", at = @At("HEAD"), argsOnly = true)
    private PacketListener consolepad$wrapSecondary(PacketListener listener) {
        if (listener instanceof ClientPacketListener real && SecondaryPlayers.isSecondary((Connection) (Object) this)) {
            return SecondaryPlayers.wrapGameListener((Connection) (Object) this, real);
        }
        return listener;
    }
}
