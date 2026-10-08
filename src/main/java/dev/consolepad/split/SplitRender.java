package dev.consolepad.split;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.consolepad.ConsolePad;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import dev.consolepad.mixin.ItemInHandRendererAccessor;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.Item;

/**
 * Fase 3, paso 4: dibuja el mundo una vez por jugador, cada uno en su mitad de la ventana (P1 izquierda, P2 derecha).
 * Cada pasada fija viewport y scissor (el clear de FogRenderer no debe borrar la otra mitad) y la proyeccion toma el
 * aspecto de su region (GameRendererMixin). P2 se dibuja primero y P1 el ultimo, asi la camara y el resto de estado de
 * GameRenderer que usa el codigo posterior (sonido, manos, outline) quedan como los de P1.
 */
public final class SplitRender {
    private static boolean loggedFirst;
    /** Diagnostico: objeto que dibuja la mano en cada pasada (ItemInHandRenderer es unico y solo lo ticka P1) y objeto real del jugador de esa pasada. */
    public static volatile Item dbgHandDrawnP1, dbgHandDrawnP2, dbgHandOwnP1, dbgHandOwnP2;

    private SplitRender() {}

    /** Contexto de P2 si la pantalla dividida esta activa y P2 esta listo para dibujarse; si no, null. */
    public static PlayerContext activeContext() {
        PlayerContext p2 = SecondaryPlayers.context();
        if (!SplitView.enabled || p2 == null || p2.player == null || p2.level == null || p2.gameMode == null
                || !(p2.renderer instanceof SplitLevelRenderer)) {
            return null;
        }
        return p2;
    }

    public static void renderLevels(GameRenderer renderer, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        PlayerContext p2 = activeContext();
        if (p2 == null) {
            renderer.renderLevel(deltaTracker);
            return;
        }
        SplitLevelRenderer r2 = (SplitLevelRenderer) p2.renderer;
        if (!r2.isLive()) {
            r2.goLive(p2.level);
            ConsolePad.LOGGER.info("[split] renderer de P2 activo");
        }

        Window window = mc.getWindow();
        int w = window.getWidth();
        int h = window.getHeight();
        int half = w / 2;
        ClientLevel p1Level = mc.level;
        if (!loggedFirst) {
            loggedFirst = true;
            ConsolePad.LOGGER.info("[split] primer frame dividido: ventana {}x{}, P1 {}x{} (izquierda), P2 {}x{} (derecha)",
                    w, h, half, h, w - half, h);
        }
        try {
            pass(mc, renderer, deltaTracker, p2, half, 0, w - half, h);
            pass(mc, renderer, deltaTracker, null, 0, 0, half, h);
        } finally {
            RenderSystem.disableScissor();
            RenderSystem.viewport(0, 0, w, h);
            SplitView.width = 0;
            SplitView.height = 0;
            mc.getEntityRenderDispatcher().setLevel(p1Level);
            mc.getBlockEntityRenderDispatcher().setLevel(p1Level);
        }
    }

    private static void pass(Minecraft mc, GameRenderer renderer, DeltaTracker deltaTracker, PlayerContext ctx,
                             int x, int y, int w, int h) {
        RenderSystem.enableScissor(x, y, w, h);
        RenderSystem.viewport(x, y, w, h);
        SplitView.x = x;
        SplitView.y = y;
        SplitView.width = w;
        SplitView.height = h;
        SplitDiag.beginPass(ctx != null ? "P2" : "P1", w, h);
        ClientLevel level = ctx != null ? ctx.level : mc.level;
        // Los dispatchers de entidades y bloques son compartidos: apuntan al nivel del jugador que se dibuja.
        mc.getEntityRenderDispatcher().setLevel(level);
        mc.getBlockEntityRenderDispatcher().setLevel(level);
        try {
            if (ctx != null) {
                ContextSwitcher.run(ctx, () -> renderer.renderLevel(deltaTracker));
            } else {
                renderer.renderLevel(deltaTracker);
            }
        } finally {
            SplitDiag.endPass();
            net.minecraft.client.renderer.ItemInHandRenderer handUsed = ctx != null && ctx.hand != null ? ctx.hand : mc.gameRenderer.itemInHandRenderer;
            Item drawn = ((ItemInHandRendererAccessor) handUsed).consolepad$getMainHandItem().getItem();
            if (ctx != null) {
                dbgHandDrawnP2 = drawn;
                dbgHandOwnP2 = ctx.player.getMainHandItem().getItem();
            } else {
                dbgHandDrawnP1 = drawn;
                dbgHandOwnP1 = mc.player.getMainHandItem().getItem();
            }
        }
    }
}
