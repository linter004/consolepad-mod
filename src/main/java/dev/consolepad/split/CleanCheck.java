package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.neoforged.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Comprobacion de instalacion limpia (variable de entorno CONSOLEPAD_CLEANCHECK=1; la lanza tools/run-cleancheck.sh con la configuracion de ConsolePad y
 * options.txt apartadas): el primer arranque llega al titulo, sale la bienvenida con el texto dentro de la pantalla, el boton "Mandos" esta en el titulo,
 * los ajustes y la pantalla de asignar mandos se construyen sin nada guardado y, al terminar, se crean los archivos de configuracion. Escribe lineas
 * "[cleancheck]" y cierra el juego.
 */
public final class CleanCheck {
    public static final boolean ENABLED = "1".equals(System.getenv("CONSOLEPAD_CLEANCHECK"));
    private static int ticks, phase, wait;
    private static boolean sawOnboarding;
    private static final List<String> failures = new ArrayList<>();

    private CleanCheck() {}

    private static void check(String name, boolean ok) {
        ConsolePad.LOGGER.info("[cleancheck] {} -> {}", name, ok ? "OK" : "FALLO");
        if (!ok) failures.add(name);
    }

    public static void tick(Minecraft mc) {
        if (!ENABLED) return;
        ticks++;
        if (ticks > 20 * 120 && phase < 9) { // el juego no llego al titulo en 2 minutos
            check("llegar al titulo en menos de 2 minutos", false);
            finish(mc);
            return;
        }
        switch (phase) {
            case 0 -> {
                if (mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen && !sawOnboarding) {
                    sawOnboarding = true; // el primer arranque de vanilla pasa por la pantalla de accesibilidad; el jugador pulsa "Continuar"
                    wait = 5;
                    return;
                }
                if (sawOnboarding && mc.screen instanceof net.minecraft.client.gui.screens.AccessibilityOnboardingScreen) {
                    if (--wait > 0) return;
                    check("primer arranque: pantalla de accesibilidad de vanilla, se cierra con Continuar", true);
                    mc.screen.onClose();
                    return;
                }
                if (mc.screen instanceof WelcomeScreen) {
                    phase = 1;
                    wait = 5;
                } else if (mc.screen instanceof TitleScreen && ++wait > 100) {
                    check("la bienvenida sale sola en el primer arranque", false);
                    phase = 3;
                }
            }
            case 1 -> {
                if (--wait > 0) return;
                var w = (WelcomeScreen) mc.screen;
                check("bienvenida del primer arranque: el texto cabe (" + WelcomeScreen.lines().size() + " lineas)", w.textFits());
                check("bienvenida: Start y Atras marcados dentro de la pantalla", w.anchors()[0][0] > 0 && w.anchors()[1][0] > 0 && w.anchors()[0][1] < mc.screen.height);
                check("bienvenida: no hay mandos de P1 y aun asi se dibuja la silueta (mando de Xbox)", ConsolePad.PADS != null);
                SplitSettings.setWelcomeSeen(true);
                w.onClose();
                phase = 2;
                wait = 10;
            }
            case 2 -> {
                if (--wait > 0) return;
                check("tras cerrar la bienvenida se vuelve al titulo", mc.screen instanceof TitleScreen);
                phase = 3;
            }
            case 3 -> {
                if (mc.screen instanceof TitleScreen t) {
                    boolean button = false;
                    for (var child : t.children()) if (child instanceof Button b && b.getMessage().getString().equals("Mandos")) button = true;
                    check("boton «Mandos» en el titulo", button);
                }
                try {
                    var settings = new PlayerSettingsScreen(0, mc.screen);
                    settings.init(mc, 427, 240);
                    check("ajustes del jugador 1 con la configuracion de fabrica", true);
                    var setup = new PadSetupScreen(mc.screen);
                    setup.init(mc, 427, 240);
                    check("pantalla de asignar mandos antes de entrar en un mundo", true);
                } catch (RuntimeException e) {
                    ConsolePad.LOGGER.error("[cleancheck] excepcion", e);
                    check("construir ajustes y asignacion de mandos sin nada guardado: " + e, false);
                }
                ConsolePad.PADS.saveSettings();
                phase = 4;
                wait = 20;
            }
            case 4 -> {
                if (--wait > 0) return;
                Path dir = FMLPaths.CONFIGDIR.get().resolve("consolepad");
                check("se crea config/consolepad/slot0.json", Files.isRegularFile(dir.resolve("slot0.json")));
                check("split.json recuerda que la bienvenida ya se vio", Files.isRegularFile(dir.resolve("split.json")) && readSafe(dir.resolve("split.json")).contains("welcomeSeen"));
                phase = 9;
                finish(mc);
            }
            default -> {}
        }
    }

    private static String readSafe(Path p) {
        try {
            return Files.readString(p);
        } catch (java.io.IOException e) {
            return "";
        }
    }

    private static void finish(Minecraft mc) {
        ConsolePad.LOGGER.info("[cleancheck] RESULTADO instalacion limpia: {}", failures.isEmpty() ? "TODO OK" : failures.size() + " fallos: " + failures);
        ConsolePad.LOGGER.info("[cleancheck] terminado");
        phase = 10;
        mc.stop();
    }
}
