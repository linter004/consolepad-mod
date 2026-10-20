package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

/**
 * Dormir juntos: mientras alguien de los jugadores locales esta en la cama y otros no, todos ven arriba, en el centro de su region, quien duerme y quien
 * falta ("Durmiendo: Jugador 2" / "Faltan: Jugador 1"), con sus nombres en su color. Vanilla solo lo dice en el chat de P1. Solo cuentan los jugadores
 * del mundo principal (como en vanilla, donde dormir solo vale en la superficie) y los que no son espectadores. Las posiciones salen del servidor
 * integrado. Se apaga con "sleepNotice": false en split.json.
 */
public final class SleepNotice {
    /** Ultimo texto dibujado por jugador (las dos lineas separadas por "|"); para las pruebas. */
    public static final String[] lastText = {"", "", "", ""};

    /** Cuanto dura el aviso (ms) y cuanto de ese tiempo es el fundido final hasta desaparecer. */
    public static final long DURATION_MS = 12_000, FADE_MS = 4_000;
    /** Ultima opacidad dibujada por jugador (0 a 1); para las pruebas. */
    public static final float[] lastAlpha = new float[4];
    private static String shownKey = "";
    private static long shownSince;
    /** Pruebas: adelanta el reloj del aviso (ms). */
    private static long shift;

    public record Who(int slot, boolean sleeping) {}

    private SleepNotice() {}

    /** Los jugadores locales que cuentan para dormir, con si estan en la cama. */
    public static List<Who> who(Minecraft mc) {
        List<Who> out = new ArrayList<>();
        var server = mc.getSingleplayerServer();
        if (server == null) return out;
        List<ServerPlayer> players;
        try {
            players = new ArrayList<>(server.getPlayerList().getPlayers());
            players.removeIf(java.util.Objects::isNull); // el servidor cambia la lista (entra o sale un jugador) mientras se copia desde el hilo de dibujo: la copia puede traer un hueco
        } catch (RuntimeException e) {
            return out;
        }
        for (ServerPlayer sp : players) {
            int slot = PlayerLabels.slotOf(mc, sp.getGameProfile().getName());
            if (slot < 0 || (slot > 0 && !SecondaryPlayers.isActive(slot)) || sp.isSpectator() || sp.level().dimension() != Level.OVERWORLD) continue;
            out.add(new Who(slot, sp.isSleeping()));
        }
        out.sort((a, b) -> Integer.compare(a.slot(), b.slot()));
        return out;
    }

    /** Los nombres de los que duermen y de los que faltan; null si no hay que avisar (nadie duerme, o ya duermen todos). */
    public static List<List<Integer>> split(List<Who> all) {
        List<Integer> sleeping = new ArrayList<>(), missing = new ArrayList<>();
        for (Who w : all) (w.sleeping() ? sleeping : missing).add(w.slot());
        if (sleeping.isEmpty() || missing.isEmpty()) return null;
        return List.of(sleeping, missing);
    }

    /** Opacidad del aviso a los ms de haberse mostrado: 1 al principio, se difumina en el ultimo tramo y es 0 pasado el tiempo. */
    public static float alpha(long elapsedMs) {
        if (elapsedMs >= DURATION_MS) return 0f;
        long fadeStart = DURATION_MS - FADE_MS;
        return elapsedMs <= fadeStart ? 1f : 1f - (elapsedMs - fadeStart) / (float) FADE_MS;
    }

    public static void advanceForTest(long ms) {
        shift += ms;
    }

    public static void resetForTest() {
        shownKey = "";
        shift = 0;
        java.util.Arrays.fill(lastAlpha, 0f);
    }

    private static MutableComponent names(List<Integer> slots) {
        MutableComponent out = Component.empty();
        for (int i = 0; i < slots.size(); i++) {
            if (i > 0) out.append(", ");
            out.append(PlayerLabels.styled(slots.get(i), PlayerLabels.labelOf(slots.get(i))));
        }
        return out;
    }

    public static void render(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        lastText[slot] = "";
        lastAlpha[slot] = 0f;
        boolean inBed = mc.screen instanceof ExtraBedScreen || mc.screen instanceof net.minecraft.client.gui.screens.InBedChatScreen; // quien duerme tiene la pantalla de la cama abierta y tambien lo ve
        if (!SplitSettings.sleepNotice() || !ConsolePad.PADS.slot(slot).settings.showHud || mc.options.hideGui || (mc.screen != null && !inBed)) return;
        var parts = split(who(mc));
        if (parts == null) {
            shownKey = ""; // ya no hay nada que avisar: la proxima vez vuelve a mostrarse entero
            shift = 0;
            return;
        }
        String sleepers = plain(parts.get(0)), missing = plain(parts.get(1));
        long now = System.currentTimeMillis();
        String key = sleepers + "|" + missing;
        if (!key.equals(shownKey)) { // empieza o cambia quien duerme: 12 s de nuevo
            shownKey = key;
            shownSince = now;
            shift = 0;
        }
        float alpha = alpha(now - shownSince + shift);
        if (alpha < 0.04f) return; // pasaron los 12 s
        lastAlpha[slot] = alpha;
        int a = Math.max(4, Math.round(alpha * 255));
        lastText[slot] = I18n.tr("Durmiendo: ") + sleepers + "|" + I18n.tr("Faltan: ") + missing;
        var font = mc.font;
        Component l1 = Component.literal(I18n.tr("Durmiendo: ")).append(names(parts.get(0))), l2 = Component.literal(I18n.tr("Faltan: ")).append(names(parts.get(1)));
        g.pose().pushPose();
        g.pose().translate(0, 0, Companions.OVERLAY_Z);
        int y = 28;
        for (Component line : new Component[] {l1, l2}) {
            int tw = font.width(line);
            g.fill(w / 2 - tw / 2 - 3, y - 2, w / 2 + tw / 2 + 3, y + font.lineHeight + 1, (Math.round(alpha * 128) << 24));
            g.drawString(font, line, w / 2 - tw / 2, y, (a << 24) | 0xFFFFFF, true);
            y += font.lineHeight + 3;
        }
        g.pose().popPose();
    }

    private static String plain(List<Integer> slots) {
        StringBuilder b = new StringBuilder();
        for (int s : slots) {
            if (b.length() > 0) b.append(", ");
            b.append(PlayerLabels.labelOf(s));
        }
        return b.toString();
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        if (split(List.of(new Who(0, false), new Who(1, false))) != null) return "nadie duerme: no se avisa";
        if (split(List.of(new Who(0, true), new Who(1, true))) != null) return "duermen todos: no se avisa";
        var parts = split(List.of(new Who(0, false), new Who(1, true), new Who(2, false)));
        if (parts == null || !parts.get(0).equals(List.of(1)) || !parts.get(1).equals(List.of(0, 2))) return "uno duerme y faltan dos: " + parts;
        if (alpha(0) != 1f || alpha(8_000) != 1f || Math.abs(alpha(10_000) - 0.5f) > 0.01f || alpha(12_000) != 0f || alpha(20_000) != 0f) return "fundido: " + alpha(0) + ", " + alpha(8_000) + ", " + alpha(10_000) + ", " + alpha(12_000);
        if (split(List.of()) != null || split(List.of(new Who(0, true))) != null) return "sin jugadores o solo uno que duerme no se avisa";
        return null;
    }
}
