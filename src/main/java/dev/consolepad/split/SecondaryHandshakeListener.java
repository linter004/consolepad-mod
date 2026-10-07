package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientHandshakePacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;

/** Login del jugador local extra: igual que el de vanilla, pero un fallo no saca al jugador 1 a una pantalla de desconexion. */
final class SecondaryHandshakeListener extends ClientHandshakePacketListenerImpl {
    private final Connection connection;

    SecondaryHandshakeListener(Connection connection, Minecraft minecraft) {
        super(connection, minecraft, null, null, false, null, status -> { }, null);
        this.connection = connection;
    }

    @Override
    public void onDisconnect(DisconnectionDetails details) {
        ConsolePad.LOGGER.warn("Login del jugador local extra fallido: {}", details.reason().getString());
        SecondaryPlayers.forget(connection);
    }
}
