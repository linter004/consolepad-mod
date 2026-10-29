package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Cooperativo (dentro de la casilla 49, con P2 dentro): la rueda tiene una segunda pagina (LB/RB), un gesto llega al chat de P1, una marca de lugar de P2
 * la ve P1 y se quita, dar el objeto de la mano pasa la pila a P1, sin dano entre jugadores salvo que se active, aviso de muerte, aviso de companero
 * lejos y el perfil sencillo. Guarda y devuelve los inventarios, los modos de juego, las posiciones y los ajustes que toca.
 */
public final class CoopTest {
    private static int t;
    private static ItemStack[] inv1, inv2;
    private static GameType mode1, mode2;
    private static boolean ff0;
    private static net.minecraft.world.phys.Vec3 pos2;
    private static float hp0;
    private static int dirt1;
    private static boolean wheelPageOk;

    private CoopTest() {}

    private static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    private static ItemStack[] save(ServerPlayer p) {
        ItemStack[] out = new ItemStack[p.getInventory().getContainerSize()];
        for (int i = 0; i < out.length; i++) out[i] = p.getInventory().getItem(i).copy();
        return out;
    }

    private static void restore(ServerPlayer p, ItemStack[] items) {
        for (int i = 0; i < items.length; i++) p.getInventory().setItem(i, items[i].copy());
    }

