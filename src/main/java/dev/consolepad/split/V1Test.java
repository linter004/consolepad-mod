package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.client.PadDriver;
import dev.consolepad.mixin.MouseHandlerInvoker;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/**
 * Cierre de la v1 (casilla 49 del autotest): Y en pantallas de contenedor, chat largo sobre el inventario, particulas y animaciones de los extra
 * y cierre del mundo sin errores. Reutiliza el ejecutor de secuencias por ticks de {@link Settings2Test}.
 */
public final class V1Test {
    private static int phase;
    private static int calls;

    private V1Test() {}

    static void check(String name, boolean ok) {
        Settings2Test.check(name, ok);
    }

    /** Lo llama la casilla 49 una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        calls++;
        switch (phase) {
            case 0 -> {
                if (!AutoTest.joinTo(mc, 2)) return false;
                Settings2Test.seq = new ArrayList<>();
                yButtonSetup(mc, 0);
                phase = 1;
            }
            case 1 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    yButtonSetup(mc, 1);
                    phase = 2;
                }
            }
            case 2 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 3;
                }
            }
            case 3 -> { // chat sobre el inventario con 1 jugador
                if (!AutoTest.leaveTo(mc, 1)) return false;
                chatOverContainerSetup(mc, 1);
                phase = 4;
            }
            case 4 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 5;
                }
            }
            case 5 -> { // y con 4
                if (!AutoTest.joinTo(mc, 4)) return false;
                chatOverContainerSetup(mc, 4);
                phase = 6;
            }
            case 6 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 7;
                }
            }
            case 7 -> {
                if (!AutoTest.leaveTo(mc, 2)) return false;
                if (!AutoTest.joinTo(mc, 2)) return false;
                breakSetup(mc, 1); // P2 rompe un bloque: sus particulas, su progreso de rotura, su gesto
                phase = 8;
            }
            case 8 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    breakSetup(mc, 0); // y al reves: P1 rompe y P2 no ve nada suyo
                    phase = 9;
                }
            }
            case 9 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    eatPickupSetup(mc);
                    phase = 10;
                }
            }
            case 10 -> {
                if (Settings2Test.tickSeq()) {
                    Settings2Test.seq = new ArrayList<>();
                    phase = 99;
                }
            }
            default -> {
                ConsolePad.LOGGER.info("[autotest] RESULTADO cierre de la v1 en conjunto -> {}", Settings2Test.allOk ? "OK" : "FALLO");
                phase = 0;
                calls = 0;
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- tarea 1: Y en pantallas de contenedor

    private static GameType yPrevMode;

    private static int hotbarDirt(ServerPlayer sp) {
        int n = 0;
        for (int i = 0; i < 9; i++) if (sp.getInventory().getItem(i).is(Items.DIRT)) n += sp.getInventory().getItem(i).getCount();
        return n;
    }

    /** Lleva el cursor de P1 (el raton real, que ya lo sigue el mando) a un punto de su pantalla en unidades de GUI. */
    private static void p1CursorTo(Minecraft mc, double gx, double gy) {
        double[] gc = PadDriver.guiCursor(mc);
        double nx = gx * gc[2], ny = gy * gc[3];
        long window = mc.getWindow().getWindow();
        GLFW.glfwSetCursorPos(window, nx, ny);
        ((MouseHandlerInvoker) mc.mouseHandler).consolepad$onMove(window, nx, ny);
    }

