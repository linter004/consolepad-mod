package dev.consolepad.split;

import dev.consolepad.pad.PadAction;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Copiar bloque en P2 (casilla 49, con P2 dentro): mira al suelo, el bloque de debajo es oro, y la cruceta abajo (copiar bloque) le pone un bloque de oro en la
 * mano (en creativo, como en vanilla con la rueda del raton). Guarda y restaura las 36 casillas, el modo de juego, el bloque y la mirada.
 */
public final class PickBlockTest {
    private static int t;
    private static BlockPos pos;
    private static BlockState before;
    private static GameType prevMode;
    private static float prevPitch;
    private static int picks0;
    private static ItemStack[] saved;

    private PickBlockTest() {}

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
            SecondaryTick.scriptActive = true;
            SecondaryTick.scriptForward = 0f;
            SecondaryTick.scriptActions = java.util.EnumSet.noneOf(PadAction.class);
            prevPitch = c.player.getXRot();
            picks0 = SecondaryTick.dbgPickBlock;
            server.execute(() -> {
                prevMode = sp.gameMode.getGameModeForPlayer();
                saved = new ItemStack[36];
                for (int i = 0; i < 36; i++) saved[i] = sp.getInventory().getItem(i).copy();
                sp.setGameMode(GameType.CREATIVE);
                pos = sp.blockPosition().below();
                before = sp.level().getBlockState(pos);
                sp.level().setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), 3);
            });
        }
        if (t >= 2 && t < 12) c.player.setXRot(90f); // mira al suelo
        if (t == 14) SecondaryTick.scriptActions = java.util.EnumSet.of(PadAction.PICK_BLOCK);
        if (t == 17) SecondaryTick.scriptActions = java.util.EnumSet.noneOf(PadAction.class);
        if (t == 30) {
            boolean gold = c.player.getMainHandItem().is(Items.GOLD_BLOCK);
            int picks = SecondaryTick.dbgPickBlock - picks0;
            check("copiar bloque con el mando de P2 (cruceta abajo): el bloque de oro bajo sus pies esta en su mano (" + c.player.getMainHandItem() + ", ejecutado " + picks + " veces, modo "
                    + sp.gameMode.getGameModeForPlayer() + ")", gold && picks >= 1);
            c.player.setXRot(prevPitch);
            server.execute(() -> {
                for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, saved[i]);
                sp.setGameMode(prevMode);
                sp.level().setBlock(pos, before, 3);
            });
        }
        if (t == 45) {
            SecondaryTick.scriptActive = false;
            t = 0;
            return true;
        }
        return false;
    }
}
