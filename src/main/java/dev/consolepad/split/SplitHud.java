package dev.consolepad.split;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.consolepad.mixin.GuiInvoker;
import dev.consolepad.mixin.GuiStateAccessor;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.item.ItemStack;

/**
 * HUD dividido. El HUD de P1 se dibuja entero pero centrado en la mitad izquierda (el ancho de la interfaz pasa a ser el
 * de la mitad). P2 recibe en la derecha, dentro de su contexto y con su propio estado de HUD: mira, hotbar, vida,
 * armadura, comida, burbujas, barra y nivel de experiencia, nombre del objeto y efectos. Quedan en P1: chat, titulos,
 * bossbars, marcador, depuracion y superposiciones de camara. Los menus y pantallas siguen a ventana completa.
 */
public final class SplitHud {
    private SplitHud() {}

    public static void render(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker) {
        PlayerContext p2 = SplitRender.activeContext();
        Minecraft mc = Minecraft.getInstance();
        SplitView.p1HalfScreen = p2 != null;
        if (p2 == null) {
            fitP1Screen(mc, graphics.guiWidth(), graphics.guiHeight());
            gui.render(graphics, deltaTracker);
            return;
        }
        int full = graphics.guiWidth(); // sin sustituir todavia: ancho real de la ventana
        int half = full / 2;
        fitP1Screen(mc, half, graphics.guiHeight());
        try {
            SplitView.guiWidth = half;
            SplitDiag.hudOwner = "P1";
            SplitDiag.hudExpected = mc.player;
            gui.render(graphics, deltaTracker);

            PoseStack pose = graphics.pose();
            pose.pushPose();
            int p1Left = gui.leftHeight, p1Right = gui.rightHeight;
            try {
                pose.translate(half, 0, 0);
                SplitView.guiWidth = full - half;
                SplitDiag.hudOwner = "P2";
                SplitDiag.hudExpected = p2.player;
                GuiInvoker invoker = (GuiInvoker) gui;
                final net.minecraft.client.multiplayer.ClientLevel p1Level = mc.level;
                ContextSwitcher.run(p2, () -> withState(gui, p2.hud, () -> {
                    gui.leftHeight = 39; // Gui.render() los reinicia al empezar cada HUD
                    gui.rightHeight = 39;
                    invoker.consolepad$renderCrosshair(graphics, deltaTracker);
                    invoker.consolepad$renderHotbar(graphics, deltaTracker);
                    invoker.consolepad$renderExperienceBar(graphics, deltaTracker);
                    invoker.consolepad$renderPlayerHealth(graphics, deltaTracker);
                    invoker.consolepad$renderExperienceLevel(graphics, deltaTracker);
                    invoker.consolepad$renderSelectedItemName(graphics, deltaTracker);
                    invoker.consolepad$renderEffects(graphics, deltaTracker);
                    SecondaryScreens.render(mc, p2, graphics, deltaTracker.getGameTimeDeltaPartialTick(false), p1Level);
                }));
            } finally {
                gui.leftHeight = p1Left;
                gui.rightHeight = p1Right;
                pose.popPose();
            }
        } finally {
            SplitView.guiWidth = 0;
            SplitDiag.hudOwner = "-";
            SplitDiag.hudExpected = null;
        }
    }

    /** La pantalla de P1 ocupa su region (mitad izquierda con P2, ventana entera sin P2). */
    private static void fitP1Screen(Minecraft mc, int width, int height) {
        var screen = mc.screen;
        if (screen != null && (screen.width != width || screen.height != height)) screen.resize(mc, width, height);
    }

    /** Tick del nombre del objeto seleccionado de P2 (Gui.tick lo hace solo con el jugador de Minecraft = P1). Dentro del contexto de P2. */
    public static void tickToolHighlight(PlayerContext ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        Gui gui = mc.gui;
        GuiStateAccessor g = (GuiStateAccessor) gui;
        withState(gui, ctx.hud, () -> {
            ItemStack now = mc.player.getInventory().getSelected();
            ItemStack last = g.consolepad$getLastToolHighlight();
            if (now.isEmpty()) {
                g.consolepad$setToolHighlightTimer(0);
            } else if (last.isEmpty() || !now.is(last.getItem())
                    || !now.getHoverName().equals(last.getHoverName())
                    || !now.getHighlightTip(now.getHoverName()).equals(last.getHighlightTip(last.getHoverName()))) {
                g.consolepad$setToolHighlightTimer((int) (40.0 * mc.options.notificationDisplayTime().get()));
            } else if (g.consolepad$getToolHighlightTimer() > 0) {
                g.consolepad$setToolHighlightTimer(g.consolepad$getToolHighlightTimer() - 1);
            }
            g.consolepad$setLastToolHighlight(now);
        });
    }

    /** Pone el estado de HUD de un jugador en Gui mientras corre el cuerpo y lo recoge al terminar; el de P1 se restaura siempre. */
    private static void withState(Gui gui, PlayerContext.HudState state, Runnable body) {
        GuiStateAccessor g = (GuiStateAccessor) gui;
        int p1Last = g.consolepad$getLastHealth(), p1Display = g.consolepad$getDisplayHealth(), p1Timer = g.consolepad$getToolHighlightTimer();
        long p1LastTime = g.consolepad$getLastHealthTime(), p1Blink = g.consolepad$getHealthBlinkTime();
        ItemStack p1Tool = g.consolepad$getLastToolHighlight();
        g.consolepad$setLastHealth(state.lastHealth);
        g.consolepad$setDisplayHealth(state.displayHealth);
        g.consolepad$setLastHealthTime(state.lastHealthTime);
        g.consolepad$setHealthBlinkTime(state.healthBlinkTime);
        g.consolepad$setToolHighlightTimer(state.toolHighlightTimer);
        g.consolepad$setLastToolHighlight(state.lastToolHighlight);
        try {
            body.run();
        } finally {
            state.lastHealth = g.consolepad$getLastHealth();
            state.displayHealth = g.consolepad$getDisplayHealth();
            state.lastHealthTime = g.consolepad$getLastHealthTime();
            state.healthBlinkTime = g.consolepad$getHealthBlinkTime();
            state.toolHighlightTimer = g.consolepad$getToolHighlightTimer();
            state.lastToolHighlight = g.consolepad$getLastToolHighlight();
            g.consolepad$setLastHealth(p1Last);
            g.consolepad$setDisplayHealth(p1Display);
            g.consolepad$setLastHealthTime(p1LastTime);
            g.consolepad$setHealthBlinkTime(p1Blink);
            g.consolepad$setToolHighlightTimer(p1Timer);
            g.consolepad$setLastToolHighlight(p1Tool);
        }
    }
}
