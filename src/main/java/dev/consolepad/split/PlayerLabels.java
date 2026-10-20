package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Nombre visible de cada jugador local ("Jugador N" o el que elija en sus ajustes): se cambia solo el nombre para mostrar (la etiqueta sobre su
 * cabeza, el chat, los avisos) mediante PlayerEvent.NameFormat. El perfil del servidor (nombre y UUID, del que depende el archivo de datos de cada
 * jugador) no se toca. P1 solo recibe la etiqueta mientras haya jugadores extra dentro, o si ha elegido un nombre.
 */
public final class PlayerLabels {
    private PlayerLabels() {}

    /** Colores de etiqueta que se pueden elegir (indice en PadSettings.labelColor): nombre y RGB; el 0 es el color normal del juego. */
    public static final String[] COLOR_NAMES = {"normal", "rojo", "naranja", "amarillo", "verde", "cian", "azul", "morado", "rosa"};
    public static final int[] COLOR_RGB = {0xFFFFFF, 0xFF5555, 0xFFAA33, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x6C8CFF, 0xB070FF, 0xFF77CC};

    private static int colorIndex(int slot) {
        int c = ConsolePad.PADS.slot(slot).settings.labelColor;
        return c < 0 || c >= COLOR_RGB.length ? 0 : c;
    }

    /** Color RGB (sin alfa) de la etiqueta del slot; blanco si no ha elegido ninguno. */
    public static int rgbOf(int slot) {
        return COLOR_RGB[colorIndex(slot)];
    }

    /** Colores de tema de cada jugador cuando no ha elegido color de etiqueta: azul, rojo, verde y amarillo. */
    public static final int[] DEFAULT_THEME = {0x6C8CFF, 0xFF5555, 0x55FF55, 0xFFD84A};

    /** Color de tema del jugador: el de su etiqueta si lo eligio, si no el de su numero. Lo usan el marcador del companero, las lineas entre regiones y el realce de su cursor. */
    public static int themeRgb(int slot) {
        int c = colorIndex(slot);
        return c != 0 ? COLOR_RGB[c] : DEFAULT_THEME[Math.floorMod(slot, DEFAULT_THEME.length)];
    }

    /** Color de realce del cursor y de la casilla seleccionada: el de tema, o blanco si el jugador apago "Colores del jugador". */
    public static int accent(int slot) {
        return ConsolePad.PADS.slot(slot).settings.playerColors ? themeRgb(slot) : 0xFFFFFF;
    }

    /** Nombre visible del slot ya con su color de etiqueta. */
    public static Component styled(int slot, String text) {
        int c = colorIndex(slot);
        net.minecraft.network.chat.MutableComponent out = I18n.lit(text);
        return c == 0 ? out : out.withStyle(net.minecraft.network.chat.Style.EMPTY.withColor(COLOR_RGB[c]));
    }

    /** Slot (0 a 3) del jugador local con ese nombre de perfil, o -1 si no es local. */
    public static int slotOf(Minecraft mc, String profileName) {
        if (mc.getSingleplayerServer() == null || profileName == null) return -1;
        if (profileName.equals(mc.getUser().getName())) return 0;
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            if (profileName.equals(SecondaryPlayers.secondaryName(mc, i))) return i;
        }
        return -1;
    }

    /** Nombre elegido por el jugador del slot, o "Jugador N". */
    public static String labelOf(int slot) {
        String own = ConsolePad.PADS.slot(slot).settings.playerName;
        return own == null || own.isBlank() ? I18n.fmt("Jugador {0}", slot + 1) : own.trim();
    }

    public static void onNameFormat(PlayerEvent.NameFormat e) {
        Minecraft mc = Minecraft.getInstance();
        if (ConsolePad.PADS == null || mc.getUser() == null) return;
        int slot = slotOf(mc, e.getEntity().getGameProfile().getName());
        if (slot < 0) return;
        String own = ConsolePad.PADS.slot(slot).settings.playerName;
        boolean named = (own != null && !own.isBlank()) || colorIndex(slot) != 0;
        if (slot == 0 && !named && !SecondaryPlayers.anyActive()) return;
        e.setDisplayname(styled(slot, labelOf(slot)));
    }

    /** Vuelve a calcular el nombre visible de todos los jugadores locales, en el cliente de cada uno y en el servidor. */
    /** Nombre en la lista de jugadores: el nombre visible mas un indicador de jugador local ("[P2]"). */
    public static void onTabName(PlayerEvent.TabListNameFormat e) {
        Minecraft mc = Minecraft.getInstance();
        if (ConsolePad.PADS == null || mc.getUser() == null) return;
        int slot = slotOf(mc, e.getEntity().getGameProfile().getName());
        if (slot < 0 || !SecondaryPlayers.anyActive()) return;
        e.setDisplayName(styled(slot, labelOf(slot) + " [P" + (slot + 1) + "]"));
    }

    private static volatile boolean pending;

    /** Lo pendiente de refreshAll pedido dentro de un contexto extra (donde Minecraft.level es el de otro jugador); lo llama PadDriver cada fotograma. */
    public static void tick(Minecraft mc) {
        if (pending && !ContextSwitcher.isSecondaryActive()) {
            pending = false;
            refreshAll(mc);
        }
    }

    public static void refreshAll(Minecraft mc) {
        if (ContextSwitcher.isSecondaryActive()) {
            pending = true;
            return;
        }
        if (mc.level != null) for (Player p : mc.level.players()) p.refreshDisplayName();
        for (PlayerContext c : SecondaryPlayers.contexts()) {
            if (c.level != null) for (Player p : c.level.players()) p.refreshDisplayName();
        }
        var server = mc.getSingleplayerServer();
        if (server != null) server.execute(() -> {
            for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
                p.refreshDisplayName();
                p.refreshTabListName();
            }
        });
    }
}
