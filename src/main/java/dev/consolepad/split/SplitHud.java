package dev.consolepad.split;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
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
    private static int lastChatWidth;

    private SplitHud() {}

    public static void render(Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft mc = Minecraft.getInstance();
        java.util.List<PlayerContext> extras = SplitRender.activeContexts();
        SplitView.p1HalfScreen = !extras.isEmpty();
        if (extras.isEmpty()) {
            fitP1Screen(mc, graphics.guiWidth(), graphics.guiHeight());
            renderP1(mc, gui, graphics, deltaTracker, graphics.guiWidth(), graphics.guiHeight());
            return;
        }
        SplitLayout.update(mc, extras);
        int[] r1 = SplitView.p1Gui;
        if (SplitView.p1HudW != lastChatWidth) { // el chat se parte en lineas al ancho de la region: volver a partirlo si cambia
            lastChatWidth = SplitView.p1HudW;
            mc.gui.getChat().rescaleChat();
        }
        GuiInvoker invoker = (GuiInvoker) gui;
        final net.minecraft.client.multiplayer.ClientLevel p1Level = mc.level;
        final float partial = deltaTracker.getGameTimeDeltaPartialTick(false);
        try {
            fitP1Screen(mc, SplitView.p1LogicalW, SplitView.p1LogicalH);
            SplitView.guiWidth = SplitView.p1HudW;
            SplitView.guiHeight = SplitView.p1HudH;
            SplitDiag.hudOwner = "P1";
            SplitDiag.hudExpected = mc.player;
            graphics.enableScissor(r1[0], r1[1], r1[0] + r1[2], r1[1] + r1[3]); // nada de P1 se sale de su region (objeto del cursor, chat...)
            graphics.pose().pushPose();
            graphics.pose().scale(SplitView.p1HudRatio, SplitView.p1HudRatio, 1f); // escala de interfaz propia de P1
            try {
                renderP1(mc, gui, graphics, deltaTracker, SplitView.p1HudW, SplitView.p1HudH); // region de P1: origen (0, 0)
            } finally {
                graphics.pose().popPose();
                graphics.disableScissor();
            }

            PoseStack pose = graphics.pose();
            int p1Left = gui.leftHeight, p1Right = gui.rightHeight;
            try {
                for (PlayerContext p : extras) {
                    pose.pushPose();
                    graphics.enableScissor(p.gx, p.gy, p.gx + p.gw, p.gy + p.gh);
                    try {
                        pose.translate(p.gx, p.gy, 0);
                        SplitView.guiWidth = p.hudW;
                        SplitView.guiHeight = p.hudH;
                        SplitDiag.hudOwner = p.label();
                        SplitDiag.hudExpected = p.player;
                        ContextSwitcher.run(p, () -> {
                            pose.pushPose();
                            pose.scale(p.hudRatio, p.hudRatio, 1f); // escala de interfaz propia de este jugador
                            PadSettings cfg = ConsolePad.PADS.slot(p.slotIndex).settings;
                            int mx = Math.round(p.hudW * cfg.hudMargin / 100f), my = Math.round(p.hudH * cfg.hudMargin / 100f); // zona segura
                            int fw = p.hudW - 2 * mx, fh = p.hudH - 2 * my;
                            p.safeW = fw;
                            p.safeH = fh;
                            pose.translate(mx, my, 0);
                            SplitView.guiWidth = fw;
                            SplitView.guiHeight = fh;
                            beginAlpha(cfg.hudOpacity);
                            try {
                                if (cfg.showHud) {
                                    withState(gui, p.hud, () -> {
                                        gui.leftHeight = 39; // Gui.render() los reinicia al empezar cada HUD
                                        gui.rightHeight = 39;
                                        invoker.consolepad$renderCameraOverlays(graphics, deltaTracker); // vineta, calabaza, nieve en polvo, portal, catalejo
                                        SplitDiag.onHudLayer("superposiciones", mc.player, graphics.guiWidth());
                                        invoker.consolepad$renderCrosshair(graphics, deltaTracker);
                                        invoker.consolepad$renderHotbar(graphics, deltaTracker);
                                        invoker.consolepad$renderExperienceBar(graphics, deltaTracker);
                                        invoker.consolepad$renderPlayerHealth(graphics, deltaTracker);
                                        invoker.consolepad$renderExperienceLevel(graphics, deltaTracker);
                                        invoker.consolepad$renderSelectedItemName(graphics, deltaTracker);
                                        invoker.consolepad$renderEffects(graphics, deltaTracker);
                                        renderExtraBoards(mc, gui, invoker, p, graphics, deltaTracker, fw);
                                    });
                                }
                                SplitNotices.render(mc, p, graphics);
                                if (cfg.showHud) PadHints.renderHud(mc, graphics, p.slotIndex, fw, fh);
                                PlayerInfo.render(mc, graphics, p.slotIndex);
                            } finally {
                                endAlpha(graphics);
                                pose.popPose();
                            }
                            SecondaryScreens.render(mc, p, graphics, partial, p1Level);
                        });
                    } finally {
                        graphics.disableScissor();
                        pose.popPose();
                    }
                }
            } finally {
                gui.leftHeight = p1Left;
                gui.rightHeight = p1Right;
            }
        } finally {
            SplitView.guiWidth = 0;
            SplitView.guiHeight = 0;
            SplitDiag.hudOwner = "-";
            SplitDiag.hudExpected = null;
        }
    }

    /** HUD de P1 (el de vanilla, la ayuda de botones y el aviso de unirse) con su zona segura, su opacidad y la opcion de ocultarlo. */
    private static void renderP1(Minecraft mc, Gui gui, GuiGraphics graphics, DeltaTracker deltaTracker, int w, int h) {
        PadSettings cfg = ConsolePad.PADS.slot(0).settings;
        int mx = Math.round(w * cfg.hudMargin / 100f), my = Math.round(h * cfg.hudMargin / 100f);
        int fw = w - 2 * mx, fh = h - 2 * my;
        int oldW = SplitView.guiWidth, oldH = SplitView.guiHeight;
        boolean oldHide = mc.options.hideGui;
        if (mx > 0 || my > 0) {
            SplitView.guiWidth = fw;
            SplitView.guiHeight = fh;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(mx, my, 0);
        beginAlpha(cfg.hudOpacity);
        try {
            if (!cfg.showHud) mc.options.hideGui = true; // como F1, solo para P1
            gui.render(graphics, deltaTracker);
            mc.options.hideGui = oldHide;
            JoinPrompt.render(mc, graphics, fw, fh);
            if (cfg.showHud) PadHints.renderHud(mc, graphics, 0, fw, fh);
            PlayerInfo.render(mc, graphics, 0);
        } finally {
            mc.options.hideGui = oldHide;
            endAlpha(graphics);
            graphics.pose().popPose();
            SplitView.guiWidth = oldW;
            SplitView.guiHeight = oldH;
        }
    }

    /** Barras de jefe, marcador lateral y lista de jugadores de un jugador extra (dentro de su contexto, por tanto con su nivel y su conexion). */
    private static void renderExtraBoards(Minecraft mc, Gui gui, GuiInvoker invoker, PlayerContext p, GuiGraphics graphics, DeltaTracker deltaTracker, int width) {
        var boss = p.bossOverlay();
        boss.render(graphics);
        invoker.consolepad$renderSleepOverlay(graphics, deltaTracker);
        net.minecraft.world.scores.Scoreboard scoreboard = mc.level.getScoreboard();
        net.minecraft.world.scores.Objective objective = null;
        var team = scoreboard.getPlayersTeam(mc.player.getScoreboardName());
        if (team != null) {
            var slot = net.minecraft.world.scores.DisplaySlot.teamColorToSlot(team.getColor());
            if (slot != null) objective = scoreboard.getDisplayObjective(slot);
        }
        net.minecraft.world.scores.Objective side = objective != null ? objective : scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
        if (side != null) {
            invoker.consolepad$displayScoreboardSidebar(graphics, side);
            SplitDiag.onHudLayer("marcador", mc.player, graphics.guiWidth());
        }
        if (p.playerListHeld) {
            var list = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.LIST);
            if (!(mc.isLocalServer() && mc.player.connection.getListedOnlinePlayers().size() <= 1 && list == null)) {
                gui.getTabList().setVisible(true);
                gui.getTabList().render(graphics, width, scoreboard, list);
                gui.getTabList().setVisible(false);
                SplitDiag.onHudLayer("lista de jugadores", mc.player, graphics.guiWidth());
            }
        }
    }

    private static void beginAlpha(float alpha) {
        SplitView.hudAlpha = alpha;
        if (alpha < 0.999f) RenderSystem.setShaderColor(1f, 1f, 1f, 1f); // RenderSystemMixin lo multiplica por la opacidad
    }

    private static void endAlpha(GuiGraphics graphics) {
        if (SplitView.hudAlpha < 0.999f) graphics.flush(); // lo pendiente se dibuja con la opacidad del jugador
        SplitView.hudAlpha = 1f;
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
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
            if (mc.getCameraEntity() != null) ((GuiInvoker) gui).consolepad$updateVignetteBrightness(mc.getCameraEntity()); // brillo de la vineta de este jugador
        });
    }

    /** Pone el estado de HUD de un jugador en Gui mientras corre el cuerpo y lo recoge al terminar; el de P1 se restaura siempre. */
    private static void withState(Gui gui, PlayerContext.HudState state, Runnable body) {
        GuiStateAccessor g = (GuiStateAccessor) gui;
        int p1Last = g.consolepad$getLastHealth(), p1Display = g.consolepad$getDisplayHealth(), p1Timer = g.consolepad$getToolHighlightTimer();
        long p1LastTime = g.consolepad$getLastHealthTime(), p1Blink = g.consolepad$getHealthBlinkTime();
        float p1Scope = g.consolepad$getScopeScale(), p1Vignette = g.consolepad$getVignetteBrightness();
        ItemStack p1Tool = g.consolepad$getLastToolHighlight();
        g.consolepad$setLastHealth(state.lastHealth);
        g.consolepad$setDisplayHealth(state.displayHealth);
        g.consolepad$setLastHealthTime(state.lastHealthTime);
        g.consolepad$setHealthBlinkTime(state.healthBlinkTime);
        g.consolepad$setToolHighlightTimer(state.toolHighlightTimer);
        g.consolepad$setLastToolHighlight(state.lastToolHighlight);
        g.consolepad$setScopeScale(state.scopeScale);
        g.consolepad$setVignetteBrightness(state.vignetteBrightness);
        try {
            body.run();
        } finally {
            state.lastHealth = g.consolepad$getLastHealth();
            state.displayHealth = g.consolepad$getDisplayHealth();
            state.lastHealthTime = g.consolepad$getLastHealthTime();
            state.healthBlinkTime = g.consolepad$getHealthBlinkTime();
            state.toolHighlightTimer = g.consolepad$getToolHighlightTimer();
            state.lastToolHighlight = g.consolepad$getLastToolHighlight();
            state.scopeScale = g.consolepad$getScopeScale();
            state.vignetteBrightness = g.consolepad$getVignetteBrightness();
            g.consolepad$setLastHealth(p1Last);
            g.consolepad$setDisplayHealth(p1Display);
            g.consolepad$setLastHealthTime(p1LastTime);
            g.consolepad$setHealthBlinkTime(p1Blink);
            g.consolepad$setToolHighlightTimer(p1Timer);
            g.consolepad$setLastToolHighlight(p1Tool);
            g.consolepad$setScopeScale(p1Scope);
            g.consolepad$setVignetteBrightness(p1Vignette);
        }
    }
}
