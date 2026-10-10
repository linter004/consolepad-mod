package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Capturas limpias para la documentacion (CONSOLEPAD_SHOTS=<carpeta de salida>): crea un mundo temporal propio, hace 4 capturas a 1920x1080
 * (2 jugadores, 4 jugadores, crafteo estilo consola, ajustes del jugador) y borra el mundo al terminar. No toca ningun mundo guardado.
 */
public final class ShotsTest {
    static final String OUT = System.getenv("CONSOLEPAD_SHOTS");
    public static final boolean ENABLED = OUT != null && !OUT.isBlank();
    private static final String WORLD = "cp-shots-temp-" + System.currentTimeMillis();
    private static int phase, ticks;
    private static String pendingName;
    private static Difficulty prevDifficulty;
    private static int prevGuiScale = -1;
    private static boolean prevPause;
    private static boolean created;
    private static String levelId;

    private ShotsTest() {}

    /** Al final de cada fotograma: si hay una captura pendiente, la guarda en la carpeta de salida con el fotograma ya dibujado. */
    public static void frame() {
        if (pendingName == null) return;
        String name = pendingName;
        pendingName = null;
        Minecraft mc = Minecraft.getInstance();
        var img = Screenshot.takeScreenshot(mc.getMainRenderTarget());
        try {
            Path dir = Path.of(OUT);
            Files.createDirectories(dir);
            img.writeToFile(dir.resolve(name));
            ConsolePad.LOGGER.info("[shots] captura {} ({}x{})", name, img.getWidth(), img.getHeight());
        } catch (Exception e) {
            ConsolePad.LOGGER.error("[shots] no se pudo guardar {}: {}", name, e.toString());
        } finally {
            img.close();
        }
    }

    private static void go(int p) {
        phase = p;
        ticks = 0;
    }

    private static ServerPlayer server(Minecraft mc, int slot) {
        var srv = mc.getSingleplayerServer();
        if (srv == null) return null;
        if (slot == 0) return mc.player == null ? null : srv.getPlayerList().getPlayer(mc.player.getUUID());
        PlayerContext c = SecondaryPlayers.context(slot);
        return c == null || c.player == null ? null : srv.getPlayerList().getPlayer(c.player.getUUID());
    }

    /** Coloca a los jugadores en un corro alrededor del punto de P1, mirando cada uno a un lado. */
    private static void spread(Minecraft mc, int n) {
        var srv = mc.getSingleplayerServer();
        ServerPlayer p1 = server(mc, 0);
        if (p1 == null) return;
        int bx = (int) Math.floor(p1.getX()), bz = (int) Math.floor(p1.getZ());
        int[][] off = {{0, 0}, {7, 0}, {0, 7}, {-7, 0}};
        float[] yaw = {-90f, 90f, 180f, 0f};
        srv.execute(() -> {
            for (int s = 0; s < n; s++) {
                ServerPlayer sp = server(mc, s);
                if (sp == null) continue;
                ServerLevel level = (ServerLevel) sp.level();
                int x = bx + off[s][0], z = bz + off[s][1];
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                sp.teleportTo(level, x + 0.5, y, z + 0.5, yaw[s], 8f);
            }
        });
    }

