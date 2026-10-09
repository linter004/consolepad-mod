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

    /**
     * Se admiten pantallas de contenedor (inventario, cofres, mesa, horno...) y las propias del mod (menus, controles, muerte); el resto
     * (titulo, ajustes de vanilla...) se ignora para los extra. La pantalla de muerte de vanilla se cambia por la propia (no desconecta a P1).
     */
    public static void set(Screen screen) {
        PlayerContext ctx = ContextSwitcher.current();
        if (ctx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (screen instanceof net.minecraft.client.gui.screens.DeathScreen death) {
            screen = new ExtraDeathScreen(ctx.slotIndex, ((dev.consolepad.mixin.DeathScreenAccessor) death).consolepad$getCauseOfDeath());
        }
        if (screen != null && !(screen instanceof AbstractContainerScreen<?>) && !(screen instanceof PlayerScreen)
                && !(screen instanceof net.minecraft.client.gui.screens.inventory.AbstractSignEditScreen)) return;
        Screen old = mc.screen;
        if (old != null && old != screen) old.removed();
        mc.screen = screen;
        ctx.screen = screen;
        if (screen != null) {
            screen.added();
            if (ctx.logicalW <= 0) SplitLayout.update(mc, SplitRender.activeContexts()); // aun sin region: primera vez que se necesita
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
            if (ConsoleCraft.applies(screen)) ConsoleCraft.renderPanel(mc, graphics, ctx.slotIndex, screen, ctx.cursorX, ctx.cursorY, w, h);
            PadHints.renderScreenBar(mc, graphics, ctx.slotIndex, screen, w, h);
            PadKeyboard.of(ctx.slotIndex).render(graphics, mc.font, w, h);
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
        if (ctx == null) return;
        if (ctx.screen == null) PadKeyboard.of(ctx.slotIndex).sync(null);
        if (ctx.player == null || ctx.screen == null || slot.device == null) return;
        PadState s = slot.device.state;
        PadSettings cfg = slot.settings;
        ContextSwitcher.run(ctx, () -> {
            Screen screen = mc.screen;
            if (screen == null) return;
            if (screen instanceof PlayerScreen ps && ps.listeningForPad()) { // remapeo: el siguiente boton es el dato, no un clic ni un cierre
                int code = firstPressed(s);
                if (code >= 0) ps.padButton(code);
                return;
            }
            PadKeyboard kb = PadKeyboard.of(ctx.slotIndex);
            kb.sync(screen);
            if (kb.active()) { // el teclado en pantalla se queda con todo el mando
                kb.handle(s, cfg, dt, screen);
                return;
            }
            // Cursor.
            PadMath.radial(s.lx, s.ly, cfg.cursorDeadzone, cfg.cursorExponent, TMP);
            if (!cfg.snapNav && (TMP[0] != 0f || TMP[1] != 0f)) { // con la navegacion por casillas el stick no mueve el cursor libre
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
            dpad(ctx, screen, s, cfg.snapNav, dt);
            // Clics: pulsar y soltar por separado, para poder arrastrar.
            boolean craftedNow = ConsoleCraft.handle(mc, screen, s, ctx.slotIndex, ctx.cursorX, ctx.cursorY); // libro de recetas: L3, LB/RB, A sobre una receta
            if (!craftedNow) click(screen, ctx, s, PadState.A, 0);
            if (s.pressed(PadState.A) && !craftedNow) kb.tryOpenFromClick(screen); // A sobre un campo de texto abre el teclado
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
                if (!ConsoleCraft.scroll(screen, steps)) screen.mouseScrolled(ctx.cursorX, ctx.cursorY, 0.0, steps);
            }
            // Cerrar: B, Start o la tecla de inventario de su mando.
            int closing = 0;
            if (s.pressed(PadState.B)) closing |= 1 << PadState.B;
            if (s.pressed(PadState.START)) closing |= 1 << PadState.START;
            for (Map.Entry<Integer, PadAction> e : cfg.bindings.entrySet()) {
                if ((e.getValue() == PadAction.INVENTORY || e.getValue() == PadAction.PAUSE) && s.pressed(e.getKey())) closing |= 1 << e.getKey();
            }
            if (closing != 0 && screen.shouldCloseOnEsc()) {
                ctx.buttonDown[0] = ctx.buttonDown[1] = false;
                ctx.input.closeLatch |= closing; // SecondaryTick no reabre la pantalla ni suelta objetos con el mismo boton
                screen.onClose();
            }
        });
    }

    /** Primer boton del mando pulsado en este fotograma (codigo de PadState), o -1. */
    public static int firstPressed(PadState s) {
        for (int code = 0; code <= PadState.RT; code++) {
            if (s.pressed(code)) return code;
        }
        return -1;
    }

    private static final int[] DPAD = {PadState.DPAD_UP, PadState.DPAD_DOWN, PadState.DPAD_LEFT, PadState.DPAD_RIGHT};

    /** D-pad: el cursor salta al hueco (contenedores) o boton (menus) mas cercano en esa direccion; con repeticion al mantener. */
    private static void dpad(PlayerContext ctx, Screen screen, PadState s, boolean snapNav, float dt) {
        boolean[] down = new boolean[4];
        PadMath.navDirections(s, snapNav, down);
        for (int i = 0; i < DPAD.length; i++) {
            boolean fire = false;
            boolean pressedNow = down[i] && !ctx.dirPrev[i];
            ctx.dirPrev[i] = down[i];
            if (pressedNow) {
                ctx.dpadActive[i] = true;
                ctx.dpadHeld[i] = 0f;
                ctx.dpadNext[i] = 0.35f;
                fire = true;
            } else if (ctx.dpadActive[i] && down[i]) {
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
    public static java.util.List<double[]> targets(Screen screen) {
        java.util.List<double[]> out = new java.util.ArrayList<>();
        if (screen instanceof AbstractContainerScreen<?> c) {
            for (Slot sl : c.getMenu().slots) {
                if (sl.isActive()) out.add(new double[] {c.getGuiLeft() + sl.x + 8, c.getGuiTop() + sl.y + 8, 16, 16});
            }
            out.addAll(ConsoleCraft.extraTargets(screen)); // libro de recetas abierto: recetas, paginas y filtro
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
        double[] best = pick(targets(screen), ctx.cursorX, ctx.cursorY, dir);
        if (best != null) {
            ctx.cursorX = best[0];
            ctx.cursorY = best[1];
            screen.mouseMoved(best[0], best[1]);
        }
    }

    /** Destino al que salta el cursor desde (cx, cy) en la direccion pedida (0 arriba, 1 abajo, 2 izq, 3 der), o null. Mismo criterio para P1 y los extra. */
    public static double[] pick(java.util.List<double[]> all, double cx, double cy, int dir) {
        double[] current = null;
        for (double[] t : all) {
            if (Math.abs(cx - t[0]) <= t[2] / 2 && Math.abs(cy - t[1]) <= t[3] / 2) current = t;
        }
        double ox = current != null ? current[0] : cx, oy = current != null ? current[1] : cy;
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
        return best;
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
