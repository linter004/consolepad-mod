package dev.consolepad.split;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.consolepad.ConsolePad;
import dev.consolepad.mixin.ItemInHandRendererAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * Dibuja el mundo una vez por jugador, cada uno en su region de la ventana (SplitLayout: 2 = mitades, 3 = mitad y dos cuartos,
 * 4 = cuadrantes). Cada pasada fija viewport y scissor (el clear de FogRenderer no debe borrar las otras regiones) y la
 * proyeccion toma el aspecto de su region (GameRendererMixin). Los extra se dibujan primero y P1 el ultimo, asi la camara y
 * el resto de estado de GameRenderer que usa el codigo posterior (sonido, manos, outline) quedan como los de P1.
 */
public final class SplitRender {
    private static boolean loggedFirst;
    /** Diagnostico (solo P2): objeto que dibuja la mano en cada pasada y objeto real del jugador de esa pasada. */
    public static volatile Item dbgHandDrawnP1, dbgHandDrawnP2, dbgHandOwnP1, dbgHandOwnP2;

    private SplitRender() {}

    private static boolean ready(PlayerContext c) {
        return c != null && c.player != null && c.level != null && c.gameMode != null && c.renderer instanceof SplitLevelRenderer;
    }

    /** Jugadores extra listos para dibujarse con la pantalla dividida activa, en orden de slot. */
    public static List<PlayerContext> activeContexts() {
        List<PlayerContext> out = new ArrayList<>(SecondaryPlayers.MAX_EXTRA);
        if (!SplitView.enabled) return out;
        for (PlayerContext c : SecondaryPlayers.contexts()) {
            if (ready(c)) out.add(c);
        }
        return out;
    }

    public static void renderLevels(GameRenderer renderer, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        List<PlayerContext> extras = activeContexts();
        if (extras.isEmpty()) {
            renderer.renderLevel(deltaTracker);
            return;
        }
        for (PlayerContext c : extras) {
            SplitLevelRenderer r = (SplitLevelRenderer) c.renderer;
            if (!r.isLive()) {
                r.goLive(c.level);
                ConsolePad.LOGGER.info("[split] renderer de {} activo", c.label());
            }
        }
        SplitLayout.update(mc, extras);

        Window window = mc.getWindow();
        int w = window.getWidth();
        int h = window.getHeight();
        ClientLevel p1Level = mc.level;
        if (!loggedFirst) {
            loggedFirst = true;
            ConsolePad.LOGGER.info("[split] primer frame dividido: ventana {}x{}, {} jugadores", w, h, 1 + extras.size());
        }
        try {
            for (int i = extras.size() - 1; i >= 0; i--) {
                PlayerContext c = extras.get(i);
                pass(mc, renderer, deltaTracker, c, c.px, c.py, c.pw, c.ph);
            }
            int[] p = SplitView.p1Px;
            pass(mc, renderer, deltaTracker, null, p[0], p[1], p[2], p[3]);
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
        SplitDiag.beginPass(ctx != null ? ctx.label() : "P1", w, h);
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
            if (ctx == null || ctx.slotIndex == 1) {
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
}
