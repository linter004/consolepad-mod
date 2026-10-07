package dev.consolepad.client;

import dev.consolepad.mixin.ContainerScreenInvoker;
import dev.consolepad.mixin.KeyMappingAccessor;
import dev.consolepad.mixin.MouseHandlerInvoker;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadMath;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.InputType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

import org.lwjgl.glfw.GLFW;

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
    private int mouseDown;
    private float scrollAcc;

    // D-pad en pantallas: arriba, abajo, izquierda, derecha.
    private static final int[] DPAD = {PadState.DPAD_UP, PadState.DPAD_DOWN, PadState.DPAD_LEFT, PadState.DPAD_RIGHT};
    private static final int[] DPAD_KEYS = {GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT};
    private static final float REPEAT_DELAY = 0.35f;
    private static final float REPEAT_INTERVAL = 0.12f;
    private final boolean[] dpadActive = new boolean[4];
    private final float[] dpadHeld = new float[4];
    private final float[] dpadNext = new float[4];

    public PadDriver() {
        // Guarda los ajustes de todos los slots al cerrar el juego.
        Runtime.getRuntime().addShutdownHook(new Thread(pads::saveSettings, "ConsolePad-save"));
    }

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

        // Con una pantalla abierta: cursor virtual.
        if (mc.screen != null) {
            releaseAll(mc);
            screenFrame(mc, s, cfg, dt);
            return;
        }
        releaseMouseButtons(mc);

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
            float k = cfg.lookSpeed * sensitivityFactor(o) * dt / 0.15f; // Entity.turn multiplica por 0.15
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

    /** Cursor virtual: stick izq = cursor, A/X = clic izq/der, Y = mover rapido, stick der = scroll, B/Start = cerrar. */
    private void screenFrame(Minecraft mc, PadState s, PadSettings cfg, float dt) {
        long window = mc.getWindow().getWindow();
        MouseHandlerInvoker mouse = (MouseHandlerInvoker) mc.mouseHandler;

        // Parte de la posicion real del raton, asi que el raton fisico sigue funcionando.
        double x = mc.mouseHandler.xpos();
        double y = mc.mouseHandler.ypos();
        PadMath.radial(s.lx, s.ly, cfg.cursorDeadzone, cfg.cursorExponent, tmp);
        if (tmp[0] != 0f || tmp[1] != 0f) {
            double maxX = Math.max(0, mc.getWindow().getScreenWidth() - 1);
            double maxY = Math.max(0, mc.getWindow().getScreenHeight() - 1);
            float speed = cfg.cursorSpeed;
            if (mc.screen instanceof AbstractContainerScreen<?> c && c.getSlotUnderMouse() != null) {
                speed *= cfg.cursorSlotFactor; // precision al apuntar sobre un slot
            }
            double nx = Math.max(0, Math.min(maxX, x + tmp[0] * speed * dt));
            double ny = Math.max(0, Math.min(maxY, y + tmp[1] * speed * dt));
            if (nx != x || ny != y) {
                GLFW.glfwSetCursorPos(window, nx, ny);
                mouse.consolepad$onMove(window, nx, ny);
                mc.setLastInputType(InputType.MOUSE); // el stick devuelve el control al cursor libre
            }
        }

        dpadNavigation(mc, mouse, window, s, dt);

        // Clics: pulsar y soltar por separado, para poder arrastrar.
        if (!(mc.screen instanceof AbstractContainerScreen<?>) && mc.getLastInputType().isKeyboard()) {
            // Modo foco (ultimo movimiento con el D-pad): A activa el elemento enfocado, como Enter.
            if (s.pressed(PadState.A)) mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            if (s.released(PadState.A) && (mouseDown & 1) != 0) { // clic empezado antes de cambiar de modo
                mouse.consolepad$onPress(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
                mouseDown &= ~1;
            }
        } else {
            mouseButton(mouse, window, s, PadState.A, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        mouseButton(mouse, window, s, PadState.X, GLFW.GLFW_MOUSE_BUTTON_RIGHT);

        if (s.pressed(PadState.Y) && mc.screen instanceof AbstractContainerScreen<?> container) {
            Slot slot = container.getSlotUnderMouse();
            if (slot != null) {
                ((ContainerScreenInvoker) container).consolepad$slotClicked(slot, slot.index, 0, ClickType.QUICK_MOVE);
            }
        }

        // Scroll con el stick derecho, acumulado por segundo (arriba = scroll hacia arriba).
        PadMath.radial(s.rx, s.ry, cfg.lookDeadzone, 1.0f, tmp);
        scrollAcc += -tmp[1] * cfg.scrollSpeed * dt;
        int steps = (int) scrollAcc;
        if (steps != 0) {
            scrollAcc -= steps;
            mouse.consolepad$onScroll(window, 0.0, steps);
        }

        if ((s.pressed(PadState.B) || s.pressed(PadState.START)) && mc.screen.shouldCloseOnEsc()) {
            releaseMouseButtons(mc);
            mc.screen.onClose();
        }
    }

    /** D-pad en pantallas: salta entre slots (contenedores) o usa la navegacion por foco de Minecraft (menus). */
    private void dpadNavigation(Minecraft mc, MouseHandlerInvoker mouse, long window, PadState s, float dt) {
        for (int i = 0; i < DPAD.length; i++) {
            boolean fire = false;
            if (s.pressed(DPAD[i])) {
                dpadActive[i] = true;
                dpadHeld[i] = 0f;
                dpadNext[i] = REPEAT_DELAY;
                fire = true;
            } else if (dpadActive[i] && s.isDown(DPAD[i])) {
                dpadHeld[i] += dt;
                if (dpadHeld[i] >= dpadNext[i]) {
                    dpadNext[i] += REPEAT_INTERVAL;
                    fire = true;
                }
            } else {
                dpadActive[i] = false;
            }
            if (!fire || mc.screen == null) continue;
            if (mc.screen instanceof AbstractContainerScreen<?> container) {
                jumpToSlot(mc, mouse, window, container, i);
            } else {
                // Mismo camino que las flechas del teclado: Screen.keyPressed -> nextFocusPath(ArrowNavigation).
                mc.setLastInputType(InputType.KEYBOARD_ARROW);
                mc.screen.keyPressed(DPAD_KEYS[i], 0, 0);
            }
        }
    }

    /** Mueve el cursor al centro del slot mas cercano en la direccion pedida (0 arriba, 1 abajo, 2 izq, 3 der). */
    private void jumpToSlot(Minecraft mc, MouseHandlerInvoker mouse, long window,
                            AbstractContainerScreen<?> screen, int dir) {
        double sx = (double) mc.getWindow().getScreenWidth() / mc.getWindow().getGuiScaledWidth();
        double sy = (double) mc.getWindow().getScreenHeight() / mc.getWindow().getGuiScaledHeight();
        double gx = mc.mouseHandler.xpos() / sx;
        double gy = mc.mouseHandler.ypos() / sy;
        Slot current = screen.getSlotUnderMouse();
        double ox = gx, oy = gy;
        if (current != null) {
            ox = screen.getGuiLeft() + current.x + 8;
            oy = screen.getGuiTop() + current.y + 8;
        }

        Slot best = null;
        double bestScore = Double.MAX_VALUE;
        for (Slot slot : screen.getMenu().slots) {
            if (slot == current || !slot.isActive()) continue;
            double cx = screen.getGuiLeft() + slot.x + 8;
            double cy = screen.getGuiTop() + slot.y + 8;
            double dx = cx - ox, dy = cy - oy;
            double score;
            if (current == null) {
                score = dx * dx + dy * dy; // lejos de cualquier slot: salta al mas cercano
            } else {
                double primary = switch (dir) {
                    case 0 -> -dy;
                    case 1 -> dy;
                    case 2 -> -dx;
                    default -> dx;
                };
                if (primary < 1.0) continue; // no esta en esa direccion
                double perp = dir < 2 ? Math.abs(dx) : Math.abs(dy);
                score = primary + 2.0 * perp;
            }
            if (score < bestScore) {
                bestScore = score;
                best = slot;
            }
        }
        if (best == null) return;

        double nx = (screen.getGuiLeft() + best.x + 8) * sx;
        double ny = (screen.getGuiTop() + best.y + 8) * sy;
        nx = Math.max(0, Math.min(mc.getWindow().getScreenWidth() - 1, nx));
        ny = Math.max(0, Math.min(mc.getWindow().getScreenHeight() - 1, ny));
        GLFW.glfwSetCursorPos(window, nx, ny);
        mouse.consolepad$onMove(window, nx, ny);
    }

    private void mouseButton(MouseHandlerInvoker mouse, long window, PadState s, int padButton, int mouseButton) {
        if (s.pressed(padButton)) {
            mouse.consolepad$onPress(window, mouseButton, GLFW.GLFW_PRESS, 0);
            mouseDown |= 1 << mouseButton;
        }
        if (s.released(padButton) && (mouseDown & (1 << mouseButton)) != 0) {
            mouse.consolepad$onPress(window, mouseButton, GLFW.GLFW_RELEASE, 0);
            mouseDown &= ~(1 << mouseButton);
        }
    }

    /** Suelta los botones de raton que el mando mantuviera pulsados. */
    private void releaseMouseButtons(Minecraft mc) {
        scrollAcc = 0f;
        java.util.Arrays.fill(dpadActive, false);
        if (mouseDown == 0) return;
        long window = mc.getWindow().getWindow();
        MouseHandlerInvoker mouse = (MouseHandlerInvoker) mc.mouseHandler;
        for (int b = 0; b < 2; b++) {
            if ((mouseDown & (1 << b)) != 0) mouse.consolepad$onPress(window, b, GLFW.GLFW_RELEASE, 0);
        }
        mouseDown = 0;
    }

    /** Misma curva que el raton en vanilla ((s*0.6+0.2)^3*8): el 50 % por defecto equivale a 1. */
    private static float sensitivityFactor(Options o) {
        double base = o.sensitivity().get() * 0.6 + 0.2;
        return (float) (base * base * base * 8.0);
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
