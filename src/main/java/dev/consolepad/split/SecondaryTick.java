package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.client.PadDriver;
import dev.consolepad.mixin.MinecraftInvoker;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadMath;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import java.util.Map;

/**
 * Fase 3, paso 3: logica de cada tick de P2, dentro de su contexto. Reproduce lo que Minecraft.tick hace por el jugador
 * local (pick, gameMode.tick, acciones de ataque/uso, tickEntities, level.tick) pero con la entrada del mando del
 * slot 1. No dibuja nada (paso 4). Si no hay mando en el slot 1, P2 se queda quieto.
 */
public final class SecondaryTick {
    /** Entrada simulada para pruebas (AutoTest): si esta activa, sustituye al mando. */
    public static volatile boolean scriptActive;
    public static volatile float scriptForward;
    public static volatile boolean scriptJump;
    public static volatile boolean scriptAttack;
    public static volatile java.util.EnumSet<PadAction> scriptActions = java.util.EnumSet.noneOf(PadAction.class);
    /** Sprint conmutado de P2: lo lee KeyMappingGuardMixin cuando LocalPlayer.aiStep pregunta por keySprint. */
    public static volatile boolean sprintHeld;

    // Contadores de diagnostico: cuantas veces ejecuta P2 cada accion.
    public static volatile int dbgStartAttack, dbgContinueTrue, dbgStartUse, dbgJumpTicks, dbgHotbarSteps;
    public static volatile int dbgDrop, dbgSwapHands, dbgPerspective, dbgInventoryRequests;

    private static java.util.EnumSet<PadAction> prevDown = java.util.EnumSet.noneOf(PadAction.class);
    private static java.util.EnumSet<PadAction> pressedNow = java.util.EnumSet.noneOf(PadAction.class);
    private static boolean sprintLatch, sneakLatch;
    private static int deadTicks;

    private static final float TICK_SECONDS = 0.05f;
    private static final int MAX_LOGGED_ERRORS = 5;

    private static final float[] TMP = new float[2];
    private static boolean prevAttack;
    private static boolean prevUse;
    private static boolean prevLb, prevRb;
    private static int errors;

    private SecondaryTick() {}

    /** P2 sale: ningun boton queda "pulsado" para la proxima vez que entre. */
    public static void reset() {
        prevAttack = prevUse = prevLb = prevRb = false;
        prevDown = java.util.EnumSet.noneOf(PadAction.class);
        pressedNow = java.util.EnumSet.noneOf(PadAction.class);
        sprintLatch = sneakLatch = sprintHeld = false;
        scriptActions = java.util.EnumSet.noneOf(PadAction.class);
        scriptActive = false;
        scriptForward = 0f;
    }

    /** Una vez por tick de cliente, despues del tick de P1. */
    public static void tick(PadManager.Slot slot) {
        PlayerContext ctx = SecondaryPlayers.context();
        if (ctx == null || ctx.player == null || ctx.level == null || ctx.gameMode == null) return;

        float forward = 0f, left = 0f, lookX = 0f, lookY = 0f;
        boolean jump = false, attack = false, use = false, lb = false, rb = false;
        java.util.EnumSet<PadAction> downSet = java.util.EnumSet.noneOf(PadAction.class);
        PadSettings cfg = slot.settings;
        if (scriptActive) {
            forward = scriptForward;
            jump = scriptJump;
            attack = scriptAttack;
            downSet.addAll(scriptActions);
        } else if (slot.device != null) {
            PadState s = slot.device.state;
            PadMath.radial(s.lx, s.ly, cfg.moveDeadzone, 1.0f, TMP);
            forward = -TMP[1];
            left = -TMP[0];
            PadMath.radial(s.rx, s.ry, cfg.lookDeadzone, cfg.lookExponent, TMP);
            lookX = TMP[0];
            lookY = cfg.invertY ? -TMP[1] : TMP[1];
            // Mismos botones que P1: se leen los bindings del slot (por defecto, copia de los del slot 0).
            for (Map.Entry<Integer, PadAction> e : cfg.bindings.entrySet()) {
                boolean down = s.isDown(e.getKey());
                if (down) downSet.add(e.getValue());
                switch (e.getValue()) {
                    case JUMP -> jump |= down;
                    case ATTACK -> attack |= down;
                    case USE -> use |= down;
                    case HOTBAR_PREV -> lb |= down;
                    case HOTBAR_NEXT -> rb |= down;
                    default -> { } // el resto (soltar, cambio de mano, perspectiva, sprint, agacharse...) se ejecuta por flancos en secondaryTick
                }
            }
        }

        if (ctx.screen != null) { // con un menu abierto P2 no se mueve ni actua en el mundo (su mando maneja el cursor)
            forward = left = lookX = lookY = 0f;
            jump = attack = use = lb = rb = false;
            downSet.clear();
        }
        java.util.EnumSet<PadAction> pressed = java.util.EnumSet.copyOf(downSet);
        pressed.removeAll(prevDown);
        prevDown = downSet;
        pressedNow = pressed;
        final float f = forward, l = left, lx = lookX, ly = lookY;
        final boolean j = jump, a = attack, u = use, bl = lb, br = rb;
        try {
            ContextSwitcher.run(ctx, () -> secondaryTick(f, l, lx, ly, j, a, u, bl, br, cfg));
        } catch (RuntimeException e) {
            if (errors++ < MAX_LOGGED_ERRORS) ConsolePad.LOGGER.error("[split] error en el tick de P2 (suprimido)", e);
        }
    }

