package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Ayuda de pruebas, solo si se arranca con la variable de entorno CONSOLEPAD_AUTOTEST=<nombre del mundo>.
 * Abre el mundo, mete a P2, lo aleja de P1 y simula pulsaciones de cada mando por separado (PadState.simDown),
 * registrando quien ejecuta cada accion. No tocar la ventana del juego mientras corre: la entrada humana contamina las medidas.
 */
public final class AutoTest {
    private static final String WORLD = System.getenv("CONSOLEPAD_AUTOTEST");

    /** {slot del mando simulado, boton, ticks pulsado} */
    private static final int[][] MATRIX = {
            {0, PadState.RT, 30}, {1, PadState.RT, 30},
            {0, PadState.LT, 30}, {1, PadState.LT, 30},
            {0, PadState.A, 10}, {1, PadState.A, 10},
            {0, PadState.RB, 3}, {1, PadState.RB, 3},
            {0, PadState.LB, 3}, {1, PadState.LB, 3},
    };
    private static final String[] BUTTON_NAMES = new String[17];
    static {
        BUTTON_NAMES[PadState.A] = "A(saltar)";
        BUTTON_NAMES[PadState.RT] = "RT(atacar)";
        BUTTON_NAMES[PadState.LT] = "LT(usar)";
        BUTTON_NAMES[PadState.LB] = "LB(hotbar-)";
        BUTTON_NAMES[PadState.RB] = "RB(hotbar+)";
    }

    private static PadManager pads;
    private static int state;
    private static int ticks;
    private static boolean teleported;
    private static Vec3 target;

    private static int testIndex;
    private static int testTicks;
    private static boolean testRunning;
    // observaciones de P1 durante la prueba
    private static boolean p1Attack, p1Use, p1Jump, p1Swing;
    private static int p1SelectedBefore, p2SelectedBefore;
    private static int c0Attack, c0Continue, c0Use, c0Jump, c0Hotbar;

    private static final int WARMUP_TICKS = 20 * 4;
    private static final int MEASURE_TICKS = 20 * 8;
    private static boolean phaseStarted;
    private static long fpsSum;
    private static int fpsSamples;
    private static boolean screenshotPending;
    private static String screenshotName = "split-step5.png";
    private static int p1SelBefore, p2SelBefore;

    private AutoTest() {}

