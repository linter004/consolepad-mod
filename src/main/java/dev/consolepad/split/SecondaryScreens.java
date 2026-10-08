package dev.consolepad.split;

import dev.consolepad.mixin.ContainerScreenInvoker;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadMath;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;

import java.util.Map;

/**
 * Pantallas de P2 (paso 4c): inventario, cofres, crafteo, horno... dentro de su mitad, con cursor virtual propio.
 * La pantalla vive en PlayerContext.screen y ContextSwitcher la pone en Minecraft.screen mientras corre el contexto de P2,
 * asi que todo el codigo de la pantalla (que lee minecraft.player, gameMode, screen) trabaja con los datos de P2.
 * No se llama al setScreen de vanilla: soltaria el raton y las teclas de P1.
 */
public final class SecondaryScreens {
    private static final float[] TMP = new float[2];

    private SecondaryScreens() {}

    /** Solo se admiten pantallas de contenedor (inventario, cofres, mesa, horno...); el resto (muerte, titulo...) se ignora para P2. */
    public static void set(Screen screen) {
        PlayerContext ctx = ContextSwitcher.current();
        if (ctx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (screen != null && !(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof P2PauseScreen)) return;
        Screen old = mc.screen;
        if (old != null && old != screen) old.removed();
        mc.screen = screen;
        ctx.screen = screen;
        if (screen != null) {
            screen.added();
            if (ctx.gw <= 0) SplitLayout.update(mc, SplitRender.activeContexts()); // aun sin region: primera vez que se necesita
            int w = SplitLayout.logicalWidth(mc, ctx), h = SplitLayout.logicalHeight(mc, ctx);
            ctx.cursorX = w / 2.0;
            ctx.cursorY = h / 2.0;
            ctx.buttonDown[0] = ctx.buttonDown[1] = false;
            screen.init(mc, w, h); // puede abrir otra pantalla (el inventario en creativo): mc.screen ya no es "screen"
        }
    }

    /** Ancho logico de las pantallas de P2 (para las pruebas). */
    public static int regionWidth(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        return c == null ? mc.getWindow().getGuiScaledWidth() / 2 : SplitLayout.logicalWidth(mc, c);
    }

    /** Dibuja la pantalla de P2 y su cursor. Dentro del contexto de P2 y con la pose ya trasladada a su mitad. */
    public static void render(Minecraft mc, PlayerContext ctx, GuiGraphics graphics, float partialTick, net.minecraft.client.multiplayer.ClientLevel p1Level) {
        Screen screen = mc.screen;
        if (screen == null) return;
        int w = SplitLayout.logicalWidth(mc, ctx), h = SplitLayout.logicalHeight(mc, ctx);
        if (screen.width != w || screen.height != h) screen.resize(mc, w, h);
        mc.getEntityRenderDispatcher().setLevel(ctx.level); // el maniqui del inventario es una entidad de este jugador
        int oldW = SplitView.guiWidth, oldH = SplitView.guiHeight;
        graphics.pose().pushPose();
        graphics.pose().scale(ctx.screenScale, ctx.screenScale, 1f); // la pantalla se dibuja a escala para que quepa en su region
        SplitView.guiWidth = w;
        SplitView.guiHeight = h;
        try {
            screen.renderWithTooltip(graphics, (int) ctx.cursorX, (int) ctx.cursorY, partialTick);
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 500);
            int cx = (int) ctx.cursorX, cy = (int) ctx.cursorY;
            for (int r = 0; r < 10; r++) { // flecha: contorno negro y relleno blanco
                int width = Math.min(r / 2 + 2, 6);
                graphics.fill(cx, cy + r, cx + width + 1, cy + r + 1, 0xFF000000);
            }
            for (int r = 1; r < 9; r++) {
                int width = Math.min(r / 2 + 1, 5);
                graphics.fill(cx + 1, cy + r, cx + width, cy + r + 1, 0xFFFFFFFF);
            }
            graphics.pose().popPose();
        } finally {
            SplitView.guiWidth = oldW;
            SplitView.guiHeight = oldH;
            graphics.pose().popPose();
            mc.getEntityRenderDispatcher().setLevel(p1Level);
        }
    }