    private static void shot(String name) {
        pendingName = name;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (phase >= 1 && phase < 90 && mc.level != null) mc.options.pauseOnLostFocus = false;
        switch (phase) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen && ticks > 60) {
                    ConsolePad.LOGGER.info("[shots] creando el mundo temporal");
                    created = true;
                    levelId = WORLD;
                    var settings = new LevelSettings(WORLD, GameType.SURVIVAL, false, Difficulty.PEACEFUL, true, new GameRules(), WorldDataConfiguration.DEFAULT);
                    mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261010L, true, false), WorldPresets::createNormalWorldDimensions, mc.screen);
                    go(1);
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null && ticks > 200) {
                    ConsolePad.LOGGER.info("[shots] mundo cargado");
                    var srv = mc.getSingleplayerServer();
                    prevDifficulty = srv.getWorldData().getDifficulty();
                    prevGuiScale = mc.options.guiScale().get();
                    prevPause = mc.options.pauseOnLostFocus;
                    mc.options.pauseOnLostFocus = false;
                    mc.options.guiScale().set(0);
                    mc.options.hideGui = false;
                    if (mc.getDebugOverlay().showDebugScreen()) mc.getDebugOverlay().toggleOverlay();
                    srv.execute(() -> {
                        srv.setDifficulty(Difficulty.PEACEFUL, true);
                        srv.overworld().setDayTime(1000L);
                        srv.getGameRules().getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, srv);
                        srv.overworld().setWeatherParameters(24000, 0, false, false);
                    });
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1920, 1080);
                    go(2);
                }
            }
            case 2 -> {
                if (ticks > 80) {
                    var w = mc.getWindow();
                    ConsolePad.LOGGER.info("[shots] marco {}x{}", w.getWidth(), w.getHeight());
                    go(3);
                }
            }
            case 3 -> { // 2 jugadores
                if (!AutoTest.joinTo(mc, 2)) return;
                if (ticks == 1) return;
                spread(mc, 2);
                go(4);
            }
            case 4 -> {
                if (ticks == 200) shot("split-2-players.png");
                if (ticks == 210) go(5);
            }
            case 5 -> { // 4 jugadores
                if (!AutoTest.joinTo(mc, 4)) return;
                spread(mc, 4);
                go(6);
            }
            case 6 -> {
                if (ticks == 250) shot("split-4-players.png");
                if (ticks == 260) go(7);
            }
            case 7 -> { // vuelta a un jugador y crafteo estilo consola
                if (!AutoTest.leaveTo(mc, 1)) return;
                if (ticks < 80) return;
                var srv = mc.getSingleplayerServer();
                ServerPlayer sp = server(mc, 0);
                BlockPos pos = sp.blockPosition().relative(sp.getDirection(), 2);
                srv.execute(() -> {
                    ServerLevel level = (ServerLevel) sp.level();
                    sp.setGameMode(GameType.SURVIVAL);
                    srv.overworld().setDayTime(1000L);
                    sp.getInventory().setItem(20, new ItemStack(Items.OAK_PLANKS, 24));
                    sp.getInventory().setItem(21, new ItemStack(Items.COBBLESTONE, 16));
                    sp.getInventory().setItem(22, new ItemStack(Items.STICK, 12));
                    sp.getInventory().setItem(23, new ItemStack(Items.IRON_INGOT, 9));
                    sp.awardRecipes(srv.getRecipeManager().getRecipes());
                    level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
                    sp.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv,
                            net.minecraft.world.inventory.ContainerLevelAccess.create(level, pos)), net.minecraft.network.chat.Component.literal("Crafting")));
                });
                go(8);
            }
            case 8 -> {
                if (ticks > 20) mc.getToasts().clear(); // sin avisos de recetas ni logros encima
                if (ticks == 40 && mc.screen != null && !ConsoleCraft.bookVisible(mc.screen)) ConsoleCraft.toggleBook(mc.screen);
                if (ticks == 90) {
                    ConsolePad.LOGGER.info("[shots] pantalla de crafteo: {}, libro visible={}", mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName(),
                            mc.screen != null && ConsoleCraft.bookVisible(mc.screen));
                    shot("console-crafting.png");
                }
                if (ticks == 100) {
                    mc.setScreen(null);
                    go(9);
                }
            }
            case 9 -> { // ajustes del jugador
                if (ticks == 20) mc.setScreen(new PlayerSettingsScreen(0, null));
                if (ticks == 70) shot("player-settings.png");
                if (ticks == 80) {
                    mc.setScreen(null);
                    go(90);
                }
            }
            case 90 -> { // salir, restaurar opciones y borrar el mundo temporal
                if (ticks == 5) {
                    var srv = mc.getSingleplayerServer();
                    if (prevDifficulty != null) srv.execute(() -> srv.setDifficulty(prevDifficulty, true));
                    if (prevGuiScale >= 0) mc.options.guiScale().set(prevGuiScale);
                    mc.options.pauseOnLostFocus = prevPause;
                    mc.level.disconnect();
                    mc.disconnect();
                    go(91);
                }
            }
            case 91 -> {
                if (mc.getSingleplayerServer() != null && ticks < 600) return;
                if (ticks < 40) return;
                boolean deleted = false;
                try (var access = mc.getLevelSource().createAccess(levelId)) {
                    access.deleteLevel();
                    deleted = true;
                } catch (Exception e) {
                    ConsolePad.LOGGER.error("[shots] no se pudo borrar el mundo temporal: {}", e.toString());
                }
                boolean gone = !Files.exists(mc.getLevelSource().getBaseDir().resolve(levelId));
                ConsolePad.LOGGER.info("[shots] mundo temporal borrado={} (ya no existe={})", deleted, gone);
                ConsolePad.LOGGER.info("[autotest] terminado");
                go(99);
            }
            default -> { }
        }
    }
}
