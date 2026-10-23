package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.horse.Horse;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Montura de P2 (dentro de la casilla 49, con P2 dentro): P2 monta un caballo ensillado y su HUD dibuja la barra de salto (en lugar de la de experiencia)
 * y los corazones del caballo; mantener el salto la carga y soltarla hace saltar al caballo en el servidor; P1 no ve nada de eso.
 */
public final class RideTest {
    private static int t;
    private static Horse horse;
    private static int jumps0, hearts0, jumpsP1;
    private static double y0, maxDy;

    private RideTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        PadManager.Device d = ConsolePad.PADS.slot(1).device;
        if (c == null || c.player == null || d == null) return true; // sin P2 o sin su mando: nada que medir
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(c.player.getUUID());
        if (t == 1) {
            mc.setScreen(null);
            SecondaryTick.scriptActive = false;
            jumps0 = SplitHud.jumpMeters[1];
            hearts0 = SplitHud.vehicleHearts[1];
            server.execute(() -> {
                ServerLevel level = (ServerLevel) sp.level();
                horse = EntityType.HORSE.create(level);
                horse.moveTo(sp.getX(), sp.getY(), sp.getZ(), sp.getYRot(), 0f);
                horse.setTamed(true);
                horse.equipSaddle(new ItemStack(Items.SADDLE), SoundSource.NEUTRAL);
                level.addFreshEntity(horse);
                sp.startRiding(horse, true);
            });
        }
        if (t == 40) {
            check("montura: P2 va a caballo y su cliente tiene una montura saltadora (montando=" + c.player.isPassenger() + ", salta=" + (c.player.jumpableVehicle() != null) + ")",
                    c.player.isPassenger() && c.player.jumpableVehicle() != null);
            check("montura: el HUD de P2 dibuja la barra de salto (" + (SplitHud.jumpMeters[1] - jumps0) + " veces) y los corazones del caballo (" + (SplitHud.vehicleHearts[1] - hearts0) + ")",
                    SplitHud.jumpMeters[1] > jumps0 && SplitHud.vehicleHearts[1] > hearts0);
            check("montura: P1 no va a caballo y no dibuja nada de eso (" + (mc.player.getVehicle() != null) + ")", mc.player.getVehicle() == null);
            jumpsP1 = SplitHud.jumpMeters[0];
            y0 = horse.getY();
            d.state.simDown = 1 << PadState.A; // mantener el salto: carga la barra
        }
        if (t == 56) {
            float scale = c.player.getJumpRidingScale();
            check("montura: mantener el salto carga la barra de P2 (" + scale + ")", scale > 0.2f);
            d.state.simDown = 0; // soltar: salta
        }
        if (t > 56 && t < 90) maxDy = Math.max(maxDy, horse.getY() - y0);
        if (t == 90) {
            check("montura: soltar el salto hace saltar al caballo en el servidor (subio " + String.format(java.util.Locale.ROOT, "%.2f", maxDy) + " bloques)", maxDy > 0.2); // 0.2: el salto depende del tiempo que se mantiene el boton y de los fotogramas (daba 0.32 a 0.40); sin salto da 0
            server.execute(() -> {
                sp.stopRiding();
                horse.discard();
            });
        }
        if (t == 100) {
            check("montura: al bajar, P2 vuelve a tener barra de experiencia (montando=" + c.player.isPassenger() + ")", !c.player.isPassenger() && c.player.jumpableVehicle() == null);
            t = 0;
            return true;
        }
        return false;
    }
}
