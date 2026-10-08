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
import java.util.ArrayList;
import java.util.List;

/**
 * Jugadores locales extra (P2, P3 y P4, en los slots de mando 1, 2 y 3): cada uno es una conexion extra en memoria al
 * servidor integrado. Los paquetes de juego de cada uno se procesan en el hilo principal dentro de su {@link PlayerContext}
 * ({@link ContextSwitcher}), de modo que ClientPacketListener crea su ClientLevel, su LocalPlayer y su MultiPlayerGameMode sin
 * pisar los de P1. Los efectos globales (pantallas, sonido, chat, particulas...) se silencian con los mixins "guarda"
 * mientras un contexto extra esta activo.
 */
public final class SecondaryPlayers {
    /** Jugadores extra posibles: slots 1..3. */
    public static final int MAX_EXTRA = 3;

    private static final class Entry {
        volatile Connection connection;
        volatile PlayerContext context;
    }

    private static final Entry[] ENTRIES = new Entry[MAX_EXTRA + 1];

    static {
        for (int i = 1; i <= MAX_EXTRA; i++) ENTRIES[i] = new Entry();
    }

    private SecondaryPlayers() {}

    private static Entry entryOf(Connection c) {
        if (c == null) return null;
        for (int i = 1; i <= MAX_EXTRA; i++) {
            if (ENTRIES[i].connection == c) return ENTRIES[i];
        }
        return null;
    }

    public static boolean isSecondary(Connection c) {
        return entryOf(c) != null;
    }

    /** P2 (slot 1) esta dentro. */
    public static boolean isActive() {
        return isActive(1);
    }

    public static boolean isActive(int slot) {
        return slot >= 1 && slot <= MAX_EXTRA && ENTRIES[slot].connection != null;
    }

    /** Hay algun jugador extra dentro (con alguno, el mundo no se pausa nunca). */
    public static boolean anyActive() {
        for (int i = 1; i <= MAX_EXTRA; i++) {
            if (ENTRIES[i].connection != null) return true;
        }
        return false;
    }

    /** Contexto de P2. */
    public static PlayerContext context() {
        return context(1);
    }

    public static PlayerContext context(int slot) {
        return slot >= 1 && slot <= MAX_EXTRA ? ENTRIES[slot].context : null;
    }

    /** Contextos existentes, en orden de slot. */
    public static List<PlayerContext> contexts() {
        List<PlayerContext> out = new ArrayList<>(MAX_EXTRA);
        for (int i = 1; i <= MAX_EXTRA; i++) {
            PlayerContext c = ENTRIES[i].context;
            if (c != null) out.add(c);
        }
        return out;
    }

    public static String secondaryName(Minecraft mc) {
        return secondaryName(mc, 1);
    }

    public static String secondaryName(Minecraft mc, int slot) {
        String base = mc.getUser().getName();
        if (base.length() > 14) base = base.substring(0, 14);
        return base + "_" + (slot + 1);
    }

    public static boolean isSecondaryName(Minecraft mc, String name) {
        for (int i = 1; i <= MAX_EXTRA; i++) {
            if (secondaryName(mc, i).equals(name)) return true;
        }
        return false;
    }

    /** Abre la conexion de P2. Debe llamarse desde el hilo principal con un mundo local cargado. */
    public static void join(Minecraft mc) {
        join(mc, 1);
    }

    /** Abre la conexion del jugador extra del slot dado (1 = P2, 2 = P3, 3 = P4). */
    public static void join(Minecraft mc, int slot) {
        IntegratedServer server = mc.getSingleplayerServer();
        if (slot < 1 || slot > MAX_EXTRA) return;
        if (server == null || mc.level == null || mc.player == null) {
            ConsolePad.LOGGER.info("Jugador {}: hace falta un mundo local cargado", slot + 1);
            return;
        }
        Entry e = ENTRIES[slot];
        if (e.connection != null) {
            ConsolePad.LOGGER.info("Jugador {}: ya esta dentro", slot + 1);
            mc.player.displayClientMessage(Component.literal("Jugador " + (slot + 1) + " ya está dentro."), false);
            return;
        }
        String name = secondaryName(mc, slot);
        GameProfile profile = UUIDUtil.createOfflineProfile(name);
        ConsolePad.LOGGER.info("Jugador {}: conectando como '{}' ({}); Minecraft.player antes = {}",
                slot + 1, name, profile.getId(), mc.player.getName().getString());

        SocketAddress address = server.getConnection().startMemoryChannel();
        Connection c = Connection.connectToLocalServer(address);
        PlayerContext ctx = new PlayerContext(new SplitLevelRenderer(mc), slot);
        ctx.hand = new net.minecraft.client.renderer.ItemInHandRenderer(mc, mc.getEntityRenderDispatcher(), mc.getItemRenderer());
        // El servidor avisa de que entra a todos menos al que entra: su aviso de entrada es local.
        ctx.notices.addLast(new PlayerContext.Notice(Component.translatable("multiplayer.player.joined", name).withStyle(net.minecraft.ChatFormatting.YELLOW),
                mc.level.getGameTime()));
        e.context = ctx;
        e.connection = c; // antes de iniciar el protocolo: ConnectionMixin envuelve el listener de juego
        PlayerLabels.refreshAll(mc); // P1 pasa a llevar su etiqueta mientras haya extras
        c.initiateServerboundPlayConnection(address.toString(), 0, new SecondaryHandshakeListener(c, mc));
        c.send(new ServerboundHelloPacket(name, profile.getId()));
    }

