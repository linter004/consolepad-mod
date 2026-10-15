package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;

/**
 * Modo compatibilidad y bienvenida (tanda B), medidos sin mundo: el modo "auto" sigue a Controlify, "on"/"off" mandan sobre la deteccion; la bienvenida
 * cabe en pantalla a 854x480 y a 1920x1080 (con y sin Controlify/Sodium/Iris en el texto) y su boton guarda "ya vista" en split.json.
 */
public final class CompatTest {
    private CompatTest() {}

    private static boolean ok = true;

    private static void verdict(String name, boolean good) {
        ok &= good;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, good ? "OK" : "FALLO");
    }

    public static boolean run(Minecraft mc) {
        String saved = SplitSettings.compat();
        boolean seen = SplitSettings.welcomeSeen();
        Boolean prevOverride = Compat.controlifyOverride;
        try {
            Compat.controlifyOverride = Boolean.FALSE;
            verdict("compat: sin Controlify, modo auto, ConsolePad maneja a P1", saved.equals("auto") ? !Compat.yieldP1() : true);
            Compat.controlifyOverride = Boolean.TRUE;
            if (saved.equals("auto")) verdict("compat: con Controlify, modo auto, P1 se cede a Controlify", Compat.yieldP1());
            verdict("compat: el resumen nombra los tres mods y el modo", Compat.summary().contains("Controlify") && Compat.summary().contains("Sodium")
                    && Compat.summary().contains("Iris") && Compat.summary().contains(saved));
            for (int[] w : new int[][] {{427, 240}, {960, 540}}) { // 854x480 y 1920x1080 con escala de interfaz 2
                Compat.controlifyOverride = Boolean.TRUE;
                WelcomeScreen ws = new WelcomeScreen(null);
                ws.init(mc, w[0], w[1]);
                verdict("bienvenida (con Controlify) cabe a " + w[0] + "x" + w[1] + " (logico)", ws.textFits());
                int[] c = ws.okCenter();
                verdict("bienvenida: el boton esta dentro de la pantalla " + w[0] + "x" + w[1], c[0] > 0 && c[0] < w[0] && c[1] > 0 && c[1] < w[1]);
                int[][] an = ws.anchors();
                boolean marks = true;
                for (int[] p : an) marks &= p[0] > 0 && p[0] < w[0] && p[1] > 0 && p[1] < w[1];
                verdict("bienvenida: Start y Atras marcados en el dibujo del mando a " + w[0] + "x" + w[1] + " (" + an[0][0] + "," + an[0][1] + " y " + an[1][0] + "," + an[1][1] + ")", marks && an[0][0] != an[1][0]);
            }
            net.minecraft.client.gui.screens.Screen prev = mc.screen;
            WelcomeScreen wsBtn = new WelcomeScreen(prev);
            wsBtn.init(mc, 427, 240);
            wsBtn.pressSettings();
            boolean opened = mc.screen instanceof PlayerSettingsScreen;
            mc.setScreen(prev);
            verdict("bienvenida: el boton de ajustes abre los ajustes de P1", opened);
            SplitSettings.setWelcomeSeen(false);
            WelcomeScreen ws = new WelcomeScreen(null);
            ws.init(mc, 427, 240);
            SplitSettings.setWelcomeSeen(true);
            verdict("bienvenida: 'no volver a mostrar' queda guardado", SplitSettings.welcomeSeen());
        } finally {
            Compat.controlifyOverride = prevOverride;
            SplitSettings.setWelcomeSeen(seen);
        }
        ConsolePad.LOGGER.info("[autotest] RESULTADO compat y bienvenida -> {}", ok ? "OK" : "FALLO");
        return true;
    }
}
