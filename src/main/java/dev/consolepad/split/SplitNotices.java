package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Avisos de cada jugador extra (paso 4g): el chat y los mensajes del servidor que recibe, el mensaje sobre la barra y el titulo se
 * guardan en su PlayerContext (vanilla los guarda en el Gui y el ChatComponent de P1) y se dibujan en su region.
 */
public final class SplitNotices {
    private static final int MAX_LINES = 5;
    /** Ticks que se ve cada linea de chat, el mensaje sobre la barra y el titulo. */
    private static final int CHAT_TICKS = 200, OVERLAY_TICKS = 60, TITLE_TICKS = 70;

    private SplitNotices() {}

    /** Ultimos mensajes que ha recibido el chat de P1 (para comprobar que no recibe los avisos de otros jugadores). */
    public static final java.util.concurrent.CopyOnWriteArrayList<String> p1Log = new java.util.concurrent.CopyOnWriteArrayList<>();

    public static void logP1(Component message) {
        p1Log.add(message.getString());
        while (p1Log.size() > 40) p1Log.remove(0);
    }

    public static void addChat(Component message) {
        PlayerContext c = ContextSwitcher.current();
        if (c == null) return;
        c.notices.addLast(new PlayerContext.Notice(message, Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()));
        while (c.notices.size() > MAX_LINES) c.notices.removeFirst();
    }

    public static void setOverlay(Component message) {
        PlayerContext c = ContextSwitcher.current();
        if (c == null) return;
        c.overlay = message;
        c.overlayTime = OVERLAY_TICKS;
    }

    public static void setTitle(Component title) {
        PlayerContext c = ContextSwitcher.current();
        if (c == null) return;
        c.title = title;
        c.titleTime = TITLE_TICKS;
    }

    /** Un tick: envejecen el mensaje sobre la barra y el titulo. */
    public static void tick(PlayerContext c) {
        if (c.overlayTime > 0 && --c.overlayTime == 0) c.overlay = null;
        if (c.titleTime > 0 && --c.titleTime == 0) c.title = null;
    }

    /** Dibuja los avisos en la region del jugador (la pose ya esta trasladada a ella; su ancho y alto de GUI son los de la region). */
    public static void render(Minecraft mc, PlayerContext c, GuiGraphics g) {
        var font = mc.font;
        long now = mc.level == null ? 0 : mc.level.getGameTime();
        int y = c.gh - 62; // por encima de las barras de vida y comida
        int drawn = 0;
        java.util.List<PlayerContext.Notice> lines = new java.util.ArrayList<>(c.notices);
        for (int i = lines.size() - 1; i >= 0; i--) {
            PlayerContext.Notice n = lines.get(i);
            long age = now - n.tick();
            if (age > CHAT_TICKS) continue;
            int alpha = age > CHAT_TICKS - 40 ? (int) (255 * (CHAT_TICKS - age) / 40.0) : 255;
            g.fill(2, y - 1, Math.min(c.gw - 2, font.width(n.text()) + 6), y + 9, (alpha / 3) << 24);
            g.drawString(font, n.text(), 4, y, 0xFFFFFF | (Math.max(alpha, 4) << 24));
            y -= 10;
            drawn++;
        }
        if (c.overlay != null && c.overlayTime > 0) {
            g.drawCenteredString(font, c.overlay, c.gw / 2, c.gh - 72, 0xFFFFFF);
            drawn++;
        }
        if (c.title != null && c.titleTime > 0) {
            g.pose().pushPose();
            g.pose().translate(c.gw / 2f, c.gh / 3f, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, c.title, 0, -10, 0xFFFFFF);
            g.pose().popPose();
            drawn++;
        }
        c.noticesDrawn = drawn;
        SplitDiag.onHudLayer("avisos", c.player, c.gw);
    }
}
