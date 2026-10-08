package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
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

    /** Se ejecuta una vez al final del siguiente fotograma con la imagen de la ventana (fila 0 = arriba). */
    private static volatile java.util.function.Consumer<com.mojang.blaze3d.platform.NativeImage> pixelProbe;

    private AutoTest() {}

    /** Al terminar cada frame (RenderFrameEvent.Post): aqui se hace la captura, con el frame ya dibujado. */
    public static void frameEnd() {
        if (pixelProbe != null) {
            var probe = pixelProbe;
            pixelProbe = null;
            Minecraft mc0 = Minecraft.getInstance();
            com.mojang.blaze3d.platform.NativeImage img = Screenshot.takeScreenshot(mc0.getMainRenderTarget());
            try {
                probe.accept(img);
            } finally {
                img.close();
            }
        }
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
    private static int prevGuiScale = -1;
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
                if (prevGuiScale >= 0) mc.options.guiScale().set(prevGuiScale);
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

    private static int actDropBefore, actP1DropBefore;
    private static boolean actSprintP2, actSprintP1, actSneakP2, actSneakP1;
    private static String actSwapReport = "", actCamReport = "";
    private static boolean actOk = true;

    private static void press(PadAction a) {
        SecondaryTick.scriptActions = java.util.EnumSet.of(a);
    }

    private static void release() {
        SecondaryTick.scriptActions = java.util.EnumSet.noneOf(PadAction.class);
    }

    /**
     * Paso 4b: acciones de P2 con los bindings de P1 como base (script en el slot 1): sprint, agacharse, soltar, cambio de mano,
     * perspectiva e inventario (solo la peticion: la pantalla es del paso 4c). Cada una cambia el estado de P2 y no el de P1.
     */
    private static void actionsTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        switch (ticks) {
            case 1 -> {
                SecondaryTick.scriptActive = true;
                SecondaryTick.scriptForward = 0f;
                mc.getSingleplayerServer().execute(() -> {
                    p2.getInventory().setItem(p2.getInventory().selected, new ItemStack(Items.DIRT, 5));
                    p2.getInventory().offhand.set(0, ItemStack.EMPTY);
                });
            }
            case 20 -> {
                SecondaryTick.scriptForward = 1f; // sprint: hay que avanzar para que se active
                press(PadAction.SPRINT_TOGGLE);
            }
            case 22 -> release();
            case 45 -> {
                actSprintP2 = c.player.isSprinting();
                actSprintP1 = mc.player.isSprinting();
                press(PadAction.SPRINT_TOGGLE); // lo apaga
            }
            case 47 -> {
                release();
                SecondaryTick.scriptForward = 0f;
            }
            case 55 -> press(PadAction.SNEAK_TOGGLE);
            case 57 -> release();
            case 65 -> {
                actSneakP2 = c.player.isShiftKeyDown();
                actSneakP1 = mc.player.isShiftKeyDown();
                press(PadAction.SNEAK_TOGGLE); // lo apaga
            }
            case 67 -> release();
            case 75 -> {
                actDropBefore = c.player.getInventory().getSelected().getCount();
                actP1DropBefore = mc.player.getInventory().getSelected().getCount();
                press(PadAction.DROP);
            }
            case 77 -> release();
            case 95 -> {
                int after = c.player.getInventory().getSelected().getCount();
                int serverAfter = p2.getInventory().getSelected().getCount();
                int p1After = mc.player.getInventory().getSelected().getCount();
                boolean ok = actDropBefore == 5 && after == 4 && serverAfter == 4 && p1After == actP1DropBefore;
                actOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO soltar de P2: cliente {} -> {}, servidor {} (esperado 5 -> 4 -> 4); P1 {} -> {} (igual) -> {}",
                        actDropBefore, after, serverAfter, actP1DropBefore, p1After, ok ? "OK" : "FALLO");
                press(PadAction.SWAP_HANDS);
            }
            case 97 -> release();
            case 115 -> {
                ItemStack off = p2.getInventory().offhand.get(0);
                ItemStack main = p2.getInventory().getSelected();
                ItemStack clientOff = c.player.getInventory().offhand.get(0);
                boolean p1Off = mc.player.getInventory().offhand.get(0).is(Items.DIRT);
                boolean ok = off.is(Items.DIRT) && off.getCount() == 4 && main.isEmpty() && clientOff.is(Items.DIRT) && !p1Off;
                actOk &= ok;
                actSwapReport = String.format("servidor: mano secundaria=%s x%d, principal vacia=%s; cliente de P2: mano secundaria=%s; P1 recibio tierra=%s -> %s",
                        off.getItem(), off.getCount(), main.isEmpty(), clientOff.getItem(), p1Off, ok ? "OK" : "FALLO");
                ConsolePad.LOGGER.info("[autotest] RESULTADO cambio de mano de P2: {}", actSwapReport);
                press(PadAction.PERSPECTIVE);
            }
            case 117 -> release();
            case 125 -> {
                var opts = (dev.consolepad.mixin.OptionsAccessor) mc.options;
                boolean ok = c.cameraType != net.minecraft.client.CameraType.FIRST_PERSON
                        && opts.consolepad$getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
                actOk &= ok;
                actCamReport = String.format("P2 %s, P1 %s -> %s", c.cameraType, opts.consolepad$getCameraType(), ok ? "OK" : "FALLO");
                press(PadAction.PERSPECTIVE);
            }
            case 127 -> release();
            case 135 -> press(PadAction.PERSPECTIVE);
            case 137 -> release();
            case 145 -> {
                var opts = (dev.consolepad.mixin.OptionsAccessor) mc.options;
                boolean back = c.cameraType == net.minecraft.client.CameraType.FIRST_PERSON && opts.consolepad$getCameraType() == net.minecraft.client.CameraType.FIRST_PERSON;
                actOk &= back;
                ConsolePad.LOGGER.info("[autotest] RESULTADO perspectiva de P2: tras 1 pulsacion {}; tras 3 vuelve a primera persona={} -> {}",
                        actCamReport, back, back ? "OK" : "FALLO");
                press(PadAction.INVENTORY);
            }
            case 147 -> release();
            case 155 -> {
                boolean ok = SecondaryTick.dbgInventoryRequests == 1;
                actOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO inventario de P2: peticiones={} (esperado 1; la pantalla llega en 4c) -> {}",
                        SecondaryTick.dbgInventoryRequests, ok ? "OK" : "FALLO");
                boolean sprintOk = actSprintP2 && !actSprintP1;
                boolean sneakOk = actSneakP2 && !actSneakP1;
                actOk &= sprintOk && sneakOk;
                ConsolePad.LOGGER.info("[autotest] RESULTADO sprint y agacharse de P2: sprint P2={} P1={} (esperado true/false) -> {}; agacharse P2={} P1={} (esperado true/false) -> {}",
                        actSprintP2, actSprintP1, sprintOk ? "OK" : "FALLO", actSneakP2, actSneakP1, sneakOk ? "OK" : "FALLO");
                ConsolePad.LOGGER.info("[autotest] RESULTADO acciones de P2 en conjunto -> {}", actOk ? "OK" : "FALLO");
                SecondaryTick.scriptActive = false;
                SecondaryTick.scriptForward = 0f;
                release();
                next();
            }
            default -> { }
        }
    }

    private static net.minecraft.world.level.GameType scrPrevMode;
    private static String scrP1Before = "";
    private static boolean scrOk = true;

    private static String p1InventoryDigest(Minecraft mc) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 36; i++) {
            ItemStack st = mc.player.getInventory().getItem(i);
            sb.append(st.isEmpty() ? "-" : st.getItem() + "x" + st.getCount()).append(',');
        }
        return sb.toString();
    }

    private static void simP2(int bits) {
        PadManager.Device d = pads.slot(1).device;
        if (d != null) d.state.simDown = bits;
    }

    private static void cursorOnSlot(PlayerContext c, int menuSlot) {
        var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) c.screen;
        net.minecraft.world.inventory.Slot sl = screen.getMenu().slots.get(menuSlot);
        c.cursorX = screen.getGuiLeft() + sl.x + 8;
        c.cursorY = screen.getGuiTop() + sl.y + 8;
    }

    /**
     * Paso 4c-1: inventario de P2 en su mitad, con su cursor. P2 (supervivencia) tiene 10 de tierra en el slot 20 del inventario;
     * se abre con INVENTORY, se coge con A, se coloca en el slot 21, se mueve con Y (mover rapido) a la barra y se cierra con B.
     * P1 no debe tener pantalla ni cambiar de inventario.
     */
    private static void screensTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        switch (ticks) {
            case 1 -> {
                scrP1Before = p1InventoryDigest(mc);
                scrPrevMode = p2.gameMode.getGameModeForPlayer();
                mc.getSingleplayerServer().execute(() -> {
                    p2.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p2.getInventory().setItem(20, new ItemStack(Items.DIRT, 10));
                    p2.getInventory().setItem(21, ItemStack.EMPTY);
                    for (int i = 0; i < 9; i++) p2.getInventory().setItem(i, ItemStack.EMPTY);
                    p2.getInventory().offhand.set(0, ItemStack.EMPTY);
                });
                SecondaryTick.scriptActive = true;
                SecondaryTick.scriptForward = 0f;
            }
            case 10 -> press(PadAction.INVENTORY);
            case 12 -> release();
            case 25 -> {
                boolean open = c.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
                int w2 = SecondaryScreens.regionWidth(mc);
                boolean sized = open && c.screen.width == w2;
                boolean p1Free = mc.screen == null;
                boolean ok = open && sized && p1Free;
                scrOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO pantalla de P2: abierta={} ({}), ancho={} (region {}), pantalla de P1={} (debe ser null) -> {}",
                        open, c.screen == null ? "null" : c.screen.getClass().getSimpleName(), c.screen == null ? -1 : c.screen.width, w2,
                        mc.screen, ok ? "OK" : "FALLO");
                screenshotName = "split-inventory.png";
                screenshotPending = true;
                if (open) cursorOnSlot(c, 20);
            }
            case 32 -> simP2(1 << PadState.A);
            case 38 -> simP2(0);
            case 45 -> {
                ItemStack carried = c.player.containerMenu.getCarried();
                boolean ok = carried.is(Items.DIRT) && carried.getCount() == 10;
                scrOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO coger con A: objeto en el cursor de P2 = {} x{} (esperado 10 de tierra) -> {}",
                        carried.getItem(), carried.getCount(), ok ? "OK" : "FALLO");
                cursorOnSlot(c, 21);
            }
            case 50 -> simP2(1 << PadState.A);
            case 56 -> simP2(0);
            case 70 -> {
                ItemStack at21 = p2.getInventory().getItem(21);
                ItemStack at20 = p2.getInventory().getItem(20);
                boolean ok = at21.is(Items.DIRT) && at21.getCount() == 10 && at20.isEmpty();
                scrOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO colocar con A: servidor slot 21 = {} x{}, slot 20 vacio={} (esperado 10 de tierra y vacio) -> {}",
                        at21.getItem(), at21.getCount(), at20.isEmpty(), ok ? "OK" : "FALLO");
                cursorOnSlot(c, 21);
            }
            case 76 -> simP2(1 << PadState.Y);
            case 82 -> simP2(0);
            case 95 -> {
                boolean slot21Empty = p2.getInventory().getItem(21).isEmpty();
                int hotbarDirt = 0;
                for (int i = 0; i < 9; i++) {
                    ItemStack st = p2.getInventory().getItem(i);
                    if (st.is(Items.DIRT)) hotbarDirt += st.getCount();
                }
                boolean ok = slot21Empty && hotbarDirt == 10;
                scrOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO mover rapido con Y: slot 21 vacio={}, tierra en la barra de P2={} (esperado 10) -> {}",
                        slot21Empty, hotbarDirt, ok ? "OK" : "FALLO");
            }
            case 100 -> simP2(1 << PadState.B);
            case 106 -> simP2(0);
            case 120 -> {
                boolean closed = c.screen == null;
                boolean serverClosed = p2.containerMenu == p2.inventoryMenu;
                boolean p1Same = p1InventoryDigest(mc).equals(scrP1Before);
                boolean p1Free = mc.screen == null;
                boolean ok = closed && serverClosed && p1Same && p1Free && ContextSwitcher.violations() == 0;
                scrOk &= ok;
                ConsolePad.LOGGER.info("[autotest] RESULTADO cerrar con B y aislamiento: pantalla de P2 cerrada={}, contenedor cerrado en el servidor={}, inventario de P1 sin cambios={}, "
                                + "pantalla de P1 null={}, fallos de restauracion={} -> {}",
                        closed, serverClosed, p1Same, p1Free, ContextSwitcher.violations(), ok ? "OK" : "FALLO");
                ConsolePad.LOGGER.info("[autotest] RESULTADO pantallas de P2 en conjunto -> {}", scrOk ? "OK" : "FALLO");
                mc.getSingleplayerServer().execute(() -> p2.setGameMode(scrPrevMode));
                SecondaryTick.scriptActive = false;
                simP2(0);
                next();
            }
            default -> { }
        }
    }

    private static net.minecraft.world.level.GameType s2Prev1, s2Prev2;
    private static String s2P1Digest = "";
    private static boolean s2Ok = true;
    private static net.minecraft.core.BlockPos s2Pos;

    private static void p1CursorOnSlot(Minecraft mc, int menuSlot, boolean click) {
        var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
        net.minecraft.world.inventory.Slot sl = screen.getMenu().slots.get(menuSlot);
        double gx = screen.getGuiLeft() + sl.x + 8, gy = screen.getGuiTop() + sl.y + 8;
        if (click) screen.mouseClicked(gx, gy, 0);
        else screen.mouseReleased(gx, gy, 0);
    }

    private static void verdict(String name, boolean ok) {
        s2Ok &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /**
     * Paso 4c-2: (a) las pantallas de P1 y P2 abiertas a la vez, cada una en su mitad y con su inventario (P1 coge/coloca con
     * clics directos sobre su pantalla, P2 con su mando); (b) un cofre y una mesa de crafteo abiertos por el servidor en P2.
     */
    private static void screens2Test(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        ServerPlayer p1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        switch (ticks) {
            case 1 -> {
                s2Prev1 = p1.gameMode.getGameModeForPlayer();
                s2Prev2 = p2.gameMode.getGameModeForPlayer();
                mc.getSingleplayerServer().execute(() -> {
                    p1.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p2.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p1.getInventory().setItem(20, new ItemStack(Items.DIRT, 10));
                    p1.getInventory().setItem(21, ItemStack.EMPTY);
                    p2.getInventory().setItem(20, new ItemStack(Items.COBBLESTONE, 7));
                    p2.getInventory().setItem(21, ItemStack.EMPTY);
                    p2.getInventory().setItem(22, ItemStack.EMPTY);
                });
                SecondaryTick.scriptActive = true;
                SecondaryTick.scriptForward = 0f;
            }
            case 10 -> {
                press(PadAction.INVENTORY);
                mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
            }
            case 12 -> release();
            case 25 -> {
                int full = mc.getWindow().getGuiScaledWidth();
                boolean p1Half = mc.screen != null && mc.screen.width == SplitView.p1LogicalW;
                boolean p2Half = c.screen != null && c.screen.width == SecondaryScreens.regionWidth(mc);
                s2P1Digest = p1InventoryDigest(mc);
                ConsolePad.LOGGER.info("[autotest] dos pantallas: P1 {} ancho {} (mitad {}), P2 {} ancho {} (mitad {}), p1HalfScreen={}",
                        mc.screen == null ? "null" : mc.screen.getClass().getSimpleName(), mc.screen == null ? -1 : mc.screen.width, SplitView.p1LogicalW,
                        c.screen == null ? "null" : c.screen.getClass().getSimpleName(), c.screen == null ? -1 : c.screen.width, SecondaryScreens.regionWidth(mc),
                        SplitView.p1HalfScreen);
                verdict("dos pantallas a la vez, cada una en su mitad", p1Half && p2Half && SplitView.p1HalfScreen);
                screenshotName = "split-two-screens.png";
                screenshotPending = true;
                if (mc.screen != null) p1CursorOnSlot(mc, 20, true);
            }
            case 27 -> p1CursorOnSlot(mc, 20, false);
            case 35 -> {
                ItemStack p1Carried = mc.player.containerMenu.getCarried();
                ItemStack p2Carried = c.player.containerMenu.getCarried();
                ItemStack p2Slot20 = p2.getInventory().getItem(20);
                verdict("P1 coge con su pantalla: cursor de P1 " + p1Carried.getItem() + " x" + p1Carried.getCount() + ", cursor de P2 vacio=" + p2Carried.isEmpty()
                        + ", slot 20 de P2 " + p2Slot20.getItem() + " x" + p2Slot20.getCount(),
                        p1Carried.is(Items.DIRT) && p1Carried.getCount() == 10 && p2Carried.isEmpty() && p2Slot20.is(Items.COBBLESTONE) && p2Slot20.getCount() == 7);
                p1CursorOnSlot(mc, 21, true);
            }
            case 37 -> p1CursorOnSlot(mc, 21, false);
            case 50 -> {
                ItemStack a = p1.getInventory().getItem(21);
                ItemStack b = p2.getInventory().getItem(20);
                verdict("P1 coloca en el slot 21 (servidor): " + a.getItem() + " x" + a.getCount() + "; P2 conserva " + b.getItem() + " x" + b.getCount(),
                        a.is(Items.DIRT) && a.getCount() == 10 && b.is(Items.COBBLESTONE) && b.getCount() == 7);
                cursorOnSlot(c, 20);
            }
            case 55 -> simP2(1 << PadState.A);
            case 61 -> simP2(0);
            case 70 -> {
                ItemStack p2Carried = c.player.containerMenu.getCarried();
                ItemStack p1Carried = mc.player.containerMenu.getCarried();
                verdict("P2 coge con su mando: cursor de P2 " + p2Carried.getItem() + " x" + p2Carried.getCount() + ", cursor de P1 vacio=" + p1Carried.isEmpty(),
                        p2Carried.is(Items.COBBLESTONE) && p2Carried.getCount() == 7 && p1Carried.isEmpty());
                cursorOnSlot(c, 22);
            }
            case 75 -> simP2(1 << PadState.A);
            case 81 -> simP2(0);
            case 95 -> {
                ItemStack a = p2.getInventory().getItem(22);
                ItemStack b = p1.getInventory().getItem(21);
                verdict("P2 coloca en el slot 22 (servidor): " + a.getItem() + " x" + a.getCount() + "; P1 conserva " + b.getItem() + " x" + b.getCount(),
                        a.is(Items.COBBLESTONE) && a.getCount() == 7 && b.is(Items.DIRT) && b.getCount() == 10);
                if (mc.screen != null) mc.screen.onClose();
                simP2(1 << PadState.B);
            }
            case 101 -> simP2(0);
            case 115 -> {
                boolean closed = mc.screen == null && c.screen == null && p1.containerMenu == p1.inventoryMenu && p2.containerMenu == p2.inventoryMenu;
                verdict("cerrar las dos pantallas (P1 con onClose, P2 con B); p1HalfScreen=" + SplitView.p1HalfScreen, closed);
                // (b) cofre en el servidor, abierto para P2
                mc.getSingleplayerServer().execute(() -> {
                    s2Pos = p2.blockPosition().above(2);
                    ServerLevel level = (ServerLevel) p2.level();
                    level.setBlockAndUpdate(s2Pos, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState());
                    var be = (net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(s2Pos);
                    be.setItem(0, new ItemStack(Items.GOLD_INGOT, 5));
                    p2.openMenu(be);
                });
            }
            case 140 -> {
                boolean isChest = c.screen instanceof net.minecraft.client.gui.screens.inventory.ContainerScreen;
                verdict("cofre abierto por el servidor en P2: " + (c.screen == null ? "null" : c.screen.getClass().getSimpleName()) + ", pantalla de P1=" + mc.screen,
                        isChest && mc.screen == null && c.screen.width == SecondaryScreens.regionWidth(mc));
                if (isChest) cursorOnSlot(c, 0);
            }
            case 145 -> simP2(1 << PadState.Y);
            case 151 -> simP2(0);
            case 165 -> {
                int gold = 0;
                for (int i = 0; i < 36; i++) {
                    ItemStack st = p2.getInventory().getItem(i);
                    if (st.is(Items.GOLD_INGOT)) gold += st.getCount();
                }
                boolean p1Gold = false;
                for (int i = 0; i < 36; i++) p1Gold |= p1.getInventory().getItem(i).is(Items.GOLD_INGOT);
                // Level.getBlockEntity devuelve null fuera del hilo del servidor: se lee alli.
                boolean chestEmpty;
                try {
                    chestEmpty = mc.getSingleplayerServer().submit(() -> {
                        var be = (net.minecraft.world.level.block.entity.ChestBlockEntity) p2.level().getBlockEntity(s2Pos);
                        return be != null && be.getItem(0).isEmpty();
                    }).get(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                    chestEmpty = false;
                }
                verdict("mover rapido del cofre al inventario de P2: oro en P2=" + gold + " (5), cofre vacio=" + chestEmpty + ", P1 sin oro=" + !p1Gold,
                        gold == 5 && chestEmpty && !p1Gold);
                simP2(1 << PadState.B);
            }
            case 171 -> simP2(0);
            case 185 -> {
                verdict("cerrar el cofre con B: pantalla de P2 null=" + (c.screen == null) + ", contenedor cerrado en el servidor=" + (p2.containerMenu == p2.inventoryMenu),
                        c.screen == null && p2.containerMenu == p2.inventoryMenu);
                // mesa de crafteo
                mc.getSingleplayerServer().execute(() -> {
                    ServerLevel level = (ServerLevel) p2.level();
                    level.setBlockAndUpdate(s2Pos, net.minecraft.world.level.block.Blocks.CRAFTING_TABLE.defaultBlockState());
                    p2.openMenu(new net.minecraft.world.SimpleMenuProvider((id, inv, pl) -> new net.minecraft.world.inventory.CraftingMenu(id, inv,
                            net.minecraft.world.inventory.ContainerLevelAccess.create(level, s2Pos)), net.minecraft.network.chat.Component.literal("Crafting")));
                });
            }
            case 210 -> {
                boolean craft = c.screen instanceof net.minecraft.client.gui.screens.inventory.CraftingScreen;
                verdict("mesa de crafteo abierta por el servidor en P2: " + (c.screen == null ? "null" : c.screen.getClass().getSimpleName()) + ", pantalla de P1=" + mc.screen,
                        craft && mc.screen == null);
                simP2(1 << PadState.B);
            }
            case 216 -> simP2(0);
            case 230 -> {
                verdict("cerrar la mesa con B", c.screen == null && p2.containerMenu == p2.inventoryMenu);
                ConsolePad.LOGGER.info("[autotest] RESULTADO pantallas 4c-2 en conjunto -> {}", s2Ok ? "OK" : "FALLO");
                mc.getSingleplayerServer().execute(() -> {
                    ((ServerLevel) p2.level()).setBlockAndUpdate(s2Pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                    p1.setGameMode(s2Prev1);
                    p2.setGameMode(s2Prev2);
                });
                SecondaryTick.scriptActive = false;
                simP2(0);
                next();
            }
            default -> { }
        }
    }

    private static Vec3 pmStart;
    private static long pmServerTime0, pmClientTime0;
    private static boolean pmOk = true;

    private static void pmVerdict(String name, boolean ok) {
        pmOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /**
     * Paso 4d: menu de pausa por jugador. La pausa de P1 (PauseScreen) se encaja en su mitad; P2 abre su propio menu con PAUSE:
     * no pausa el mundo, P2 no se mueve mientras esta en el menu, "Volver" lo cierra con el cursor de su mando y "Salir" saca solo a P2
     * (P1 sigue en el mundo). Despues P2 vuelve a entrar para el resto de pruebas.
     */
    private static void pauseMenuTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        switch (ticks) {
            case 1 -> {
                SecondaryTick.scriptActive = true;
                SecondaryTick.scriptForward = 0f;
            }
            case 5 -> press(PadAction.PAUSE);
            case 7 -> release();
            case 15 -> {
                boolean p2Menu = c.screen instanceof PlayerPauseScreen && c.screen.width == SecondaryScreens.regionWidth(mc);
                pmVerdict("menu de pausa de P2 abierto en su mitad: " + (c.screen == null ? "null" : c.screen.getClass().getSimpleName())
                        + ", pantalla de P1=" + mc.screen, p2Menu && mc.screen == null);
                mc.pauseGame(false); // pausa de P1 a la vez
                pmStart = p2.position();
                pmServerTime0 = mc.getSingleplayerServer().overworld().getGameTime();
                pmClientTime0 = mc.level.getGameTime();
                SecondaryTick.scriptForward = 1f; // si P2 no estuviera en el menu, andaria
            }
            case 25 -> {
                boolean p1Pause = mc.screen instanceof PlayerPauseScreen && mc.screen.width == SplitView.p1LogicalW;
                pmVerdict("pausa de P1 en su mitad: " + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()) + " ancho "
                        + (mc.screen == null ? -1 : mc.screen.width) + " (su region: " + SplitView.p1LogicalW + ")", p1Pause);
                screenshotName = "split-pause-menus.png";
                screenshotPending = true;
            }
            case 40 -> mc.setScreen(null);
            case 70 -> {
                double moved = p2.position().distanceTo(pmStart);
                long st = mc.getSingleplayerServer().overworld().getGameTime() - pmServerTime0;
                long ct = mc.level.getGameTime() - pmClientTime0;
                boolean ok = moved < 0.5 && st >= 40 && ct >= 40 && !mc.isPaused();
                pmVerdict("con los dos menus abiertos: P2 se movio " + String.format(java.util.Locale.ROOT, "%.2f", moved) + " bloques (<0.5), ticks de servidor " + st
                        + " y de cliente " + ct + " (>=40), mc.isPaused=" + mc.isPaused(), ok);
                int[] r = ((PlayerPauseScreen) c.screen).resumeCenter();
                c.cursorX = r[0];
                c.cursorY = r[1];
            }
            case 75 -> simP2(1 << PadState.A);
            case 81 -> simP2(0);
            case 90 -> {
                pmVerdict("\"Volver\" con el cursor de P2 cierra su menu: pantalla de P2 null=" + (c.screen == null), c.screen == null);
                pmStart = p2.position();
            }
            case 120 -> {
                double moved = p2.position().distanceTo(pmStart);
                pmVerdict("P2 vuelve a moverse al cerrar el menu: " + String.format(java.util.Locale.ROOT, "%.2f", moved) + " bloques (>=2)", moved >= 2.0);
                SecondaryTick.scriptForward = 0f;
                press(PadAction.PAUSE);
            }
            case 122 -> release();
            case 135 -> {
                if (c.screen instanceof PlayerPauseScreen menu) {
                    int[] q = menu.quitCenter();
                    c.cursorX = q[0];
                    c.cursorY = q[1];
                }
            }
            case 140 -> simP2(1 << PadState.A);
            case 146 -> simP2(0);
            case 175 -> {
                boolean gone = !SecondaryPlayers.isActive() && serverP2(mc) == null;
                boolean p1ok = mc.player != null && mc.level != null && mc.screen == null && mc.getSingleplayerServer() != null;
                pmVerdict("\"Salir\" saca solo a P2: P2 fuera=" + gone + ", P1 sigue en el mundo=" + p1ok, gone && p1ok);
                SecondaryPlayers.join(mc);
            }
            default -> {
                if (ticks > 250 && p2Ready(mc)) {
                    ConsolePad.LOGGER.info("[autotest] RESULTADO menus de pausa 4d en conjunto -> {}", pmOk ? "OK" : "FALLO");
                    SecondaryTick.scriptActive = false;
                    simP2(0);
                    next();
                } else if (ticks > 1400) {
                    ConsolePad.LOGGER.info("[autotest] RESULTADO menus de pausa 4d: P2 no volvio a entrar -> FALLO");
                    next();
                }
            }
        }
    }

    /** P2 dentro, con su nivel y el chunk bajo sus pies cargados en el cliente, y en el suelo en el servidor (puede andar). */
    private static boolean p2Ready(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        return c != null && c.player != null && c.level != null && p2 != null && p2.onGround()
                && c.level.hasChunkAt(c.player.blockPosition());
    }

    private static boolean jlOk = true;

    private static void jlVerdict(String name, boolean ok) {
        jlOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /**
     * Paso 4e: entrada y salida de P2 con Start+Back del slot 1. Una pulsacion corta (1 s) no lo saca; mantenerlo (3,5 s) lo saca sin
     * romper a P1; pulsar otra vez lo mete y conserva su inventario (el cofre y el inventario de pruebas anteriores).
     */
    private static void joinLeaveTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        int both = (1 << PadState.START) | (1 << PadState.BACK);
        switch (ticks) {
            case 1 -> simP2(both);
            case 22 -> simP2(0);
            case 35 -> jlVerdict("pulsacion corta (1 s) de Start+Back no saca a P2: P2 dentro=" + SecondaryPlayers.isActive(), SecondaryPlayers.isActive());
            case 40 -> simP2(both);
            case 110 -> simP2(0);
            case 120 -> {
                boolean gone = !SecondaryPlayers.isActive() && serverP2(mc) == null;
                boolean p1ok = mc.player != null && mc.level != null && mc.getSingleplayerServer() != null;
                jlVerdict("mantener Start+Back 2 s saca a P2 (P2 fuera=" + gone + ") y P1 sigue (" + p1ok + ")", gone && p1ok);
            }
            case 130 -> simP2(both);
            case 135 -> simP2(0);
            default -> {
                if (ticks > 250 && ticks < 5000 && p2Ready(mc)) {
                    ServerPlayer p2 = serverP2(mc);
                    ItemStack kept = p2.getInventory().getItem(22);
                    jlVerdict("pulsar Start+Back mete a P2 otra vez y conserva su inventario: slot 22 = " + kept.getItem() + " x" + kept.getCount() + " (adoquin x7)",
                            kept.is(Items.COBBLESTONE) && kept.getCount() == 7);
                    ConsolePad.LOGGER.info("[autotest] RESULTADO entrada y salida por mando 4e en conjunto -> {}", jlOk ? "OK" : "FALLO");
                    next();
                } else if (ticks >= 5000) {
                    ConsolePad.LOGGER.info("[autotest] RESULTADO entrada y salida por mando 4e: P2 no volvio a entrar -> FALLO");
                    next();
                }
            }
        }
    }

    private static int mtPhase;
    private static boolean mtOk = true;
    private static net.minecraft.world.level.GameType mtPrev1, mtPrev2;
    private static long mtFpsSum;
    private static int mtFpsN;

    private static void mtVerdict(String name, boolean ok) {
        mtOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static ServerPlayer serverExtra(Minecraft mc, int slot) {
        return mc.getSingleplayerServer() == null ? null
                : mc.getSingleplayerServer().getPlayerList().getPlayerByName(SecondaryPlayers.secondaryName(mc, slot));
    }

    private static boolean extraReady(Minecraft mc, int slot) {
        PlayerContext c = SecondaryPlayers.context(slot);
        ServerPlayer sp = serverExtra(mc, slot);
        return c != null && c.player != null && c.level != null && sp != null && sp.onGround() && c.level.hasChunkAt(c.player.blockPosition());
    }

    /** Las regiones cubren la ventana entera sin solaparse (area total = ventana, todas dentro de ella, sin interseccion). */
    private static boolean regionsTileWindow(Minecraft mc, java.util.List<PlayerContext> extras) {
        int W = mc.getWindow().getWidth(), H = mc.getWindow().getHeight();
        java.util.List<int[]> r = new java.util.ArrayList<>();
        r.add(SplitView.p1Px.clone());
        for (PlayerContext c : extras) r.add(new int[] {c.px, c.py, c.pw, c.ph});
        long area = 0;
        for (int[] a : r) {
            if (a[0] < 0 || a[1] < 0 || a[0] + a[2] > W || a[1] + a[3] > H || a[2] <= 0 || a[3] <= 0) return false;
            area += (long) a[2] * a[3];
        }
        for (int i = 0; i < r.size(); i++) {
            for (int j = i + 1; j < r.size(); j++) {
                int[] a = r.get(i), b = r.get(j);
                boolean overlap = a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
                if (overlap) return false;
            }
        }
        return area == (long) W * H;
    }

    /** Graba aspecto y HUD durante unos ticks (llamado con ticks == 1) y los evalua (ticks == 12) para n jugadores. */
    private static void mtRecordStart() {
        SplitView.enabled = true;
        SplitDiag.clear();
        SplitDiag.recording = true;
        SplitDiag.hudClear();
        SplitDiag.hudRecording = true;
    }

    private static void mtEvaluate(Minecraft mc, int n) {
        SplitDiag.recording = false;
        SplitDiag.hudRecording = false;
        java.util.List<PlayerContext> extras = SplitRender.activeContexts();
        boolean count = extras.size() == n - 1;
        int bad = SplitDiag.check(n + "j", 0.01);
        java.util.Set<String> passes = new java.util.TreeSet<>();
        for (SplitDiag.Sample sm : SplitDiag.samples()) passes.add(sm.pass());
        boolean allPasses = passes.size() == n;
        boolean tile = regionsTileWindow(mc, extras);
        StringBuilder regs = new StringBuilder("P1=" + java.util.Arrays.toString(SplitView.p1Px));
        for (PlayerContext c : extras) regs.append(' ').append(c.label()).append('=').append(java.util.Arrays.toString(new int[] {c.px, c.py, c.pw, c.ph}));
        mtVerdict(n + " jugadores: aspecto y regiones: pasadas=" + passes + " (" + n + "), muestras fuera de tolerancia=" + bad + ", regiones cubren la ventana sin solapes=" + tile
                + " [" + regs + "]", count && allPasses && bad == 0 && tile);
        var seen = SplitDiag.hudSeen();
        StringBuilder missing = new StringBuilder();
        for (int i = 1; i <= n; i++) {
            var layers = seen.getOrDefault("P" + i, new java.util.TreeMap<>());
            for (String l : HUD_LAYERS) if (!layers.containsKey(l)) missing.append('P').append(i).append(':').append(l).append(' ');
        }
        mtVerdict(n + " jugadores: HUD por jugador: capas que faltan=[" + missing.toString().trim() + "], capas con datos del jugador equivocado=" + SplitDiag.hudWrong()
                + " " + SplitDiag.hudWrongWhat(), missing.length() == 0 && SplitDiag.hudWrong() == 0);
    }

    /** P1 con 4 jugadores: su inventario cabe en su cuadrante (escala < 1) y un clic por la ruta real del raton coge el objeto. */
    private static void mtP1Inventory(Minecraft mc, boolean start) {
        var win = mc.getWindow();
        var mouse = (dev.consolepad.mixin.MouseHandlerInvoker) mc.mouseHandler;
        if (start) {
            boolean open = mc.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
            if (!open) {
                mtVerdict("P1: inventario abierto en su cuadrante", false);
                return;
            }
            var scr = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
            float k = SplitView.p1ScreenScale;
            int[] r = SplitView.p1Gui;
            int lw = SplitView.p1LogicalW, lh = SplitView.p1LogicalH;
            boolean sized = scr.width == lw && scr.height == lh;
            boolean fits = scr.getGuiLeft() >= 0 && scr.getGuiTop() >= 0 && scr.getGuiLeft() + 176 <= lw && scr.getGuiTop() + 166 <= lh;
            mtVerdict("P1: inventario en su cuadrante (" + r[2] + "x" + r[3] + " unidades, escala " + String.format(java.util.Locale.ROOT, "%.2f", k) + ", logico " + lw + "x" + lh
                    + "): tamano logico=" + sized + ", cabe entero=" + fits, k < 1f && sized && fits);
            net.minecraft.world.inventory.Slot sl = scr.getMenu().slots.get(20);
            double gx = (scr.getGuiLeft() + sl.x + 8) * k, gy = (scr.getGuiTop() + sl.y + 8) * k; // unidades de GUI de la ventana
            double sx = gx * win.getScreenWidth() / win.getGuiScaledWidth(), sy = gy * win.getScreenHeight() / win.getGuiScaledHeight();
            mouse.consolepad$onMove(win.getWindow(), sx, sy);
            mouse.consolepad$onPress(win.getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, org.lwjgl.glfw.GLFW.GLFW_PRESS, 0);
        } else {
            ItemStack carried = mc.player.containerMenu.getCarried();
            mouse.consolepad$onPress(win.getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT, org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
            mtVerdict("P1: clic real del raton sobre un hueco de su inventario escalado: objeto en el cursor = " + carried.getItem() + " x" + carried.getCount() + " (10 de tierra)",
                    carried.is(Items.DIRT) && carried.getCount() == 10);
            mc.setScreen(null);
        }
    }

    private static void mtOpenInventory(Minecraft mc, PlayerContext c) {
        ContextSwitcher.run(c, () -> mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(c.player)));
    }

    private static void mtCheckInventoryFits(Minecraft mc, PlayerContext c) {
        boolean open = c.screen instanceof net.minecraft.client.gui.screens.inventory.InventoryScreen;
        int lw = SplitLayout.logicalWidth(mc, c), lh = SplitLayout.logicalHeight(mc, c);
        boolean sized = open && c.screen.width == lw && c.screen.height == lh;
        boolean fits = false;
        if (open) {
            var scr = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) c.screen;
            fits = scr.getGuiLeft() >= 0 && scr.getGuiTop() >= 0 && scr.getGuiLeft() + 176 <= lw && scr.getGuiTop() + 166 <= lh;
        }
        mtVerdict(c.label() + ": inventario en su region (" + c.gw + "x" + c.gh + " unidades, escala " + String.format(java.util.Locale.ROOT, "%.2f", c.screenScale)
                + ", logico " + lw + "x" + lh + "): abierto=" + open + ", tamano logico=" + sized + ", cabe entero=" + fits, open && sized && fits);
        ContextSwitcher.run(c, () -> {
            if (mc.screen != null) mc.screen.onClose();
        });
    }

    /**
     * Paso 4f: 3 y 4 jugadores. Con P3 (mitad + dos cuartos) y con P4 (cuadrantes): aspecto por region, las regiones cubren la
     * ventana sin solaparse, HUD completo por jugador, el inventario de cada extra cabe en su region, FPS; despues P4 y P3 salen
     * y se vuelve a 2 jugadores.
     */
    private static void multiTest(Minecraft mc) {
        ServerPlayer p1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        switch (mtPhase) {
            case 0 -> {
                ServerPlayer p2 = serverP2(mc);
                mtPrev1 = p1.gameMode.getGameModeForPlayer();
                mtPrev2 = p2.gameMode.getGameModeForPlayer();
                mc.getSingleplayerServer().execute(() -> {
                    p1.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                    p2.setGameMode(net.minecraft.world.level.GameType.SURVIVAL);
                });
                SecondaryPlayers.join(mc, 2);
                mtPhase = 1;
                ticks = 0;
            }
            case 1 -> {
                if (ticks < 100 && !(ticks > 20 && extraReady(mc, 2))) return;
                if (!extraReady(mc, 2)) {
                    if (ticks < 1500) return;
                    mtVerdict("P3 entro en la partida", false);
                    mtPhase = 100;
                    return;
                }
                mc.getSingleplayerServer().execute(() -> serverExtra(mc, 2).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
                mtPhase = 2;
                ticks = 0;
            }
            case 2 -> {
                if (ticks == 20) mtRecordStart();
                if (ticks == 32) {
                    mtEvaluate(mc, 3);
                    mtOpenInventory(mc, SecondaryPlayers.context(2));
                    mtPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (ticks == 15) {
                    mtCheckInventoryFits(mc, SecondaryPlayers.context(2));
                    SecondaryPlayers.join(mc, 3);
                    mtPhase = 4;
                    ticks = 0;
                }
            }
            case 4 -> {
                if (ticks < 100 && !(ticks > 20 && extraReady(mc, 3))) return;
                if (!extraReady(mc, 3)) {
                    if (ticks < 1500) return;
                    mtVerdict("P4 entro en la partida", false);
                    mtPhase = 100;
                    return;
                }
                mc.getSingleplayerServer().execute(() -> serverExtra(mc, 3).setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
                mtPhase = 5;
                ticks = 0;
            }
            case 5 -> {
                if (ticks == 20) mtRecordStart();
                if (ticks == 32) {
                    mtEvaluate(mc, 4);
                    mc.getSingleplayerServer().execute(() -> {
                        p1.getInventory().setItem(20, new ItemStack(Items.DIRT, 10));
                        p1.getInventory().setItem(21, ItemStack.EMPTY);
                    });
                    mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)); // P1 tambien, en su cuadrante
                    mtOpenInventory(mc, SecondaryPlayers.context(1));
                    mtOpenInventory(mc, SecondaryPlayers.context(2));
                    mtOpenInventory(mc, SecondaryPlayers.context(3));
                    mtPhase = 6;
                    ticks = 0;
                }
            }
            case 6 -> {
                if (ticks == 15) {
                    screenshotName = "split-four-screens.png";
                    screenshotPending = true;
                }
                if (ticks == 20) {
                    mtCheckInventoryFits(mc, SecondaryPlayers.context(1));
                    mtCheckInventoryFits(mc, SecondaryPlayers.context(2));
                    mtCheckInventoryFits(mc, SecondaryPlayers.context(3));
                    mtP1Inventory(mc, true);
                }
                if (ticks == 26) {
                    mtP1Inventory(mc, false);
                    mc.options.renderDistance().set(8);
                    mc.options.enableVsync().set(false);
                    mc.options.framerateLimit().set(260);
                    mtPhase = 7;
                    ticks = 0;
                    mtFpsSum = 0;
                    mtFpsN = 0;
                }
            }
            case 7 -> {
                if (ticks > 60 && ticks % 10 == 0) {
                    mtFpsSum += mc.getFps();
                    mtFpsN++;
                }
                if (ticks >= 160) {
                    ConsolePad.LOGGER.info("[autotest] FPS con 4 jugadores (854x480 aprox., distancia 8): media {}", mtFpsN == 0 ? 0 : mtFpsSum / mtFpsN);
                    SecondaryPlayers.leave(mc, 3);
                    mtPhase = 8;
                    ticks = 0;
                }
            }
            case 8 -> {
                boolean gone = !SecondaryPlayers.isActive(3) && serverExtra(mc, 3) == null;
                if (!gone && ticks < 400) return;
                mtVerdict("P4 sale: fuera=" + gone + ", jugadores dibujados=" + (1 + SplitRender.activeContexts().size()) + " (3)", gone && SplitRender.activeContexts().size() == 2);
                mtFpsSum = 0;
                mtFpsN = 0;
                mtPhase = 9;
                ticks = 0;
            }
            case 9 -> {
                if (ticks > 60 && ticks % 10 == 0) {
                    mtFpsSum += mc.getFps();
                    mtFpsN++;
                }
                if (ticks >= 160) {
                    ConsolePad.LOGGER.info("[autotest] FPS con 3 jugadores (distancia 8): media {}", mtFpsN == 0 ? 0 : mtFpsSum / mtFpsN);
                    SecondaryPlayers.leave(mc, 2);
                    mtPhase = 10;
                    ticks = 0;
                }
            }
            case 10 -> {
                boolean gone = !SecondaryPlayers.isActive(2) && serverExtra(mc, 2) == null;
                if (!gone && ticks < 400) return;
                boolean p2Still = SecondaryPlayers.isActive(1) && serverP2(mc) != null;
                mtVerdict("P3 sale: fuera=" + gone + ", P2 sigue dentro=" + p2Still + ", jugadores dibujados=" + (1 + SplitRender.activeContexts().size()) + " (2)",
                        gone && p2Still && SplitRender.activeContexts().size() == 1);
                mtPhase = 100;
            }
            case 100 -> {
                ConsolePad.LOGGER.info("[autotest] RESULTADO 3 y 4 jugadores (4f) en conjunto -> {}", mtOk ? "OK" : "FALLO");
                ServerPlayer p2 = serverP2(mc);
                mc.getSingleplayerServer().execute(() -> {
                    p1.setGameMode(mtPrev1);
                    if (p2 != null) p2.setGameMode(mtPrev2);
                });
                mtPhase = 101;
                next();
            }
            default -> { }
        }
    }

    private static boolean ntOk = true;
    private static float ntP1Speed;

    private static void ntVerdict(String name, boolean ok) {
        ntOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static boolean ntHasFov(String pass, double target) {
        for (SplitDiag.Sample sm : SplitDiag.samples()) {
            if (sm.pass().equals(pass) && sm.where().equals("getProjectionMatrix") && Math.abs(sm.fovDeg() - target) < 0.6) return true;
        }
        return false;
    }

    /**
     * Paso 4g: avisos y ajustes por jugador. (1) Un mensaje de sistema, uno sobre la barra y un titulo enviados solo a P2 acaban en el
     * contexto de P2 (y se dibujan en su region) y no en el chat de P1. (2) FOV propio: P1 a 90 y P2 a 60 se miden en la proyeccion de
     * cada pasada. (3) Desde el menu de pausa de P2 se cambian su FOV y su sensibilidad con el cursor de su mando.
     */
    private static void notesTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        var pads0 = ConsolePad.PADS.slot(0).settings;
        var pads1 = ConsolePad.PADS.slot(1).settings;
        switch (ticks) {
            case 1 -> {
                SplitNotices.p1Log.clear();
                SplitView.enabled = true;
                ntP1Speed = pads0.lookSpeed;
                mc.getSingleplayerServer().execute(() -> {
                    p2.sendSystemMessage(net.minecraft.network.chat.Component.literal("aviso para P2"));
                    p2.displayClientMessage(net.minecraft.network.chat.Component.literal("barra de P2"), true);
                    p2.connection.send(new net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket(net.minecraft.network.chat.Component.literal("TITULO P2")));
                });
            }
            case 30 -> {
                boolean chat = c.notices.stream().anyMatch(n -> n.text().getString().equals("aviso para P2"));
                boolean overlay = c.overlay != null && c.overlay.getString().equals("barra de P2");
                boolean title = c.title != null && c.title.getString().equals("TITULO P2");
                var p1Title = ((dev.consolepad.mixin.GuiStateAccessor) mc.gui).consolepad$getTitle();
                boolean p1NoTitle = p1Title == null || !p1Title.getString().equals("TITULO P2");
                boolean p1NoChat = SplitNotices.p1Log.stream().noneMatch(m -> m.contains("aviso para P2"));
                boolean drawn = c.noticesDrawn >= 3;
                ntVerdict("avisos de P2: chat propio=" + chat + ", mensaje sobre la barra=" + overlay + ", titulo=" + title + ", dibujados=" + c.noticesDrawn + " (>=3), "
                        + "el chat de P1 no recibe el aviso=" + p1NoChat + ", el titulo de P1 sigue sin el de P2=" + p1NoTitle, chat && overlay && title && drawn && p1NoChat && p1NoTitle);
            }
            case 40 -> {
                pads0.fov = 90;
                pads1.fov = 60;
                SplitDiag.clear();
                SplitDiag.recording = true;
            }
            case 52 -> {
                SplitDiag.recording = false;
                boolean ok = ntHasFov("P1", 90) && !ntHasFov("P1", 60) && ntHasFov("P2", 60) && !ntHasFov("P2", 90);
                ntVerdict("FOV propio por jugador: P1 a 90 (" + ntHasFov("P1", 90) + "), P2 a 60 (" + ntHasFov("P2", 60) + "), sin mezcla", ok);
                pads0.fov = 0;
                pads1.fov = 0;
                pads1.lookSpeed = 180f; // punto de partida conocido para el ciclo del menu
                SecondaryTick.scriptActive = true;
                SecondaryTick.scriptForward = 0f;
                press(PadAction.PAUSE);
            }
            case 54 -> release();
            case 66 -> {
                if (c.screen instanceof PlayerPauseScreen m) {
                    int[] f = m.settingsCenter();
                    c.cursorX = f[0];
                    c.cursorY = f[1];
                }
            }
            case 68 -> simP2(1 << PadState.A);
            case 74 -> simP2(0);
            case 80 -> {
                if (c.screen instanceof PlayerSettingsScreen m) {
                    int[] f = m.fovCenter();
                    c.cursorX = f[0];
                    c.cursorY = f[1];
                }
            }
            case 83 -> simP2(1 << PadState.A);
            case 89 -> simP2(0);
            case 98 -> {
                ntVerdict("ajuste de FOV de P2 desde su menu: fov de su slot=" + pads1.fov + " (esperado 60), P1=" + pads0.fov + " (0)", pads1.fov == 60 && pads0.fov == 0);
                if (c.screen instanceof PlayerSettingsScreen m) {
                    int[] f = m.sensCenter();
                    c.cursorX = f[0];
                    c.cursorY = f[1];
                }
            }
            case 101 -> simP2(1 << PadState.A);
            case 107 -> simP2(0);
            case 116 -> {
                ntVerdict("ajuste de sensibilidad de P2 desde su menu: velocidad de giro=" + pads1.lookSpeed + " (esperado 225), P1=" + pads0.lookSpeed + " (sin cambios)",
                        Math.abs(pads1.lookSpeed - 225f) < 1f && Math.abs(pads0.lookSpeed - ntP1Speed) < 1f);
                pads1.fov = 0;
                pads1.lookSpeed = 180f;
                simP2(1 << PadState.B);
            }
            case 122 -> simP2(0);
            case 128 -> {
                ntVerdict("B en los ajustes vuelve al menu de P2", c.screen instanceof PlayerPauseScreen);
                simP2(1 << PadState.B);
            }
            case 134 -> simP2(0);
            case 145 -> {
                ntVerdict("cerrar el menu de P2 con B tras los ajustes", c.screen == null);
                ConsolePad.LOGGER.info("[autotest] RESULTADO avisos y ajustes por jugador (4g) en conjunto -> {}", ntOk ? "OK" : "FALLO");
                SecondaryTick.scriptActive = false;
                simP2(0);
                next();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- ayudas para pruebas con 3 y 4 jugadores

    /** Mete jugadores hasta tener n en total (mando virtual incluido). Se llama cada tick; true cuando los n estan dentro y listos. */
    private static boolean joinTo(Minecraft mc, int n) {
        for (int slot = 1; slot < n; slot++) {
            pads.attachVirtual(slot);
            if (!SecondaryPlayers.isActive(slot)) {
                SecondaryPlayers.join(mc, slot);
                return false;
            }
            if (!extraReady(mc, slot)) return false;
        }
        return true;
    }

    /** Saca jugadores hasta dejar n en total. true cuando los de fuera ya no estan ni en el cliente ni en el servidor. */
    private static boolean leaveTo(Minecraft mc, int n) {
        boolean done = true;
        for (int slot = SecondaryPlayers.MAX_EXTRA; slot >= n; slot--) {
            if (SecondaryPlayers.isActive(slot)) {
                SecondaryPlayers.leave(mc, slot);
                done = false;
            } else if (serverExtra(mc, slot) != null) {
                done = false;
            }
        }
        return done;
    }

    private static net.minecraft.client.gui.screens.Screen screenOf(Minecraft mc, int slot) {
        if (slot == 0) return mc.screen;
        PlayerContext c = SecondaryPlayers.context(slot);
        return c == null ? null : c.screen;
    }

    private static boolean isPauseMenu(net.minecraft.client.gui.screens.Screen s) {
        return s instanceof net.minecraft.client.gui.screens.PauseScreen || s instanceof PlayerPauseScreen;
    }

    /** Pantallas abiertas de los slots 0..3 como texto ("-" = ninguna). */
    private static String screensDigest(Minecraft mc) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            var sc = SecondaryPlayers.isActive(i) || i == 0 ? screenOf(mc, i) : null;
            b.append('P').append(i + 1).append('=').append(sc == null ? "-" : sc.getClass().getSimpleName()).append(' ');
        }
        return b.toString().trim();
    }

    private static boolean onlyOpen(Minecraft mc, int slot, boolean pauseMenu) {
        for (int i = 0; i < 4; i++) {
            if (i != slot && SecondaryPlayers.isActive(i) | i == 0) {
                if (screenOf(mc, i) != null) return false;
            }
        }
        var sc = screenOf(mc, slot);
        return pauseMenu ? isPauseMenu(sc) : sc == null;
    }

    // ---------------------------------------------------------------- HUD de P2 a P4: calabaza, barras de jefe, marcador y lista de jugadores

    private static boolean hbdOk = true;
    private static int hbdPhase;
    private static volatile int hbdCornerP2, hbdCornerP3;
    private static net.minecraft.server.level.ServerBossEvent hbdBoss;
    private static net.minecraft.world.scores.Objective hbdObjective;

    private static void hbdVerdict(String name, boolean ok) {
        hbdOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Brillo medio de un bloque de pixeles junto a la esquina superior izquierda de una region. */
    private static int hbdCorner(com.mojang.blaze3d.platform.NativeImage img, PlayerContext c) {
        int x = c.px + 3, y = img.getHeight() - (c.py + c.ph) + 3, sum = 0;
        for (int dy = 0; dy < 4; dy++) {
            for (int dx = 0; dx < 4; dx++) {
                int rgba = img.getPixelRGBA(x + dx, y + dy);
                sum += (rgba & 0xFF) + ((rgba >> 8) & 0xFF) + ((rgba >> 16) & 0xFF);
            }
        }
        return sum / 16;
    }

    private static int hbdBossCount(net.minecraft.client.gui.components.BossHealthOverlay overlay) {
        try {
            var f = net.minecraft.client.gui.components.BossHealthOverlay.class.getDeclaredField("events");
            f.setAccessible(true);
            return ((java.util.Map<?, ?>) f.get(overlay)).size();
        } catch (ReflectiveOperationException e) {
            return -1;
        }
    }

    /**
     * Lo que antes solo dibujaba P1: la calabaza en la cabeza de P2 oscurece solo la region de P2; la barra de jefe que ve P3 sale solo en la de P3
     * (la de P1 y la de P2 siguen vacias); el marcador lateral sale en la region de cada jugador; y mantener el boton de lista de jugadores en el
     * mando de P3 dibuja la lista solo en su region, con el indicador de jugador local ("[P2]") en el nombre.
     */
    private static void hudBoardsTest(Minecraft mc) {
        int t = ticks;
        switch (hbdPhase) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (joinTo(mc, 3) && t > 20) {
                    var server = mc.getSingleplayerServer();
                    ServerPlayer sp2 = serverExtra(mc, 1), sp3 = serverExtra(mc, 2);
                    server.execute(() -> {
                        sp2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, new ItemStack(Items.CARVED_PUMPKIN));
                        hbdBoss = new net.minecraft.server.level.ServerBossEvent(net.minecraft.network.chat.Component.literal("Jefe de prueba"),
                                net.minecraft.world.BossEvent.BossBarColor.PINK, net.minecraft.world.BossEvent.BossBarOverlay.PROGRESS);
                        hbdBoss.addPlayer(sp3);
                        var sb = server.getScoreboard();
                        hbdObjective = sb.addObjective("cp_test", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, net.minecraft.network.chat.Component.literal("Prueba"),
                                net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, true, null);
                        sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, hbdObjective);
                        sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("linea"), hbdObjective).set(5);
                        sp2.refreshTabListName();
                    });
                    int list = PadHints.buttonFor(pads.slot(2).settings, PadAction.PLAYER_LIST);
                    simSlot(2, 1 << list); // P3 mantiene el boton de la lista de jugadores
                    hbdPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                PlayerContext c2 = SecondaryPlayers.context(1), c3 = SecondaryPlayers.context(2);
                if (t == 14) {
                    SplitDiag.hudClear();
                    SplitDiag.hudRecording = true;
                }
                if (t == 16) pixelProbe = img -> {
                    hbdCornerP2 = hbdCorner(img, c2);
                    hbdCornerP3 = hbdCorner(img, c3);
                };
                if (t == 28) {
                    SplitDiag.hudRecording = false;
                    var seen = SplitDiag.hudSeen();
                    hbdVerdict("calabaza en la cabeza de P2: brillo de la esquina de su region " + hbdCornerP2 + " contra " + hbdCornerP3 + " en la de P3 (debe ser menos de la mitad)",
                            hbdCornerP3 > 60 && hbdCornerP2 * 2 < hbdCornerP3);
                    int b1 = hbdBossCount(mc.gui.getBossOverlay()), b2 = hbdBossCount(c2.bossOverlay()), b3 = hbdBossCount(c3.bossOverlay());
                    hbdVerdict("barra de jefe vista por P3: barras de P1=" + b1 + ", P2=" + b2 + ", P3=" + b3 + " (esperado 0, 0, 1)", b1 == 0 && b2 == 0 && b3 == 1);
                    hbdVerdict("marcador lateral: capa dibujada en P2=" + seen.getOrDefault("P2", new java.util.TreeMap<>()).containsKey("marcador") + ", en P3="
                            + seen.getOrDefault("P3", new java.util.TreeMap<>()).containsKey("marcador"),
                            seen.getOrDefault("P2", new java.util.TreeMap<>()).containsKey("marcador") && seen.getOrDefault("P3", new java.util.TreeMap<>()).containsKey("marcador"));
                    hbdVerdict("superposiciones de camara (viñeta, calabaza, portal) dibujadas por jugador: P2="
                            + seen.getOrDefault("P2", new java.util.TreeMap<>()).containsKey("superposiciones") + ", P3=" + seen.getOrDefault("P3", new java.util.TreeMap<>()).containsKey("superposiciones"),
                            seen.getOrDefault("P2", new java.util.TreeMap<>()).containsKey("superposiciones") && seen.getOrDefault("P3", new java.util.TreeMap<>()).containsKey("superposiciones"));
                    boolean listP3 = seen.getOrDefault("P3", new java.util.TreeMap<>()).containsKey("lista de jugadores");
                    boolean listP2 = seen.getOrDefault("P2", new java.util.TreeMap<>()).containsKey("lista de jugadores");
                    hbdVerdict("lista de jugadores: mantenida en el mando de P3 se dibuja en P3=" + listP3 + " y no en P2=" + listP2, listP3 && !listP2);
                    String tab = String.valueOf(serverExtra(mc, 1).getTabListDisplayName());
                    hbdVerdict("indicador de jugador local en la lista: '" + tab + "' (debe contener [P2])", tab.contains("[P2]"));
                    simSlot(2, 0);
                    var server = mc.getSingleplayerServer();
                    ServerPlayer sp2 = serverExtra(mc, 1);
                    server.execute(() -> {
                        sp2.setItemSlot(net.minecraft.world.entity.EquipmentSlot.HEAD, ItemStack.EMPTY);
                        hbdBoss.removeAllPlayers();
                        server.getScoreboard().setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, null);
                        server.getScoreboard().removeObjective(hbdObjective);
                    });
                    ConsolePad.LOGGER.info("[autotest] RESULTADO HUD de P2 a P4: calabaza, barras de jefe, marcador y lista de jugadores en conjunto -> {}", hbdOk ? "OK" : "FALLO");
                    hbdPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (t > 10 && leaveTo(mc, 2) || t > 600) {
                    hbdPhase = 3;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- controles de movimiento por jugador

    private static boolean mvOk = true;
    private static int mvPhase;
    private static float mvYaw0, mvPitch0, mvYawDelta, mvPitchDelta;

    private static void mvVerdict(String name, boolean ok) {
        mvOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static void mvStick(int slot, float lx, float ly, float rx, float ry) {
        var st = pads.slot(slot).device.state;
        st.simLx = lx;
        st.simLy = ly;
        st.simRx = rx;
        st.simRy = ry;
    }

    private static net.minecraft.world.entity.player.Player mvPlayer(Minecraft mc, int slot) {
        return slot == 0 ? mc.player : SecondaryPlayers.context(slot).player;
    }

    private static void mvSetPitch(Minecraft mc, int slot, float pitch) {
        if (slot == 0) mc.player.setXRot(pitch);
        else ContextSwitcher.run(SecondaryPlayers.context(slot), () -> mc.player.setXRot(pitch));
    }

    /**
     * Cada jugador tiene su sensibilidad horizontal y vertical (el mismo empujon del stick derecho gira el doble en horizontal si es el doble), el eje Y
     * invertido cambia el signo del giro vertical y dos empujones seguidos del stick izquierdo hacia delante activan el sprint (solo si la opcion esta
     * activa) hasta soltar el stick. Se mide en P1 y en P2.
     */
    private static void moveTest(Minecraft mc) {
        int t = ticks;
        var cfgs = new PadSettings[] {pads.slot(0).settings, pads.slot(1).settings};
        switch (mvPhase) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (joinTo(mc, 2) && t > 20) {
                    for (var cfg : cfgs) {
                        cfg.lookSpeed = 180f;
                        cfg.lookSpeedY = 90f;
                        cfg.invertY = false;
                        cfg.doubleTapSprint = true;
                    }
                    mvPhase = 1;
                    ticks = 0;
                }
            }
            case 1, 2 -> { // mvPhase 1 = P1, 2 = P2: giro horizontal, vertical y vertical invertido
                int slot = mvPhase - 1;
                String who = "P" + (slot + 1);
                var player = mvPlayer(mc, slot);
                var cfg = cfgs[slot];
                if (t == 2) {
                    mvSetPitch(mc, slot, 0f);
                    mvYaw0 = player.getYRot();
                }
                if (t == 4) mvStick(slot, 0f, 0f, 1f, 0f);
                if (t == 24) {
                    mvStick(slot, 0f, 0f, 0f, 0f);
                    mvYawDelta = player.getYRot() - mvYaw0;
                }
                if (t == 30) mvPitch0 = player.getXRot();
                if (t == 32) mvStick(slot, 0f, 0f, 0f, 1f);
                if (t == 52) {
                    mvStick(slot, 0f, 0f, 0f, 0f);
                    mvPitchDelta = player.getXRot() - mvPitch0;
                    float ratio = mvPitchDelta == 0f ? 0f : mvYawDelta / mvPitchDelta;
                    mvVerdict(who + ": sensibilidad horizontal 180 y vertical 90: giro horizontal " + mvYawDelta + " grados, vertical " + mvPitchDelta + " (cociente " + ratio + ", esperado 2)",
                            mvYawDelta > 1f && mvPitchDelta > 0.5f && ratio > 1.7f && ratio < 2.3f);
                    cfg.invertY = true;
                    mvSetPitch(mc, slot, 0f);
                }
                if (t == 56) mvPitch0 = player.getXRot();
                if (t == 58) mvStick(slot, 0f, 0f, 0f, 1f);
                if (t == 70) {
                    mvStick(slot, 0f, 0f, 0f, 0f);
                    float d = player.getXRot() - mvPitch0;
                    mvVerdict(who + ": con el eje Y invertido el mismo empujon gira al reves (" + d + " grados, antes " + mvPitchDelta + ")", d < -0.3f && mvPitchDelta > 0f);
                    cfg.invertY = false;
                    mvSetPitch(mc, slot, 0f);
                    mvPhase = slot == 0 ? 2 : 3;
                    ticks = 0;
                }
            }
            case 3, 4 -> { // mvPhase 3 = P1, 4 = P2: sprint con doble toque
                int slot = mvPhase - 3;
                String who = "P" + (slot + 1);
                var cfg = cfgs[slot];
                boolean sprint = slot == 0 ? mc.options.keySprint.isDown() : SecondaryPlayers.context(slot).input.sprintHeld;
                if (t == 4) mvStick(slot, 0f, -1f, 0f, 0f); // primer empujon
                if (t == 7) mvStick(slot, 0f, 0f, 0f, 0f);
                if (t == 10) mvStick(slot, 0f, -1f, 0f, 0f); // segundo empujon, mantenido
                if (t == 14) {
                    mvVerdict(who + ": dos empujones seguidos del stick hacia delante activan el sprint (sprint=" + sprint + ")", sprint);
                }
                if (t == 24) mvStick(slot, 0f, 0f, 0f, 0f);
                if (t == 30) {
                    mvVerdict(who + ": al soltar el stick el sprint de doble toque se acaba (sprint=" + sprint + ")", !sprint);
                    cfg.doubleTapSprint = false;
                }
                if (t == 34) mvStick(slot, 0f, -1f, 0f, 0f);
                if (t == 37) mvStick(slot, 0f, 0f, 0f, 0f);
                if (t == 40) mvStick(slot, 0f, -1f, 0f, 0f);
                if (t == 46) {
                    mvVerdict(who + ": con la opcion desactivada dos empujones no activan el sprint (sprint=" + sprint + ")", !sprint);
                    mvStick(slot, 0f, 0f, 0f, 0f);
                    cfg.doubleTapSprint = true;
                    mvPhase = slot == 0 ? 4 : 5;
                    ticks = 0;
                }
            }
            case 5 -> {
                if (t == 1) ConsolePad.LOGGER.info("[autotest] RESULTADO controles de movimiento por jugador en conjunto -> {}", mvOk ? "OK" : "FALLO");
                if (t > 10) {
                    for (int i = 0; i < 2; i++) {
                        cfgs[i].lookSpeedY = 0f;
                        mvStick(i, 0f, 0f, 0f, 0f);
                    }
                    mvPhase = 6;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- opciones de HUD por jugador

    private static boolean hoOk = true;
    private static int hoPhase;
    private static int[] hoHands0 = new int[4];
    private static volatile int hoPixelA, hoPixelB;

    private static void hoVerdict(String name, boolean ok) {
        hoOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Suma de colores de un bloque de pixeles de la imagen final dentro de la region de un extra (ver la nota de hudOptionsTest). */
    private static int hoSelectedSlotPixels(com.mojang.blaze3d.platform.NativeImage img, PlayerContext c) {
        int eff = c.guiScaleEff;
        int unitX = c.hudW / 2 - 92 + c.player.getInventory().selected * 20 + 1; // borde izquierdo del marco del hueco elegido
        int unitY = c.hudH - 22 - 1 + 11;
        int x = c.px + unitX * eff;
        int top = img.getHeight() - (c.py + c.ph);
        int y = top + unitY * eff;
        int sum = 0;
        for (int dy = 0; dy < Math.max(2, eff); dy++) {
            for (int dx = 0; dx < Math.max(2, eff); dx++) {
                int rgba = img.getPixelRGBA(x + dx, y + dy);
                sum += (rgba & 0xFF) + ((rgba >> 8) & 0xFF) + ((rgba >> 16) & 0xFF);
            }
        }
        return sum / (Math.max(2, eff) * Math.max(2, eff));
    }

    /**
     * Opciones de HUD por jugador: la zona segura reduce el ancho y alto con que cada jugador dibuja su HUD (el de la region menos el margen de los
     * dos lados), ocultar el HUD o la mano de uno no afecta a los demas, y la opacidad cambia el color de los pixeles del HUD en la imagen final.
     */
    private static void hudOptionsTest(Minecraft mc) {
        int t = ticks;
        var s0 = pads.slot(0).settings;
        var s1 = pads.slot(1).settings;
        var s2 = pads.slot(2).settings;
        switch (hoPhase) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (joinTo(mc, 3) && t > 20) {
                    s0.hudMargin = 10;
                    s1.hudMargin = 10;
                    s2.showHud = false;
                    s2.showHand = false;
                    hoHands0 = SplitDiag.handsDrawn.clone();
                    SplitDiag.hudClear();
                    SplitDiag.hudRecording = true;
                    hoPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (t == 12) {
                    SplitDiag.hudRecording = false;
                    var seen = SplitDiag.hudSeen();
                    PlayerContext c2 = SecondaryPlayers.context(1), c3 = SecondaryPlayers.context(2);
                    int w1 = SplitView.p1HudW - 2 * Math.round(SplitView.p1HudW * 0.10f), w2 = c2.hudW - 2 * Math.round(c2.hudW * 0.10f);
                    int g1 = seen.getOrDefault("P1", new java.util.TreeMap<>()).getOrDefault("hotbar", -1);
                    int g2 = seen.getOrDefault("P2", new java.util.TreeMap<>()).getOrDefault("hotbar", -1);
                    hoVerdict("zona segura del 10 %: P1 dibuja su barra de objetos con ancho " + g1 + " (esperado " + w1 + " de " + SplitView.p1HudW + "), P2 con " + g2 + " (esperado " + w2 + " de " + c2.hudW + ")",
                            g1 == w1 && g2 == w2 && c2.safeW == w2);
                    var l3 = seen.getOrDefault("P3", new java.util.TreeMap<>());
                    hoVerdict("P3 con el HUD oculto no dibuja sus capas (" + l3.keySet() + ") pero conserva los avisos, y P1/P2 siguen con las suyas",
                            !l3.containsKey("hotbar") && !l3.containsKey("vida") && l3.containsKey("avisos") && g1 > 0 && g2 > 0);
                    int[] now = SplitDiag.handsDrawn;
                    hoVerdict("mano oculta de P3: manos dibujadas en los ultimos fotogramas P1 +" + (now[0] - hoHands0[0]) + ", P2 +" + (now[1] - hoHands0[1]) + ", P3 +" + (now[2] - hoHands0[2]) + " (P3 debe quedarse en 0)",
                            now[2] - hoHands0[2] == 0 && now[0] - hoHands0[0] > 0 && now[1] - hoHands0[1] > 0);
                    s0.hudMargin = 0;
                    s1.hudMargin = 0;
                    s2.showHud = true;
                    s2.showHand = true;
                    s1.hudOpacity = 1f;
                    hoPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                PlayerContext c2 = SecondaryPlayers.context(1);
                if (t == 8) pixelProbe = img -> hoPixelA = hoSelectedSlotPixels(img, c2);
                if (t == 12) s1.hudOpacity = 0.25f;
                if (t == 20) pixelProbe = img -> hoPixelB = hoSelectedSlotPixels(img, c2);
                if (t == 26) {
                    hoVerdict("opacidad del HUD de P2 al 25 %: color medio del marco del hueco elegido " + hoPixelA + " (100 %) -> " + hoPixelB + " (25 %), debe cambiar al menos 40",
                            Math.abs(hoPixelA - hoPixelB) >= 40);
                    s1.hudOpacity = 1f;
                    ConsolePad.LOGGER.info("[autotest] RESULTADO opciones de HUD por jugador en conjunto -> {}", hoOk ? "OK" : "FALLO");
                    hoPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (leaveTo(mc, 2) || t > 600) {
                    hoPhase = 4;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- etiqueta con nombre configurable sobre cada jugador local

    private static boolean lbOk = true;
    private static int lbPhase;

    private static void lbVerdict(String name, boolean ok) {
        lbOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static String lbName(net.minecraft.world.entity.player.Player p) {
        return p == null ? "(no existe)" : p.getDisplayName().getString();
    }

    /** Nombre que ve cada vista: P1 ve a P2, P2 ve a P1 y el servidor ve a los dos. */
    private static String lbViews(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        ServerPlayer sp2 = serverExtra(mc, 1);
        ServerPlayer sp1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        return "P1 ve a P2='" + lbName(mc.level.getPlayerByUUID(sp2.getUUID())) + "', P2 ve a P1='" + lbName(c.level.getPlayerByUUID(mc.player.getUUID()))
                + "', servidor P2='" + lbName(sp2) + "' P1='" + lbName(sp1) + "'";
    }

    private static boolean lbExpect(Minecraft mc, String p1, String p2) {
        PlayerContext c = SecondaryPlayers.context(1);
        ServerPlayer sp2 = serverExtra(mc, 1);
        ServerPlayer sp1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
        return lbName(mc.level.getPlayerByUUID(sp2.getUUID())).equals(p2) && lbName(c.level.getPlayerByUUID(mc.player.getUUID())).equals(p1)
                && lbName(sp2).equals(p2) && lbName(sp1).equals(p1);
    }

    /**
     * Cada jugador local lleva su nombre para mostrar ("Jugador N" por defecto, o el que elija en sus ajustes) en la etiqueta que ven los demas, en el
     * cliente de cada uno y en el servidor, sin tocar el perfil (nombre y UUID siguen siendo los de siempre: de ellos depende su archivo de datos).
     */
    private static void labelTest(Minecraft mc) {
        var s1 = pads.slot(1).settings;
        int t = ticks;
        switch (lbPhase) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (joinTo(mc, 2) && t > 20) {
                    PlayerLabels.refreshAll(mc);
                    lbPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (t == 8) {
                    ServerPlayer sp2 = serverExtra(mc, 1);
                    lbVerdict("nombre por defecto con P2 dentro: " + lbViews(mc), lbExpect(mc, "Jugador 1", "Jugador 2"));
                    lbVerdict("el perfil de P2 no cambia: '" + sp2.getGameProfile().getName() + "' (esperado '" + SecondaryPlayers.secondaryName(mc, 1) + "') y UUID " + sp2.getUUID(),
                            sp2.getGameProfile().getName().equals(SecondaryPlayers.secondaryName(mc, 1)) && sp2.getUUID().equals(net.minecraft.core.UUIDUtil.createOfflinePlayerUUID(SecondaryPlayers.secondaryName(mc, 1))));
                    s1.playerName = "Ana";
                    PlayerLabels.refreshAll(mc);
                }
                if (t == 16) {
                    lbVerdict("P2 se llama Ana en sus ajustes: " + lbViews(mc), lbExpect(mc, "Jugador 1", "Ana"));
                    s1.playerName = "";
                    PlayerLabels.refreshAll(mc);
                }
                if (t == 24) {
                    lbVerdict("al quitar el nombre vuelve a Jugador 2: " + lbViews(mc), lbExpect(mc, "Jugador 1", "Jugador 2"));
                    PlayerContext c = SecondaryPlayers.context(1);
                    ContextSwitcher.run(c, () -> mc.setScreen(new PlayerNameScreen(1, null)));
                }
                if (t == 34) {
                    PlayerContext c = SecondaryPlayers.context(1);
                    lbVerdict("la pantalla del nombre abre el teclado (pantalla " + c.screen + ", teclado=" + PadKeyboard.of(1).active() + ")", c.screen instanceof PlayerNameScreen && PadKeyboard.of(1).active());
                }
                kbStep(1, t, 36, 0, KB_A); // 'q'
                kbStep(1, t, 36, 1, KB_B); // cierra el teclado
                if (t == 36 + 2 * 6 - 2) {
                    PlayerContext c = SecondaryPlayers.context(1);
                    if (c.screen instanceof PlayerNameScreen ns) {
                        c.cursorX = ns.doneCenter()[0];
                        c.cursorY = ns.doneCenter()[1];
                    }
                }
                kbStep(1, t, 36, 2, KB_A); // Hecho
                if (t == 36 + 3 * 6 + 10) {
                    PlayerContext c = SecondaryPlayers.context(1);
                    lbVerdict("escribir 'q' en la pantalla del nombre y pulsar Hecho lo guarda en su slot: playerName='" + s1.playerName + "', pantalla " + c.screen + ", " + lbViews(mc),
                            "q".equals(s1.playerName) && c.screen == null && lbExpect(mc, "Jugador 1", "q"));
                    s1.playerName = "";
                    PlayerLabels.refreshAll(mc);
                    lbPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // P2 sale: P1 recupera su nombre real
                if (leaveTo(mc, 1) && t > 10) {
                    ServerPlayer sp1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    lbVerdict("sin jugadores extra P1 vuelve a su nombre real: '" + lbName(sp1) + "' (esperado '" + mc.player.getName().getString() + "')", lbName(sp1).equals(mc.player.getName().getString()));
                    ConsolePad.LOGGER.info("[autotest] RESULTADO etiquetas con nombre de los jugadores locales en conjunto -> {}", lbOk ? "OK" : "FALLO");
                    lbPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (joinTo(mc, 2) || t > 600) {
                    lbPhase = 4;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- teclado en pantalla por jugador

    private static boolean kbOk = true;
    private static int kbPhase;
    private static BlockPos kbSignPos;
    private static volatile String kbSignLine = "?";

    private static void kbVerdict(String name, boolean ok) {
        kbOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Pulsa un boton del slot en el tick base+paso*6 y lo suelta 3 ticks despues. */
    private static void kbStep(int slot, int t, int base, int step, int button) {
        int at = base + step * 6;
        if (t == at) simSlot(slot, 1 << button);
        if (t == at + 3) simSlot(slot, 0);
    }

    private static final int KB_A = PadState.A, KB_B = PadState.B, KB_X = PadState.X, KB_Y = PadState.Y, KB_UP = PadState.DPAD_UP,
            KB_DOWN = PadState.DPAD_DOWN, KB_RIGHT = PadState.DPAD_RIGHT;

    /**
     * Teclado en pantalla: (P2) el boton de chat abre el chat de P2 con el teclado abierto; escribir con A, espacio con Y, borrar con X, B cierra el
     * teclado y A sobre el campo de texto lo vuelve a abrir; la tecla Enviar manda el mensaje con la conexion de P2 (le llega como aviso en su region).
     * (P1) el chat de vanilla tambien abre el teclado y escribe en su campo. (Cartel) un cartel abierto para P2 se rellena con el teclado y llega al servidor.
     */
    private static void keyboardTest(Minecraft mc) {
        int t = ticks;
        var s0 = pads.slot(0).settings;
        var s1 = pads.slot(1).settings;
        switch (kbPhase) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (joinTo(mc, 2) && t > 20) {
                    PadKeyboard.of(1).resetTyped();
                    kbPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> { // chat de P2
                PlayerContext c = SecondaryPlayers.context(1);
                int chat = PadHints.buttonFor(s1, PadAction.CHAT);
                int base = 30;
                if (t == 5) simSlot(1, 1 << chat);
                if (t == 9) simSlot(1, 0);
                if (t == 20) {
                    kbVerdict("P2: el boton de chat abre su chat con el teclado abierto (pantalla " + c.screen + ", teclado=" + PadKeyboard.of(1).active() + ")",
                            c.screen instanceof ExtraChatScreen && PadKeyboard.of(1).active());
                    screenshotName = "consola-teclado-p2.png";
                    screenshotPending = true;
                    kbVerdict("barra de ayuda con el teclado abierto: '" + PadHints.lastBar[1] + "'", PadHints.lastBar[1].startsWith("0:Escribir|1:Cerrar|2:Borrar|3:Espacio"));
                }
                int[] seq = {KB_A, KB_RIGHT, KB_A, KB_Y, KB_X, KB_DOWN, KB_A, KB_B, -1, KB_DOWN, KB_DOWN, KB_DOWN, KB_RIGHT, KB_RIGHT, KB_RIGHT, KB_RIGHT, KB_A};
                for (int k = 0; k < seq.length; k++) {
                    if (seq[k] < 0) { // A sobre el campo de texto: reabre el teclado
                        if (t == base + k * 6 - 2 && c.screen instanceof ExtraChatScreen cs) {
                            c.cursorX = cs.inputCenter()[0];
                            c.cursorY = cs.inputCenter()[1];
                        }
                        kbStep(1, t, base, k, KB_A);
                    } else {
                        kbStep(1, t, base, k, seq[k]);
                    }
                }
                if (t == base + 7 * 6 - 1 && c.screen instanceof ExtraChatScreen cs) {
                    kbVerdict("P2 escribe con el teclado: A, derecha, A, espacio, borrar, abajo, A -> '" + cs.text() + "' (esperado 'qws')", cs.text().equals("qws"));
                }
                if (t == base + 8 * 6 - 1) kbVerdict("B cierra el teclado y deja el chat abierto (teclado=" + PadKeyboard.of(1).active() + ", pantalla " + c.screen + ")",
                        !PadKeyboard.of(1).active() && c.screen instanceof ExtraChatScreen);
                if (t == base + 9 * 6 - 1) kbVerdict("A sobre el campo de texto reabre el teclado (teclado=" + PadKeyboard.of(1).active() + ", " + PadKeyboard.of(1).lastClickInfo + ", cursor " + SecondaryPlayers.context(1).cursorX + "," + SecondaryPlayers.context(1).cursorY + " campo " + java.util.Arrays.toString(((ExtraChatScreen) SecondaryPlayers.context(1).screen).inputCenter()) + ")", PadKeyboard.of(1).active());
                if (t == base + seq.length * 6 + 15) {
                    boolean got = c.notices.stream().anyMatch(n -> n.text().getString().contains("qws"));
                    kbVerdict("la tecla Enviar manda el mensaje: pantalla " + c.screen + ", teclado=" + PadKeyboard.of(1).active() + ", aviso 'qws' recibido por P2=" + got,
                            c.screen == null && !PadKeyboard.of(1).active() && got);
                    kbPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // chat de P1 (vanilla)
                int chat = PadHints.buttonFor(s0, PadAction.CHAT);
                if (t == 5) simSlot(0, 1 << chat);
                if (t == 9) simSlot(0, 0);
                if (t == 20) {
                    kbVerdict("P1: el chat de vanilla abre el teclado (pantalla " + mc.screen + ", teclado=" + PadKeyboard.of(0).active() + ")",
                            mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen && PadKeyboard.of(0).active());
                }
                kbStep(0, t, 25, 0, KB_A);
                if (t == 36) {
                    String text = mc.screen != null && mc.screen.getFocused() instanceof net.minecraft.client.gui.components.EditBox eb ? eb.getValue() : "?";
                    kbVerdict("P1 escribe 'q' en el campo del chat de vanilla: '" + text + "'", text.equals("q"));
                }
                kbStep(0, t, 40, 0, KB_B);
                if (t == 46) kbVerdict("B cierra el teclado de P1 y el chat sigue abierto", !PadKeyboard.of(0).active() && mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen);
                kbStep(0, t, 50, 0, KB_B);
                if (t == 58) {
                    kbVerdict("un segundo B cierra el chat de P1 (pantalla " + mc.screen + ")", mc.screen == null);
                    kbPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> { // cartel de P2
                ServerPlayer sp = serverExtra(mc, 1);
                if (t == 1) {
                    var server = mc.getSingleplayerServer();
                    server.execute(() -> {
                        ServerLevel level = (ServerLevel) sp.level();
                        kbSignPos = sp.blockPosition().above(3);
                        level.setBlock(kbSignPos, net.minecraft.world.level.block.Blocks.OAK_SIGN.defaultBlockState(), 2);
                        if (level.getBlockEntity(kbSignPos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                            sign.setAllowedPlayerEditor(sp.getUUID()); // como SignBlock.openTextEdit
                            sp.openTextEdit(sign, true);
                        }
                    });
                }
                PlayerContext c = SecondaryPlayers.context(1);
                if (t == 20) {
                    kbVerdict("P2: el cartel abierto por el servidor sale en su region con el teclado (pantalla " + c.screen + ", teclado=" + PadKeyboard.of(1).active() + ")",
                            c.screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen && PadKeyboard.of(1).active());
                }
                kbStep(1, t, 25, 0, KB_A); // 'q' en la primera linea
                if (t == 38 && c.screen != null) {
                    try {
                        var f = net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen.class.getDeclaredField("messages");
                        f.setAccessible(true);
                        ConsolePad.LOGGER.info("[autotest] cartel de P2 antes de cerrar: lineas = {}", java.util.Arrays.toString((String[]) f.get(c.screen)));
                    } catch (ReflectiveOperationException ex) {
                        ConsolePad.LOGGER.info("[autotest] no se pudo leer el cartel: {}", ex.toString());
                    }
                }
                if (t == 40 && c.screen != null) {
                    ContextSwitcher.run(c, () -> mc.screen.onClose()); // cerrar el cartel envia el texto al servidor
                }
                if (t == 52) {
                    mc.getSingleplayerServer().execute(() -> { // Level.getBlockEntity solo vale en el hilo del servidor
                        String line = "?";
                        if (sp.level().getBlockEntity(kbSignPos) instanceof net.minecraft.world.level.block.entity.SignBlockEntity sign) {
                            line = sign.getFrontText().getMessage(0, false).getString();
                        }
                        kbSignLine = line;
                    });
                }
                if (t == 60) {
                    String line = kbSignLine;
                    kbVerdict("el texto del cartel de P2 llega al servidor: primera linea '" + line + "' (esperada 'q')", line.equals("q"));
                    mc.getSingleplayerServer().execute(() -> sp.level().setBlock(kbSignPos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2));
                    ConsolePad.LOGGER.info("[autotest] RESULTADO teclado en pantalla en conjunto -> {}", kbOk ? "OK" : "FALLO");
                    kbPhase = 4;
                    ticks = 0;
                }
            }
            case 4 -> {
                if (t > 10) {
                    kbPhase = 5;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- menu de pausa de P1 jugando solo y velocidad del cursor

    private static boolean smOk = true;
    private static int smPhase;
    private static float smSpeed0;
    private static SplitSettings.Orientation smOrient0;

    private static void smVerdict(String name, boolean ok) {
        smOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Un clic de P1 por la ruta real del raton: pulsa en t0 y suelta 3 ticks despues. */
    private static void smClick(Minecraft mc, int t, int t0, int[] at) {
        if (t == t0) p1ClickAt(mc, at);
        if (t == t0 + 3) p1Mouse(mc, false);
    }

    /**
     * P1 sin nadie mas en la partida: Start abre el menu de pausa del mod (no el de vanilla) con Ajustes, Controles y Reparto; desde Ajustes se cambia la
     * velocidad del cursor y el modo de cursor (se guardan en su slot) y desde el menu se abren los Controles.
     */
    private static void soloMenuTest(Minecraft mc) {
        var s0 = pads.slot(0).settings;
        int t = ticks;
        switch (smPhase) {
            case 0 -> {
                if (t == 1) mc.setScreen(null);
                if (leaveTo(mc, 1) && t > 20) {
                    smSpeed0 = s0.cursorSpeed;
                    smOrient0 = SplitSettings.orientation();
                    smPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (t == 5) simSlot(0, 1 << PadState.START);
                if (t == 12) simSlot(0, 0);
                if (t == 20) {
                    smVerdict("P1 solo: Start abre el menu de pausa del mod (pantalla: " + mc.screen + ", extras dentro=" + SecondaryPlayers.anyActive() + ")",
                            mc.screen instanceof PlayerPauseScreen pp && pp.slot() == 0 && !SecondaryPlayers.anyActive());
                }
                if (mc.screen instanceof PlayerPauseScreen pp) {
                    smClick(mc, t, 21, pp.layoutCenter());
                    if (t == 28) {
                        smVerdict("P1 solo: Reparto cambia la orientacion (" + smOrient0 + " -> " + SplitSettings.orientation() + ")", SplitSettings.orientation() != smOrient0);
                        SplitSettings.setOrientation(smOrient0);
                    }
                    smClick(mc, t, 30, pp.settingsCenter());
                }
                if (t == 40) {
                    smVerdict("P1 solo: Ajustes del jugador se abre (pantalla: " + mc.screen + ")", mc.screen instanceof PlayerSettingsScreen);
                }
                if (mc.screen instanceof PlayerSettingsScreen ss) {
                    if (t == 41) p1ClickAt(mc, ss.centerOnItsPage("cursorSpeed")); // la opcion puede estar en otra pagina segun el alto de la ventana
                    if (t == 44) p1Mouse(mc, false);
                    if (t == 48) {
                        float exp = PlayerSettingsScreen.nextFloat(PlayerSettingsScreen.CURSOR_SPEEDS, smSpeed0);
                        smVerdict("velocidad del cursor de P1: " + smSpeed0 + " -> " + s0.cursorSpeed + " (esperada " + exp + ")", s0.cursorSpeed == exp && exp != smSpeed0);
                        s0.cursorSpeed = smSpeed0;
                    }
                    if (t == 50) p1ClickAt(mc, ss.centerOnItsPage("snapNav"));
                    if (t == 53) p1Mouse(mc, false);
                    if (t == 58) {
                        smVerdict("cursor por casillas de P1 se activa en su slot (snapNav=" + s0.snapNav + ")", s0.snapNav);
                        s0.snapNav = false;
                    }
                    smClick(mc, t, 60, ss.backCenter());
                }
                if (t == 70) {
                    smVerdict("Atras en Ajustes vuelve al menu de pausa (pantalla: " + mc.screen + ")", mc.screen instanceof PlayerPauseScreen);
                }
                if (mc.screen instanceof PlayerPauseScreen pp) smClick(mc, t, 71, pp.controlsCenter());
                if (t == 80) {
                    smVerdict("P1 solo: Controles se abre (pantalla: " + mc.screen + ")", mc.screen instanceof PlayerControlsScreen);
                    mc.setScreen(null);
                    ConsolePad.LOGGER.info("[autotest] RESULTADO menu de pausa de P1 jugando solo en conjunto -> {}", smOk ? "OK" : "FALLO");
                    smPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (joinTo(mc, 2) || t > 600) {
                    smPhase = 3;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- navegacion por casillas con el stick izquierdo

    private static boolean snOk = true;
    private static int snPhase;
    private static int snP2Slot0, snP1Slot0;
    private static double snCursor0;

    private static void snVerdict(String name, boolean ok) {
        snOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static int snSlotAt(net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> s, double x, double y) {
        for (net.minecraft.world.inventory.Slot sl : s.getMenu().slots) {
            double cx = s.getGuiLeft() + sl.x + 8, cy = s.getGuiTop() + sl.y + 8;
            if (Math.abs(x - cx) <= 8 && Math.abs(y - cy) <= 8) return sl.index;
        }
        return -1;
    }

    private static void snStick(float lx) {
        for (int i = 0; i < 2; i++) {
            PadManager.Device d = pads.slot(i).device;
            if (d != null) d.state.simLx = lx;
        }
    }

    /**
     * Con "Cursor en menus: por casillas" el stick izquierdo salta de hueco en hueco como el D-pad (un salto por empujon, sin mover el cursor libre);
     * con cursor libre sigue moviendo el cursor. Se mide en el inventario de P1 y de P2 (hotbar: del hueco 37 al 38 con el stick a la derecha).
     */
    private static void snapNavTest(Minecraft mc) {
        var s0 = pads.slot(0).settings;
        var s1 = pads.slot(1).settings;
        int t = ticks;
        switch (snPhase) {
            case 0 -> {
                if (joinTo(mc, 2) && t > 20) {
                    scaOpenScreens(mc);
                    s0.snapNav = true;
                    s1.snapNav = true;
                    snPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                PlayerContext c = SecondaryPlayers.context(1);
                var p1s = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) mc.screen;
                var p2s = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) c.screen;
                if (t == 3) {
                    cursorOnSlot(c, 37);
                    var sl = p1s.getMenu().slots.get(37);
                    p1MoveTo(mc, p1s.getGuiLeft() + sl.x + 8, p1s.getGuiTop() + sl.y + 8);
                }
                if (t == 8) {
                    snP2Slot0 = snSlotAt(p2s, c.cursorX, c.cursorY);
                    snP1Slot0 = p1s.getSlotUnderMouse() == null ? -1 : p1s.getSlotUnderMouse().index;
                    snStick(1f);
                }
                if (t == 13) snStick(0f);
                if (t == 18) {
                    int p2Slot = snSlotAt(p2s, c.cursorX, c.cursorY);
                    int p1Slot = p1s.getSlotUnderMouse() == null ? -1 : p1s.getSlotUnderMouse().index;
                    snVerdict("stick a la derecha en modo casillas: P2 salta del hueco " + snP2Slot0 + " al " + p2Slot + " (esperado 37 -> 38)", snP2Slot0 == 37 && p2Slot == 38);
                    snVerdict("stick a la derecha en modo casillas: P1 salta del hueco " + snP1Slot0 + " al " + p1Slot + " (esperado 37 -> 38)", snP1Slot0 == 37 && p1Slot == 38);
                    snCursor0 = c.cursorX;
                    snStick(0.35f); // empuja poco: pasa la zona muerta del cursor pero no el umbral del salto
                }
                if (t == 30) {
                    int p2Slot = snSlotAt(p2s, c.cursorX, c.cursorY);
                    int p1Slot = p1s.getSlotUnderMouse() == null ? -1 : p1s.getSlotUnderMouse().index;
                    snVerdict("modo casillas: con el stick poco inclinado el cursor no se mueve libre (P2 x " + snCursor0 + " -> " + c.cursorX + ", hueco P1 " + p1Slot + ")",
                            c.cursorX == snCursor0 && p2Slot == 38 && p1Slot == 38);
                    s0.snapNav = false;
                    s1.snapNav = false;
                    snStick(0.9f);
                }
                if (t == 42) {
                    snVerdict("cursor libre: el mismo empujon si mueve el cursor de P2 (x " + snCursor0 + " -> " + c.cursorX + ")", c.cursorX > snCursor0 + 5);
                    snStick(0f);
                    scaCloseScreens(mc);
                    ConsolePad.LOGGER.info("[autotest] RESULTADO navegacion por casillas con el stick izquierdo en conjunto -> {}", snOk ? "OK" : "FALLO");
                    snPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (t > 10) {
                    snPhase = 3;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- ayuda de botones en el HUD y en las pantallas

    private static boolean hbOk = true;
    private static int hbPhase;
    private static ItemStack hbSaved = ItemStack.EMPTY;

    private static void hbVerdict(String name, boolean ok) {
        hbOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static String hbHint(int code, String text) {
        return code < 0 ? null : code + ":" + text;
    }

    private static String hbJoin(String... parts) {
        StringBuilder b = new StringBuilder();
        for (String p : parts) {
            if (p == null) continue;
            if (b.length() > 0) b.append('|');
            b.append(p);
        }
        return b.toString();
    }

    private static int[] hbRegion(Minecraft mc, int slot) {
        if (slot == 0) return new int[] {SplitView.p1HudW, SplitView.p1HudH};
        PlayerContext c = SecondaryPlayers.context(slot);
        return new int[] {c.hudW, c.hudH};
    }

    /** Ayuda esperada del HUD de un jugador segun sus bindings y lo que apunta/lleva (la misma regla que PadHints pero escrita aparte). */
    private static String[] hbExpected(Minecraft mc, int slot) {
        var cfg = pads.slot(slot).settings;
        net.minecraft.world.entity.player.Player player;
        net.minecraft.world.phys.HitResult hit;
        if (slot == 0) {
            player = mc.player;
            hit = mc.hitResult;
        } else {
            PlayerContext c = SecondaryPlayers.context(slot);
            player = c.player;
            hit = c.hitResult;
        }
        String atk = hit == null ? null : hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK ? "Romper" : hit.getType() == net.minecraft.world.phys.HitResult.Type.ENTITY ? "Atacar" : null;
        String use = player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem ? "Colocar bloque" : "Usar objeto";
        String left = hbJoin(hbHint(PadHints.buttonFor(cfg, PadAction.DROP), "Tirar objeto"), hbHint(PadHints.buttonFor(cfg, PadAction.INVENTORY), "Inventario"));
        String right = hbJoin(atk == null ? null : hbHint(PadHints.buttonFor(cfg, PadAction.ATTACK), atk), hbHint(PadHints.buttonFor(cfg, PadAction.USE), use));
        return new String[] {left, right};
    }

    private static String hbBarExpected(boolean container, boolean menu) {
        return container ? "0:Seleccionar|1:Salir|2:Mitad|3:Mover rápido" : menu ? "0:Seleccionar|1:Volver" : "0:Seleccionar|1:Salir";
    }

    /**
     * Ayuda de botones: con 4 jugadores, cada uno ve abajo a la izquierda "Tirar objeto" e "Inventario" y abajo a la derecha las acciones de atacar y
     * usar (contextuales: Romper al apuntar a un bloque, Colocar bloque con un bloque en la mano) segun SUS bindings, dentro de su region y sin pisar la
     * barra de objetos. Se oculta con la opcion del jugador, una accion sin boton no muestra nada, y en las pantallas (inventario, menu) hay una barra
     * con los botones que valen. Los iconos cambian segun el tipo de mando.
     */
    private static void hintsTest(Minecraft mc) {
        switch (hbPhase) {
            case 0 -> {
                if (joinTo(mc, 4) && ticks > 20) {
                    mc.setScreen(null);
                    mc.player.setXRot(90f); // P1 mira al suelo: apunta a un bloque
                    hbPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (ticks != 12) return;
                for (int slot = 0; slot < 4; slot++) {
                    String[] exp = hbExpected(mc, slot);
                    int[] reg = hbRegion(mc, slot);
                    int[] bl = PadHints.lastBoxL[slot], br = PadHints.lastBoxR[slot];
                    boolean inside = bl[2] > 0 && br[2] > 0 && bl[0] >= 0 && br[0] + br[2] <= reg[0] && Math.min(bl[1], br[1]) >= 0 && Math.max(bl[1] + bl[3], br[1] + br[3]) <= reg[1];
                    boolean clear = bl[0] + bl[2] <= reg[0] / 2 - 91 + 1 && br[0] >= reg[0] / 2 + 91 - 1; // no pisa la barra de objetos (182 de ancho en el centro)
                    hbVerdict("P" + (slot + 1) + ": ayuda del HUD izquierda '" + PadHints.lastLeft[slot] + "' (esperada '" + exp[0] + "'), derecha '" + PadHints.lastRight[slot] + "' (esperada '" + exp[1] + "')",
                            PadHints.lastLeft[slot].equals(exp[0]) && PadHints.lastRight[slot].equals(exp[1]) && !exp[0].isEmpty());
                    hbVerdict("P" + (slot + 1) + ": la ayuda cabe en su region " + reg[0] + "x" + reg[1] + " " + java.util.Arrays.toString(bl) + " " + java.util.Arrays.toString(br) + " y no pisa la barra de objetos", inside && clear);
                }
                screenshotName = "consola-ayuda-hud-4j.png";
                screenshotPending = true;
                hbVerdict("P1 apunta a un bloque: el boton de atacar dice Romper (" + PadHints.lastRight[0] + ")", PadHints.lastRight[0].contains(":Romper"));
                hbPhase = 2;
                ticks = 0;
            }
            case 2 -> { // P1 con un bloque en la mano: Colocar bloque
                if (ticks == 1) {
                    hbSaved = mc.player.getInventory().getSelected().copy();
                    mc.player.getInventory().setItem(mc.player.getInventory().selected, new ItemStack(Items.DIRT, 5));
                } else if (ticks == 8) {
                    hbVerdict("P1 con tierra en la mano: el boton de usar dice Colocar bloque (" + PadHints.lastRight[0] + ")", PadHints.lastRight[0].endsWith(":Colocar bloque"));
                    mc.player.getInventory().setItem(mc.player.getInventory().selected, hbSaved);
                    hbPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> { // acciones sin boton, opcion de ocultar y texto contextual
                var cfg1 = pads.slot(1).settings;
                if (ticks == 1) {
                    hbVerdict("apuntar a una entidad dice Atacar y a nada no muestra el boton de atacar",
                            "Atacar".equals(PadHints.attackLabel(new net.minecraft.world.phys.EntityHitResult(mc.player)))
                                    && PadHints.attackLabel(net.minecraft.world.phys.BlockHitResult.miss(Vec3.ZERO, net.minecraft.core.Direction.UP, BlockPos.ZERO)) == null);
                    int drop = PadHints.buttonFor(cfg1, PadAction.DROP);
                    hbSaved = ItemStack.EMPTY;
                    hbDropButton = drop;
                    cfg1.bindings.remove(drop);
                    pads.slot(0).settings.showHints = false;
                } else if (ticks == 8) {
                    hbVerdict("P2 sin boton de Tirar objeto: no se muestra (" + PadHints.lastLeft[1] + ")", !PadHints.lastLeft[1].contains("Tirar objeto") && PadHints.lastLeft[1].contains("Inventario"));
                    hbVerdict("P1 con la ayuda desactivada no dibuja nada ('" + PadHints.lastLeft[0] + "' '" + PadHints.lastRight[0] + "') y P3 la sigue viendo",
                            PadHints.lastLeft[0].isEmpty() && PadHints.lastRight[0].isEmpty() && !PadHints.lastLeft[2].isEmpty());
                    cfg1.bindings.put(hbDropButton, PadAction.DROP);
                    pads.slot(0).settings.showHints = true;
                    hbPhase = 4;
                    ticks = 0;
                }
            }
            case 4 -> { // barra de ayuda en inventarios
                if (ticks == 1) {
                    scaOpenScreens(mc);
                } else if (ticks == 10) {
                    screenshotName = "consola-barra-inventario-4j.png";
                    screenshotPending = true;
                    String exp = hbBarExpected(true, false);
                    for (int slot = 0; slot < 4; slot++) {
                        hbVerdict("P" + (slot + 1) + ": barra de ayuda del inventario '" + PadHints.lastBar[slot] + "'", PadHints.lastBar[slot].equals(exp));
                    }
                    scaCloseScreens(mc);
                    hbPhase = 5;
                    ticks = 0;
                }
            }
            case 5 -> { // barra de ayuda en menus de pausa
                if (ticks == 1) {
                    mc.setScreen(new PlayerPauseScreen(0));
                    for (PlayerContext c : SplitRender.activeContexts()) ContextSwitcher.run(c, () -> mc.setScreen(new PlayerPauseScreen(c.slotIndex)));
                } else if (ticks == 10) {
                    String exp = hbBarExpected(false, true);
                    for (int slot = 0; slot < 4; slot++) {
                        hbVerdict("P" + (slot + 1) + ": barra de ayuda del menu de pausa '" + PadHints.lastBar[slot] + "'", PadHints.lastBar[slot].equals(exp));
                    }
                    // el cursor sobre "Volver a la partida" de cada jugador marca el boton como "encima" (el recorte de su region y su pantalla tienen unidades distintas)
                    if (mc.screen instanceof PlayerPauseScreen pp0) p1MoveTo(mc, pp0.resumeCenter()[0], pp0.resumeCenter()[1]);
                    for (PlayerContext c : SplitRender.activeContexts()) {
                        if (c.screen instanceof PlayerPauseScreen pp) {
                            c.cursorX = pp.resumeCenter()[0];
                            c.cursorY = pp.resumeCenter()[1];
                        }
                    }
                } else if (ticks == 16) {
                    for (int slot = 0; slot < 4; slot++) {
                        var sc = screenOf(mc, slot);
                        boolean hov = sc instanceof PlayerPauseScreen pp && pp.resumeHovered();
                        hbVerdict("P" + (slot + 1) + ": el boton Volver se marca como encima con el cursor sobre el (pantalla " + sc + ")", hov);
                    }
                    scaCloseScreens(mc);
                    hbPhase = 6;
                    ticks = 0;
                }
            }
            case 6 -> {
                if (ticks == 1) {
                    String a = PadIcons.glyph(PadState.A, dev.consolepad.pad.PadType.XBOX), b = PadIcons.glyph(PadState.A, dev.consolepad.pad.PadType.PLAYSTATION),
                            c = PadIcons.glyph(PadState.A, dev.consolepad.pad.PadType.NINTENDO);
                    hbVerdict("icono del boton inferior segun el mando: Xbox '" + a + "', PlayStation '" + b + "', Nintendo '" + c + "'", a.equals("A") && b.equals("\u00d7") && c.equals("B"));
                    mc.player.setXRot(0f);
                    ConsolePad.LOGGER.info("[autotest] RESULTADO ayuda de botones del HUD y de las pantallas en conjunto -> {}", hbOk ? "OK" : "FALLO");
                }
                if (ticks > 1 && leaveTo(mc, 2) || ticks > 600) {
                    hbPhase = 7;
                    next();
                }
            }
            default -> { }
        }
    }

    private static int hbDropButton;

    // ---------------------------------------------------------------- aviso "Unirse como el jugador N" y Start para entrar

    private static boolean jpOk = true;
    private static int jpPhase;
    private static int jpExpect;
    private static long jpT0;
    private static boolean jpSeen10, jpSeen14;

    private static void jpVerdict(String name, boolean ok) {
        jpOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static int jpPendingMask() {
        int mask = 0;
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            if (pads.slot(i).device != null && !SecondaryPlayers.isActive(i)) mask |= 1 << i;
        }
        return mask;
    }

    /**
     * Con mandos conectados cuyo jugador no esta dentro, P1 ve abajo en el centro de su region "[boton] Unirse como el jugador N" (una linea por
     * mando pendiente, con el nombre del boton Start segun el tipo de mando). Pulsar Start (sin Back) en ese mando mete al jugador, el aviso de
     * ese mando desaparece y el jugador no entra con el menu de pausa abierto. Con una pantalla abierta en P1 no se dibuja.
     */
    private static void joinPromptTest(Minecraft mc) {
        switch (jpPhase) {
            case 0 -> {
                if (ticks == 1) mc.setScreen(null);
                if (leaveTo(mc, 1) && ticks > 20) {
                    for (int i = 1; i <= 3; i++) pads.detachVirtual(i); // mandos nuevos: el reloj del aviso de cada uno empieza ahora
                    pads.attachVirtual(1);
                    pads.attachVirtual(2);
                    jpExpect = jpPendingMask();
                    jpT0 = System.nanoTime();
                    jpPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                if (ticks == 15) {
                    int[] box = JoinPrompt.lastBox, reg = JoinPrompt.lastRegion;
                    jpVerdict("aviso de unirse: lineas dibujadas para los slots " + Integer.toBinaryString(JoinPrompt.lastSlots) + " (esperados " + Integer.toBinaryString(jpExpect) + ")",
                            JoinPrompt.lastSlots == jpExpect && jpExpect != 0);
                    boolean inside = box[2] > 0 && box[0] >= 0 && box[1] >= 0 && box[0] + box[2] <= reg[0] && box[1] + box[3] <= reg[1];
                    boolean centered = Math.abs(box[0] + box[2] / 2 - reg[0] / 2) <= 3;
                    boolean bottom = box[1] + box[3] > reg[1] / 2;
                    jpVerdict("aviso de unirse: caja " + java.util.Arrays.toString(box) + " dentro de la region " + reg[0] + "x" + reg[1] + ", centrada y en la mitad inferior",
                            inside && centered && bottom);
                    screenshotName = "consola-aviso-unirse.png";
                    screenshotPending = true;
                    String xb = PadIcons.glyph(PadState.START, dev.consolepad.pad.PadType.XBOX);
                    String ps = PadIcons.glyph(PadState.START, dev.consolepad.pad.PadType.PLAYSTATION);
                    String ns = PadIcons.glyph(PadState.START, dev.consolepad.pad.PadType.NINTENDO);
                    jpVerdict("nombre del boton Start segun el mando: Xbox '" + xb + "', PlayStation '" + ps + "', Nintendo '" + ns + "'",
                            xb.equals("Menú") && ps.equals("Options") && ns.equals("+"));
                    mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
                } else if (ticks == 30) {
                    jpVerdict("aviso de unirse: con una pantalla abierta en P1 no se dibuja (lineas=" + Integer.toBinaryString(JoinPrompt.lastSlots) + ")", JoinPrompt.lastSlots == 0);
                    mc.setScreen(null);
                    jpPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // Start solo (sin Back) en el mando 2
                if (ticks == 5) simSlot(1, 1 << PadState.START);
                if (ticks == 12) simSlot(1, 0);
                if (ticks > 12 && SecondaryPlayers.isActive(1) && extraReady(mc, 1)) {
                    jpVerdict("Start solo (sin Back) en el mando 2 mete a P2", true);
                    jpPhase = 3;
                    ticks = 0;
                } else if (ticks > 400) {
                    jpVerdict("Start solo (sin Back) en el mando 2 mete a P2", false);
                    jpPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (ticks == 20) {
                    jpVerdict("tras unirse P2, su aviso desaparece y queda el de los demas: lineas=" + Integer.toBinaryString(JoinPrompt.lastSlots) + " (esperadas " + Integer.toBinaryString(jpPendingMask()) + ")",
                            JoinPrompt.lastSlots == jpPendingMask() && (JoinPrompt.lastSlots & 2) == 0 && jpPendingMask() == (jpExpect & ~2));
                    jpVerdict("P2 entra sin menu de pausa abierto (pantalla de P2: " + screenOf(mc, 1) + ")", screenOf(mc, 1) == null);
                }
                if (ticks > 20) {
                    jpPhase = 4;
                    ticks = 0;
                }
            }
            case 4 -> { // el aviso se oculta a los 12 s y solo vuelve con un mando nuevo
                double secs = (System.nanoTime() - jpT0) / 1e9;
                if (!jpSeen10 && secs >= 10.0) {
                    jpSeen10 = true;
                    jpVerdict("aviso de unirse: a los " + String.format("%.1f", secs) + " s sigue visible (lineas=" + Integer.toBinaryString(JoinPrompt.lastSlots) + ")", JoinPrompt.lastSlots != 0);
                }
                if (!jpSeen14 && secs >= 14.0) {
                    jpSeen14 = true;
                    jpVerdict("aviso de unirse: a los " + String.format("%.1f", secs) + " s (limite 12) se ha ocultado (lineas=" + Integer.toBinaryString(JoinPrompt.lastSlots) + ", pendientes=" + Integer.toBinaryString(jpPendingMask()) + ")",
                            JoinPrompt.lastSlots == 0 && jpPendingMask() != 0);
                    pads.attachVirtual(3); // mando nuevo conectado
                    ticks = 0;
                }
                if (jpSeen14 && ticks == 15) {
                    jpVerdict("aviso de unirse: al conectar un mando nuevo (slot 4) reaparece solo el suyo (lineas=" + Integer.toBinaryString(JoinPrompt.lastSlots) + ", esperado 1000)", JoinPrompt.lastSlots == 8);
                    ConsolePad.LOGGER.info("[autotest] RESULTADO aviso de unirse y Start para entrar en conjunto -> {}", jpOk ? "OK" : "FALLO");
                    jpPhase = 5;
                    ticks = 0;
                }
            }
            case 5 -> {
                if (leaveTo(mc, 2) || ticks > 600) {
                    jpPhase = 6;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- Start y B cierran el menu de su jugador

    private static int deathTestPhase;
    private static int scPhase;
    private static boolean scOk = true;
    private static int scDrops0;

    private static void scVerdict(String name, boolean ok) {
        scOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Una pulsacion de t0 a t1 ticks (mas larga que un tick, como un dedo de verdad). */
    private static void scPress(int slot, int bit, int t, int from, int to) {
        if (t == from) simSlot(slot, 1 << bit);
        if (t == to) simSlot(slot, 0);
    }

    /**
     * Cada jugador (P1..P4) abre su menu con Start y solo se abre el suyo; Start lo cierra (y no lo reabre mientras sigue pulsado);
     * B tambien lo cierra y no suelta ningun objeto. Con dos menus abiertos a la vez, cerrar uno no cierra el otro.
     */
    private static void startCloseTest(Minecraft mc) {
        switch (scPhase) {
            case 0 -> {
                if (joinTo(mc, 4)) {
                    scPhase = 1;
                    ticks = 0;
                    scDrops0 = SecondaryTick.dbgDrop;
                    mc.setScreen(null);
                }
            }
            case 1, 2, 3, 4 -> {
                int slot = scPhase - 1;
                String who = "P" + (slot + 1);
                scPress(slot, PadState.START, ticks, 5, 12);
                if (ticks == 25) scVerdict("Start abre el menu de pausa de " + who + " y solo el suyo [" + screensDigest(mc) + "]", onlyOpen(mc, slot, true));
                scPress(slot, PadState.START, ticks, 28, 35);
                if (ticks == 55) scVerdict("Start cierra el menu de pausa de " + who + " y no lo reabre [" + screensDigest(mc) + "]", onlyOpen(mc, slot, false));
                scPress(slot, PadState.START, ticks, 58, 65);
                if (ticks == 80) scVerdict("Start vuelve a abrir el menu de " + who + " [" + screensDigest(mc) + "]", onlyOpen(mc, slot, true));
                scPress(slot, PadState.B, ticks, 83, 90);
                if (ticks == 110) {
                    scVerdict("B cierra el menu de pausa de " + who + " y no lo reabre [" + screensDigest(mc) + "]", onlyOpen(mc, slot, false));
                    scPhase++;
                    ticks = 0;
                }
            }
            case 5 -> { // dos menus abiertos a la vez (P2 y P3): Start en P3 cierra solo el de P3
                scPress(1, PadState.START, ticks, 5, 12);
                scPress(2, PadState.START, ticks, 15, 22);
                if (ticks == 40) {
                    boolean both = isPauseMenu(screenOf(mc, 1)) && isPauseMenu(screenOf(mc, 2)) && screenOf(mc, 0) == null && screenOf(mc, 3) == null;
                    scVerdict("P2 y P3 abren su menu a la vez [" + screensDigest(mc) + "]", both);
                }
                scPress(2, PadState.START, ticks, 45, 52);
                if (ticks == 70) {
                    boolean ok = isPauseMenu(screenOf(mc, 1)) && screenOf(mc, 2) == null && screenOf(mc, 0) == null && screenOf(mc, 3) == null;
                    scVerdict("Start en P3 cierra solo el menu de P3, el de P2 sigue [" + screensDigest(mc) + "]", ok);
                }
                scPress(1, PadState.B, ticks, 75, 82);
                if (ticks == 100) {
                    scVerdict("B en P2 cierra su menu [" + screensDigest(mc) + "]", screenOf(mc, 1) == null && screenOf(mc, 2) == null);
                    scVerdict("cerrar menus con B no suelta objetos (soltar ejecutado " + (SecondaryTick.dbgDrop - scDrops0) + " veces)", SecondaryTick.dbgDrop == scDrops0);
                    scPhase = 6;
                    ticks = 0;
                }
            }
            case 6 -> {
                if (ticks == 1) simSlot(0, 0);
                if (leaveTo(mc, 2) || ticks > 600) {
                    ConsolePad.LOGGER.info("[autotest] RESULTADO Start y B cierran el menu de su jugador (P1 a P4) en conjunto -> {}", scOk ? "OK" : "FALLO");
                    scPhase = 7;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- ayudas del raton de P1 (coordenadas logicas de su pantalla)

    private static void p1MoveTo(Minecraft mc, double lx, double ly) {
        var win = mc.getWindow();
        var mouse = (dev.consolepad.mixin.MouseHandlerInvoker) mc.mouseHandler;
        float k = SplitView.p1Scale();
        double sx = lx * k * win.getScreenWidth() / win.getGuiScaledWidth(), sy = ly * k * win.getScreenHeight() / win.getGuiScaledHeight();
        mouse.consolepad$onMove(win.getWindow(), sx, sy);
    }

    private static void p1Mouse(Minecraft mc, boolean down) {
        var mouse = (dev.consolepad.mixin.MouseHandlerInvoker) mc.mouseHandler;
        mouse.consolepad$onPress(mc.getWindow().getWindow(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT,
                down ? org.lwjgl.glfw.GLFW.GLFW_PRESS : org.lwjgl.glfw.GLFW.GLFW_RELEASE, 0);
    }

    /** Clic completo de P1 por la ruta real del raton (pulsar ahora; soltar lo hace quien llama unos ticks despues con p1Mouse(false)). */
    private static void p1ClickAt(Minecraft mc, int[] c) {
        p1MoveTo(mc, c[0], c[1]);
        p1Mouse(mc, true);
    }

    // ---------------------------------------------------------------- escala de interfaz y orientacion del reparto

    private static final int[][] SCA_WINDOWS = {{854, 480}, {1920, 1080}};
    private static final int[][] SCA_LADDER = {{2, 3, 4}, {4, 3, 2}};
    private static int scaPhase, scaWin, scaIdx;
    private static boolean scaOk = true;
    private static SplitSettings.Orientation scaOrig;

    private static void scaVerdict(String name, boolean ok) {
        scaOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Escala automatica que elegiria Minecraft para una region (calculo propio de la prueba, independiente de SplitLayout). */
    private static int autoScaleOf(int pw, int ph) {
        int s = 1;
        while (pw / (s + 1) >= 320 && ph / (s + 1) >= 240) s++;
        return s;
    }

    /** La forma del reparto (regiones en pixeles con origen abajo a la izquierda) es la que toca para n jugadores y esa orientacion. */
    private static boolean shapeOk(int n, SplitSettings.Orientation o, int W, int H, java.util.List<int[]> r) {
        boolean stacked = o == SplitSettings.Orientation.STACKED;
        int[] a = r.get(0), b = r.get(1);
        if (n == 2) {
            return stacked
                    ? a[2] == W && b[2] == W && a[3] + b[3] == H && a[0] == 0 && b[0] == 0 && b[1] == 0 && a[1] == b[3]
                    : a[3] == H && b[3] == H && a[2] + b[2] == W && a[0] == 0 && b[0] == a[2] && a[1] == 0 && b[1] == 0;
        }
        int[] c = r.get(2);
        if (n == 3) {
            return stacked
                    ? a[2] == W && a[0] == 0 && a[1] == b[3] && a[3] + b[3] == H && b[3] == c[3] && b[0] == 0 && b[1] == 0 && c[1] == 0 && c[0] == b[2] && b[2] + c[2] == W
                    : a[3] == H && a[0] == 0 && a[1] == 0 && b[0] == a[2] && c[0] == a[2] && b[2] == W - a[2] && c[2] == b[2] && b[3] + c[3] == H && c[1] == 0 && b[1] == c[3];
        }
        int[] d = r.get(3);
        return a[0] == 0 && a[1] == H - a[3] && b[0] == a[2] && b[1] == a[1] && c[0] == 0 && c[1] == 0 && d[0] == c[2] && d[1] == 0
                && a[2] + b[2] == W && c[2] + d[2] == W && a[3] + c[3] == H && b[3] + d[3] == H;
    }

    private static void scaOpenScreens(Minecraft mc) {
        mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player));
        for (PlayerContext c : SplitRender.activeContexts()) mtOpenInventory(mc, c);
    }

    private static void scaCloseScreens(Minecraft mc) {
        mc.setScreen(null);
        for (PlayerContext c : SplitRender.activeContexts()) {
            ContextSwitcher.run(c, () -> {
                if (mc.screen != null) mc.screen.onClose();
            });
        }
    }

    /** Aspecto, forma del reparto, escala de interfaz y HUD de cada region, mas el tamano de las pantallas abiertas. */
    private static void scaEvaluate(Minecraft mc, String label, int n, SplitSettings.Orientation o) {
        SplitDiag.recording = false;
        SplitDiag.hudRecording = false;
        var win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight(), G = (int) win.getGuiScale();
        java.util.List<PlayerContext> extras = SplitRender.activeContexts();
        java.util.List<int[]> regions = new java.util.ArrayList<>();
        regions.add(SplitView.p1Px.clone());
        for (PlayerContext c : extras) regions.add(new int[] {c.px, c.py, c.pw, c.ph});
        boolean count = extras.size() == n - 1;
        boolean shape = count && shapeOk(n, o, W, H, regions);
        boolean tile = regionsTileWindow(mc, extras);
        int bad = SplitDiag.check(label, 0.01);
        java.util.Set<String> passes = new java.util.TreeSet<>();
        for (SplitDiag.Sample sm : SplitDiag.samples()) passes.add(sm.pass());
        var seen = SplitDiag.hudSeen();
        StringBuilder detail = new StringBuilder();
        boolean hudOk = SplitDiag.hudWrong() == 0;
        for (int i = 0; i < n && count; i++) {
            int[] r = regions.get(i);
            int eff = i == 0 ? SplitView.p1GuiScaleEff : extras.get(i - 1).guiScaleEff;
            int hudW = i == 0 ? SplitView.p1HudW : extras.get(i - 1).hudW;
            int hudH = i == 0 ? SplitView.p1HudH : extras.get(i - 1).hudH;
            int expectEff = autoScaleOf(r[2], r[3]);
            int hotbarW = seen.getOrDefault("P" + (i + 1), new java.util.TreeMap<>()).getOrDefault("hotbar", -1);
            boolean this_ok = eff == expectEff && hudW == r[2] / eff && hudH == r[3] / eff && hotbarW == hudW && hudW >= 240 && hudH >= 110 && hudW * eff <= r[2];
            hudOk &= this_ok;
            detail.append(" P").append(i + 1).append("[region ").append(r[2]).append('x').append(r[3]).append(" escala ").append(eff).append(" (auto ").append(expectEff)
                    .append(") interfaz ").append(hudW).append('x').append(hudH).append(" hotbar ").append(hotbarW).append(this_ok ? "" : " <==").append(']');
        }
        scaVerdict(label + ": " + n + " jugadores " + o + " ventana " + W + "x" + H + " (escala de ventana " + G + "): regiones OK=" + shape + ", cubren la ventana=" + tile
                + ", pasadas=" + passes.size() + "/" + n + ", aspecto fuera de tolerancia=" + bad + ", escala e interfaz de cada region:" + detail,
                count && shape && tile && passes.size() == n && bad == 0 && hudOk);
        // pantallas abiertas: cada una con el tamano logico de su region actual (se recalculan al cambiar ventana u orientacion)
        StringBuilder sc = new StringBuilder();
        boolean screensOk = true;
        for (int i = 0; i < n && count; i++) {
            net.minecraft.client.gui.screens.Screen scr = i == 0 ? mc.screen : extras.get(i - 1).screen;
            int lw = i == 0 ? SplitView.p1LogicalW : extras.get(i - 1).logicalW;
            int lh = i == 0 ? SplitView.p1LogicalH : extras.get(i - 1).logicalH;
            boolean ok = scr instanceof net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?> cs
                    && scr.width == lw && scr.height == lh && cs.getGuiLeft() >= 0 && cs.getGuiTop() >= 0
                    && cs.getGuiLeft() + 176 <= lw && cs.getGuiTop() + 166 <= lh;
            screensOk &= ok;
            sc.append(" P").append(i + 1).append('=').append(scr == null ? "-" : scr.width + "x" + scr.height).append(" (logico ").append(lw).append('x').append(lh).append(ok ? ")" : ") <==");
        }
        scaVerdict(label + ": pantallas abiertas con el tamano de su region:" + sc, count && screensOk);
    }

    /**
     * Para cada ventana (854x480 y 1920x1080) y cada numero de jugadores (2, 3, 4), en las dos orientaciones: la forma del reparto, el aspecto
     * de cada region, la escala de interfaz de cada una (la automatica que elegiria Minecraft para su region), el HUD con ese ancho y el
     * tamano de las pantallas abiertas; al cambiar la orientacion con las pantallas abiertas todo se recalcula sin esperar a nada.
     */
    private static void scaleTest(Minecraft mc) {
        var window = mc.getWindow();
        switch (scaPhase) {
            case 0 -> {
                scaOrig = SplitSettings.orientation();
                scaWin = 0;
                scaIdx = 0;
                scaPhase = 1;
                ticks = 0;
            }
            case 1 -> { // cambia el tamano de la ventana
                int[] sz = SCA_WINDOWS[scaWin];
                if (ticks == 1) org.lwjgl.glfw.GLFW.glfwSetWindowSize(window.getWindow(), sz[0], sz[1]);
                if (ticks >= 70) {
                    ConsolePad.LOGGER.info("[autotest] ventana: {}x{} (framebuffer), escala de interfaz {}", window.getWidth(), window.getHeight(), window.getGuiScale());
                    scaPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // numero de jugadores de este escalon
                int n = SCA_LADDER[scaWin][scaIdx];
                int now = 1 + SecondaryPlayers.contexts().size();
                boolean done = n >= now ? joinTo(mc, n) : leaveTo(mc, n);
                if (done && ticks > 10) {
                    scaPhase = 3;
                    ticks = 0;
                } else if (ticks > 2400) {
                    scaVerdict("pasar a " + n + " jugadores", false);
                    scaPhase = 9;
                }
            }
            case 3 -> {
                int n = SCA_LADDER[scaWin][scaIdx];
                String label = SCA_WINDOWS[scaWin][0] + "x" + SCA_WINDOWS[scaWin][1] + "/" + n + "j";
                switch (ticks) {
                    case 1 -> {
                        SplitSettings.setOrientation(SplitSettings.Orientation.SIDE_BY_SIDE);
                        scaOpenScreens(mc);
                    }
                    case 5 -> mtRecordStart();
                    case 18 -> scaEvaluate(mc, label, n, SplitSettings.Orientation.SIDE_BY_SIDE);
                    case 19 -> SplitSettings.setOrientation(SplitSettings.Orientation.STACKED); // con las pantallas abiertas
                    case 23 -> mtRecordStart();
                    case 36 -> scaEvaluate(mc, label, n, SplitSettings.Orientation.STACKED);
                    case 37 -> {
                        scaCloseScreens(mc);
                        scaIdx++;
                        if (scaIdx >= SCA_LADDER[scaWin].length) {
                            scaIdx = 0;
                            scaWin++;
                        }
                        scaPhase = scaWin >= SCA_WINDOWS.length ? 9 : (scaIdx == 0 ? 1 : 2);
                        ticks = 0;
                    }
                    default -> { }
                }
            }
            case 9 -> {
                SplitSettings.setOrientation(scaOrig);
                ConsolePad.LOGGER.info("[autotest] RESULTADO escala de interfaz y orientacion del reparto (2, 3 y 4 jugadores, 854x480 y 1920x1080, las dos orientaciones) en conjunto -> {}",
                        scaOk ? "OK" : "FALLO");
                scaPhase = 10;
                next();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- recorte por region: objeto del cursor y desenfoque de fondo

    private static int ucPhase;
    private static boolean ucOk = true;
    private static SplitSettings.Orientation ucOrig;

    private static void ucVerdict(String name, boolean ok) {
        ucOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static boolean boxInside(int[] box, int[] region, int tol) {
        return box[0] >= region[0] - tol && box[1] >= region[1] - tol && box[0] + box[2] <= region[0] + region[2] + tol && box[1] + box[3] <= region[1] + region[3] + tol
                && box[2] >= region[2] - tol && box[3] >= region[3] - tol;
    }

    /**
     * Con 3 jugadores y un objeto (ojo de ender) en el cursor de cada uno, colocado lo mas lejos posible de su region (P1 con el raton
     * en la region de otro jugador): el scissor real al dibujar el objeto y el desenfoque de fondo es el de la region de su jugador.
     */
    private static void uiClipTest(Minecraft mc) {
        var win = mc.getWindow();
        int G = (int) win.getGuiScale();
        switch (ucPhase) {
            case 0 -> {
                if (ticks == 1) {
                    ucOrig = SplitSettings.orientation();
                    SplitSettings.setOrientation(SplitSettings.Orientation.SIDE_BY_SIDE);
                }
                if (joinTo(mc, 3) && ticks > 20) {
                    ucPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> { // objeto del cursor lo mas lejos posible de la region de su jugador
                if (ticks == 1) {
                    scaOpenScreens(mc);
                } else if (ticks == 6) {
                    mc.player.containerMenu.setCarried(new ItemStack(Items.ENDER_EYE, 7));
                    for (PlayerContext c : SplitRender.activeContexts()) {
                        ContextSwitcher.run(c, () -> mc.player.containerMenu.setCarried(new ItemStack(Items.ENDER_EYE, 3)));
                        c.cursorX = c.slotIndex == 1 ? 0 : c.screen.width - 1; // P2 contra su borde izquierdo, P3 contra la esquina inferior derecha
                        c.cursorY = c.slotIndex == 1 ? 20 : c.screen.height - 1;
                    }
                    p1MoveTo(mc, SplitView.p1LogicalW * 1.6, SplitView.p1LogicalH * 0.5); // el raton de P1 en la region de otro jugador
                    SplitDiag.uiClear();
                    SplitDiag.watchItem = Items.ENDER_EYE;
                    SplitDiag.uiRecording = true;
                } else if (ticks == 16) {
                    SplitDiag.uiRecording = false;
                    SplitDiag.watchItem = null;
                    var draws = SplitDiag.uiDraws();
                    java.util.Map<String, int[]> region = ucRegions();
                    int tol = 2 * G + 1;
                    for (String owner : new String[] {"P1", "P2", "P3"}) {
                        var items = draws.stream().filter(d -> d.owner().equals(owner) && d.what().equals("objeto")).toList();
                        int[] r = region.get(owner);
                        boolean itemsIn = !items.isEmpty() && items.stream().allMatch(d -> boxInside(d.scissor(), r, tol));
                        ucVerdict(owner + ": el objeto del cursor (" + items.size() + " dibujados"
                                + (items.isEmpty() ? "" : ", scissor " + java.util.Arrays.toString(items.get(0).scissor()) + ", posicion " + items.get(0).x() + "," + items.get(0).y())
                                + ") se recorta a su region " + java.util.Arrays.toString(r) + " (ventana " + win.getWidth() + "x" + win.getHeight() + ")", itemsIn);
                    }
                    mc.player.containerMenu.setCarried(ItemStack.EMPTY);
                    scaCloseScreens(mc);
                    ucPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // el desenfoque del menu de pausa de cada jugador solo cubre su region
                if (ticks == 1) {
                    mc.setScreen(new PlayerPauseScreen(0));
                    for (PlayerContext c : SplitRender.activeContexts()) {
                        ContextSwitcher.run(c, () -> mc.setScreen(new PlayerPauseScreen(c.slotIndex)));
                    }
                } else if (ticks == 6) {
                    SplitDiag.uiClear();
                    SplitDiag.uiRecording = true;
                } else if (ticks == 16) {
                    SplitDiag.uiRecording = false;
                    var draws = SplitDiag.uiDraws();
                    java.util.Map<String, int[]> region = ucRegions();
                    int tol = 2 * G + 1;
                    for (String owner : new String[] {"P1", "P2", "P3"}) {
                        var blurs = draws.stream().filter(d -> d.owner().equals(owner) && d.what().equals("desenfoque")).toList();
                        int[] r = region.get(owner);
                        boolean blurIn = !blurs.isEmpty() && blurs.stream().allMatch(d -> boxInside(d.scissor(), r, tol) && d.scissor()[2] < win.getWidth());
                        ucVerdict(owner + ": el desenfoque de su menu de pausa (" + blurs.size() + " veces" + (blurs.isEmpty() ? "" : ", scissor " + java.util.Arrays.toString(blurs.get(0).scissor()))
                                + ") solo cubre su region " + java.util.Arrays.toString(r) + " (ventana " + win.getWidth() + "x" + win.getHeight() + ")", blurIn);
                    }
                    scaCloseScreens(mc);
                    ucPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> {
                if (ticks == 1) {
                    SplitSettings.setOrientation(ucOrig);
                    ConsolePad.LOGGER.info("[autotest] RESULTADO recorte por region (objeto del cursor y desenfoque) en conjunto -> {}", ucOk ? "OK" : "FALLO");
                }
                if (leaveTo(mc, 2) || ticks > 600) {
                    ucPhase = 4;
                    next();
                }
            }
            default -> { }
        }
    }

    private static java.util.Map<String, int[]> ucRegions() {
        java.util.Map<String, int[]> region = new java.util.HashMap<>();
        region.put("P1", SplitView.p1Px.clone());
        for (PlayerContext c : SplitRender.activeContexts()) region.put(c.label(), new int[] {c.px, c.py, c.pw, c.ph});
        return region;
    }

    // ---------------------------------------------------------------- pantalla de muerte por jugador

    private static boolean dtOk = true;
    private static int dtPhase;

    private static void dtVerdict(String name, boolean ok) {
        dtOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static void kill(Minecraft mc, ServerPlayer p) {
        mc.getSingleplayerServer().execute(p::kill);
    }

    /** P2 muere: su pantalla de muerte en su region (reaparecer con su mando); P3 muere y sale; P1 muere: la de vanilla en su region. */
    private static void deathTest(Minecraft mc) {
        PlayerContext c2 = SecondaryPlayers.context(1);
        PlayerContext c3 = SecondaryPlayers.context(2);
        switch (dtPhase) {
            case 0 -> {
                deathTestPhase = 1;
                if (joinTo(mc, 3) && ticks > 20) {
                    dtPhase = 1;
                    ticks = 0;
                    kill(mc, serverExtra(mc, 1));
                }
            }
            case 1 -> { // P2 muerto
                if (ticks == 50) {
                    boolean screen = c2.screen instanceof ExtraDeathScreen && c2.screen.width == c2.logicalW && c2.screen.height == c2.logicalH;
                    dtVerdict("P2 muere: su pantalla de muerte (" + (c2.screen == null ? "ninguna" : c2.screen.getClass().getSimpleName()) + ") en su region ("
                            + (c2.screen == null ? "-" : c2.screen.width + "x" + c2.screen.height) + " de " + c2.logicalW + "x" + c2.logicalH + "), la de P1=" + mc.screen
                            + ", la de P3=" + c3.screen + ", P2 muerto=" + c2.player.isDeadOrDying() + ", reaparicion automatica=" + SecondaryTick.autoRespawn,
                            screen && mc.screen == null && c3.screen == null && c2.player.isDeadOrDying() && !SecondaryTick.autoRespawn);
                    if (c2.screen instanceof ExtraDeathScreen d) {
                        int[] rc = d.respawnCenter();
                        c2.cursorX = rc[0];
                        c2.cursorY = rc[1];
                    }
                }
                if (ticks == 53) simP2(1 << PadState.A);
                if (ticks == 59) simP2(0);
                if (ticks == 100) {
                    boolean alive = !c2.player.isDeadOrDying() && serverExtra(mc, 1) != null && serverExtra(mc, 1).isAlive();
                    dtVerdict("Reaparecer con el cursor de P2: vivo=" + alive + ", su pantalla=" + c2.screen + ", P1 sin pantalla=" + (mc.screen == null) + ", P3 sin pantalla=" + (c3.screen == null),
                            alive && c2.screen == null && mc.screen == null && c3.screen == null);
                    kill(mc, serverExtra(mc, 2));
                    dtPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // P3 muere y usa "Salir de la partida"
                if (ticks == 50) {
                    dtVerdict("P3 muere: su pantalla de muerte en su region (" + (c3.screen == null ? "ninguna" : c3.screen.getClass().getSimpleName()) + "), la de P2=" + c2.screen,
                            c3.screen instanceof ExtraDeathScreen && c2.screen == null && mc.screen == null);
                    if (c3.screen instanceof ExtraDeathScreen d) {
                        int[] qc = d.quitCenter();
                        c3.cursorX = qc[0];
                        c3.cursorY = qc[1];
                    }
                }
                if (ticks == 53) simSlot(2, 1 << PadState.A);
                if (ticks == 59) simSlot(2, 0);
                if (ticks == 120) {
                    boolean gone = !SecondaryPlayers.isActive(2) && serverExtra(mc, 2) == null;
                    dtVerdict("Salir de la partida desde la muerte de P3: P3 fuera=" + gone + ", P2 dentro=" + SecondaryPlayers.isActive(1) + ", P1 sigue en el mundo=" + (mc.player != null && mc.level != null),
                            gone && SecondaryPlayers.isActive(1) && mc.player != null);
                    ServerPlayer sp1 = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    kill(mc, sp1);
                    dtPhase = 3;
                    ticks = 0;
                }
            }
            case 3 -> { // P1 muere: la pantalla de vanilla en su region
                if (ticks == 50) {
                    boolean death = mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && mc.screen.width == SplitView.p1LogicalW && mc.screen.height == SplitView.p1LogicalH;
                    dtVerdict("P1 muere: pantalla de muerte (" + (mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName()) + ") en su region ("
                            + (mc.screen == null ? "-" : mc.screen.width + "x" + mc.screen.height) + " de " + SplitView.p1LogicalW + "x" + SplitView.p1LogicalH + "), la de P2=" + c2.screen,
                            death && c2.screen == null);
                    if (mc.screen != null) {
                        for (var child : mc.screen.children()) {
                            if (child instanceof net.minecraft.client.gui.components.Button b) { // el primero es "Reaparecer"
                                p1ClickAt(mc, new int[] {b.getX() + b.getWidth() / 2, b.getY() + b.getHeight() / 2});
                                break;
                            }
                        }
                    }
                }
                if (ticks == 56) p1Mouse(mc, false);
                if (ticks == 110) {
                    boolean alive = mc.player != null && !mc.player.isDeadOrDying() && mc.screen == null;
                    dtVerdict("Reaparecer con el raton en la pantalla de muerte de P1: vivo=" + alive + ", pantalla=" + mc.screen, alive);
                    dtPhase = 4;
                    ticks = 0;
                }
            }
            case 4 -> {
                deathTestPhase = 0;
                ConsolePad.LOGGER.info("[autotest] RESULTADO pantalla de muerte por jugador en conjunto -> {}", dtOk ? "OK" : "FALLO");
                dtPhase = 5;
                next();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- chat y avisos en la region de cada jugador

    private static boolean ctOk = true;
    private static int ctPhase;

    private static void ctVerdict(String name, boolean ok) {
        ctOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static boolean hasNotice(PlayerContext c, String part) {
        return c.notices.stream().anyMatch(n -> n.text().getString().contains(part));
    }

    private static final String LONG_TEXT = "este es un mensaje muy largo del servidor que no cabe en una sola linea de una region pequena de la pantalla dividida y por eso se parte en varias lineas";

    /** Los avisos del servidor llegan a todos (P1 en su chat, cada extra en su region), un aviso largo se parte al ancho de la region y el chat de P1 no la desborda. */
    private static void chatTest(Minecraft mc) {
        PlayerContext c2 = SecondaryPlayers.context(1);
        var server = mc.getSingleplayerServer();
        switch (ctPhase) {
            case 0 -> {
                if (ticks == 1) {
                    SplitNotices.p1Log.clear();
                    SecondaryPlayers.join(mc, 2);
                    pads.attachVirtual(2);
                }
                if (ticks > 30 && extraReady(mc, 2)) {
                    ctPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> {
                PlayerContext c3 = SecondaryPlayers.context(2);
                if (ticks == 20) {
                    // El aviso lleva el nombre visible del jugador (PlayerLabels: "Jugador 3" o el elegido), no el del perfil.
                    String n3 = PlayerLabels.labelOf(2);
                    boolean p1 = SplitNotices.p1Log.stream().anyMatch(m -> m.contains(n3) && m.contains("joined"));
                    ctVerdict("\"" + n3 + " joined the game\" llega a P1 (" + p1 + "), a P2 (" + hasNotice(c2, n3 + " joined the game") + ") y al propio P3 (" + hasNotice(c3, n3 + " joined the game") + ")",
                            p1 && hasNotice(c2, n3 + " joined the game") && hasNotice(c3, n3 + " joined the game"));
                    server.execute(() -> {
                        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal("aviso para todos"), false);
                        server.getPlayerList().broadcastSystemMessage(net.minecraft.network.chat.Component.literal(LONG_TEXT), false);
                    });
                }
                if (ticks == 45) {
                    boolean p1 = SplitNotices.p1Log.stream().anyMatch(m -> m.contains("aviso para todos"));
                    ctVerdict("un aviso del servidor llega a todos: P1=" + p1 + ", P2=" + hasNotice(c2, "aviso para todos") + ", P3=" + hasNotice(c3, "aviso para todos"),
                            p1 && hasNotice(c2, "aviso para todos") && hasNotice(c3, "aviso para todos"));
                    int w2 = c2.noticeMaxWidth, w3 = c3.noticeMaxWidth;
                    ctVerdict("el aviso largo se parte al ancho de la region: linea mas ancha P2=" + w2 + " (region " + c2.hudW + "), P3=" + w3 + " (region " + c3.hudW + "), lineas dibujadas P2="
                            + c2.noticesDrawn, w2 > 0 && w2 <= c2.hudW && w3 <= c3.hudW && c2.noticesDrawn >= 3);
                    int cap = mc.gui.getChat().getWidth();
                    ctVerdict("el chat de P1 no es mas ancho que su region: ancho " + cap + " (region " + SplitView.p1HudW + ")", cap > 0 && cap <= SplitView.p1HudW);
                    mc.setScreen(new net.minecraft.client.gui.screens.ChatScreen(""));
                }
                if (ticks == 60) {
                    boolean fits = mc.screen instanceof net.minecraft.client.gui.screens.ChatScreen && mc.screen.width == SplitView.p1LogicalW && mc.screen.height == SplitView.p1LogicalH;
                    ctVerdict("la pantalla del chat de P1 ocupa su region: " + (mc.screen == null ? "-" : mc.screen.width + "x" + mc.screen.height) + " de " + SplitView.p1LogicalW + "x" + SplitView.p1LogicalH, fits);
                    mc.setScreen(null);
                    ctPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> {
                if (ticks == 1) ConsolePad.LOGGER.info("[autotest] RESULTADO chat y avisos por region en conjunto -> {}", ctOk ? "OK" : "FALLO");
                if (leaveTo(mc, 2) || ticks > 600) {
                    ctPhase = 3;
                    next();
                }
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- menu ampliado: ajustes, reparto y remapeo de controles

    private static boolean mnOk = true;
    private static int mnPhase;
    private static SplitSettings.Orientation mnOrig;
    private static float mnDead0;
    private static int mnJump0, mnSwap0;

    private static void mnVerdict(String name, boolean ok) {
        mnOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static void mnCursor(PlayerContext c, int[] at) {
        c.cursorX = at[0];
        c.cursorY = at[1];
    }

    /** Pulsacion del boton A de P2 en el tick t0 (suelta 6 ticks despues). */
    private static void mnClick2(int t, int t0) {
        if (t == t0) simP2(1 << PadState.A);
        if (t == t0 + 6) simP2(0);
    }

    private static void menusTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        var s0 = ConsolePad.PADS.slot(0).settings;
        var s1 = ConsolePad.PADS.slot(1).settings;
        int t = ticks;
        switch (mnPhase) {
            case 0 -> { // ajustes de P2 desde su menu
                if (t == 1) {
                    mnOrig = SplitSettings.orientation();
                    SplitSettings.setOrientation(SplitSettings.Orientation.SIDE_BY_SIDE);
                    s1.guiScale = 0;
                    mnDead0 = s1.moveDeadzone;
                    SecondaryTick.scriptActive = true;
                    SecondaryTick.scriptForward = 0f;
                    press(PadAction.PAUSE);
                }
                if (t == 3) release();
                if (t == 20) {
                    mnVerdict("Start abre el menu ampliado de P2: " + (c.screen == null ? "ninguno" : c.screen.getClass().getSimpleName()), c.screen instanceof PlayerPauseScreen);
                    if (c.screen instanceof PlayerPauseScreen m) mnCursor(c, m.settingsCenter());
                }
                mnClick2(t, 23);
                if (t == 40) {
                    boolean ok = c.screen instanceof PlayerSettingsScreen;
                    mnVerdict("\"Ajustes del jugador\" abre sus ajustes: " + (c.screen == null ? "ninguno" : c.screen.getClass().getSimpleName()), ok);
                    if (c.screen instanceof PlayerSettingsScreen m) mnCursor(c, m.guiScaleCenter());
                }
                for (int clickAt : new int[] {43, 53, 63}) { // automatica -> 1 -> 2 -> 3; al cambiar la escala la pantalla se recoloca, el cursor se vuelve a poner encima
                    if (t == clickAt - 1 && c.screen instanceof PlayerSettingsScreen m) mnCursor(c, m.guiScaleCenter());
                    mnClick2(t, clickAt);
                }
                if (t == 80) {
                    int expectW = c.pw / 3;
                    mnVerdict("escala de interfaz de P2 a 3 desde su menu: ajuste=" + s1.guiScale + ", escala en vigor=" + c.guiScaleEff + ", interfaz " + c.hudW + " (esperado " + expectW
                            + "), P1 sin cambios (ajuste " + s0.guiScale + ", escala " + SplitView.p1GuiScaleEff + ")",
                            s1.guiScale == 3 && c.guiScaleEff == 3 && c.hudW == expectW && s0.guiScale == 0);
                    if (c.screen instanceof PlayerSettingsScreen m) mnCursor(c, m.moveDeadCenter());
                }
                mnClick2(t, 83);
                if (t == 98) {
                    mnVerdict("zona muerta del movimiento de P2: " + mnDead0 + " -> " + s1.moveDeadzone + " (cambia), P1 " + s0.moveDeadzone, Math.abs(s1.moveDeadzone - mnDead0) > 0.01f);
                    simP2(1 << PadState.B);
                }
                if (t == 104) simP2(0);
                if (t == 115) {
                    var onDisk = dev.consolepad.pad.PadSettingsStore.load(1, new dev.consolepad.pad.PadSettings());
                    mnVerdict("B en los ajustes vuelve al menu y guarda en slot1.json: pantalla=" + (c.screen == null ? "-" : c.screen.getClass().getSimpleName()) + ", escala guardada=" + onDisk.guiScale,
                            c.screen instanceof PlayerPauseScreen && onDisk.guiScale == 3);
                    if (c.screen instanceof PlayerPauseScreen m) mnCursor(c, m.layoutCenter());
                }
                mnClick2(t, 118);
                if (t == 135) {
                    var rr = new java.util.ArrayList<int[]>();
                    rr.add(SplitView.p1Px.clone());
                    rr.add(new int[] {c.px, c.py, c.pw, c.ph});
                    var win = mc.getWindow();
                    boolean toggled = SplitSettings.orientation() == SplitSettings.Orientation.STACKED && shapeOk(2, SplitSettings.Orientation.STACKED, win.getWidth(), win.getHeight(), rr);
                    boolean menuFits = c.screen != null && c.screen.width == c.logicalW && c.screen.height == c.logicalH;
                    boolean saved = false;
                    try {
                        saved = java.nio.file.Files.readString(net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("consolepad").resolve("split.json")).contains("STACKED");
                    } catch (java.io.IOException e) {
                        // se informa abajo
                    }
                    mnVerdict("\"Reparto\" desde el menu de P2: apilado=" + toggled + ", su menu abierto se reajusta a su nueva region (" + (c.screen == null ? "-" : c.screen.width + "x" + c.screen.height)
                            + " de " + c.logicalW + "x" + c.logicalH + ")=" + menuFits + ", guardado en split.json=" + saved, toggled && menuFits && saved);
                    if (c.screen instanceof PlayerPauseScreen m) mnCursor(c, m.layoutCenter());
                }
                mnClick2(t, 138);
                if (t == 155) {
                    mnVerdict("\"Reparto\" otra vez: vuelve a lado a lado (" + SplitSettings.orientation() + ")", SplitSettings.orientation() == SplitSettings.Orientation.SIDE_BY_SIDE);
                    s1.guiScale = 0;
                    s1.moveDeadzone = mnDead0;
                }
                if (t == 157 && c.screen instanceof PlayerPauseScreen m) mnCursor(c, m.controlsCenter()); // despues de recolocarse con la escala automatica
                mnClick2(t, 158);
                if (t == 175) {
                    mnVerdict("\"Controles\" abre el remapeo de P2: " + (c.screen == null ? "ninguno" : c.screen.getClass().getSimpleName()), c.screen instanceof PlayerControlsScreen);
                    if (c.screen instanceof PlayerControlsScreen m) mnCursor(c, m.rowCenter(PadAction.JUMP));
                    mnPhase = 1;
                    ticks = 0;
                }
            }
            case 1 -> { // remapeo: conflicto + intercambio
                PlayerControlsScreen cs = c.screen instanceof PlayerControlsScreen m ? m : null;
                mnClick2(t, 3); // fila "Saltar"
                if (t == 15) {
                    mnVerdict("elegir la accion Saltar: escuchando=" + (cs == null ? "-" : cs.listeningAction()) + ", pantalla pide el boton=" + (cs != null && cs.listeningForPad()), cs != null && cs.listeningAction() == PadAction.JUMP && cs.listeningForPad());
                    simP2(1 << PadState.X); // X ya es "Cambiar de mano"
                }
                if (t == 21) simP2(0);
                if (t == 30) {
                    String w = cs == null ? "<sin pantalla>" : cs.warning().getString();
                    boolean unchanged = s1.bindings.get(PadState.A) == PadAction.JUMP && s1.bindings.get(PadState.X) == PadAction.SWAP_HANDS;
                    mnVerdict("boton ya en uso: aviso \"" + w + "\", sin cambios todavia=" + unchanged, w.contains("ya usa") && unchanged && cs != null && cs.listeningForPad());
                    simP2(1 << PadState.X); // otra vez: intercambiar
                }
                if (t == 36) simP2(0);
                if (t == 45) {
                    var onDisk = dev.consolepad.pad.PadSettingsStore.load(1, new dev.consolepad.pad.PadSettings());
                    boolean swapped = s1.bindings.get(PadState.X) == PadAction.JUMP && s1.bindings.get(PadState.A) == PadAction.SWAP_HANDS;
                    mnVerdict("intercambio: X=" + s1.bindings.get(PadState.X) + " (Saltar), A=" + s1.bindings.get(PadState.A) + " (Cambiar de mano), P1 intacto (A=" + s0.bindings.get(PadState.A)
                            + ", X=" + s0.bindings.get(PadState.X) + "), guardado en slot1.json: X=" + onDisk.bindings.get(PadState.X) + ", escuchando=" + (cs != null && cs.listeningForPad()),
                            swapped && cs != null && !cs.listeningForPad() && s0.bindings.get(PadState.A) == PadAction.JUMP && s0.bindings.get(PadState.X) == PadAction.SWAP_HANDS
                                    && onDisk.bindings.get(PadState.X) == PadAction.JUMP);
                    simP2(1 << PadState.B); // vuelve al menu
                }
                if (t == 51) simP2(0);
                if (t == 60) simP2(1 << PadState.B); // cierra el menu
                if (t == 66) simP2(0);
                if (t == 80) {
                    mnVerdict("tras remapear se cierran los menus", c.screen == null);
                    SecondaryTick.scriptActive = false; // el mando de P2 manda otra vez
                    mnJump0 = SecondaryTick.dbgJumpTicks;
                    mnSwap0 = SecondaryTick.dbgSwapHands;
                    simP2(1 << PadState.X);
                }
                if (t == 90) {
                    simP2(0);
                    boolean jumped = SecondaryTick.dbgJumpTicks - mnJump0 >= 3;
                    mnVerdict("X salta con el mando de P2 tras remapear: ticks de salto +" + (SecondaryTick.dbgJumpTicks - mnJump0), jumped);
                    simP2(1 << PadState.A);
                }
                if (t == 100) {
                    simP2(0);
                    mnVerdict("A cambia de mano con el mando de P2 tras remapear: cambios de mano +" + (SecondaryTick.dbgSwapHands - mnSwap0), SecondaryTick.dbgSwapHands - mnSwap0 >= 1);
                    SecondaryTick.scriptActive = true;
                    SecondaryTick.scriptForward = 0f;
                    press(PadAction.PAUSE);
                }
                if (t == 103) release();
                if (t == 120 && c.screen instanceof PlayerPauseScreen m) mnCursor(c, m.controlsCenter());
                mnClick2(t, 123);
                if (t == 140) {
                    if (c.screen instanceof PlayerControlsScreen m) mnCursor(c, m.rowCenter(PadAction.DROP));
                }
                mnClick2(t, 143); // fila "Soltar" (en B)
                if (t == 155) {
                    simP2(1 << PadState.DPAD_LEFT); // boton libre
                }
                if (t == 161) simP2(0);
                if (t == 170) {
                    boolean free = s1.bindings.get(PadState.DPAD_LEFT) == PadAction.DROP && !s1.bindings.containsKey(PadState.B);
                    mnVerdict("boton libre: Cruceta izquierda=" + s1.bindings.get(PadState.DPAD_LEFT) + " (Soltar), B sin accion=" + !s1.bindings.containsKey(PadState.B) + ", aviso=\"" + (cs == null ? "-" : cs.warning().getString()) + "\"",
                            free && cs != null && !cs.warning().getString().contains("ya usa"));
                    if (cs != null) mnCursor(c, cs.rowCenter(PadAction.ATTACK));
                }
                mnClick2(t, 173); // "Atacar" y no se pulsa nada: se cancela solo
                if (t == 185) mnVerdict("esperando un boton", cs != null && cs.listeningForPad());
                if (t == 290) {
                    mnVerdict("sin pulsar nada la escucha se cancela sola", cs != null && !cs.listeningForPad() && cs.listeningAction() == null);
                    if (cs != null) mnCursor(c, cs.resetCenter());
                }
                mnClick2(t, 293);
                if (t == 310) {
                    var onDisk = dev.consolepad.pad.PadSettingsStore.load(1, new dev.consolepad.pad.PadSettings());
                    boolean def = s1.bindings.equals(dev.consolepad.pad.PadSettings.defaultBindings()) && onDisk.bindings.equals(dev.consolepad.pad.PadSettings.defaultBindings());
                    mnVerdict("Restablecer devuelve los controles de fabrica (en memoria y en slot1.json)=" + def, def);
                    simP2(1 << PadState.B);
                }
                if (t == 316) simP2(0);
                if (t == 325) simP2(1 << PadState.B);
                if (t == 331) simP2(0);
                if (t == 345) {
                    mnVerdict("menus de P2 cerrados", c.screen == null);
                    SecondaryTick.scriptActive = false;
                    mnPhase = 2;
                    ticks = 0;
                }
            }
            case 2 -> { // P1: menu ampliado con el raton y remapeo con su mando
                if (t == 1) simSlot(0, 1 << PadState.START);
                if (t == 8) simSlot(0, 0);
                if (t == 20) {
                    mnVerdict("Start abre el menu ampliado de P1: " + (mc.screen == null ? "ninguno" : mc.screen.getClass().getSimpleName()) + " (anchura " + (mc.screen == null ? -1 : mc.screen.width)
                            + " de " + SplitView.p1LogicalW + ")", mc.screen instanceof PlayerPauseScreen && mc.screen.width == SplitView.p1LogicalW);
                    if (mc.screen instanceof PlayerPauseScreen m) p1ClickAt(mc, m.controlsCenter());
                }
                if (t == 26) p1Mouse(mc, false);
                if (t == 40) {
                    mnVerdict("P1: \"Controles\" con el raton: " + (mc.screen == null ? "ninguno" : mc.screen.getClass().getSimpleName()), mc.screen instanceof PlayerControlsScreen);
                    if (mc.screen instanceof PlayerControlsScreen m) p1ClickAt(mc, m.rowCenter(PadAction.JUMP));
                }
                if (t == 46) p1Mouse(mc, false);
                if (t == 55) {
                    boolean listening = mc.screen instanceof PlayerControlsScreen m && m.listeningForPad();
                    mnVerdict("P1: Saltar espera un boton de su mando=" + listening, listening);
                    simSlot(0, 1 << PadState.DPAD_LEFT); // boton libre
                }
                if (t == 62) simSlot(0, 0);
                if (t == 75) {
                    boolean ok = s0.bindings.get(PadState.DPAD_LEFT) == PadAction.JUMP && s1.bindings.get(PadState.A) == PadAction.JUMP;
                    mnVerdict("P1 remapea Saltar a Cruceta izquierda con su mando: " + s0.bindings.get(PadState.DPAD_LEFT) + "; A de P1=" + s0.bindings.get(PadState.A) + "; P2 intacto (A=" + s1.bindings.get(PadState.A) + ")",
                            ok && s0.bindings.get(PadState.A) == null);
                    if (mc.screen instanceof PlayerControlsScreen m) p1ClickAt(mc, m.resetCenter());
                }
                if (t == 81) p1Mouse(mc, false);
                if (t == 95) {
                    mnVerdict("P1: Restablecer deja los controles de fabrica=" + s0.bindings.equals(dev.consolepad.pad.PadSettings.defaultBindings()), s0.bindings.equals(dev.consolepad.pad.PadSettings.defaultBindings()));
                    simSlot(0, 1 << PadState.B);
                }
                if (t == 101) simSlot(0, 0);
                if (t == 110) simSlot(0, 1 << PadState.START);
                if (t == 117) simSlot(0, 0);
                if (t == 135) {
                    mnVerdict("Start cierra el menu de P1 desde el menu ampliado (dos pantallas encadenadas): " + (mc.screen == null ? "ninguna" : mc.screen.getClass().getSimpleName()), mc.screen == null);
                    mnPhase = 9;
                    ticks = 0;
                }
            }
            case 9 -> {
                SplitSettings.setOrientation(mnOrig);
                s1.guiScale = 0;
                SecondaryTick.scriptActive = false;
                simP2(0);
                ConsolePad.LOGGER.info("[autotest] RESULTADO menu ampliado (ajustes, reparto y controles) en conjunto -> {}", mnOk ? "OK" : "FALLO");
                mnPhase = 10;
                next();
            }
            default -> { }
        }
    }

    // ---------------------------------------------------------------- maniqui del jugador en el inventario de cada jugador

    private static boolean mqOk = true;
    private static int mqPhase;

    private static void mqVerdict(String name, boolean ok) {
        mqOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    /** Colores distintos dentro de un recuadro dado en el scissor GL (origen abajo a la izquierda) sobre la imagen (fila 0 arriba). */
    private static int distinctColors(com.mojang.blaze3d.platform.NativeImage img, int[] box) {
        java.util.Set<Integer> colors = new java.util.HashSet<>();
        int top = img.getHeight() - (box[1] + box[3]);
        for (int y = Math.max(0, top); y < Math.min(img.getHeight(), top + box[3]); y++) {
            for (int x = Math.max(0, box[0]); x < Math.min(img.getWidth(), box[0] + box[2]); x++) {
                colors.add(img.getPixelRGBA(x, y) & 0xFFFFFF);
            }
        }
        return colors.size();
    }

    /**
     * El maniqui del jugador en su inventario (P1 a P4): su recorte cae dentro de su region con un tamano razonable y, mirando los pixeles de
     * ese recuadro en la imagen final, hay colores de un modelo dibujado (no el fondo plano). La causa del maniqui vacio era el recorte:
     * enableScissor no mira la pose y el de P2-P4 caia en la region de P1.
     */
    private static void mannequinTest(Minecraft mc) {
        switch (mqPhase) {
            case 0 -> {
                if (joinTo(mc, 4) && ticks > 20) {
                    mqPhase = 1;
                    ticks = 0;
                    scaOpenScreens(mc);
                }
            }
            case 1 -> {
                if (ticks == 10) {
                    SplitDiag.uiClear();
                    SplitDiag.uiRecording = true;
                }
                if (ticks == 14) {
                    SplitDiag.uiRecording = false;
                    pixelProbe = img -> {
                        var draws = SplitDiag.uiDraws();
                        java.util.Map<String, int[]> region = ucRegions();
                        int base = -1;
                        for (String owner : new String[] {"P1", "P2", "P3", "P4"}) {
                            int[] r = region.get(owner);
                            int[] best = null;
                            for (var d : draws) {
                                int[] b = d.scissor();
                                if (!d.owner().equals(owner) || !d.what().equals("scissor") || b[2] <= 0 || b[3] <= 0) continue;
                                boolean small = b[2] < r[2] - 10 && b[3] < r[3] - 10;
                                if (small && b[0] >= r[0] - 2 && b[1] >= r[1] - 2 && b[0] + b[2] <= r[0] + r[2] + 2 && b[1] + b[3] <= r[1] + r[3] + 2) best = b;
                            }
                            int colors = best == null ? 0 : distinctColors(img, best);
                            if (owner.equals("P1")) base = colors;
                            boolean ok = best != null && colors >= 15 && (owner.equals("P1") || colors >= base * 0.4);
                            mqVerdict(owner + ": maniqui del inventario: recorte " + (best == null ? "no encontrado en su region" : java.util.Arrays.toString(best) + " dentro de " + java.util.Arrays.toString(r))
                                    + ", colores distintos en el recuadro=" + colors + " (P1=" + base + ", minimo 15 y 40 % del de P1)", ok);
                        }
                        scaCloseScreens(mc);
                        mqPhase = 2;
                        ticks = 0;
                    };
                }
            }
            case 2 -> {
                if (ticks == 1) ConsolePad.LOGGER.info("[autotest] RESULTADO maniqui del jugador en el inventario de P1 a P4 en conjunto -> {}", mqOk ? "OK" : "FALLO");
                if (leaveTo(mc, 2) || ticks > 600) {
                    mqPhase = 3;
                    next();
                }
            }
            default -> { }
        }
    }

    private static boolean ptOk = true;
    private static net.minecraft.world.level.GameType ptPrev;
    private static int ptPhase;

    private static void ptVerdict(String name, boolean ok) {
        ptOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    private static void simSlot(int slot, int bits) {
        PadManager.Device d = pads.slot(slot).device;
        if (d != null) d.state.simDown = bits;
    }

    private static boolean cursorAt(PlayerContext c, double x, double y) {
        return Math.abs(c.cursorX - x) < 1.5 && Math.abs(c.cursorY - y) < 1.5;
    }

    private static double[] slotCenter(PlayerContext c, int menuSlot) {
        var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) c.screen;
        net.minecraft.world.inventory.Slot sl = screen.getMenu().slots.get(menuSlot);
        return new double[] {screen.getGuiLeft() + sl.x + 8, screen.getGuiTop() + sl.y + 8};
    }

    /** Indice de slot de menu cuyo centro esta en (x, y) (con tolerancia), o -1. */
    private static int slotAt(PlayerContext c, double x, double y) {
        var screen = (net.minecraft.client.gui.screens.inventory.AbstractContainerScreen<?>) c.screen;
        for (net.minecraft.world.inventory.Slot sl : screen.getMenu().slots) {
            if (Math.abs(screen.getGuiLeft() + sl.x + 8 - x) < 1.5 && Math.abs(screen.getGuiTop() + sl.y + 8 - y) < 1.5) return sl.index;
        }
        return -1;
    }

    /**
     * Pasos pendientes de medir: (A) D-pad en el inventario de P2 (el cursor salta de hueco en hueco), (B) D-pad en su menu de pausa (de boton
     * en boton), (C) atajo Start+Back de P3 y P4 con mandos virtuales en los slots 2 y 3: pulsar entra, mantener 2 s sale.
     */
    private static void padTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        int both = (1 << PadState.START) | (1 << PadState.BACK);
        switch (ptPhase) {
            case 0 -> {
                switch (ticks) {
                    case 1 -> {
                        ptPrev = p2.gameMode.getGameModeForPlayer();
                        mc.getSingleplayerServer().execute(() -> p2.setGameMode(net.minecraft.world.level.GameType.SURVIVAL));
                        SecondaryTick.scriptActive = true;
                        SecondaryTick.scriptForward = 0f;
                    }
                    case 10 -> press(PadAction.INVENTORY);
                    case 12 -> release();
                    case 30 -> {
                        double[] a = slotCenter(c, 20);
                        c.cursorX = a[0];
                        c.cursorY = a[1];
                    }
                    case 33 -> simP2(1 << PadState.DPAD_RIGHT);
                    case 37 -> simP2(0);
                    case 50 -> {
                        double[] e = slotCenter(c, 21);
                        ptVerdict("D-pad derecha en el inventario de P2: cursor (" + c.cursorX + ", " + c.cursorY + ") en el centro del slot 21 (" + e[0] + ", " + e[1] + ")", cursorAt(c, e[0], e[1]));
                    }
                    case 52 -> simP2(1 << PadState.DPAD_DOWN);
                    case 56 -> simP2(0);
                    case 70 -> {
                        double[] e = slotCenter(c, 21);
                        int below = slotAt(c, e[0], e[1] + 18);
                        ptVerdict("D-pad abajo en el inventario de P2: cursor sobre el slot " + slotAt(c, c.cursorX, c.cursorY) + ", el de debajo del 21 es el " + below, below >= 0 && cursorAt(c, e[0], e[1] + 18));
                    }
                    case 72 -> simP2(1 << PadState.DPAD_LEFT);
                    case 76 -> simP2(0);
                    case 90 -> {
                        double[] e = slotCenter(c, 20);
                        ptVerdict("D-pad izquierda en el inventario de P2: cursor sobre el slot " + slotAt(c, c.cursorX, c.cursorY) + ", esperado el de debajo del 20", cursorAt(c, e[0], e[1] + 18));
                        simP2(1 << PadState.B);
                    }
                    case 96 -> simP2(0);
                    case 110 -> {
                        ptVerdict("cerrar el inventario con B", c.screen == null);
                        press(PadAction.PAUSE);
                    }
                    case 112 -> release();
                    case 130 -> {
                        if (c.screen instanceof PlayerPauseScreen m) {
                            int[] r = m.resumeCenter();
                            c.cursorX = r[0];
                            c.cursorY = r[1];
                        }
                    }
                    case 133 -> simP2(1 << PadState.DPAD_DOWN);
                    case 137 -> simP2(0);
                    case 145 -> {
                        int[] f = ((PlayerPauseScreen) c.screen).settingsCenter();
                        ptVerdict("D-pad abajo en el menu de P2: de Volver a Ajustes del jugador", cursorAt(c, f[0], f[1]));
                    }
                    case 147 -> simP2(1 << PadState.DPAD_DOWN);
                    case 151 -> simP2(0);
                    case 160 -> {
                        int[] q = ((PlayerPauseScreen) c.screen).controlsCenter();
                        ptVerdict("D-pad abajo en el menu de P2: de Ajustes del jugador a Controles", cursorAt(c, q[0], q[1]));
                    }
                    case 162 -> simP2(1 << PadState.DPAD_UP);
                    case 166 -> simP2(0);
                    case 175 -> {
                        int[] f = ((PlayerPauseScreen) c.screen).settingsCenter();
                        ptVerdict("D-pad arriba en el menu de P2: de Controles a Ajustes del jugador", cursorAt(c, f[0], f[1]));
                        simP2(1 << PadState.B);
                    }
                    case 181 -> simP2(0);
                    case 195 -> {
                        ptVerdict("cerrar el menu con B", c.screen == null);
                        mc.getSingleplayerServer().execute(() -> p2.setGameMode(ptPrev));
                        SecondaryTick.scriptActive = false;
                        pads.attachVirtual(2);
                        pads.attachVirtual(3);
                        ptPhase = 1;
                        ticks = 0;
                    }
                    default -> { }
                }
            }
            case 1 -> { // P3 entra con Start+Back en el slot 2
                if (ticks == 5) simSlot(2, both);
                if (ticks == 10) simSlot(2, 0);
                if (ticks > 30 && SecondaryPlayers.isActive(2) && extraReady(mc, 2)) {
                    ptVerdict("Start+Back en el mando 3 mete a P3 (slot 2)", true);
                    ptPhase = 2;
                    ticks = 0;
                } else if (ticks > 1500) {
                    ptVerdict("Start+Back en el mando 3 mete a P3 (slot 2)", false);
                    ptPhase = 4;
                    ticks = 0;
                }
            }
            case 2 -> { // P4 entra con Start+Back en el slot 3
                if (ticks == 5) simSlot(3, both);
                if (ticks == 10) simSlot(3, 0);
                if (ticks > 30 && SecondaryPlayers.isActive(3) && extraReady(mc, 3)) {
                    ptVerdict("Start+Back en el mando 4 mete a P4 (slot 3), con P2 y P3 dentro: jugadores dibujados=" + (1 + SplitRender.activeContexts().size()) + " (4)",
                            SplitRender.activeContexts().size() == 3);
                    ptPhase = 3;
                    ticks = 0;
                } else if (ticks > 1500) {
                    ptVerdict("Start+Back en el mando 4 mete a P4 (slot 3)", false);
                    ptPhase = 4;
                    ticks = 0;
                }
            }
            case 3 -> { // mantener 2 s en el slot 3 saca a P4; en el slot 2 saca a P3
                if (ticks == 5) simSlot(3, both);
                if (ticks == 80) simSlot(3, 0);
                if (ticks == 95) {
                    boolean gone = !SecondaryPlayers.isActive(3) && serverExtra(mc, 3) == null;
                    ptVerdict("mantener Start+Back en el mando 4 saca solo a P4: P4 fuera=" + gone + ", P3 dentro=" + SecondaryPlayers.isActive(2) + ", P2 dentro=" + SecondaryPlayers.isActive(1),
                            gone && SecondaryPlayers.isActive(2) && SecondaryPlayers.isActive(1));
                    simSlot(2, both);
                }
                if (ticks == 170) simSlot(2, 0);
                if (ticks == 190) {
                    boolean gone = !SecondaryPlayers.isActive(2) && serverExtra(mc, 2) == null;
                    ptVerdict("mantener Start+Back en el mando 3 saca solo a P3: P3 fuera=" + gone + ", P2 dentro=" + SecondaryPlayers.isActive(1), gone && SecondaryPlayers.isActive(1));
                    ptPhase = 4;
                    ticks = 0;
                }
            }
            case 4 -> {
                ConsolePad.LOGGER.info("[autotest] RESULTADO D-pad y atajos de P3/P4 en conjunto -> {}", ptOk ? "OK" : "FALLO");
                ptPhase = 5;
                next();
            }
            default -> { }
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
        SecondaryTick.autoRespawn = deathTestPhase == 0; // las pruebas dejan reaparecer solos a los extra, salvo la de la pantalla de muerte
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        // Si un mundo guardado trae a alguien muerto (los mobs de la noche), reaparece: la pantalla de muerte bloquearia la prueba.
        if (deathTestPhase == 0 && mc.screen instanceof net.minecraft.client.gui.screens.DeathScreen && mc.player != null && ticks % 20 == 0) mc.player.respawn();
        PlayerContext dead = SecondaryPlayers.context();
        if (deathTestPhase == 0 && dead != null && dead.player != null && dead.player.getHealth() <= 0f && ticks % 20 == 0) dead.player.respawn();
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
                    prevGuiScale = mc.options.guiScale().get();
                    mc.options.guiScale().set(0); // escala de interfaz automatica, como al instalar el juego (la del usuario puede ser fija)
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
            case 6 -> actionsTest(mc);
            case 7 -> screensTest(mc);
            case 8 -> screens2Test(mc);
            case 9 -> pauseMenuTest(mc);
            case 10 -> joinLeaveTest(mc);
            case 11 -> multiTest(mc);
            case 12 -> notesTest(mc);
            case 13 -> padTest(mc);
            case 14 -> aspectTest(mc, "pequena");
            case 15 -> resizeWindow(mc);
            case 16 -> aspectTest(mc, "1920x1080");
            case 17 -> pauseTest(mc);
            case 18 -> fovTest(mc);
            case 19 -> fpsPhase(mc, false);
            case 20 -> fpsPhase(mc, true);
            case 22 -> startCloseTest(mc);
            case 23 -> scaleTest(mc);
            case 24 -> uiClipTest(mc);
            case 25 -> deathTest(mc);
            case 26 -> chatTest(mc);
            case 27 -> menusTest(mc);
            case 28 -> mannequinTest(mc);
            case 29 -> joinPromptTest(mc);
            case 30 -> hintsTest(mc);
            case 31 -> soloMenuTest(mc);
            case 32 -> snapNavTest(mc);
            case 33 -> keyboardTest(mc);
            case 34 -> labelTest(mc);
            case 35 -> moveTest(mc);
            case 36 -> hudOptionsTest(mc);
            case 37 -> hudBoardsTest(mc);
            case 40 -> leaveTest(mc);
            default -> {
                if (state > 2 && state < 40) next(); // casilla libre entre las pruebas y la salida
            }
        }
    }

    /**
     * CONSOLEPAD_AUTOTEST_CASES=22,23 ejecuta solo esas pruebas (ademas de la preparacion y la salida): para repetir una sola sin pasar
     * por todo el autotest. Sin la variable corren todas.
     */
    private static final java.util.Set<Integer> ONLY = parseCases();

    private static java.util.Set<Integer> parseCases() {
        String v = System.getenv("CONSOLEPAD_AUTOTEST_CASES");
        if (v == null || v.isBlank()) return null;
        java.util.Set<Integer> out = new java.util.HashSet<>();
        for (String p : v.split(",")) out.add(Integer.parseInt(p.trim()));
        return out;
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
                // Pasillo llano hacia +z (hacia donde mira P2): las pruebas de andar miden bloques recorridos y el sitio cambia de una partida a otra
                // (P1 se mueve y se guarda), asi que un escalon o un arbol en el camino las hacia fallar segun el terreno.
                BlockPos.MutableBlockPos bp = new BlockPos.MutableBlockPos();
                for (int dz = -2; dz <= 72; dz++) {
                    for (int dx = -1; dx <= 1; dx++) {
                        bp.set(x + dx, y - 1, z + dz);
                        if (!level.getBlockState(bp).isCollisionShapeFullBlock(level, bp)) level.setBlock(bp, net.minecraft.world.level.block.Blocks.STONE.defaultBlockState(), 2);
                        for (int dy = 0; dy <= 2; dy++) {
                            bp.set(x + dx, y + dy, z + dz);
                            if (!level.getBlockState(bp).isAir()) level.setBlock(bp, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 2);
                        }
                    }
                }
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
        while (ONLY != null && state >= 3 && state < 40 && !ONLY.contains(state)) state++;
        ticks = 0;
    }
}
