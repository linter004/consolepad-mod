package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Particulas ambientales de los extra (casilla 49, con P2 dentro): una antorcha junto a P2 (y lejos de P1) suelta llama y humo en el mundo de P2 y ninguna en
 * el de P1; Minecraft solo llamaba a animateTick con el jugador 1, asi que antes no salia nada.
 */
public final class AmbientTest {
    private static int t, flames, smokes, flamesP1;
    private static double minP1;
    private static BlockPos pos;
    private static BlockState before;

    private AmbientTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null) return true;
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(c.player.getUUID());
        if (t == 1) {
            mc.setScreen(null);
            pos = sp.blockPosition().east(3).above(2);
            minP1 = Double.MAX_VALUE;
            ServerLevel level = (ServerLevel) sp.level();
            server.execute(() -> {
                before = level.getBlockState(pos);
                level.setBlock(pos, Blocks.TORCH.defaultBlockState(), 3);
            });
        }
        if (t > 20 && t <= 140) {
            int f = 0, s = 0, f1 = 0;
            for (var part : V1Test.allParticles(mc)) {
                String n = part.getClass().getSimpleName();
                boolean flame = n.equals("FlameParticle"), smoke = n.contains("Smoke");
                if (!flame && !smoke) continue;
                var lv = V1Test.levelOf(part);
                double d = part.getBoundingBox().getCenter().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos));
                if (lv == c.level && d < 2.5) {
                    if (flame) f++;
                    else s++;
                }
                if (lv == mc.level && d < 2.5) f1++;
            }
            flames = Math.max(flames, f);
            smokes = Math.max(smokes, s);
            flamesP1 = Math.max(flamesP1, f1);
            minP1 = Math.min(minP1, mc.player.position().distanceTo(net.minecraft.world.phys.Vec3.atCenterOf(pos))); // P1 puede moverse durante la prueba
        }
        if (t == 150) {
            check("particulas ambientales: la antorcha junto a P2 suelta llama (" + flames + ") y humo (" + smokes + ") en el mundo de P2", flames > 0 && smokes > 0);
            boolean p1Far = minP1 > 24; // con P1 cerca de la antorcha tambien la ve (es el mismo mundo)
            check("particulas ambientales: nada de eso en el mundo de P1 si esta lejos (" + (p1Far ? "lejos" : "cerca, no se mide") + ", " + flamesP1 + ")", !p1Far || flamesP1 == 0);
            ServerLevel level = (ServerLevel) sp.level();
            server.execute(() -> level.setBlock(pos, before, 3));
        }
        if (t == 160) {
            t = 0;
            flames = smokes = flamesP1 = 0;
            minP1 = Double.MAX_VALUE;
            return true;
        }
        return false;
    }
}
