package dev.consolepad.split;

import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.Locale;

/** Ajustes de un jugador (se guardan en el archivo de su slot): escala de interfaz, sensibilidad, campo de vision y zonas muertas. */
public final class PlayerSettingsScreen extends PlayerMenuScreen {
    public static final int[] GUI_SCALES = {0, 1, 2, 3, 4};
    public static final int[] FOVS = {0, 60, 70, 80, 90, 100, 110};
    public static final float[] SPEEDS = {90f, 135f, 180f, 225f, 270f, 315f};
    public static final float[] DEADZONES = {0.05f, 0.10f, 0.15f, 0.20f, 0.25f, 0.30f, 0.35f};

    private final Screen parent;
    private Button guiScale, sens, fov, moveDead, lookDead, back;

    public PlayerSettingsScreen(int slot, Screen parent) {
        super(slot, Component.literal("Ajustes del jugador " + (slot + 1)));
        this.parent = parent;
    }

    private Component guiScaleLabel() {
        int v = settings().guiScale;
        return Component.literal("Escala de interfaz: " + (v <= 0 ? "automática" : String.valueOf(v)));
    }

    private Component fovLabel() {
        int v = settings().fov;
        return Component.literal("Campo de visión: " + (v <= 0 ? "global" : String.valueOf(v)));
    }

    private Component sensLabel() {
        return Component.literal("Sensibilidad: " + Math.round(settings().lookSpeed));
    }

    private static String dz(float v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static float nextFloat(float[] options, float current) {
        int idx = 0;
        float best = Float.MAX_VALUE;
        for (int k = 0; k < options.length; k++) {
            float d = Math.abs(options[k] - current);
            if (d < best) {
                best = d;
                idx = k;
            }
        }
        return options[(idx + 1) % options.length];
    }

    @Override
    protected void init() {
        int rows = 6, i = 0;
        guiScale = addRow(i++, rows, guiScaleLabel(), b -> {
            PadSettings s = settings();
            int idx = 0;
            for (int k = 0; k < GUI_SCALES.length; k++) if (GUI_SCALES[k] == s.guiScale) idx = k;
            s.guiScale = GUI_SCALES[(idx + 1) % GUI_SCALES.length];
            b.setMessage(guiScaleLabel());
        });
        sens = addRow(i++, rows, sensLabel(), b -> {
            PadSettings s = settings();
            s.lookSpeed = nextFloat(SPEEDS, s.lookSpeed);
            b.setMessage(sensLabel());
        });
        fov = addRow(i++, rows, fovLabel(), b -> {
            PadSettings s = settings();
            int idx = 0;
            for (int k = 0; k < FOVS.length; k++) if (FOVS[k] == s.fov) idx = k;
            s.fov = FOVS[(idx + 1) % FOVS.length];
            b.setMessage(fovLabel());
        });
        moveDead = addRow(i++, rows, Component.literal("Zona muerta del movimiento: " + dz(settings().moveDeadzone)), b -> {
            PadSettings s = settings();
            s.moveDeadzone = nextFloat(DEADZONES, s.moveDeadzone);
            b.setMessage(Component.literal("Zona muerta del movimiento: " + dz(s.moveDeadzone)));
        });
        lookDead = addRow(i++, rows, Component.literal("Zona muerta de la cámara: " + dz(settings().lookDeadzone)), b -> {
            PadSettings s = settings();
            s.lookDeadzone = nextFloat(DEADZONES, s.lookDeadzone);
            b.setMessage(Component.literal("Zona muerta de la cámara: " + dz(s.lookDeadzone)));
        });
        back = addRow(i, rows, Component.literal("Atrás"), b -> this.onClose());
    }

    @Override
    public void onClose() {
        dev.consolepad.ConsolePad.PADS.saveSettings();
        Minecraft.getInstance().setScreen(parent);
    }

    public int[] guiScaleCenter() {
        return center(guiScale);
    }

    public int[] sensCenter() {
        return center(sens);
    }

    public int[] fovCenter() {
        return center(fov);
    }

    public int[] moveDeadCenter() {
        return center(moveDead);
    }

    public int[] lookDeadCenter() {
        return center(lookDead);
    }

    public int[] backCenter() {
        return center(back);
    }
}