    private static void secondaryTick(float forward, float left, float lookX, float lookY,
                                      boolean jump, boolean attack, boolean use, boolean lb, boolean rb, PadSettings cfg) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        MinecraftInvoker inv = (MinecraftInvoker) mc;

        if (player.getHealth() <= 0f || player.isDeadOrDying()) { // sin pantalla de muerte para P2: reaparece solo
            if (++deadTicks == 60) player.respawn();
        } else {
            deadTicks = 0;
        }
        if (mc.screen != null) mc.screen.tick();
        if (!(player.input instanceof PadInput)) player.input = new PadInput();
        PadInput input = (PadInput) player.input;
        input.rawForward = forward;
        input.rawLeft = left;
        input.jumping = jump;
        input.shiftKeyDown = sneakLatch;
        if (jump) dbgJumpTicks++;

        if (inv.consolepad$getRightClickDelay() > 0) inv.consolepad$setRightClickDelay(inv.consolepad$getRightClickDelay() - 1);
        mc.gameRenderer.pick(1.0F); // a que apunta P2 (hitResult de su contexto)
        mc.gameMode.tick();
        ((dev.consolepad.mixin.GameRendererAccessor) mc.gameRenderer).consolepad$tickFov(); // FOV propio de P2 (camara = P2)
        PlayerContext self = SecondaryPlayers.context();
        if (self != null && self.hand != null) self.hand.tick(); // mano propia de P2 (lee minecraft.player = P2)
        if (self != null) SplitHud.tickToolHighlight(self); // nombre del objeto seleccionado de P2

        if (lookX != 0f || lookY != 0f) {
            float k = cfg.lookSpeed * PadDriver.sensitivityFactor(mc.options) * TICK_SECONDS / 0.15f;
            player.turn(lookX * k, lookY * k);
        }

        // Atacar y usar, como Minecraft.handleKeybinds pero con el mando de P2.
        if (attack && !prevAttack) {
            if (PadManager.PADLOG) ConsolePad.LOGGER.info("[padlog] P2 inicia ataque (lee slot 1)");
            dbgStartAttack++;
            inv.consolepad$startAttack();
        }
        if (attack) dbgContinueTrue++;
        inv.consolepad$continueAttack(attack);
        prevAttack = attack;
        if (use) {
            if (inv.consolepad$getRightClickDelay() == 0 && !player.isUsingItem()) {
                dbgStartUse++;
                inv.consolepad$startUseItem();
            }
        } else if (prevUse && player.isUsingItem()) {
            mc.gameMode.releaseUsingItem(player);
        }
        prevUse = use;

        // Hotbar de P2 (misma logica que P1: LB = anterior, RB = siguiente) sobre el inventario de P2.
        if (lb && !prevLb) {
            player.getInventory().swapPaint(1.0);
            dbgHotbarSteps++;
        }
        if (rb && !prevRb) {
            player.getInventory().swapPaint(-1.0);
            dbgHotbarSteps++;
        }
        prevLb = lb;
        prevRb = rb;
        // Acciones por flanco, con los bindings de P2 (por defecto, los de P1): como Minecraft.handleKeybinds / PadDriver.onPress.
        for (PadAction act : pressedNow) {
            switch (act) {
                case DROP -> {
                    if (!player.isSpectator() && player.drop(false)) player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
                    dbgDrop++;
                }
                case SWAP_HANDS -> {
                    if (!player.isSpectator()) {
                        player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerActionPacket(
                                net.minecraft.network.protocol.game.ServerboundPlayerActionPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                                net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.DOWN));
                    }
                    dbgSwapHands++;
                }
                case PERSPECTIVE -> {
                    var opts = (dev.consolepad.mixin.OptionsAccessor) mc.options; // dentro del contexto: el tipo de camara es el de P2
                    opts.consolepad$setCameraType(opts.consolepad$getCameraType().cycle());
                    dbgPerspective++;
                }
                case SPRINT_TOGGLE -> {
                    sprintLatch = !sprintLatch;
                    sprintHeld = sprintLatch;
                }
                case SNEAK_TOGGLE -> {
                    sneakLatch = !sneakLatch;
                    input.shiftKeyDown = sneakLatch;
                }
                case PAUSE -> mc.setScreen(new P2PauseScreen());
                case INVENTORY -> {
                    dbgInventoryRequests++;
                    if (mc.gameMode.isServerControlledInventory()) player.sendOpenInventory();
                    else mc.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(player));
                }
                default -> { }
            }
        }
        if (inv.consolepad$getMissTime() > 0) inv.consolepad$setMissTime(inv.consolepad$getMissTime() - 1);

        mc.level.tickRateManager().tick();
        mc.level.tickEntities();
        mc.level.tick(() -> true);
    }
}