    /** Entrada de P2 con una pantalla abierta, una vez por fotograma: stick izq = cursor, A/X = clic izq/der, Y = mover rapido, stick der = scroll, B/inventario = cerrar. */
    public static void frame(Minecraft mc, PadManager.Slot slot, float dt, PlayerContext ctx) {
        if (ctx == null || ctx.player == null || ctx.screen == null || slot.device == null) return;
        PadState s = slot.device.state;
        PadSettings cfg = slot.settings;
        ContextSwitcher.run(ctx, () -> {
            Screen screen = mc.screen;
            if (screen == null) return;
            // Cursor.
            PadMath.radial(s.lx, s.ly, cfg.cursorDeadzone, cfg.cursorExponent, TMP);
            if (TMP[0] != 0f || TMP[1] != 0f) {
                double speed = cfg.cursorSpeed / mc.getWindow().getGuiScale() / ctx.screenScale; // pixeles/s -> unidades logicas/s
                if (screen instanceof AbstractContainerScreen<?> c && c.getSlotUnderMouse() != null) speed *= cfg.cursorSlotFactor;
                double nx = Math.max(0, Math.min(screen.width - 1, ctx.cursorX + TMP[0] * speed * dt));
                double ny = Math.max(0, Math.min(screen.height - 1, ctx.cursorY + TMP[1] * speed * dt));
                double dx = nx - ctx.cursorX, dy = ny - ctx.cursorY;
                ctx.cursorX = nx;
                ctx.cursorY = ny;
                if (ctx.buttonDown[0]) screen.mouseDragged(nx, ny, 0, dx, dy);
                else if (ctx.buttonDown[1]) screen.mouseDragged(nx, ny, 1, dx, dy);
                else screen.mouseMoved(nx, ny);
            }
            dpad(ctx, screen, s, dt);
            // Clics: pulsar y soltar por separado, para poder arrastrar.
            click(screen, ctx, s, PadState.A, 0);
            click(screen, ctx, s, PadState.X, 1);
            if (s.pressed(PadState.Y) && screen instanceof AbstractContainerScreen<?> container) {
                Slot hovered = container.getSlotUnderMouse();
                if (hovered != null) ((ContainerScreenInvoker) container).consolepad$slotClicked(hovered, hovered.index, 0, ClickType.QUICK_MOVE);
            }
            // Scroll con el stick derecho.
            PadMath.radial(s.rx, s.ry, cfg.lookDeadzone, 1.0f, TMP);
            ctx.scrollAcc += -TMP[1] * cfg.scrollSpeed * dt;
            int steps = (int) ctx.scrollAcc;
            if (steps != 0) {
                ctx.scrollAcc -= steps;
                screen.mouseScrolled(ctx.cursorX, ctx.cursorY, 0.0, steps);
            }
            // Cerrar: B, Start o la tecla de inventario de su mando.
            boolean close = s.pressed(PadState.B) || s.pressed(PadState.START);
            for (Map.Entry<Integer, PadAction> e : cfg.bindings.entrySet()) {
                if (e.getValue() == PadAction.INVENTORY && s.pressed(e.getKey())) close = true;
            }
            if (close && screen.shouldCloseOnEsc()) {
                ctx.buttonDown[0] = ctx.buttonDown[1] = false;
                screen.onClose();
            }
        });
    }

    private static final int[] DPAD = {PadState.DPAD_UP, PadState.DPAD_DOWN, PadState.DPAD_LEFT, PadState.DPAD_RIGHT};

    /** D-pad: el cursor salta al hueco (contenedores) o boton (menus) mas cercano en esa direccion; con repeticion al mantener. */
    private static void dpad(PlayerContext ctx, Screen screen, PadState s, float dt) {
        for (int i = 0; i < DPAD.length; i++) {
            boolean fire = false;
            if (s.pressed(DPAD[i])) {
                ctx.dpadActive[i] = true;
                ctx.dpadHeld[i] = 0f;
                ctx.dpadNext[i] = 0.35f;
                fire = true;
            } else if (ctx.dpadActive[i] && s.isDown(DPAD[i])) {
                ctx.dpadHeld[i] += dt;
                if (ctx.dpadHeld[i] >= ctx.dpadNext[i]) {
                    ctx.dpadNext[i] += 0.12f;
                    fire = true;
                }
            } else {
                ctx.dpadActive[i] = false;
            }
            if (fire) jump(ctx, screen, i);
        }
    }

    /** Centros (x, y, ancho, alto) de lo que se puede apuntar en la pantalla: slots activos o botones activos. */
    private static java.util.List<double[]> targets(Screen screen) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        if (screen instanceof AbstractContainerScreen<?> c) {
            for (Slot sl : c.getMenu().slots) {
                if (sl.isActive()) out.add(new double[] {c.getGuiLeft() + sl.x + 8, c.getGuiTop() + sl.y + 8, 16, 16});
            }
        } else {
            for (var child : screen.children()) {
                if (child instanceof net.minecraft.client.gui.components.AbstractWidget w && w.active && w.visible) {
                    out.add(new double[] {w.getX() + w.getWidth() / 2.0, w.getY() + w.getHeight() / 2.0, w.getWidth(), w.getHeight()});
                }
            }
        }
        return out;
    }

    /** dir: 0 arriba, 1 abajo, 2 izquierda, 3 derecha. */
    private static void jump(PlayerContext ctx, Screen screen, int dir) {
        java.util.List<double[]> all = targets(screen);
        double[] current = null;
        for (double[] t : all) {
            if (Math.abs(ctx.cursorX - t[0]) <= t[2] / 2 && Math.abs(ctx.cursorY - t[1]) <= t[3] / 2) current = t;
        }
        double ox = current != null ? current[0] : ctx.cursorX, oy = current != null ? current[1] : ctx.cursorY;
        double[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (double[] t : all) {
            if (t == current) continue;
            double dx = t[0] - ox, dy = t[1] - oy, score;
            if (current == null) {
                score = dx * dx + dy * dy;
            } else {
                double primary = switch (dir) {
                    case 0 -> -dy;
                    case 1 -> dy;
                    case 2 -> -dx;
                    default -> dx;
                };
                if (primary < 1.0) continue;
                score = primary + 2.0 * (dir < 2 ? Math.abs(dx) : Math.abs(dy));
            }
            if (score < bestScore) {
                bestScore = score;
                best = t;
            }
        }
        if (best != null) {
            ctx.cursorX = best[0];
            ctx.cursorY = best[1];
            screen.mouseMoved(best[0], best[1]);
        }
    }

    private static void click(Screen screen, PlayerContext ctx, PadState s, int padButton, int mouseButton) {
        if (s.pressed(padButton)) {
            ctx.buttonDown[mouseButton] = true;
            screen.mouseClicked(ctx.cursorX, ctx.cursorY, mouseButton);
        }
        if (s.released(padButton) && ctx.buttonDown[mouseButton]) {
            ctx.buttonDown[mouseButton] = false;
            screen.mouseReleased(ctx.cursorX, ctx.cursorY, mouseButton);
        }
    }
}