    private static void yButtonSetup(Minecraft mc, int slot) {
        PadManager pads = ConsolePad.PADS;
        PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(slot == 0 ? mc.player.getUUID() : c.player.getUUID());
        String who = "P" + (slot + 1);
        PadManager.Device dev = pads.slot(slot).device;
        IntConsumer sim = bits -> {
            if (dev != null) dev.state.simDown = bits;
        };
        Supplier<Screen> screen = () -> slot == 0 ? mc.screen : c.screen;
        Runnable open = () -> {
            if (slot == 0) mc.setScreen(new InventoryScreen(mc.player));
            else ContextSwitcher.run(c, () -> mc.setScreen(new InventoryScreen(mc.player)));
        };
        Runnable onSlot20 = () -> {
            if (!(screen.get() instanceof AbstractContainerScreen<?> s)) return;
            var sl = s.getMenu().slots.get(20);
            double gx = s.getGuiLeft() + sl.x + 8, gy = s.getGuiTop() + sl.y + 8;
            if (slot == 0) p1CursorTo(mc, gx, gy);
            else {
                c.cursorX = gx;
                c.cursorY = gy;
            }
        };
        Runnable nowhere = () -> {
            if (slot == 0) p1CursorTo(mc, 1, 1);
            else {
                c.cursorX = 1;
                c.cursorY = 1;
            }
        };
        String[] digest = new String[1];
        ItemStack[] saved = new ItemStack[36];
        Settings2Test.startSeq(
                Settings2Test.step(who + " prepara: supervivencia y 10 de tierra en el hueco 20", 12, () -> {
                    if (slot == 0 && mc.screen != null) mc.setScreen(null);
                    if (Settings2Test.seqTick == 0) {
                        yPrevMode = sp.gameMode.getGameModeForPlayer();
                        for (int i = 0; i < 36; i++) saved[i] = sp.getInventory().getItem(i).copy();
                        server.execute(() -> {
                            sp.setGameMode(GameType.SURVIVAL);
                            for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, ItemStack.EMPTY);
                            sp.getInventory().setItem(20, new ItemStack(Items.DIRT, 10));
                        });
                    }
                }, null),
                Settings2Test.step(who + " abre el inventario", 10, () -> {
                    if (Settings2Test.seqTick == 0) open.run();
                }, () -> screen.get() instanceof InventoryScreen),
                Settings2Test.step(who + " cursor sobre el hueco", 6, onSlot20, null),
                Settings2Test.step(who + " pulsa Y", 4, () -> {
                    onSlot20.run();
                    sim.accept(1 << PadState.Y);
                }, null),
                Settings2Test.step(who + " Y sobre un hueco: mueve el objeto rapido (hueco 20 vacio, 10 de tierra en la barra) y NO cierra el inventario", 12, () -> {
                    onSlot20.run();
                    sim.accept(0);
                }, () -> screen.get() instanceof InventoryScreen && sp.getInventory().getItem(20).isEmpty() && hotbarDirt(sp) == 10),
                Settings2Test.step(who + " cursor fuera de todo hueco", 6, () -> {
                    nowhere.run();
                    if (Settings2Test.seqTick == 0) digest[0] = sp.getInventory().items.toString();
                }, null),
                Settings2Test.step(who + " pulsa Y sin hueco", 4, () -> {
                    nowhere.run();
                    sim.accept(1 << PadState.Y);
                }, null),
                Settings2Test.step(who + " Y sin hueco bajo el cursor no hace nada (inventario igual y pantalla abierta)", 12, () -> {
                    nowhere.run();
                    sim.accept(0);
                }, () -> screen.get() instanceof InventoryScreen && sp.getInventory().items.toString().equals(digest[0]) && hotbarDirt(sp) == 10),
                Settings2Test.step(who + " pulsa B", 4, () -> sim.accept(1 << PadState.B), null),
                Settings2Test.step(who + " B cierra el inventario", 10, () -> {
                    sim.accept(0);
                    if (Settings2Test.seqTick == 9) {
                        server.execute(() -> {
                            sp.setGameMode(yPrevMode);
                            for (int i = 0; i < 36; i++) sp.getInventory().setItem(i, saved[i]);
                        });
                    }
                }, () -> screen.get() == null));
    }

    // ---------------------------------------------------------------- tarea 2: chat largo sobre el inventario

    private static void sayAll(Minecraft mc, String tag) {
        var server = mc.getSingleplayerServer();
        server.execute(() -> {
            for (int i = 0; i < 6; i++) server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(tag + " linea " + i + " de un chat largo"), false);
        });
    }

    private static void forEachPlayer(Minecraft mc, int n, java.util.function.IntConsumer body) {
        for (int slot = 0; slot < n; slot++) body.accept(slot);
    }

    private static int extrasLines(int n) {
        int min = Integer.MAX_VALUE, max = 0;
        for (int slot = 1; slot < n; slot++) {
            PlayerContext c = SecondaryPlayers.context(slot);
            int v = c == null ? -1 : c.chatLinesDrawn;
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return n == 1 ? 0 : (max == 0 ? 0 : min);
    }

    private static void chatOverContainerSetup(Minecraft mc, int n) {
        String who = n + " jugador" + (n > 1 ? "es" : "");
        int[] base = new int[2];
        Settings2Test.startSeq(
                Settings2Test.step(who + ": prepara (sin pantallas) y escribe 6 lineas de chat", 25, () -> {
                    if (Settings2Test.seqTick == 0) {
                        if (mc.screen != null) mc.setScreen(null);
                        forEachPlayer(mc, n, slot -> {
                            PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
                            if (c != null && c.screen != null) ContextSwitcher.run(c, () -> mc.setScreen(null));
                        });
                        sayAll(mc, "A");
                    }
                    if (Settings2Test.seqTick == 12) {
                        base[0] = SplitDiag.p1ChatShown;
                        base[1] = SplitDiag.p1ChatHidden;
                    }
                }, () -> SplitDiag.p1ChatShown > base[0] && SplitDiag.p1ChatHidden == base[1] && (n == 1 || extrasLines(n) > 0)),
                Settings2Test.step(who + ": abre el inventario de todos", 4, () -> {
                    if (Settings2Test.seqTick == 0) {
                        forEachPlayer(mc, n, slot -> {
                            PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
                            if (slot == 0) mc.setScreen(new InventoryScreen(mc.player));
                            else ContextSwitcher.run(c, () -> mc.setScreen(new InventoryScreen(mc.player)));
                        });
                        sayAll(mc, "B");
                    }
                }, null),
                Settings2Test.step(who + ": con un contenedor abierto nadie dibuja el historial", 4, () -> {
                    if (Settings2Test.seqTick == 0) {
                        base[0] = SplitDiag.p1ChatShown;
                        base[1] = SplitDiag.p1ChatHidden;
                    }
                }, null),
                Settings2Test.step(who + ": con un contenedor abierto el chat de cada jugador se oculta (P1 oculto en varios fotogramas, 0 dibujado; extras 0 lineas)", 20, () -> { },
                        () -> SplitDiag.p1ChatShown == base[0] && SplitDiag.p1ChatHidden > base[1] + 5
                                && (n == 1 || (allExtrasHaveScreen(n) && extrasLinesAll(n, 0)))),
                Settings2Test.step(who + ": cierra los inventarios", 4, () -> {
                    if (Settings2Test.seqTick == 0) {
                        forEachPlayer(mc, n, slot -> {
                            PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
                            if (slot == 0) mc.setScreen(null);
                            else ContextSwitcher.run(c, () -> mc.setScreen(null));
                        });
                        sayAll(mc, "C");
                        base[0] = SplitDiag.p1ChatShown;
                    }
                }, null),
                Settings2Test.step(who + ": sin pantallas el chat vuelve en todos", 20, () -> { },
                        () -> SplitDiag.p1ChatShown > base[0] + 5 && (n == 1 || extrasLines(n) > 0)));
    }

    private static boolean allExtrasHaveScreen(int n) {
        for (int slot = 1; slot < n; slot++) {
            PlayerContext c = SecondaryPlayers.context(slot);
            if (c == null || !(c.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>)) return false;
        }
        return true;
    }

    private static boolean extrasLinesAll(int n, int expected) {
        for (int slot = 1; slot < n; slot++) {
            PlayerContext c = SecondaryPlayers.context(slot);
            if (c == null || c.chatLinesDrawn != expected) return false;
        }
        return true;
    }

    // ---------------------------------------------------------------- tarea 3: particulas y animaciones por jugador

    private static java.util.List<net.minecraft.client.particle.Particle> allParticles(Minecraft mc) {
        java.util.List<net.minecraft.client.particle.Particle> out = new java.util.ArrayList<>();
        var map = ((dev.consolepad.mixin.ParticleEngineAccessor) mc.particleEngine).consolepad$getParticles();
        for (var q : map.values()) out.addAll(q);
        return out;
    }

    private static net.minecraft.client.multiplayer.ClientLevel levelOf(net.minecraft.client.particle.Particle p) {
        return ((dev.consolepad.mixin.ParticleAccessor) p).consolepad$getLevel();
    }

    @SuppressWarnings("unchecked")
    private static boolean crackOverlay(net.minecraft.client.renderer.LevelRenderer renderer, int entityId) {
        try {
            var f = net.minecraft.client.renderer.LevelRenderer.class.getDeclaredField("destroyingBlocks");
            f.setAccessible(true);
            return ((it.unimi.dsi.fastutil.ints.Int2ObjectMap<?>) f.get(renderer)).containsKey(entityId);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static int bitOf(int slot, dev.consolepad.pad.PadAction action) {
        for (var e : ConsolePad.PADS.slot(slot).settings.bindings.entrySet()) if (e.getValue() == action) return 1 << e.getKey();
        return 0;
    }

    /** Contadores de lo medido mientras un jugador rompe un bloque. */
    private static final class Mining {
        int ownMax, crossMax, crossBase = -1;
        boolean crack, swingSeenByOther, swingOwn;
        int drawnBase, foreignBase;
    }

    /** El jugador del slot rompe un bloque de tierra a 2 bloques: particulas en su mundo y no en el del otro, progreso de rotura propio y gesto de golpear visto por el otro. */
    private static void breakSetup(Minecraft mc, int slot) {
        int other = slot == 0 ? 1 : 0;
        PlayerContext c = slot == 0 ? null : SecondaryPlayers.context(slot);
        PlayerContext oc = other == 0 ? null : SecondaryPlayers.context(other);
        net.minecraft.client.player.LocalPlayer me = slot == 0 ? mc.player : c.player;
        net.minecraft.client.player.LocalPlayer them = other == 0 ? mc.player : oc.player;
        var myLevel = slot == 0 ? mc.level : c.level;
        var theirLevel = other == 0 ? mc.level : oc.level;
        var renderer = slot == 0 ? mc.levelRenderer : c.renderer;
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(me.getUUID());
        String who = "P" + (slot + 1);
        net.minecraft.core.BlockPos feet = me.blockPosition();
        net.minecraft.core.BlockPos tgt = feet.offset(0, 0, 2);
        net.minecraft.world.phys.Vec3 tc = net.minecraft.world.phys.Vec3.atCenterOf(tgt);
        Mining m = new Mining();
        PadManager.Device padDev = ConsolePad.PADS.slot(slot).device;
        GameType[] prev = new GameType[1];
        Runnable aim = () -> {
            me.setYRot(0f);
            me.setXRot((float) Math.toDegrees(Math.atan2(me.getEyeY() - tc.y, tc.z - me.getZ())));
        };
        Settings2Test.startSeq(
                Settings2Test.step(who + " prepara el bloque de tierra a 2 bloques", 16, () -> {
                    if (Settings2Test.seqTick == 0) {
                        if (slot == 0 && mc.screen != null) mc.setScreen(null);
                        prev[0] = sp.gameMode.getGameModeForPlayer();
                        server.execute(() -> {
                            var lv = sp.serverLevel();
                            sp.setGameMode(GameType.SURVIVAL);
                            for (int dy = 0; dy <= 2; dy++) for (int dz = 1; dz <= 2; dz++) lv.setBlock(feet.offset(0, dy, dz), net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
                            lv.setBlock(tgt, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState(), 3);
                            for (int i = 0; i < 9; i++) sp.getInventory().setItem(i, net.minecraft.world.item.ItemStack.EMPTY);
                        });
                    }
                    aim.run();
                }, null),
                Settings2Test.step(who + " rompe el bloque (mantiene atacar)", 55, () -> {
                    if (Settings2Test.seqTick == 0) {
                        m.drawnBase = SplitDiag.particlesDrawn[slot];
                        m.foreignBase = SplitDiag.particlesForeign[other];
                    }
                    aim.run();
                    if (padDev != null) padDev.state.simDown = bitOf(slot, dev.consolepad.pad.PadAction.ATTACK);
                    int own = 0, cross = 0;
                    for (var part : allParticles(mc)) {
                        if (!part.getClass().getSimpleName().equals("TerrainParticle")) continue; // los de rotura y golpe de bloque (la lluvia y los efectos no cuentan)
                        var lv = levelOf(part);
                        double d = part.getBoundingBox().getCenter().distanceTo(tc);
                        if (lv == myLevel && d < 4.5) own++;
                        if (part.getBoundingBox().getCenter().distanceTo(them.position()) < 3) cross++; // nada junto al otro jugador
                    }
                    m.ownMax = Math.max(m.ownMax, own);
                    if (m.crossBase < 0) m.crossBase = cross; // restos de la prueba anterior
                    m.crossMax = Math.max(m.crossMax, cross);
                    m.crack |= crackOverlay(renderer, me.getId());
                    m.swingOwn |= me.swinging;
                    var seen = theirLevel.getPlayerByUUID(me.getUUID());
                    m.swingSeenByOther |= seen != null && seen.swinging;
                    if (Settings2Test.seqTick == 54) {
                        if (padDev != null) padDev.state.simDown = 0;
                    }
                }, () -> {
                    boolean blockGone = sp.serverLevel().getBlockState(tgt).isAir();
                    check(who + " rompe el bloque en el servidor (bloque roto=" + blockGone + ")", blockGone);
                    double apart = me.position().distanceTo(them.position());
                    check(who + " rompe: " + m.ownMax + " particulas de su mundo cerca del bloque (>= 8) y " + m.crossMax + " junto al otro jugador (restos previos " + m.crossBase + "; estan a "
                            + Math.round(apart) + " bloques)", m.ownMax >= 8 && (m.crossMax <= m.crossBase || apart < 10));
                    check(who + " rompe: su region dibujo " + (SplitDiag.particlesDrawn[slot] - m.drawnBase) + " particulas y la del otro jugador se salto "
                            + (SplitDiag.particlesForeign[other] - m.foreignBase) + " ajenas", SplitDiag.particlesDrawn[slot] > m.drawnBase && SplitDiag.particlesForeign[other] > m.foreignBase);
                    check(who + " rompe: progreso de rotura propio en su dibujante de mundo", m.crack);
                    check(who + " rompe: gesto de golpear propio=" + m.swingOwn + " y visto por el otro jugador=" + m.swingSeenByOther, m.swingOwn && m.swingSeenByOther);
                    server.execute(() -> sp.setGameMode(prev[0]));
                    return true;
                }));
    }

    private static void eatPickupSetup(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        var me = c.player;
        var server = mc.getSingleplayerServer();
        ServerPlayer sp = server.getPlayerList().getPlayer(me.getUUID());
        var seenByP1 = mc.level.getPlayerByUUID(me.getUUID());
        GameType[] prev = new GameType[1];
        int[] eat = new int[3];
        PadManager.Device mc2dev = ConsolePad.PADS.slot(1).device;
        boolean[] flags = new boolean[3];
        Settings2Test.startSeq(
                Settings2Test.step("P2 prepara: una manzana dorada en la mano", 12, () -> {
                    if (Settings2Test.seqTick == 0) {
                        prev[0] = sp.gameMode.getGameModeForPlayer();
                        server.execute(() -> {
                            sp.setGameMode(GameType.SURVIVAL);
                            sp.getInventory().setItem(sp.getInventory().selected, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.GOLDEN_APPLE));
                        });
                    }
                }, null),
                Settings2Test.step("P2 come (mantiene usar) y se agacha", 50, () -> {
                    mc2dev.state.simDown = bitOf(1, dev.consolepad.pad.PadAction.USE) | bitOf(1, dev.consolepad.pad.PadAction.SNEAK_TOGGLE);
                    int item = 0, spell = 0;
                    for (var part : allParticles(mc)) {
                        if (levelOf(part) != c.level) continue;
                        String n = part.getClass().getSimpleName();
                        if (n.contains("BreakingItem")) item++;
                        if (n.contains("Spell")) spell++;
                    }
                    eat[0] = Math.max(eat[0], item);
                    eat[1] = Math.max(eat[1], spell);
                    var seen = mc.level.getPlayerByUUID(me.getUUID());
                    flags[0] |= seen != null && seen.isUsingItem();
                    flags[1] |= seen != null && seen.isCrouching();
                }, () -> {
                    check("P2 come: " + eat[0] + " particulas de comida y " + eat[1] + " de efecto de pocion en su mundo", eat[0] > 0 && eat[1] > 0);
                    check("P2 come y se agacha: P1 lo ve usando el objeto=" + flags[0] + " y agachado=" + flags[1], flags[0] && flags[1]);
                    mc2dev.state.simDown = 0;
                    return true;
                }),
                Settings2Test.step("P2 recoge un objeto del suelo", 30, () -> {
                    if (Settings2Test.seqTick == 0) {
                        server.execute(() -> {
                            var item = new net.minecraft.world.entity.item.ItemEntity(sp.serverLevel(), sp.getX(), sp.getY(), sp.getZ(),
                                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COBBLESTONE, 3), 0, 0, 0);
                            item.setNoPickUpDelay();
                            sp.serverLevel().addFreshEntity(item);
                        });
                    }
                    for (var part : allParticles(mc)) {
                        if (levelOf(part) == c.level && part.getClass().getSimpleName().contains("ItemPickup")) flags[2] = true;
                    }
                }, () -> {
                    check("P2 recoge un objeto: animacion de recogida (ItemPickupParticle) en su mundo=" + flags[2], flags[2]);
                    server.execute(() -> {
                        sp.setGameMode(prev[0]);
                        sp.getInventory().setItem(sp.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
                    });
                    return true;
                }));
    }
}
