package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
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

    private static boolean mSwing1, mSwing2, mDestroy1, mDestroy2;
    private static int mStartAttack0, mContinue0;
    private static BlockPos mPos1, mPos2;
    private static BlockState mState1, mState2;

    /**
     * Tarea 1: clic izquierdo del RATON de P1 (KeyMapping.click + set sobre options.keyAttack, como MouseHandler.onPress).
     * Mide por jugador: swing, rotura iniciada y bloque apuntado; P2 no debe iniciar ni continuar ataque. Tambien mide de quien
     * es la mano dibujada en cada mitad (P1 y P2 llevan objetos distintos: espada y palo).
     */
    private static void mouseTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        net.minecraft.client.KeyMapping key = mc.options.keyAttack;
        if (ticks == 1) {
            ServerPlayer p2 = serverP2(mc);
            ServerPlayer p1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            mc.getSingleplayerServer().execute(() -> {
                p1.getInventory().setItem(p1.getInventory().selected, new ItemStack(Items.DIAMOND_SWORD));
                p2.getInventory().setItem(p2.getInventory().selected, new ItemStack(Items.STICK));
            });
            mc.player.setXRot(90f);
            c.player.setXRot(90f);
        }
        if (ticks == 40) {
            mc.player.setXRot(90f);
            c.player.setXRot(90f);
            mSwing1 = mSwing2 = mDestroy1 = mDestroy2 = false;
            mStartAttack0 = SecondaryTick.dbgStartAttack;
            mContinue0 = SecondaryTick.dbgContinueTrue;
            mPos1 = mc.hitResult instanceof net.minecraft.world.phys.BlockHitResult b1 ? b1.getBlockPos() : null;
            mPos2 = c.hitResult instanceof net.minecraft.world.phys.BlockHitResult b2 ? b2.getBlockPos() : null;
            mState1 = mPos1 == null ? null : mc.level.getBlockState(mPos1);
            mState2 = mPos2 == null ? null : c.level.getBlockState(mPos2);
            KeyMapping.click(key.getKey());
            KeyMapping.set(key.getKey(), true);
        }
        if (ticks > 40 && ticks <= 100) {
            mSwing1 |= mc.player.swinging;
            mSwing2 |= c.player.swinging;
            mDestroy1 |= mc.gameMode.isDestroying();
            mDestroy2 |= c.gameMode.isDestroying();
        }
        if (ticks == 100) KeyMapping.set(key.getKey(), false);
        if (ticks == 115) {
            int startP2 = SecondaryTick.dbgStartAttack - mStartAttack0;
            int contP2 = SecondaryTick.dbgContinueTrue - mContinue0;
            boolean changed2 = mPos2 != null && !c.level.getBlockState(mPos2).equals(mState2);
            boolean changed1 = mPos1 != null && !mc.level.getBlockState(mPos1).equals(mState1);
            boolean ok = startP2 == 0 && contP2 == 0 && !mSwing2 && !mDestroy2 && !changed2 && mSwing1;
            Item d1 = SplitRender.dbgHandDrawnP1, d2 = SplitRender.dbgHandDrawnP2;
            Item o1 = SplitRender.dbgHandOwnP1, o2 = SplitRender.dbgHandOwnP2;
            ConsolePad.LOGGER.info("[autotest] RESULTADO clic de raton de P1: P1[swing={} destruyendo={} bloque cambiado={} ({})] "
                            + "P2[inicios de ataque={} continuaciones={} swing={} destruyendo={} bloque cambiado={} ({})] -> {}",
                    mSwing1, mDestroy1, changed1, mPos1, startP2, contP2, mSwing2, mDestroy2, changed2, mPos2, ok ? "OK" : "FALLO");
            ConsolePad.LOGGER.info("[autotest] RESULTADO mano dibujada: mitad izquierda dibuja {} (P1 lleva {}) -> {}; "
                            + "mitad derecha dibuja {} (P2 lleva {}) -> {}",
                    d1, o1, d1 == o1 ? "de P1, OK" : "NO ES DE P1", d2, o2, d2 == o2 ? "de P2, OK" : "NO ES DE P2 (pendiente, tarea 4a)");
            mc.setScreen(null);
            next();
        }
    }

    /** Resume, por pasada, los FOV (vertical y horizontal) de las proyecciones de mundo grabadas desde el ultimo clear. */
    private static String fovSummary() {
        java.util.Map<String, java.util.TreeSet<String>> byPass = new java.util.TreeMap<>();
        for (SplitDiag.Sample sm : SplitDiag.samples()) {
            if (!sm.where().equals("getProjectionMatrix")) continue;
            double v = sm.fovDeg();
            double h = Math.toDegrees(2 * Math.atan(sm.aspect() * Math.tan(Math.toRadians(v) / 2)));
            byPass.computeIfAbsent(sm.pass(), k -> new java.util.TreeSet<>())
                    .add(String.format(java.util.Locale.ROOT, "V%.1f/H%.1f", v, h));
        }
        return byPass.toString();
    }

    /**
     * Tarea 2: FOV vertical/horizontal por pasada con 1 jugador y dividido, parado y corriendo (P1 sprint), con el ajuste
     * "Efectos de FOV" de Minecraft a 1,0 y a 0. Si correr cambia el FOV solo con el efecto a 1,0, es el efecto de vanilla.
     */
    private static void fovTest(Minecraft mc) {
        switch (ticks) {
            case 1 -> {
                mc.options.fovEffectScale().set(1.0);
                mc.player.setSprinting(false);
                SplitView.enabled = false;
                SplitDiag.clear();
                SplitDiag.recording = true;
            }
            case 10 -> {
                SplitDiag.recording = false;
                ConsolePad.LOGGER.info("[autotest] FOV 1 jugador, parado: {}", fovSummary());
                SplitView.enabled = true;
                SplitDiag.clear();
                SplitDiag.recording = true;
            }
            case 20 -> {
                SplitDiag.recording = false;
                ConsolePad.LOGGER.info("[autotest] FOV dividido, parado: {}", fovSummary());
                SplitDiag.clear();
            }
            case 45 -> {
                SplitDiag.recording = true;
            }
            case 50 -> {
                SplitDiag.recording = false;
                String sum = fovSummary();
                boolean p2Sprint = sum.contains("P2=[V70.0/H63.8, V80");
                ConsolePad.LOGGER.info("[autotest] FOV dividido, P1 corriendo, efectos de FOV 1,0: {} -> P2 hereda el FOV de P1: {}",
                        sum, p2Sprint ? "SI (FALLO)" : "NO (OK)");
                mc.options.fovEffectScale().set(0.0);
                SplitDiag.clear();
            }
            case 70 -> SplitDiag.recording = true;
            case 75 -> {
                SplitDiag.recording = false;
                ConsolePad.LOGGER.info("[autotest] FOV dividido, P1 corriendo, efectos de FOV 0: {}", fovSummary());
                mc.options.fovEffectScale().set(1.0);
                mc.player.setSprinting(false);
                next();
            }
            default -> { }
        }
        if (ticks > 20 && ticks < 75) mc.player.setSprinting(true);
    }

    private static net.minecraft.world.Difficulty prevDifficulty;
    private static int leaveSub;
    private static java.nio.file.Path leaveFile;
    private static long leaveFileTime0;
    private static SplitLevelRenderer leaveRenderer;

    /**
     * Paso 7: P2 recibe 7 diamantes en el servidor, sale (cierra su conexion), se comprueba la limpieza y su playerdata,
     * vuelve a entrar y debe conservar el inventario; por ultimo P1 sale del mundo y no debe quedar nada de P2.
     */
    private static void leaveTest(Minecraft mc) {
        switch (leaveSub) {
            case 0 -> {
                ServerPlayer p2 = serverP2(mc);
                leaveFile = mc.getSingleplayerServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.PLAYER_DATA_DIR)
                        .resolve(p2.getUUID() + ".dat");
                mc.getSingleplayerServer().execute(() -> p2.getInventory().setItem(20, new ItemStack(Items.DIAMOND, 7)));
                leaveSub = 1;
                ticks = 0;
            }
            case 1 -> {
                if (ticks < 40) return;
                leaveFileTime0 = fileTime(leaveFile);
                leaveRenderer = (SplitLevelRenderer) SecondaryPlayers.context().renderer;
                SecondaryPlayers.leave(mc);
                leaveSub = 2;
                ticks = 0;
            }
            case 2 -> {
                if ((SecondaryPlayers.isActive() || serverP2(mc) != null) && ticks < 200) return;
                boolean ctxNull = SecondaryPlayers.context() == null;
                boolean gone = serverP2(mc) == null;
                boolean released = !leaveRenderer.isLive();
                long t1 = fileTime(leaveFile);
                boolean saved = t1 > 0 && t1 >= leaveFileTime0;
                boolean p1ok = mc.player != null && mc.level != null && !mc.player.getName().getString().equals(SecondaryPlayers.secondaryName(mc));
                boolean ok = !SecondaryPlayers.isActive() && ctxNull && gone && released && saved && p1ok && ContextSwitcher.violations() == 0;
                ConsolePad.LOGGER.info("[autotest] RESULTADO salida de P2: conexion cerrada={} contexto liberado={} fuera del servidor={} "
                                + "renderer liberado={} playerdata existe y guardado={} (marca {} -> {}) P1 intacto={} fallos de restauracion={} -> {}",
                        !SecondaryPlayers.isActive(), ctxNull, gone, released, saved, leaveFileTime0, t1, p1ok,
                        ContextSwitcher.violations(), ok ? "OK" : "FALLO");
                SecondaryPlayers.join(mc);
                leaveSub = 3;
                ticks = 0;
            }
            case 3 -> {
                PlayerContext c = SecondaryPlayers.context();
                ServerPlayer p2 = serverP2(mc);
                if ((c == null || c.player == null || c.level == null || p2 == null || ticks < 100) && ticks < 400) return;
                ItemStack server = p2 == null ? ItemStack.EMPTY : p2.getInventory().getItem(20);
                ItemStack client = c == null || c.player == null ? ItemStack.EMPTY : c.player.getInventory().getItem(20);
                boolean ok = server.is(Items.DIAMOND) && server.getCount() == 7 && client.is(Items.DIAMOND) && client.getCount() == 7;
                ConsolePad.LOGGER.info("[autotest] RESULTADO reentrada de P2: inventario slot 20 en servidor = {} x{}, en cliente = {} x{} (esperado 7 diamantes) -> {}",
                        server.getItem(), server.getCount(), client.getItem(), client.getCount(), ok ? "OK" : "FALLO");
                leaveSub = 4;
                ticks = 0;
            }
            case 4 -> {
                if (ticks < 40) return;
                var srv = mc.getSingleplayerServer();
                if (prevDifficulty != null) srv.execute(() -> srv.setDifficulty(prevDifficulty, true));
                ConsolePad.LOGGER.info("[autotest] P1 sale del mundo (dificultad restaurada a {})", prevDifficulty);
                mc.level.disconnect(); // como PauseScreen.onDisconnect: cierra la conexion de P1 y el servidor se detiene
                mc.disconnect();
                leaveSub = 5;
                ticks = 0;
            }
            case 5 -> {
                boolean idle = mc.getSingleplayerServer() == null && !SecondaryPlayers.isActive() && SecondaryPlayers.context() == null;
                if (!idle && ticks < 600) return;
                ConsolePad.LOGGER.info("[autotest] RESULTADO salir del mundo: servidor cerrado={} P2 activo={} contexto de P2 presente={} fallos de restauracion={} -> {}",
                        mc.getSingleplayerServer() == null, SecondaryPlayers.isActive(), SecondaryPlayers.context() != null,
                        ContextSwitcher.violations(), idle ? "OK" : "FALLO");
                ConsolePad.LOGGER.info("[autotest] terminado");
                next();
            }
            default -> { }
        }
    }

    private static long fileTime(java.nio.file.Path p) {
        try {
            return java.nio.file.Files.exists(p) ? java.nio.file.Files.getLastModifiedTime(p).toMillis() : -1;
        } catch (java.io.IOException e) {
            return -1;
        }
    }

    private static net.minecraft.world.level.GameType hudPrevMode1, hudPrevMode2;
    private static final java.util.List<String> HUD_LAYERS = java.util.List.of("mira", "hotbar", "barra de experiencia", "vida",
            "armadura", "comida", "burbujas", "nivel de experiencia", "nombre del objeto", "efectos");

    /**
     * Paso 4a: HUD completo de cada jugador. Pone a P2 vida 8, comida 10, aire 100, peto de hierro, 5 niveles, un efecto y otro
     * objeto que P1 (supervivencia para los dos). Graba, por capa y por mitad, el jugador cuyos datos usa y el ancho de su
     * region; todas las capas deben salir en las dos mitades, con datos del jugador de esa mitad. Tambien comprueba que el
     * estado propio del HUD (ultima vida, objeto resaltado) no se mezcla.
     */
    private static void hudTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        if (ticks == 1) {
            ServerPlayer p2 = serverP2(mc);
            ServerPlayer p1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            hudPrevMode1 = p1.gameMode.getGameModeForPlayer();
            hudPrevMode2 = p2.gameMode.getGameModeForPlayer();
            mc.getSingleplayerServer().execute(() -> {
                for (ServerPlayer p : new ServerPlayer[] {p1, p2}) {
                    p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p.setAirSupply(100);
                }
                p1.getInventory().setItem(p1.getInventory().selected, new ItemStack(Items.DIAMOND_PICKAXE));
                p2.getInventory().setItem(p2.getInventory().selected, new ItemStack(Items.IRON_AXE));
                p2.setHealth(8f);
                p2.getFoodData().setFoodLevel(10);
                p2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));
                p2.giveExperienceLevels(5);
                p2.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED, 6000));
            });
        }
        if (ticks == 20) {
            SplitView.enabled = true;
            SplitDiag.hudClear();
            SplitDiag.hudRecording = true;
        }
        if (ticks == 24) {
            screenshotName = "split-hud.png";
            screenshotPending = true;
        }
        if (ticks == 30) {
            SplitDiag.hudRecording = false;
            // Fuera de la prueba: modo de juego, vida y efectos como estaban (en supervivencia de noche los mobs matan a los jugadores).
            ServerPlayer s2 = serverP2(mc);
            ServerPlayer s1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            mc.getSingleplayerServer().execute(() -> {
                s1.setGameMode(hudPrevMode1);
                s2.setGameMode(hudPrevMode2);
                for (ServerPlayer p : new ServerPlayer[] {s1, s2}) {
                    p.setHealth(p.getMaxHealth());
                    p.getFoodData().setFoodLevel(20);
                    p.setAirSupply(p.getMaxAirSupply());
                    p.removeAllEffects();
                }
            });
            var seen = SplitDiag.hudSeen();
            boolean ok = SplitDiag.hudWrong() == 0;
            StringBuilder missing = new StringBuilder();
            for (String owner : new String[] {"P1", "P2"}) {
                var layers = seen.getOrDefault(owner, new java.util.TreeMap<>());
                for (String l : HUD_LAYERS) {
                    if (!layers.containsKey(l)) {
                        ok = false;
                        missing.append(owner).append(':').append(l).append(' ');
                    }
                }
            }
            int w1 = seen.getOrDefault("P1", new java.util.TreeMap<>()).getOrDefault("hotbar", -1);
            int w2 = seen.getOrDefault("P2", new java.util.TreeMap<>()).getOrDefault("hotbar", -1);
            var gs = (dev.consolepad.mixin.GuiStateAccessor) mc.gui;
            boolean state = c.hud.lastHealth > 0 && c.hud.lastHealth < 20 && gs.consolepad$getLastHealth() == 20
                    && c.hud.lastToolHighlight.is(Items.IRON_AXE) && gs.consolepad$getLastToolHighlight().is(Items.DIAMOND_PICKAXE)
                    && c.hud.toolHighlightTimer > 0;
            ok &= state && w1 > 0 && w2 > 0;
            ConsolePad.LOGGER.info("[autotest] RESULTADO HUD por jugador: capas dibujadas P1={} P2={} (de {}); capas con datos del jugador equivocado={} {}; "
                            + "capas que faltan: [{}]; ancho de interfaz de la region (hotbar) P1={} P2={}; "
                            + "estado propio: P2 ultima vida={} (<20, la suya; empezo en 8 y regenera en pacifico) y objeto resaltado={} (hacha, timer {}); P1 ultima vida={} (20) y objeto resaltado={} (pico) -> {}",
                    seen.getOrDefault("P1", new java.util.TreeMap<>()).size(), seen.getOrDefault("P2", new java.util.TreeMap<>()).size(),
                    HUD_LAYERS.size(), SplitDiag.hudWrong(), SplitDiag.hudWrongWhat(), missing.toString().trim(), w1, w2,
                    c.hud.lastHealth, c.hud.lastToolHighlight.getItem(), c.hud.toolHighlightTimer,
                    gs.consolepad$getLastHealth(), gs.consolepad$getLastToolHighlight().getItem(), ok ? "OK" : "FALLO");
            next();
        }
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
                            + "choque horizontal P2={}, vida de P2={} -> {}",
                    pauseScreen, mc.screen == null ? "null" : mc.screen.getClass().getSimpleName(), mc.isPaused(),
                    mc.getSingleplayerServer().isPaused(), String.format(java.util.Locale.ROOT, "%.2f", control),
                    String.format(java.util.Locale.ROOT, "%.2f", moved), serverTicks, clientTicks,
                    p2.horizontalCollision, p2.getHealth(), ok ? "OK" : "FALLO");
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
        // Si un mundo guardado trae a alguien muerto (los mobs de la noche), reaparece: la pantalla de muerte bloquearia la prueba.
        if (mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null && ticks % 20 == 0) mc.player.respawn();
        PlayerContext dead = SecondaryPlayers.context();
        if (dead != null && dead.player != null && dead.player.getHealth() <= 0f && ticks % 20 == 0) dead.player.respawn();
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
                    // Prueba determinista: de dia y en pacifico (los mobs de la noche matan a los jugadores en supervivencia).
                    // La dificultad se restaura al final (leaveTest); la hora del dia solo se adelanta.
                    var server = mc.getSingleplayerServer();
                    prevDifficulty = server.getWorldData().getDifficulty();
                    server.execute(() -> {
                        server.setDifficulty(net.minecraft.world.Difficulty.PEACEFUL, true);
                        server.overworld().setDayTime(1000L);
                    });
                    SecondaryPlayers.join(mc);
                    next();
                }
            }
            case 2 -> teleportAway(mc);
            case 3 -> hotbarTest(mc);
            case 4 -> mouseTest(mc);
            case 5 -> hudTest(mc);
            case 6 -> aspectTest(mc, "pequena");
            case 7 -> resizeWindow(mc);
            case 8 -> aspectTest(mc, "1920x1080");
            case 9 -> pauseTest(mc);
            case 10 -> fovTest(mc);
            case 11 -> fpsPhase(mc, false);
            case 12 -> fpsPhase(mc, true);
            case 13 -> leaveTest(mc);
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
