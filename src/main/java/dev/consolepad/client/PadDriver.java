package dev.consolepad.client;

import dev.consolepad.mixin.KeyMappingAccessor;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadMath;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import java.util.EnumSet;
import java.util.Map;

/** Traduce el mando del slot 0 a acciones del jugador local, una vez por frame. */
public final class PadDriver {
    // Leidos por KeyboardInputMixin en cada tick.
    public static volatile boolean analogActive;
    public static volatile float analogForward;
    public static volatile float analogLeft;

    private final PadManager pads = new PadManager();
    private final EnumSet<PadAction> held = EnumSet.noneOf(PadAction.class);
    private final float[] tmp = new float[2];
    private long lastNanos = System.nanoTime();
    private boolean sprintLatch;
    private boolean sneakLatch;

    public void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        pads.poll();
        PadManager.Slot slot = pads.slot(0);
        PadManager.Device dev = slot.device;
        if (dev == null || !mc.isWindowActive()) {
            releaseAll(mc);
            return;
        }
        PadState s = dev.state;
        PadSettings cfg = slot.settings;

        // Con una pantalla abierta: solo cerrar con B / Start (y Y en inventarios). Cursor = fase 2.
        if (mc.screen != null) {
            releaseAll(mc);
            boolean close = s.pressed(PadState.B) || s.pressed(PadState.START)
                    || (s.pressed(PadState.Y) && mc.screen instanceof AbstractContainerScreen<?>);
            if (close && mc.screen.shouldCloseOnEsc()) mc.screen.onClose();
            return;
        }

        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            releaseAll(mc);
            return;
        }
        Options o = mc.options;

        // Movimiento (stick izquierdo): lo aplica el mixin en el tick.
        PadMath.radial(s.lx, s.ly, cfg.moveDeadzone, 1.0f, tmp);
        analogForward = -tmp[1];
        analogLeft = -tmp[0];
        analogActive = tmp[0] != 0f || tmp[1] != 0f;
        if (sprintLatch && !analogActive) {
            sprintLatch = false;
            o.keySprint.setDown(false);
        }

        // Camara (stick derecho), independiente de los ticks para que sea fluida.
        PadMath.radial(s.rx, s.ry, cfg.lookDeadzone, cfg.lookExponent, tmp);
        if (tmp[0] != 0f || tmp[1] != 0f) {
            float k = cfg.lookSpeed * dt / 0.15f; // Entity.turn multiplica por 0.15
            float pitch = cfg.invertY ? -tmp[1] : tmp[1];
            player.turn(tmp[0] * k, pitch * k);
        }

        // Botones segun los bindings del jugador.
        for (Map.Entry<Integer, PadAction> e : cfg.bindings.entrySet()) {
            int code = e.getKey();
            PadAction action = e.getValue();
            if (s.pressed(code)) onPress(mc, player, o, action);
            if (s.released(code)) onRelease(o, action);
        }
    }

    private void onPress(Minecraft mc, LocalPlayer player, Options o, PadAction action) {
        KeyMapping hold = holdKey(o, action);
        if (hold != null) {
            hold.setDown(true);
            tap(hold);
            held.add(action);
            return;
        }
        switch (action) {
            case DROP -> tap(o.keyDrop);
            case SWAP_HANDS -> tap(o.keySwapOffhand);
            case INVENTORY -> tap(o.keyInventory);
            case PERSPECTIVE -> tap(o.keyTogglePerspective);
            case PICK_BLOCK -> tap(o.keyPickItem);
            case CHAT -> tap(o.keyChat);
            case HOTBAR_PREV -> player.getInventory().swapPaint(1.0);
            case HOTBAR_NEXT -> player.getInventory().swapPaint(-1.0);
            case SPRINT_TOGGLE -> {
                sprintLatch = !sprintLatch;
                o.keySprint.setDown(sprintLatch);
            }
            case SNEAK_TOGGLE -> {
                sneakLatch = !sneakLatch;
                o.keyShift.setDown(sneakLatch);
            }
            case PAUSE -> mc.pauseGame(false);
            default -> { }
        }
    }

    private void onRelease(Options o, PadAction action) {
        KeyMapping hold = holdKey(o, action);
        if (hold != null) {
            hold.setDown(false);
            held.remove(action);
        }
    }

    private static KeyMapping holdKey(Options o, PadAction a) {
        return switch (a) {
            case JUMP -> o.keyJump;
            case ATTACK -> o.keyAttack;
            case USE -> o.keyUse;
            case PLAYER_LIST -> o.keyPlayerList;
            default -> null;
        };
    }

    /** Equivale a una pulsacion de tecla: Minecraft la consume en su siguiente tick. */
    private static void tap(KeyMapping key) {
        KeyMappingAccessor acc = (KeyMappingAccessor) key;
        acc.consolepad$setClickCount(acc.consolepad$getClickCount() + 1);
    }

    /** Suelta todo lo que el mando mantenia pulsado (pantalla abierta, mando desconectado, ventana sin foco). */
    private void releaseAll(Minecraft mc) {
        analogActive = false;
        analogForward = 0f;
        analogLeft = 0f;
        if (held.isEmpty() && !sprintLatch && !sneakLatch) return;
        Options o = mc.options;
        for (PadAction a : held) {
            KeyMapping k = holdKey(o, a);
            if (k != null) k.setDown(false);
        }
        held.clear();
        if (sprintLatch) o.keySprint.setDown(false);
        if (sneakLatch) o.keyShift.setDown(false);
        sprintLatch = false;
        sneakLatch = false;
    }
}
