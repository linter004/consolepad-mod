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
        return own == null || own.isBlank() ? "Jugador " + (slot + 1) : own.trim();
    }

    public static void onNameFormat(PlayerEvent.NameFormat e) {
        Minecraft mc = Minecraft.getInstance();
        if (ConsolePad.PADS == null || mc.getUser() == null) return;
        int slot = slotOf(mc, e.getEntity().getGameProfile().getName());
        if (slot < 0) return;
        String own = ConsolePad.PADS.slot(slot).settings.playerName;
        boolean named = own != null && !own.isBlank();
        if (slot == 0 && !named && !SecondaryPlayers.anyActive()) return;
        e.setDisplayname(Component.literal(labelOf(slot)));
    }

    /** Vuelve a calcular el nombre visible de todos los jugadores locales, en el cliente de cada uno y en el servidor. */
    /** Nombre en la lista de jugadores: el nombre visible mas un indicador de jugador local ("[P2]"). */
    public static void onTabName(PlayerEvent.TabListNameFormat e) {
        Minecraft mc = Minecraft.getInstance();
        if (ConsolePad.PADS == null || mc.getUser() == null) return;
        int slot = slotOf(mc, e.getEntity().getGameProfile().getName());
        if (slot < 0 || !SecondaryPlayers.anyActive()) return;
        e.setDisplayName(Component.literal(labelOf(slot) + " [P" + (slot + 1) + "]"));
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
