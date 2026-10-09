package dev.consolepad.client;

import dev.consolepad.ConsolePad;
import dev.consolepad.mixin.ContainerScreenInvoker;
import dev.consolepad.mixin.KeyMappingAccessor;
import dev.consolepad.mixin.MouseHandlerInvoker;
import dev.consolepad.pad.HoldToggle;
import dev.consolepad.pad.LookControl;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadMath;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import dev.consolepad.split.AutoTest;
import dev.consolepad.split.ConsoleCraft;
import dev.consolepad.split.PadKeyboard;
import dev.consolepad.split.SecondaryPlayers;
import dev.consolepad.split.SecondaryScreens;
import dev.consolepad.split.SecondaryTick;
import dev.consolepad.split.SplitView;
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
    private final LookControl.State lookState = new LookControl.State();
    private long lastNanos = System.nanoTime();
    private boolean sprintLatch;
    private boolean fwdState;
    private long lastFwdTap;
    private static final long DOUBLE_TAP_NANOS = 350_000_000L;
    private boolean sneakLatch;
    private int mouseDown;
    private float scrollAcc;

    // D-pad en pantallas: arriba, abajo, izquierda, derecha.
    private static final int[] DPAD = {PadState.DPAD_UP, PadState.DPAD_DOWN, PadState.DPAD_LEFT, PadState.DPAD_RIGHT};
    private static final int[] DPAD_KEYS = {GLFW.GLFW_KEY_UP, GLFW.GLFW_KEY_DOWN, GLFW.GLFW_KEY_LEFT, GLFW.GLFW_KEY_RIGHT};
    private static final float REPEAT_DELAY = 0.35f;
    private static final float REPEAT_INTERVAL = 0.12f;
    private final boolean[] dpadActive = new boolean[4];
    private final boolean[] dirPrev = new boolean[4];
    private final float[] dpadHeld = new float[4];
    private final float[] dpadNext = new float[4];

    public PadManager pads() {
        return pads;
    }

    public PadDriver() {
        // Guarda los ajustes de todos los slots al cerrar el juego.
        Runtime.getRuntime().addShutdownHook(new Thread(pads::saveSettings, "ConsolePad-save"));
    }

    private static final float LEAVE_HOLD_SECONDS = 2.0f;
    private final float[] joinHold = new float[SecondaryPlayers.MAX_EXTRA + 1];
    private final boolean[] joinLatch = new boolean[SecondaryPlayers.MAX_EXTRA + 1];

    public void onFrame(RenderFrameEvent.Pre event) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNanos) / 1.0e9f);
        lastNanos = now;

        pads.poll();
        dev.consolepad.split.ViewDistances.frame(mc);
        dev.consolepad.split.PadLost.frame(mc);
        dev.consolepad.split.PlayerLabels.tick(mc);
        // Start+Back en el mando de un slot 1..3: pulsar = entra ese jugador local extra; mantener 2 s con el jugador dentro = sale.
        for (int i = 1; i <= SecondaryPlayers.MAX_EXTRA; i++) {
            PadManager.Device devI = pads.slot(i).device;
            if (devI == null) {
                joinHold[i] = 0f;
                continue;
            }
            PadState si = devI.state;
            boolean both = si.isDown(PadState.START) && si.isDown(PadState.BACK);
            if (!SecondaryPlayers.isActive(i)) {
                // Fuera de la partida: pulsar Start en ese mando mete al jugador (el aviso de P1 lo anuncia); Start+Back sigue valiendo.
                joinHold[i] = 0f;
                if (si.pressed(PadState.START) || (both && si.pressed(PadState.BACK))) {
                    SecondaryPlayers.join(mc, i);
                    var joined = SecondaryPlayers.context(i);
                    if (joined != null) joined.input.comboLatch = true; // Start (y Back) no cuentan como pulsaciones del jugador hasta soltarlos
                    joinLatch[i] = true;
                }
                if (!si.isDown(PadState.START) && !si.isDown(PadState.BACK)) joinLatch[i] = false;
            } else if (!both) {
                joinHold[i] = 0f;
                joinLatch[i] = false; // hay que soltar los botones antes de que cuenten otra vez
            } else if (!joinLatch[i]) {
                joinHold[i] += dt; // dentro de la partida, Start+Back mantenido 2 s lo saca
                if (joinHold[i] >= LEAVE_HOLD_SECONDS) {
                    SecondaryPlayers.leave(mc, i);
                    joinLatch[i] = true;
                    joinHold[i] = 0f;
                }
            }
        }
        for (var extra : SecondaryPlayers.contexts()) {
            SecondaryScreens.frame(mc, pads.slot(extra.slotIndex), dt, extra);
            SecondaryTick.frameLook(mc, pads.slot(extra.slotIndex), extra, dt); // giro de camara por fotograma
        }
        PadManager.Slot slot = pads.slot(0);
        PadManager.Device dev = slot.device;
        if (dev == null || (!mc.isWindowActive() && !AutoTest.running())) {
            releaseAll(mc);
            return;
        }
        PadState s = dev.state;
        PadSettings cfg = slot.settings;

        // Con una pantalla abierta: cursor virtual.
        if (mc.screen != null) {
            releaseAll(mc);
            PadKeyboard kb = PadKeyboard.of(0);
            kb.sync(mc.screen);
            if (kb.active()) { // el teclado en pantalla se queda con todo el mando
                kb.handle(s, cfg, dt, mc.screen);
                return;
            }
            if (mc.screen instanceof dev.consolepad.split.PlayerScreen ps && ps.listeningForPad()) { // remapeo de controles
                int code = SecondaryScreens.firstPressed(s);
                if (code >= 0) ps.padButton(code);
                return;
            }
            screenFrame(mc, s, cfg, dt);
            return;
        }
        releaseMouseButtons(mc);
        PadKeyboard.of(0).sync(null);

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
        // Dos empujones seguidos del stick hacia delante = correr (se suelta al soltar el stick).
        if (analogForward > 0.7f && !fwdState) {
            fwdState = true;
            long tapNow = System.nanoTime();
            if (cfg.doubleTapSprint && lastFwdTap != 0L && tapNow - lastFwdTap < DOUBLE_TAP_NANOS) {
                sprintLatch = true;
                o.keySprint.setDown(true);
                lastFwdTap = 0L;
            } else {
                lastFwdTap = tapNow;
            }
        } else if (analogForward < 0.4f) {
            fwdState = false;
        }

        // Camara (stick derecho), independiente de los ticks para que sea fluida.
        LookControl.compute(cfg, s.rx, s.ry, LookControl.isAiming(player), sensitivityFactor(o), dt, lookState, tmp);
        if (tmp[0] != 0f || tmp[1] != 0f) player.turn(tmp[0], tmp[1]);

        // Botones segun los bindings del jugador.
        for (Map.Entry<Integer, PadAction> e : cfg.bindings.entrySet()) {
            int code = e.getKey();
            PadAction action = e.getValue();
            if (s.pressed(code)) onPress(mc, player, o, action, cfg);
            if (s.released(code)) onRelease(o, action, cfg);
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
        if (!cfg.snapNav && (tmp[0] != 0f || tmp[1] != 0f)) { // con la navegacion por casillas el stick no mueve el cursor libre
            int regionW = SplitView.p1HalfScreen ? SplitView.p1Px[2] : mc.getWindow().getScreenWidth();
            int regionH = SplitView.p1HalfScreen ? SplitView.p1Px[3] : mc.getWindow().getScreenHeight();
            double maxX = Math.max(0, regionW - 1); // con extras, el cursor de P1 se queda en su region (arriba a la izquierda)
            double maxY = Math.max(0, regionH - 1);
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

        dpadNavigation(mc, mouse, window, s, cfg.snapNav, dt);

        // Clics: pulsar y soltar por separado, para poder arrastrar.
        if (!(mc.screen instanceof AbstractContainerScreen<?>) && mc.getLastInputType().isKeyboard()) {
            // Modo foco (ultimo movimiento con el D-pad): A activa el elemento enfocado, como Enter.
            if (s.pressed(PadState.A)) mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER, 0, 0);
            if (s.released(PadState.A) && (mouseDown & 1) != 0) { // clic empezado antes de cambiar de modo
                mouse.consolepad$onPress(window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
                mouseDown &= ~1;
            }
        } else {
            double[] gc = guiCursor(mc);
            boolean craftedNow = ConsoleCraft.handle(mc, mc.screen, s, 0, gc[0], gc[1]); // libro de recetas: L3, LB/RB, A sobre una receta
            if (!craftedNow) mouseButton(mouse, window, s, PadState.A, GLFW.GLFW_MOUSE_BUTTON_LEFT);
        }
        mouseButton(mouse, window, s, PadState.X, GLFW.GLFW_MOUSE_BUTTON_RIGHT);
        if (s.pressed(PadState.A) && mc.screen != null) PadKeyboard.of(0).tryOpenFromClick(mc.screen); // A sobre un campo de texto abre el teclado

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
            if (!ConsoleCraft.scroll(mc.screen, steps)) mouse.consolepad$onScroll(window, 0.0, steps);
        }

        boolean closeByPause = false;
        for (Map.Entry<Integer, PadAction> e : cfg.bindings.entrySet()) {
            if (e.getValue() == PadAction.PAUSE && s.pressed(e.getKey())) closeByPause = true;
        }
        if ((s.pressed(PadState.B) || s.pressed(PadState.START) || closeByPause) && mc.screen.shouldCloseOnEsc()) {
            releaseMouseButtons(mc);
            mc.screen.onClose();
        }
    }

    /** D-pad en pantallas: salta entre slots (contenedores) o usa la navegacion por foco de Minecraft (menus). */
    private void dpadNavigation(Minecraft mc, MouseHandlerInvoker mouse, long window, PadState s, boolean snapNav, float dt) {
        boolean[] down = new boolean[4];
        PadMath.navDirections(s, snapNav, down);
        for (int i = 0; i < DPAD.length; i++) {
            boolean fire = false;
            boolean pressedNow = down[i] && !dirPrev[i];
            dirPrev[i] = down[i];
            if (pressedNow) {
                dpadActive[i] = true;
                dpadHeld[i] = 0f;
                dpadNext[i] = REPEAT_DELAY;
                fire = true;
            } else if (dpadActive[i] && down[i]) {
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

    /** Posicion del cursor de P1 en unidades de su pantalla (la pantalla de P1 puede dibujarse a otra escala que la ventana). */
    public static double[] guiCursor(Minecraft mc) {
        float k = SplitView.p1Scale();
        double sx = (double) mc.getWindow().getScreenWidth() / (mc.getWindow().getGuiScaledWidth() / k);
        double sy = (double) mc.getWindow().getScreenHeight() / (mc.getWindow().getGuiScaledHeight() / k);
        return new double[] {mc.mouseHandler.xpos() / sx, mc.mouseHandler.ypos() / sy, sx, sy};
    }

    /** Mueve el cursor al centro del hueco o boton de receta mas cercano en la direccion pedida (0 arriba, 1 abajo, 2 izq, 3 der). */
    private void jumpToSlot(Minecraft mc, MouseHandlerInvoker mouse, long window,
                            AbstractContainerScreen<?> screen, int dir) {
        double[] gc = guiCursor(mc);
        double[] best = SecondaryScreens.pick(SecondaryScreens.targets(screen), gc[0], gc[1], dir);
        if (best == null) return;
        double nx = Math.max(0, Math.min(mc.getWindow().getScreenWidth() - 1, best[0] * gc[2]));
        double ny = Math.max(0, Math.min(mc.getWindow().getScreenHeight() - 1, best[1] * gc[3]));
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
    public static float sensitivityFactor(Options o) {
        double base = o.sensitivity().get() * 0.6 + 0.2;
        return (float) (base * base * base * 8.0);
    }

    private void onPress(Minecraft mc, LocalPlayer player, Options o, PadAction action, PadSettings cfg) {
        if (PadManager.PADLOG && action == PadAction.ATTACK) ConsolePad.LOGGER.info("[padlog] P1 inicia ataque (lee slot 0)");
        KeyMapping hold = holdKey(o, action);
        if (hold != null) {
            if (cfg.isToggle(action) && held.contains(action)) { // alternar: la segunda pulsacion lo suelta
                hold.setDown(false);
                held.remove(action);
                return;
            }
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
                sprintLatch = HoldToggle.next(cfg.isToggle(action), sprintLatch, true, true);
                o.keySprint.setDown(sprintLatch);
            }
            case SNEAK_TOGGLE -> {
                sneakLatch = HoldToggle.next(cfg.isToggle(action), sneakLatch, true, true);
                o.keyShift.setDown(sneakLatch);
            }
            case PAUSE -> mc.pauseGame(false);
            default -> { }
        }
    }

    private void onRelease(Options o, PadAction action, PadSettings cfg) {
        KeyMapping hold = holdKey(o, action);
        if (hold != null) {
            if (cfg.isToggle(action)) return; // alternar: soltar el boton no suelta la accion
            hold.setDown(false);
            held.remove(action);
        } else if (action == PadAction.SPRINT_TOGGLE && !cfg.isToggle(action)) {
            sprintLatch = false;
            o.keySprint.setDown(false);
        } else if (action == PadAction.SNEAK_TOGGLE && !cfg.isToggle(action)) {
            sneakLatch = false;
            o.keyShift.setDown(false);
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
