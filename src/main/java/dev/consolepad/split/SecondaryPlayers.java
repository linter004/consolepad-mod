package dev.consolepad.split;

import com.mojang.authlib.GameProfile;
import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * Fase 3, paso 1: segundo jugador local como conexion extra en memoria al servidor integrado.
 *
 * Andamio temporal hasta el paso 2 (contexto por jugador): el cliente de P2 es un "fantasma". El servidor lo ve como un
 * jugador real, pero su listener de juego esta envuelto en un proxy que ignora casi todos los paquetes, para que
 * ClientPacketListener.handleLogin/handleRespawn no pisen Minecraft.player/level/gameMode de P1.
 */
public final class SecondaryPlayers {
    /** Paquetes que el cliente fantasma si procesa con el listener real (no tocan campos globales de Minecraft). */
    private static final Set<String> FORWARDED_HANDLERS = Set.of(
            "handleKeepAlive", "handlePing", "handleChunkBatchStart", "handleChunkBatchFinished", "handleDisconnect");

    private static final Set<Connection> CONNECTIONS = Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private SecondaryPlayers() {}

    public static boolean isSecondary(Connection connection) {
        return CONNECTIONS.contains(connection);
    }

    public static String secondaryName(Minecraft mc) {
        String base = mc.getUser().getName();
        if (base.length() > 14) base = base.substring(0, 14);
        return base + "_2";
    }

    /** Abre la conexion de P2. Debe llamarse desde el hilo principal con un mundo local cargado. */
    public static void join(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (server == null || mc.level == null || mc.player == null) {
            ConsolePad.LOGGER.info("Jugador 2: hace falta un mundo local cargado");
            return;
        }
        if (!CONNECTIONS.isEmpty()) {
            ConsolePad.LOGGER.info("Jugador 2: ya esta dentro");
            mc.player.displayClientMessage(Component.literal("Jugador 2 ya está dentro."), false);
            return;
        }
        String name = secondaryName(mc);
        GameProfile profile = UUIDUtil.createOfflineProfile(name);
        ConsolePad.LOGGER.info("Jugador 2: conectando como '{}' ({}); Minecraft.player antes = {}",
                name, profile.getId(), mc.player.getName().getString());

        SocketAddress address = server.getConnection().startMemoryChannel();
        Connection connection = Connection.connectToLocalServer(address);
        CONNECTIONS.add(connection); // antes de iniciar el protocolo: ConnectionMixin envuelve el listener de juego
        connection.initiateServerboundPlayConnection(address.toString(), 0, new SecondaryHandshakeListener(connection, mc));
        connection.send(new ServerboundHelloPacket(name, profile.getId()));
    }

    /** Llamado por ConnectionMixin al instalar el listener de juego de una conexion extra. */
    public static PacketListener wrapGameListener(Connection connection, ClientPacketListener real) {
        InvocationHandler handler = (proxy, method, args) -> {
            String n = method.getName();
            try {
                if (n.equals("onDisconnect")) {
                    DisconnectionDetails details = (DisconnectionDetails) args[0];
                    ConsolePad.LOGGER.info("Jugador 2 desconectado: {}", details.reason().getString());
                    forget(connection);
                    return null;
                }
                if (n.equals("tick")) return null; // el tick real consulta el estado global de Minecraft
                if (n.startsWith("handle") && args != null && args.length == 1
                        && args[0] instanceof ClientboundPlayerPositionPacket p) {
                    connection.send(new ServerboundAcceptTeleportationPacket(p.getId()));
                    return null;
                }
                // Solo los handleXxx (void) se ignoran; el resto (shouldHandleMessage, flow, protocol...) va al listener real.
                if (n.startsWith("handle") && !FORWARDED_HANDLERS.contains(n)) return null;
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (PacketListener) Proxy.newProxyInstance(SecondaryPlayers.class.getClassLoader(),
                new Class<?>[] {ClientGamePacketListener.class, TickablePacketListener.class}, handler);
    }

    static void forget(Connection connection) {
        CONNECTIONS.remove(connection);
    }

    /** Nadie mas hace tick de las conexiones extra: procesa sus desconexiones pendientes. */
    public static void tick() {
        List<Connection> copy;
        synchronized (CONNECTIONS) {
            if (CONNECTIONS.isEmpty()) return;
            copy = new ArrayList<>(CONNECTIONS);
        }
        for (Connection c : copy) c.tick();
    }
}
