package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundClientInformationPacket;
import net.minecraft.server.level.ClientInformation;

/**
 * Distancia de chunks por jugador. Cada jugador local (P1 a P4) tiene la suya ({@link PerfLimits#viewDistanceFor}: su eleccion, con la automatica por numero de
 * jugadores como tope). Se aplica en dos sitios: (1) al dibujar su region ({@code OptionsLimitsMixin}, por el contexto activo), y (2) en el servidor, porque
 * cada jugador le manda su distancia en la informacion de cliente ({@link ServerboundClientInformationPacket}) y {@code ChunkMap} le envia solo
 * {@code clamp(distancia pedida, 2, distancia del servidor)} chunks; la del servidor integrado es la mayor de las de los jugadores dentro.
 */
public final class ViewDistances {
    private static final int[] lastSent = {-1, -1, -1, -1};
    /** Ultima distancia calculada por jugador (0 = no esta dentro), para las pruebas. */
    public static final int[] effective = new int[4];

    private ViewDistances() {}

    public static ClientInformation withDistance(ClientInformation in, int distance) {
        return new ClientInformation(in.language(), distance, in.chatVisibility(), in.chatColors(), in.modelCustomisation(), in.mainHand(),
                in.textFilteringEnabled(), in.allowsListing());
    }

    /** Una vez por fotograma, fuera de cualquier contexto extra. */
    public static void frame(Minecraft mc) {
        if (mc.player == null || mc.level == null || mc.options == null || ContextSwitcher.isSecondaryActive()) {
            if (mc.player == null) {
                PerfLimits.serverMax = 0;
                java.util.Arrays.fill(lastSent, -1);
                java.util.Arrays.fill(effective, 0);
            }
            return;
        }
        int requested = mc.options.renderDistance().get();
        int max = 0;
        for (int slot = 0; slot < lastSent.length; slot++) {
            PlayerContext ctx = slot == 0 ? null : SecondaryPlayers.context(slot);
            net.minecraft.client.player.LocalPlayer player = slot == 0 ? mc.player : ctx == null ? null : ctx.player;
            if (player == null || player.connection == null || (slot > 0 && !SecondaryPlayers.isActive(slot))) {
                lastSent[slot] = -1;
                effective[slot] = 0;
                continue;
            }
            int eff = PerfLimits.viewDistanceFor(slot, requested);
            effective[slot] = eff;
            max = Math.max(max, eff);
            if (eff != lastSent[slot]) {
                player.connection.send(new ServerboundClientInformationPacket(withDistance(mc.options.buildPlayerInformation(), eff)));
                lastSent[slot] = eff;
            }
        }
        PerfLimits.serverMax = max;
    }
}
