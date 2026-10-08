package dev.consolepad.split;

import com.mojang.authlib.GameProfile;
import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.Connection;
import net.minecraft.network.DisconnectionDetails;
import net.minecraft.network.PacketListener;
import net.minecraft.network.TickablePacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.login.ServerboundHelloPacket;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.SocketAddress;

/**
 * Fase 3: segundo jugador local como conexion extra en memoria al servidor integrado.
 *
 * Paso 1: login por startMemoryChannel. Paso 2 (este): los paquetes de juego de P2 se procesan en el hilo principal
 * dentro de su {@link PlayerContext} ({@link ContextSwitcher}), de modo que ClientPacketListener crea el ClientLevel,
 * el LocalPlayer y el MultiPlayerGameMode de P2 sin pisar los de P1. Los efectos globales (pantallas, sonido, chat,
 * particulas...) se silencian con los mixins "guarda" mientras el contexto de P2 esta activo.
 * P2 aun no hace tick ni se dibuja (pasos 3 y 4).
 */
public final class SecondaryPlayers {
    private static volatile Connection connection;
    private static volatile PlayerContext context;

    private SecondaryPlayers() {}

    public static boolean isSecondary(Connection c) {
        return c != null && c == connection;
    }

    public static boolean isActive() {
        return connection != null;
    }

    public static PlayerContext context() {
        return context;
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
        if (connection != null) {
            ConsolePad.LOGGER.info("Jugador 2: ya esta dentro");
            mc.player.displayClientMessage(Component.literal("Jugador 2 ya está dentro."), false);
            return;
        }
        String name = secondaryName(mc);
        GameProfile profile = UUIDUtil.createOfflineProfile(name);
        ConsolePad.LOGGER.info("Jugador 2: conectando como '{}' ({}); Minecraft.player antes = {}",
                name, profile.getId(), mc.player.getName().getString());

        SocketAddress address = server.getConnection().startMemoryChannel();
        Connection c = Connection.connectToLocalServer(address);
        context = new PlayerContext(new SplitLevelRenderer(mc));
        context.hand = new net.minecraft.client.renderer.ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer());
        connection = c; // antes de iniciar el protocolo: ConnectionMixin envuelve el listener de juego
        c.initiateServerboundPlayConnection(address.toString(), 0, new SecondaryHandshakeListener(c, mc));
        c.send(new ServerboundHelloPacket(name, profile.getId()));
    }

    /**
     * P2 sale de la partida: cierra su conexion. El servidor lo quita y guarda su playerdata; la limpieza local
     * (renderer, contexto, estado de entrada) la hace {@link #forget} cuando el cierre llega al listener.
     */
    public static void leave(Minecraft mc) {
        Connection c = connection;
        if (c == null) {
            ConsolePad.LOGGER.info("Jugador 2: no esta dentro");
            return;
        }
        ConsolePad.LOGGER.info("Jugador 2: saliendo");
        c.disconnect(Component.translatable("multiplayer.status.quitting"));
    }

    /**
     * P1 sale del mundo (evento LoggingOut): P2 se cierra y se libera ya, sin esperar a que llegue la desconexion del
     * servidor. Si no, su tick seguiria corriendo con el mundo descargado (config de NeoForge ya sin cargar).
     */
    public static void shutdown() {
        Connection c = connection;
        if (c == null) return;
        ConsolePad.LOGGER.info("Jugador 2: P1 sale del mundo, se libera P2");
        c.disconnect(Component.translatable("multiplayer.status.quitting"));
        forget(c);
    }

    /** Llamado por ConnectionMixin al instalar el listener de juego de la conexion extra. */
    public static PacketListener wrapGameListener(Connection c, ClientPacketListener real) {
        PlayerContext ctx = context;
        Minecraft mc = Minecraft.getInstance();
        InvocationHandler handler = (proxy, method, args) -> {
            String n = method.getName();
            try {
                if (n.equals("onDisconnect")) {
                    DisconnectionDetails details = (DisconnectionDetails) args[0];
                    ConsolePad.LOGGER.info("Jugador 2 desconectado: {}", details.reason().getString());
                    forget(c);
                    return null;
                }
                if (n.equals("tick")) return null; // el tick de P2 es el paso 3
                if (n.startsWith("handle")) {
                    // Hilo de red -> hilo principal, dentro del contexto de P2. El orden de llegada se conserva.
                    mc.execute(() -> ContextSwitcher.run(ctx, () -> handle(real, method, args)));
                    return null;
                }
                // shouldHandleMessage, flow, protocol, isAcceptingMessages...: consultas sin estado global.
                return method.invoke(real, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        };
        return (PacketListener) Proxy.newProxyInstance(SecondaryPlayers.class.getClassLoader(),
                new Class<?>[] {ClientGamePacketListener.class, TickablePacketListener.class}, handler);
    }

    private static void handle(ClientPacketListener real, Method method, Object[] args) {
        try {
            method.invoke(real, args);
        } catch (InvocationTargetException e) {
            ConsolePad.LOGGER.error("[split] error al procesar {} de P2 (suprimido)", method.getName(), e.getCause());
        } catch (IllegalAccessException e) {
            ConsolePad.LOGGER.error("[split] no se pudo invocar {} de P2", method.getName(), e);
        }
    }

    static void forget(Connection c) {
        if (connection != c) return;
        connection = null;
        PlayerContext ctx = context;
        context = null;
        SecondaryTick.reset();
        SplitView.width = 0;
        SplitView.height = 0;
        if (ctx != null) {
            Runnable release = () -> {
                try {
                    if (ctx.renderer instanceof SplitLevelRenderer r) r.release();
                    else ctx.renderer.close();
                } catch (RuntimeException e) {
                    ConsolePad.LOGGER.warn("[split] no se pudo cerrar el renderer latente de P2: {}", e.toString());
                }
                ctx.player = null;
                ctx.level = null;
                ctx.gameMode = null;
                ctx.cameraEntity = null;
                ctx.hitResult = null;
                ctx.crosshairPickEntity = null;
                ConsolePad.LOGGER.info("[split] P2 liberado (renderer, nivel y jugador soltados)");
            };
            Minecraft mc = Minecraft.getInstance();
            if (mc.isSameThread()) release.run();
            else mc.execute(release);
        }
    }

    /** Nadie mas hace tick de la conexion extra: procesa sus desconexiones pendientes. */
    public static void tick() {
        Connection c = connection;
        if (c != null) c.tick();
        ContextSwitcher.watchdog(context);
    }
}
