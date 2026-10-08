package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/**
 * Avisos de cada jugador extra (paso 4g): el chat y los mensajes del servidor que recibe, el mensaje sobre la barra y el titulo se
 * guardan en su PlayerContext (vanilla los guarda en el Gui y el ChatComponent de P1) y se dibujan en su region.
 */
public final class SplitNotices {
    private static final int MAX_NOTICES = 40, MAX_VISIBLE_LINES = 8;
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
        while (c.notices.size() > MAX_NOTICES) c.notices.removeFirst();
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
        int y = c.hudH - 62; // por encima de las barras de vida y comida
        int drawn = 0;
        int widest = 0;
        int maxWidth = Math.max(40, c.hudW - 12); // cada aviso se parte en lineas al ancho de la region
        java.util.List<PlayerContext.Notice> lines = new java.util.ArrayList<>(c.notices);
        for (int i = lines.size() - 1; i >= 0 && drawn < MAX_VISIBLE_LINES && y > 12; i--) {
            PlayerContext.Notice n = lines.get(i);
            long age = now - n.tick();
            if (age > CHAT_TICKS) continue;
            int alpha = age > CHAT_TICKS - 40 ? (int) (255 * (CHAT_TICKS - age) / 40.0) : 255;
            var parts = font.split(n.text(), maxWidth);
            for (int p = parts.size() - 1; p >= 0 && drawn < MAX_VISIBLE_LINES && y > 12; p--) {
                widest = Math.max(widest, font.width(parts.get(p)) + 6);
                g.fill(2, y - 1, Math.min(c.hudW - 2, font.width(parts.get(p)) + 6), y + 9, (alpha / 3) << 24);
                g.drawString(font, parts.get(p), 4, y, 0xFFFFFF | (Math.max(alpha, 4) << 24));
                y -= 10;
                drawn++;
            }
        }
        if (c.overlay != null && c.overlayTime > 0) {
            g.drawCenteredString(font, c.overlay, c.hudW / 2, c.hudH - 72, 0xFFFFFF);
            drawn++;
        }
        if (c.title != null && c.titleTime > 0) {
            g.pose().pushPose();
            g.pose().translate(c.hudW / 2f, c.hudH / 3f, 0);
            g.pose().scale(2f, 2f, 1f);
            g.drawCenteredString(font, c.title, 0, -10, 0xFFFFFF);
            g.pose().popPose();
            drawn++;
        }
        c.noticesDrawn = drawn;
        c.noticeMaxWidth = widest;
        SplitDiag.onHudLayer("avisos", c.player, c.hudW);
    }
}
