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
    private static float scrollAcc;

    private SecondaryScreens() {}

    /** Solo se admiten pantallas de contenedor (inventario, cofres, mesa, horno...); el resto (muerte, titulo...) se ignora para P2. */
    public static void set(Screen screen) {
        PlayerContext ctx = ContextSwitcher.current();
        if (ctx == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (screen != null && !(screen instanceof AbstractContainerScreen<?>)) return;
        Screen old = mc.screen;
        if (old != null && old != screen) old.removed();
        mc.screen = screen;
        ctx.screen = screen;
        if (screen != null) {
            screen.added();
            int w = regionWidth(mc), h = mc.getWindow().getGuiScaledHeight();
            ctx.cursorX = w / 2.0;
            ctx.cursorY = h / 2.0;
            ctx.buttonDown[0] = ctx.buttonDown[1] = false;
            screen.init(mc, w, h); // puede abrir otra pantalla (el inventario en creativo): mc.screen ya no es "screen"
        }
    }

    /** Ancho de GUI de la mitad derecha. */
    public static int regionWidth(Minecraft mc) {
        int full = mc.getWindow().getGuiScaledWidth();
        return full - full / 2;
    }

    /** Dibuja la pantalla de P2 y su cursor. Dentro del contexto de P2 y con la pose ya trasladada a su mitad. */
    public static void render(Minecraft mc, PlayerContext ctx, GuiGraphics graphics, float partialTick) {
        Screen screen = mc.screen;
        if (screen == null) return;
        int w = regionWidth(mc), h = graphics.guiHeight();
        if (screen.width != w || screen.height != h) screen.resize(mc, w, h);
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
    }

    /** Entrada de P2 con una pantalla abierta, una vez por fotograma: stick izq = cursor, A/X = clic izq/der, Y = mover rapido, stick der = scroll, B/inventario = cerrar. */
    public static void frame(Minecraft mc, PadManager.Slot slot, float dt) {
        PlayerContext ctx = SecondaryPlayers.context();
        if (ctx == null || ctx.player == null || ctx.screen == null || slot.device == null) return;
        PadState s = slot.device.state;
        PadSettings cfg = slot.settings;
        ContextSwitcher.run(ctx, () -> {
            Screen screen = mc.screen;
            if (screen == null) return;
            // Cursor.
            PadMath.radial(s.lx, s.ly, cfg.cursorDeadzone, cfg.cursorExponent, TMP);
            if (TMP[0] != 0f || TMP[1] != 0f) {
                double speed = cfg.cursorSpeed / mc.getWindow().getGuiScale(); // pixeles/s -> unidades de GUI/s
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
            // Clics: pulsar y soltar por separado, para poder arrastrar.
            click(screen, ctx, s, PadState.A, 0);
            click(screen, ctx, s, PadState.X, 1);
            if (s.pressed(PadState.Y) && screen instanceof AbstractContainerScreen<?> container) {
                Slot hovered = container.getSlotUnderMouse();
                if (hovered != null) ((ContainerScreenInvoker) container).consolepad$slotClicked(hovered, hovered.index, 0, ClickType.QUICK_MOVE);
            }
            // Scroll con el stick derecho.
            PadMath.radial(s.rx, s.ry, cfg.lookDeadzone, 1.0f, TMP);
            scrollAcc += -TMP[1] * cfg.scrollSpeed * dt;
            int steps = (int) scrollAcc;
            if (steps != 0) {
                scrollAcc -= steps;
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