    /**
     * El jugador extra sale de la partida: cierra su conexion. El servidor lo quita y guarda su playerdata; la limpieza local
     * (renderer, contexto, estado de entrada) la hace {@link #forget} cuando el cierre llega al listener.
     */
    public static void leave(Minecraft mc) {
        leave(mc, 1);
    }

    public static void leave(Minecraft mc, int slot) {
        Connection c = slot >= 1 && slot <= MAX_EXTRA ? ENTRIES[slot].connection : null;
        if (c == null) {
            ConsolePad.LOGGER.info("Jugador {}: no esta dentro", slot + 1);
            return;
        }
        ConsolePad.LOGGER.info("Jugador {}: saliendo", slot + 1);
        c.disconnect(Component.translatable("multiplayer.status.quitting"));
    }

    /**
     * P1 sale del mundo (evento LoggingOut): los extra se cierran y se liberan ya, sin esperar a que llegue la desconexion
     * del servidor. Si no, su tick seguiria corriendo con el mundo descargado (config de NeoForge ya sin cargar).
     */
    public static void shutdown() {
        for (int i = 1; i <= MAX_EXTRA; i++) {
            Connection c = ENTRIES[i].connection;
            if (c == null) continue;
            ConsolePad.LOGGER.info("Jugador {}: P1 sale del mundo, se libera", i + 1);
            c.disconnect(Component.translatable("multiplayer.status.quitting"));
            forget(c);
        }
    }

    /** Llamado por ConnectionMixin al instalar el listener de juego de una conexion extra. */
    public static PacketListener wrapGameListener(Connection c, ClientPacketListener real) {
        Entry entry = entryOf(c);
        PlayerContext ctx = entry == null ? null : entry.context;
        int slot = ctx == null ? 0 : ctx.slotIndex;
        Minecraft mc = Minecraft.getInstance();
        InvocationHandler handler = (proxy, method, args) -> {
            String n = method.getName();
            try {
                if (n.equals("onDisconnect")) {
                    DisconnectionDetails details = (DisconnectionDetails) args[0];
                    ConsolePad.LOGGER.info("Jugador {} desconectado: {}", slot + 1, details.reason().getString());
                    forget(c);
                    return null;
                }
                if (n.equals("tick")) return null; // el tick del jugador extra lo hace SecondaryTick
                // Cualquier manejador de paquete (un unico argumento Packet), no solo los llamados handle*: en 1.21.1 hay cuatro con otro
                // nombre (setTitleText, setSubtitleText, setTitlesAnimation, setActionBarText) y se procesarian con los datos de P1.
                if (args != null && args.length == 1 && args[0] instanceof net.minecraft.network.protocol.Packet<?> && !n.equals("shouldHandleMessage")) {
                    // Hilo de red -> hilo principal, dentro del contexto del jugador. El orden de llegada se conserva.
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
            ConsolePad.LOGGER.error("[split] error al procesar {} de un jugador extra (suprimido)", method.getName(), e.getCause());
        } catch (IllegalAccessException e) {
            ConsolePad.LOGGER.error("[split] no se pudo invocar {} de un jugador extra", method.getName(), e);
        }
    }

    static void forget(Connection c) {
        Entry entry = entryOf(c);
        if (entry == null) return;
        int slot = entry.context == null ? 0 : entry.context.slotIndex;
        entry.connection = null;
        PlayerContext ctx = entry.context;
        entry.context = null;
        if (slot == 1) SecondaryTick.reset();
        PlayerLabels.refreshAll(Minecraft.getInstance()); // sin extras P1 recupera su nombre
        SplitView.width = 0;
        SplitView.height = 0;
        if (ctx != null) {
            Runnable release = () -> {
                try {
                    if (ctx.renderer instanceof SplitLevelRenderer r) r.release();
                    else ctx.renderer.close();
                } catch (RuntimeException e) {
                    ConsolePad.LOGGER.warn("[split] no se pudo cerrar el renderer latente de un jugador extra: {}", e.toString());
                }
                ctx.player = null;
                ctx.level = null;
                ctx.gameMode = null;
                ctx.cameraEntity = null;
                ctx.hitResult = null;
                ctx.crosshairPickEntity = null;
                ctx.screen = null;
                ConsolePad.LOGGER.info("[split] {} liberado (renderer, nivel y jugador soltados)", ctx.label());
            };
            Minecraft mc = Minecraft.getInstance();
            if (mc.isSameThread()) release.run();
            else mc.execute(release);
        }
    }

    /** Nadie mas hace tick de las conexiones extra: procesa sus desconexiones pendientes. */
    public static void tick() {
        for (int i = 1; i <= MAX_EXTRA; i++) {
            Connection c = ENTRIES[i].connection;
            if (c != null) c.tick();
            ContextSwitcher.watchdog(ENTRIES[i].context);
        }
    }
}
