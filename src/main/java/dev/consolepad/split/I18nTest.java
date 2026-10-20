package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.ButtonLayouts;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Idiomas (casilla 48): con el ingles forzado se recorren las pantallas del mod y los textos que salen de funciones (nombres de botones, ayudas, avisos de
 * interaccion, nombres de colores y disposiciones...) y se comprueba que no queda ninguno en espanol: ni textos con aspecto de espanol entre los que se
 * ven (botones, titulos, etiquetas de los ajustes) ni textos que se pidieron a {@link I18n#tr} sin traduccion. Con el espanol forzado, los textos son
 * los de siempre (las demas pruebas dependen de ellos).
 */
public final class I18nTest {
    private I18nTest() {}

    private static void collect(Screen s, Minecraft mc, List<String> out) {
        s.init(mc, 427, 240);
        out.add(s.getTitle().getString());
        for (var child : s.children()) {
            if (child instanceof AbstractWidget w) out.add(w.getMessage().getString());
        }
    }

    /** Devuelve null si bien, o la lista de textos en espanol que quedan en ingles. */
    public static String englishLeftovers(Minecraft mc) {
        String before = I18n.forced();
        I18n.force("en");
        I18n.missing.clear();
        List<String> seen = new ArrayList<>();
        try {
            for (int slot = 0; slot <= 1; slot++) {
                collect(new PlayerPauseScreen(slot), mc, seen);
                collect(new PlayerControlsScreen(slot, null), mc, seen);
                var settings = new PlayerSettingsScreen(slot, null);
                collect(settings, mc, seen);
                seen.addAll(settings.labelsForTest());
                collect(new WheelEditorScreen(slot, null), mc, seen);
                collect(new WheelEntryScreen(slot, -1, null), mc, seen);
                collect(new ProfilesScreen(slot, null), mc, seen);
                collect(new NewProfileScreen(slot, null), mc, seen);
                collect(new PlayerNameScreen(slot, null), mc, seen);
                collect(new PadAssignScreen(slot, null), mc, seen);
                collect(new PadLostScreen(slot), mc, seen);
            }
            collect(new PadSetupScreen(null), mc, seen);
            collect(new PlayerCountScreen(null), mc, seen);
            collect(new KickPlayerScreen(null), mc, seen);
            collect(new ExtraDeathScreen(1, Component.literal("x")), mc, seen);
            var profiles = Profiles.all();
            if (!profiles.isEmpty()) collect(new ProfileScreen(1, profiles.get(0), null), mc, seen);
            // Textos que salen de funciones
            for (PadType t : PadType.values()) {
                for (int code = 0; code <= 20; code++) seen.add(PadNames.button(code, t));
            }
            for (PadAction a : PadAction.values()) seen.add(PadNames.action(a));
            for (String n : PlayerLabels.COLOR_NAMES) seen.add(I18n.tr(n));
            for (ButtonLayouts.Layout l : ButtonLayouts.Layout.values()) seen.add(I18n.tr(l.label));
            seen.add(PadHints.attackLabel(new net.minecraft.world.phys.EntityHitResult(mc.player)));
            seen.add(PadHints.useLabel(mc.player));
            for (var d : net.minecraft.core.Direction.values()) seen.add(PlayerInfo.directionName(d));
            seen.add(Interact.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD)));
            seen.add(Interact.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW)));
            seen.add(Interact.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIRT)));
            seen.add(Interact.forItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.STICK)));
            seen.add(Interact.forBlock(net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), mc.level, net.minecraft.core.BlockPos.ZERO));
            seen.add(Interact.forBlock(net.minecraft.world.level.block.Blocks.RED_BED.defaultBlockState(), mc.level, net.minecraft.core.BlockPos.ZERO));
            for (QuickWheel.Entry e : QuickWheel.defaultEntriesForTest()) seen.add(e.label());
            seen.add(PlayerLabels.labelOf(0));
            seen.add(PlayerLabels.labelOf(3));
        } finally {
            I18n.force(before);
        }
        TreeSet<String> bad = new TreeSet<>();
        for (String m : I18n.missing) if (!m.contains(" - /")) bad.add(m);
        for (String s : seen) if (s != null && I18n.looksSpanish(s) && !s.contains(" - /") && !s.endsWith(": Español") && !s.contains("Virtual (")) bad.add(s); // las entradas guardadas de la rueda y el mando virtual de pruebas no cuentan
        return bad.isEmpty() ? null : bad.size() + " textos en espanol: " + String.join(" | ", bad);
    }

    /** El ingles no cambia el espanol: con el espanol forzado salen los textos de siempre. null si bien. */
    public static String spanishUnchanged(Minecraft mc) {
        String before = I18n.forced();
        I18n.force("es");
        try {
            String a = I18n.tr("Volver a la partida"), b = I18n.fmt("El jugador {0} salió de la partida: su mando lleva {1} s desconectado.", 2, 30);
            if (!a.equals("Volver a la partida") || !b.equals("El jugador 2 salió de la partida: su mando lleva 30 s desconectado.")) return "el espanol cambio: '" + a + "' / '" + b + "'";
            if (!PlayerLabels.labelOf(1).equals("Jugador 2")) return "el nombre de fabrica en espanol: " + PlayerLabels.labelOf(1);
        } finally {
            I18n.force(before);
        }
        I18n.force("en");
        try {
            String x = I18n.tr("Volver a la partida"), y = I18n.fmt("El jugador {0} salió de la partida: su mando lleva {1} s desconectado.", 2, 30);
            if (x.equals("Volver a la partida") || !y.contains("2") || !y.contains("30") || I18n.looksSpanish(y)) return "el ingles no traduce: '" + x + "' / '" + y + "'";
            if (!PlayerLabels.labelOf(1).equals("Player 2")) return "el nombre de fabrica en ingles: " + PlayerLabels.labelOf(1);
            if (I18n.tableSize() < 100) return "la tabla de ingles es corta: " + I18n.tableSize();
        } finally {
            I18n.force(before);
        }
        return null;
    }
}