    private static int count(ServerPlayer p, net.minecraft.world.item.Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) if (p.getInventory().getItem(i).is(item)) n += p.getInventory().getItem(i).getCount();
        return n;
    }

    private static void moveP2(ServerPlayer sp1, ServerPlayer sp2, double dx) {
        ServerLevel level = (ServerLevel) sp1.level();
        int x = (int) Math.floor(sp1.getX() + dx), z = (int) Math.floor(sp1.getZ());
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        sp2.teleportTo(level, x + 0.5, y, z + 0.5, 0f, 0f);
        sp2.fallDistance = 0f;
    }

    /** Pulsa el boton de la rueda, pasa a la pagina pedida con RB, elige la entrada i del anillo de 8 y suelta. start = tick de inicio. */
    private static void wheel(int start, int page, int index, int wheelBtn) {
        var d = ConsolePad.PADS.slot(1).device;
        if (d == null) return;
        int tt = t - start;
        double a = index * Math.PI * 2 / 3; // la pagina de la rueda tiene 3 entradas
        if (tt == 0) d.state.simDown = 1 << wheelBtn;
        if (tt == 4 && page == 1) d.state.simDown = (1 << wheelBtn) | (1 << PadState.RB);
        if (tt == 7) d.state.simDown = 1 << wheelBtn;
        if (tt == 9 && page == 1) wheelPageOk = QuickWheel.page(1) == 1;
        if (tt == 10) {
            d.state.simRx = (float) Math.sin(a);
            d.state.simRy = (float) -Math.cos(a);
        }
        if (tt == 16) d.state.simDown = 0;
        if (tt == 18) {
            d.state.simRx = 0f;
            d.state.simRy = 0f;
        }
    }

    /** Una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        t++;
        PlayerContext c = SecondaryPlayers.context(1);
        if (c == null || c.player == null || mc.player == null) return true;
        var server = mc.getSingleplayerServer();
        ServerPlayer sp2 = server.getPlayerList().getPlayer(c.player.getUUID());
        ServerPlayer sp1 = server.getPlayerList().getPlayer(mc.player.getUUID());
        PadSettings cfg2 = ConsolePad.PADS.slot(1).settings;
        int wb = QuickWheel.buttonOf(cfg2);
        if (t == 1) {
            mc.setScreen(null);
            SecondaryTick.scriptActive = false;
            QuickWheel.setEntriesForTest(null); // las entradas normales: dos paginas
            inv1 = save(sp1);
            inv2 = save(sp2);
            mode1 = sp1.gameMode.getGameModeForPlayer();
            mode2 = sp2.gameMode.getGameModeForPlayer();
            pos2 = sp2.position();
            ff0 = SplitSettings.friendlyFire();
            Coop.clearWaypoints();
            java.util.Arrays.fill(Coop.marksSet, 0);
            java.util.Arrays.fill(Coop.gives, 0);
            server.execute(() -> {
                sp1.setGameMode(GameType.SURVIVAL);
                sp2.setGameMode(GameType.SURVIVAL);
            });
            check("cooperativo: la rueda tiene dos paginas (entradas propias y marcas)", QuickWheel.pageCount() == 2 && wb >= 0);
        }
        // ---- marca de lugar: P2 a 12 bloques de P1, la marca la ve P1 y se quita
        if (t == 40) server.execute(() -> moveP2(sp1, sp2, 12));
        wheel(50, 1, 0, wb);
        if (t == 76) {
            check("cooperativo: LB/RB pasan a la pagina de marcas (pagina " + QuickWheel.page(1) + " con la rueda abierta)", wheelPageOk);
            boolean seen = false;
            double dist = 0;
            for (Companions.Mark m : Companions.marks(mc, 0)) {
                if (m.waypoint()) {
                    seen = true;
                    dist = m.distance();
                }
            }
            check("cooperativo: la marca de lugar de P2 la ve P1 en su indicador (marcas " + Coop.marksSet[1] + ", vista=" + seen + ", a " + (int) dist + " m)",
                    Coop.marksSet[1] == 1 && Coop.waypoints().size() == 1 && seen && dist > 4);
        }
        wheel(80, 1, 1, wb);
        if (t == 106) {
            boolean gone = true;
            for (Companions.Mark m : Companions.marks(mc, 0)) if (m.waypoint()) gone = false;
            check("cooperativo: quitar la marca la borra para todos (marcas activas " + Coop.waypoints().size() + ")", Coop.waypoints().isEmpty() && gone);
        }
        // ---- dar el objeto de la mano a P1
        if (t == 108) {
            server.execute(() -> {
                moveP2(sp1, sp2, 3);
                sp2.getInventory().selected = 0;
                sp2.getInventory().setItem(0, new ItemStack(Items.DIRT, 5));
            });
            dirt1 = count(sp1, Items.DIRT);
        }
        wheel(112, 1, 2, wb);
        if (t == 140) {
            int now = count(sp1, Items.DIRT);
            check("cooperativo: dar objeto pasa la pila de la mano de P2 al inventario de P1 (tierra de P1 " + dirt1 + " -> " + now + ", en la mano de P2: "
                    + sp2.getInventory().getItem(0).getCount() + ", veces " + Coop.gives[1] + ")", now - dirt1 == 5 && sp2.getInventory().getItem(0).isEmpty() && Coop.gives[1] == 1);
        }
        // ---- sin dano entre jugadores
        if (t == 142) {
            SplitSettings.setFriendlyFire(false);
            hp0 = sp1.getHealth();
            int b0 = Coop.blockedHits;
            server.execute(() -> {
                sp1.invulnerableTime = 0;
                sp1.hurt(sp2.damageSources().playerAttack(sp2), 4f);
            });
            ffBlocked0 = b0;
        }
        if (t == 146) {
            check("cooperativo: sin dano entre jugadores, un golpe de P2 no le quita vida a P1 (" + hp0 + " -> " + sp1.getHealth() + ", golpes parados " + (Coop.blockedHits - ffBlocked0) + ")",
                    sp1.getHealth() >= hp0 && Coop.blockedHits > ffBlocked0);
            SplitSettings.setFriendlyFire(true);
            hp0 = sp1.getHealth();
            server.execute(() -> {
                sp1.invulnerableTime = 0;
                sp1.hurt(sp2.damageSources().playerAttack(sp2), 4f);
            });
        }
        if (t == 150) {
            check("cooperativo: con el dano entre jugadores activado el golpe si hace dano (" + hp0 + " -> " + sp1.getHealth() + ")", sp1.getHealth() < hp0);
            SplitSettings.setFriendlyFire(ff0);
            server.execute(() -> sp1.setHealth(sp1.getMaxHealth()));
        }
        // ---- aviso de muerte
        if (t == 152) {
            deaths0 = Coop.deathNotices;
            server.execute(() -> Coop.onDeath(sp2));
        }
        if (t == 158) {
            boolean said = false;
            for (String l : SplitNotices.p1Log) if (l.contains("murió en")) said = true;
            check("cooperativo: el aviso de muerte de P2 llega al chat de P1 (avisos +" + (Coop.deathNotices - deaths0) + ", visto=" + said + ")", Coop.deathNotices > deaths0 && said);
        }
        // ---- aviso de companero lejos
        if (t == 160) {
            far0 = Coop.farWarnings;
            Coop.farOverride = 20;
            server.execute(() -> moveP2(sp1, sp2, 45));
        }
        if (t == 245) {
            check("cooperativo: un companero a mas de la distancia de aviso genera un aviso (avisos +" + (Coop.farWarnings - far0) + ")", Coop.farWarnings > far0);
            Coop.farOverride = -1;
        }
        // ---- perfil sencillo
        if (t == 246) {
            PadSettings tmp = new PadSettings();
            tmp.bigHints = false;
            tmp.snapNav = false;
            tmp.vibration = 1f;
            PlayerSettingsScreen.applyEasyProfile(tmp);
            check("cooperativo: el perfil sencillo pone ayudas grandes, cursor por casillas y vibracion suave", tmp.bigHints && tmp.snapNav && tmp.vibration < 0.5f && tmp.showCompanionDistance);
            check("cooperativo: las nuevas opciones del ajuste estan en categorias (Cooperativo y perfil sencillo)", categoriesHave("friendlyFire") && categoriesHave("easyProfile"));
        }
        if (t == 252) {
            ConsolePad.PADS.slot(0).settings.showMinimap = true;
            Minimap.drawn[0] = 0;
        }
        if (t == 268) {
            check("cooperativo: el minimapa de P1 se dibuja con terreno y el companero (fotogramas " + Minimap.drawn[0] + ", celdas con color " + Minimap.lastColored[0] + ", puntos " + Minimap.lastDots[0] + ")",
                    Minimap.drawn[0] > 0 && Minimap.lastColored[0] > 300 && Minimap.lastDots[0] >= 1);
            ConsolePad.PADS.slot(0).settings.showMinimap = false;
        }
        if (t == 270) {
            server.execute(() -> {
                restore(sp1, inv1);
                restore(sp2, inv2);
                sp1.setGameMode(mode1);
                sp2.setGameMode(mode2);
                sp2.teleportTo((ServerLevel) sp2.level(), pos2.x, pos2.y, pos2.z, 0f, 0f);
                sp2.fallDistance = 0f;
            });
            Coop.clearWaypoints();
            QuickWheel.setEntriesForTest(null);
            t = 0;
            return true;
        }
        return false;
    }

    private static int ffBlocked0, deaths0, far0;

    private static boolean categoriesHave(String id) {
        var screen = new PlayerSettingsScreen(0, null);
        for (String s : screen.labelsForTest()) { /* fuerza la construccion de las opciones */ }
        return screen.hasItemForTest(id);
    }
}
