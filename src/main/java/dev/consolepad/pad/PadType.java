package dev.consolepad.pad;

import java.util.Locale;

/** Familia de mando, solo para iconos/glifos mas adelante. El layout de GLFW es posicional. */
public enum PadType {
    XBOX, PLAYSTATION, NINTENDO;

    public static PadType detect(String name) {
        String n = name == null ? "" : name.toLowerCase(Locale.ROOT);
        if (n.contains("nintendo") || n.contains("switch") || n.contains("joy-con") || n.contains("pro controller")) {
            return NINTENDO;
        }
        if (n.contains("ps3") || n.contains("ps4") || n.contains("ps5") || n.contains("dualshock")
                || n.contains("dualsense") || n.contains("sony") || n.contains("playstation")
                || n.contains("wireless controller")) {
            return PLAYSTATION;
        }
        return XBOX;
    }
}