    /** Al terminar cada frame (RenderFrameEvent.Post): aqui se hace la captura, con el frame ya dibujado. */
    public static void frameEnd() {
        if (!screenshotPending) return;
        screenshotPending = false;
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, screenshotName, mc.getMainRenderTarget(),
                msg -> ConsolePad.LOGGER.info("[autotest] captura: {}", msg.getString()));
    }

    /** Paso 5: P2 cambia de casilla con RB (mando virtual/real del slot 1) y P1 no debe cambiar; captura con las dos hotbars. */
    private static void hotbarTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        PadState s1 = pads.slot(1).device.state;
        if (ticks == 1) {
            p1SelBefore = mc.player.getInventory().selected;
            p2SelBefore = c.player.getInventory().selected;
            ConsolePad.LOGGER.info("[autotest] hotbar: antes P1={} P2={}", p1SelBefore, p2SelBefore);
        }
        s1.simDown = (ticks >= 2 && ticks < 5) || (ticks >= 10 && ticks < 13) ? 1 << PadState.RB : 0;
        if (ticks == 30) {
            ConsolePad.LOGGER.info("[autotest] RESULTADO hotbar: P1 {} -> {} (debe ser igual); P2 {} -> {} (debe avanzar 2 casillas)",
                    p1SelBefore, mc.player.getInventory().selected, p2SelBefore, c.player.getInventory().selected);
            screenshotName = "split-step5-small.png";
            screenshotPending = true;
        }
        if (ticks >= 45) next();
    }

    private static int aspectBad;
    private static Vec3 pauseP2Start, pauseP2Mid;
    private static long pauseGameTime0, pauseServerTime0;

    /**
     * Paso 6: P1 abre el menu de pausa y P2 sigue moviendose. Fase de control (40 ticks sin menu) y fase de pausa (40 ticks
     * con el menu abierto). Mide: el menu es de pausa, Minecraft y el servidor integrado NO estan en pausa, el reloj avanza
     * ~40 ticks y P2 recorre en pausa al menos el 75 % de lo que recorre en el control (que a su vez debe ser >= 3 bloques).
     */
    private static void pauseTest(Minecraft mc) {
        ServerPlayer p2 = serverP2(mc);
        if (ticks == 1) {
            mc.setScreen(null);
            pauseP2Start = p2.position();
            SecondaryTick.scriptActive = true;
            SecondaryTick.scriptForward = 1f;
        }
        if (ticks == 41) {
            pauseP2Mid = p2.position();
            mc.pauseGame(false);
            pauseGameTime0 = mc.level.getGameTime();
            pauseServerTime0 = mc.getSingleplayerServer().overworld().getGameTime();
            ConsolePad.LOGGER.info("[autotest] pausa: menu abierto = {}", mc.screen);
        }
        if (ticks == 81) {
            double control = pauseP2Mid.distanceTo(pauseP2Start);
            double moved = p2.position().distanceTo(pauseP2Mid);
            long clientTicks = mc.level.getGameTime() - pauseGameTime0;
            long serverTicks = mc.getSingleplayerServer().overworld().getGameTime() - pauseServerTime0;
            boolean pauseScreen = mc.screen != null && mc.screen.isPauseScreen();
            boolean ok = pauseScreen && !mc.isPaused() && !mc.getSingleplayerServer().isPaused() && control >= 3.0
                    && moved >= 0.75 * control && serverTicks >= 35 && clientTicks >= 35;
            ConsolePad.LOGGER.info("[autotest] RESULTADO pausa: pantalla de pausa={} ({}), mc.isPaused={}, servidor en pausa={}, "
                            + "P2 control {} bloques (>=3), en pausa {} (>=75% del control), ticks de servidor {} y de cliente {} (>=35), "
                            + "choque horizontal P2={} -> {}",
                    pauseScreen, mc.screen == null ? "null" : mc.screen.getClass().getSimpleName(), mc.isPaused(),
                    mc.getSingleplayerServer().isPaused(), String.format(java.util.Locale.ROOT, "%.2f", control),
                    String.format(java.util.Locale.ROOT, "%.2f", moved), serverTicks, clientTicks,
                    p2.horizontalCollision, ok ? "OK" : "FALLO");
            SecondaryTick.scriptActive = false;
            SecondaryTick.scriptForward = 0f;
            mc.setScreen(null);
        }
        if (ticks >= 90) next();
    }

    /**
     * Comprobacion numerica del aspecto: durante unos frames graba, por pasada, el viewport GL real y la proyeccion y
     * falla si el aspecto de la proyeccion o del viewport se desvia mas de un 1 % del de la region de la pasada.
     */
    private static void aspectTest(Minecraft mc, String label) {
        if (ticks == 1) {
            SplitView.enabled = true;
            SplitDiag.clear();
            SplitDiag.recording = true;
            com.mojang.blaze3d.platform.Window w = mc.getWindow();
            ConsolePad.LOGGER.info("[autotest] aspecto '{}': ventana {}x{}", label, w.getWidth(), w.getHeight());
        }
        if (ticks == 10) {
            SplitDiag.recording = false;
            int n = SplitDiag.samples().size();
            int bad = SplitDiag.check(label, 0.01);
            aspectBad += bad;
            ConsolePad.LOGGER.info("[autotest] RESULTADO aspecto '{}': {} muestras, {} fuera de tolerancia (1 %) -> {}", label, n, bad,
                    n > 0 && bad == 0 ? "OK" : "FALLO");
            screenshotName = "split-aspect-" + label + ".png";
            screenshotPending = true;
        }
        if (ticks >= 20) next();
    }

    /** Pone la ventana a 1920x1080 (o lo mas cercano que admita la pantalla) y espera a que se aplique. */
    private static void resizeWindow(Minecraft mc) {
        com.mojang.blaze3d.platform.Window window = mc.getWindow();
        if (ticks == 1) {
            org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(), 1920, 1080);
        }
        if (ticks >= 60) {
            ConsolePad.LOGGER.info("[autotest] ventana: {}x{} (framebuffer), escala de interfaz {}", window.getWidth(), window.getHeight(),
                    window.getGuiScale());
            next();
        }
    }

    /** Fase 3 (sin division, 1 jugador) y fase 4 (dividido, 2 jugadores): mide FPS con distancia de renderizado 8. */
    private static void fpsPhase(Minecraft mc, boolean split) {
        if (!phaseStarted) {
            phaseStarted = true;
            mc.options.renderDistance().set(8);
            mc.options.enableVsync().set(false);
            mc.options.framerateLimit().set(260);
            SplitView.enabled = split;
            fpsSum = 0;
            fpsSamples = 0;
            ticks = 0;
            ConsolePad.LOGGER.info("[autotest] fase {}: {} (distancia 8, sin vsync)", split ? "dividida" : "base",
                    split ? "pantalla dividida, 2 jugadores" : "1 jugador, sin division");
            return;
        }
        if (ticks > WARMUP_TICKS && ticks % 20 == 0) {
            fpsSum += mc.getFps();
            fpsSamples++;
        }
        if (split && ticks == WARMUP_TICKS + 40) {
            screenshotName = "split-step5-1080.png";
            screenshotPending = true;
        }
        if (ticks >= WARMUP_TICKS + MEASURE_TICKS) {
            PlayerContext c = SecondaryPlayers.context();
            String p2Stats = c != null && c.renderer instanceof SplitLevelRenderer r && r.isLive() ? r.getSectionStatistics() : "n/a";
            ConsolePad.LOGGER.info("[autotest] RESULTADO FPS {}: media {} fps ({} muestras). Secciones P1: {} | P2: {}",
                    split ? "DIVIDIDO (2 jugadores)" : "BASE (1 jugador)", fpsSamples == 0 ? 0 : fpsSum / fpsSamples, fpsSamples,
                    mc.levelRenderer.getSectionStatistics(), p2Stats);
            phaseStarted = false;
            next();
            if (split) ConsolePad.LOGGER.info("[autotest] terminado");
        }
    }

    public static void init(PadManager manager) {
        pads = manager;
    }

    private static ServerPlayer serverP2(Minecraft mc) {
        return mc.getSingleplayerServer() == null ? null
                : mc.getSingleplayerServer().getPlayerList().getPlayerByName(SecondaryPlayers.secondaryName(mc));
    }

    public static void tick() {
        if (WORLD == null) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        switch (state) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen && ticks > 60) {
                    ConsolePad.LOGGER.info("[autotest] abriendo el mundo '{}'", WORLD);
                    next();
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> ConsolePad.LOGGER.error("[autotest] no se pudo abrir el mundo"));
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null && ticks > 200) {
                    ConsolePad.LOGGER.info("[autotest] mundo cargado; metiendo a P2");
                    SecondaryPlayers.join(mc);
                    next();
                }
            }
            case 2 -> teleportAway(mc);
            case 3 -> hotbarTest(mc);
            case 4 -> aspectTest(mc, "pequena");
            case 5 -> resizeWindow(mc);
            case 6 -> aspectTest(mc, "1920x1080");
            case 7 -> pauseTest(mc);
            case 8 -> fpsPhase(mc, false);
            case 9 -> fpsPhase(mc, true);
            default -> { }
        }
    }

    private static void teleportAway(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        if (c == null || c.player == null || c.level == null || p2 == null || ticks < 100) return;
        if (!teleported) {
            Vec3 p1 = mc.player.position();
            ServerLevel level = (ServerLevel) p2.level();
            mc.getSingleplayerServer().execute(() -> {
                int x = (int) Math.floor(p1.x) + 24;
                int z = (int) Math.floor(p1.z);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                target = new Vec3(x + 0.5, y, z + 0.5);
                p2.teleportTo(level, target.x, target.y, target.z, 0f, 0f);
            });
            teleported = true;
            ticks = 0;
            return;
        }
        if (target != null && ticks > 100 && p2.position().distanceTo(target) < 3.0 && p2.onGround()
                && c.level.hasChunkAt(c.player.blockPosition())) {
            pads.attachVirtual(0); // si falta algun mando real, se simula uno virtual (solo en el autotest)
            pads.attachVirtual(1);
            PadManager.Device d0 = pads.slot(0).device;
            PadManager.Device d1 = pads.slot(1).device;
            ConsolePad.LOGGER.info("[autotest] P2 listo y lejos de P1. Mandos: slot0 = {} ; slot1 = {}",
                    d0 == null ? "NINGUNO" : "jid " + d0.jid + " '" + d0.name + "'",
                    d1 == null ? "NINGUNO" : "jid " + d1.jid + " '" + d1.name + "'");
            if (d0 == null || d1 == null) {
                ConsolePad.LOGGER.error("[autotest] hacen falta dos mandos para esta prueba");
                state = 99;
                return;
            }
            ConsolePad.LOGGER.info("[autotest] lectura de estado: P1 lee slot0 en PadDriver.onFrame (bindings -> keyAttack/keyUse/keyJump + clickCount);"
                    + " P2 lee slot1 en SecondaryTick.tick (PadState.isDown -> startAttack/continueAttack/startUseItem/PadInput.jumping)");
            next();
        }
    }

    private static void matrix(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        if (testIndex >= MATRIX.length) {
            ConsolePad.LOGGER.info("[autotest] terminado");
            next();
            return;
        }
        int[] t = MATRIX[testIndex];
        PadState simulated = pads.slot(t[0]).device.state;
        if (!testRunning) {
            if (ticks < 30) return; // pausa entre pruebas
            // mirar al cielo para no romper bloques ni golpear nada
            mc.player.setXRot(-90f);
            c.player.setXRot(-90f);
            p1Attack = p1Use = p1Jump = p1Swing = false;
            c0Attack = SecondaryTick.dbgStartAttack;
            c0Continue = SecondaryTick.dbgContinueTrue;
            c0Use = SecondaryTick.dbgStartUse;
            c0Jump = SecondaryTick.dbgJumpTicks;
            c0Hotbar = SecondaryTick.dbgHotbarSteps;
            p1SelectedBefore = mc.player.getInventory().selected;
            p2SelectedBefore = c.player.getInventory().selected;
            simulated.simDown = 1 << t[1];
            testRunning = true;
            testTicks = 0;
            return;
        }
        testTicks++;
        p1Attack |= mc.options.keyAttack.isDown();
        p1Use |= mc.options.keyUse.isDown();
        p1Jump |= mc.options.keyJump.isDown();
        p1Swing |= mc.player.swinging;
        if (testTicks == t[2]) simulated.simDown = 0;
        if (testTicks >= t[2] + 15) { // margen para que se vea el efecto y se suelten las teclas
            PadState s0 = pads.slot(0).device.state;
            PadState s1 = pads.slot(1).device.state;
            ConsolePad.LOGGER.info("[autotest] RESULTADO mando slot{} {}: P1[keyAttack={} keyUse={} keyJump={} swing={} hotbar {}->{}] "
                            + "P2[startAttack={} continueAttack(true)={} startUse={} saltoTicks={} hotbarPasos={} hotbar {}->{}] "
                            + "estado simulado en slot0={} slot1={} (bits); ventana activa={}",
                    t[0], BUTTON_NAMES[t[1]], p1Attack, p1Use, p1Jump, p1Swing, p1SelectedBefore, mc.player.getInventory().selected,
                    SecondaryTick.dbgStartAttack - c0Attack, SecondaryTick.dbgContinueTrue - c0Continue,
                    SecondaryTick.dbgStartUse - c0Use, SecondaryTick.dbgJumpTicks - c0Jump,
                    SecondaryTick.dbgHotbarSteps - c0Hotbar, p2SelectedBefore, c.player.getInventory().selected,
                    s0.downBits(), s1.downBits(), mc.isWindowActive());
            testRunning = false;
            testIndex++;
            ticks = 0;
        }
    }

    private static void next() {
        state++;
        ticks = 0;
    }
}
