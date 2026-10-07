package dev.consolepad.pad;

import java.util.HashMap;
import java.util.Map;

/** Ajustes de UN jugador. Cada slot tiene su propia instancia (base para ajustes por jugador). */
public final class PadSettings {
    public float moveDeadzone = 0.20f;
    public float lookDeadzone = 0.15f;
    /** Grados por segundo a inclinacion maxima. */
    public float lookSpeed = 180f;
    /** 1 = lineal; >1 da mas precision cerca del centro. */
    public float lookExponent = 1.6f;
    public boolean invertY = false;
    public float triggerThreshold = 0.5f;
    /** Cursor virtual en pantallas: pixeles por segundo a inclinacion maxima. */
    public float cursorSpeed = 900f;
    public float cursorDeadzone = 0.2f;
    public float cursorExponent = 1.5f;
    /** Pasos de scroll por segundo a inclinacion maxima del stick derecho. */
    public float scrollSpeed = 6f;

    /** codigo de boton -> accion. Remapeable por jugador. */
    public final Map<Integer, PadAction> bindings = defaultBindings();

    public static Map<Integer, PadAction> defaultBindings() {
        Map<Integer, PadAction> m = new HashMap<>();
        m.put(PadState.A, PadAction.JUMP);
        m.put(PadState.B, PadAction.DROP);
        m.put(PadState.X, PadAction.SWAP_HANDS);
        m.put(PadState.Y, PadAction.INVENTORY);
        m.put(PadState.LB, PadAction.HOTBAR_PREV);
        m.put(PadState.RB, PadAction.HOTBAR_NEXT);
        m.put(PadState.START, PadAction.PAUSE);
        m.put(PadState.L3, PadAction.SPRINT_TOGGLE);
        m.put(PadState.R3, PadAction.SNEAK_TOGGLE);
        m.put(PadState.DPAD_UP, PadAction.PERSPECTIVE);
        m.put(PadState.DPAD_DOWN, PadAction.PICK_BLOCK);
        m.put(PadState.DPAD_RIGHT, PadAction.CHAT);
        m.put(PadState.BACK, PadAction.PLAYER_LIST);
        m.put(PadState.RT, PadAction.ATTACK);
        m.put(PadState.LT, PadAction.USE);
        return m;
    }
}
