package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadAction;
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
                boolean p1Half = mc.screen != null && mc.screen.width == full / 2;
                boolean p2Half = c.screen != null && c.screen.width == full - full / 2;
                s2P1Digest = p1InventoryDigest(mc);
                ConsolePad.LOGGER.info("[autotest] dos pantallas: P1 {} ancho {} (mitad {}), P2 {} ancho {} (mitad {}), p1HalfScreen={}",
                        mc.screen == null ? "null" : mc.screen.getClass().getSimpleName(), mc.screen == null ? -1 : mc.screen.width, full / 2,
                        c.screen == null ? "null" : c.screen.getClass().getSimpleName(), c.screen == null ? -1 : c.screen.width, full - full / 2,
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
                int full = mc.getWindow().getGuiScaledWidth();
                boolean p2Menu = c.screen instanceof P2PauseScreen && c.screen.width == full - full / 2;
                pmVerdict("menu de pausa de P2 abierto en su mitad: " + (c.screen == null ? "null" : c.screen.getClass().getSimpleName())
                        + ", pantalla de P1=" + mc.screen, p2Menu && mc.screen == null);
                mc.pauseGame(false); // pausa de P1 a la vez
                pmStart = p2.position();
                pmServerTime0 = mc.getSingleplayerServer().overworld().getGameTime();
                pmClientTime0 = mc.level.getGameTime();
                SecondaryTick.scriptForward = 1f; // si P2 no estuviera en el menu, andaria
            }
            case 25 -> {
                int full = mc.getWindow().getGuiScaledWidth();
                boolean p1Pause = mc.screen instanceof net.minecraft.client.gui.screens.PauseScreen && mc.screen.width == full / 2;
                pmVerdict("pausa de P1 en su mitad: " + (mc.screen == null ? "null" : mc.screen.getClass().getSimpleName()) + " ancho "
                        + (mc.screen == null ? -1 : mc.screen.width) + " (mitad " + (full / 2) + ")", p1Pause);
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
                int[] r = ((P2PauseScreen) c.screen).resumeCenter();
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
                if (c.screen instanceof P2PauseScreen menu) {
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
            case 70 -> {
                if (c.screen instanceof P2PauseScreen m) {
                    int[] f = m.fovCenter();
                    c.cursorX = f[0];
                    c.cursorY = f[1];
                }
            }
            case 73 -> simP2(1 << PadState.A);
            case 79 -> simP2(0);
            case 88 -> {
                ntVerdict("ajuste de FOV de P2 desde su menu: fov de su slot=" + pads1.fov + " (esperado 60), P1=" + pads0.fov + " (0)", pads1.fov == 60 && pads0.fov == 0);
                if (c.screen instanceof P2PauseScreen m) {
                    int[] f = m.sensCenter();
                    c.cursorX = f[0];
                    c.cursorY = f[1];
                }
            }
            case 91 -> simP2(1 << PadState.A);
            case 97 -> simP2(0);
            case 106 -> {
                ntVerdict("ajuste de sensibilidad de P2 desde su menu: velocidad de giro=" + pads1.lookSpeed + " (esperado 225), P1=" + pads0.lookSpeed + " (sin cambios)",
                        Math.abs(pads1.lookSpeed - 225f) < 1f && Math.abs(pads0.lookSpeed - ntP1Speed) < 1f);
                pads1.fov = 0;
                pads1.lookSpeed = 180f;
                simP2(1 << PadState.B);
            }
            case 112 -> simP2(0);
            case 125 -> {
                ntVerdict("cerrar el menu de P2 con B tras los ajustes", c.screen == null);
                ConsolePad.LOGGER.info("[autotest] RESULTADO avisos y ajustes por jugador (4g) en conjunto -> {}", ntOk ? "OK" : "FALLO");
                SecondaryTick.scriptActive = false;
                simP2(0);
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
            case 6 -> actionsTest(mc);
            case 7 -> screensTest(mc);
            case 8 -> screens2Test(mc);
            case 9 -> pauseMenuTest(mc);
            case 10 -> joinLeaveTest(mc);
            case 11 -> multiTest(mc);
            case 12 -> notesTest(mc);
            case 13 -> aspectTest(mc, "pequena");
            case 14 -> resizeWindow(mc);
            case 15 -> aspectTest(mc, "1920x1080");
            case 16 -> pauseTest(mc);
            case 17 -> fovTest(mc);
            case 18 -> fpsPhase(mc, false);
            case 19 -> fpsPhase(mc, true);
            case 20 -> leaveTest(mc);
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
