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

    // Contadores de diagnostico: cuantas veces ejecuta P2 cada accion.
    public static volatile int dbgStartAttack, dbgContinueTrue, dbgStartUse, dbgJumpTicks, dbgHotbarSteps;

    private static final float TICK_SECONDS = 0.05f;
    private static final int MAX_LOGGED_ERRORS = 5;

    private static final float[] TMP = new float[2];
    private static boolean prevAttack;
    private static boolean prevUse;
    private static boolean prevLb, prevRb;
    private static int errors;

    private SecondaryTick() {}

    /** Una vez por tick de cliente, despues del tick de P1. */
    public static void tick(PadManager.Slot slot) {
        PlayerContext ctx = SecondaryPlayers.context();
        if (ctx == null || ctx.player == null || ctx.level == null || ctx.gameMode == null) return;

        float forward = 0f, left = 0f, lookX = 0f, lookY = 0f;
        boolean jump = false, attack = false, use = false, lb = false, rb = false;
        PadSettings cfg = slot.settings;
        if (scriptActive) {
            forward = scriptForward;
            jump = scriptJump;
            attack = scriptAttack;
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
                switch (e.getValue()) {
                    case JUMP -> jump |= down;
                    case ATTACK -> attack |= down;
                    case USE -> use |= down;
                    case HOTBAR_PREV -> lb |= down;
                    case HOTBAR_NEXT -> rb |= down;
                    default -> { } // el resto de acciones aun no existe para P2 (pantallas, soltar, etc.)
                }
            }
        }

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

        if (!(player.input instanceof PadInput)) player.input = new PadInput();
        PadInput input = (PadInput) player.input;
        input.rawForward = forward;
        input.rawLeft = left;
        input.jumping = jump;
        if (jump) dbgJumpTicks++;

        if (inv.consolepad$getRightClickDelay() > 0) inv.consolepad$setRightClickDelay(inv.consolepad$getRightClickDelay() - 1);
        mc.gameRenderer.pick(1.0F); // a que apunta P2 (hitResult de su contexto)
        mc.gameMode.tick();

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
        if (inv.consolepad$getMissTime() > 0) inv.consolepad$setMissTime(inv.consolepad$getMissTime() - 1);

        mc.level.tickRateManager().tick();
        mc.level.tickEntities();
        mc.level.tick(() -> true);
    }
}
