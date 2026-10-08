package dev.consolepad.pad;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/** Estado de un mando con deteccion de flancos (pulsado / soltado). Codigos posicionales de GLFW. */
public final class PadState {
    public static final int A = GLFW.GLFW_GAMEPAD_BUTTON_A;
    public static final int B = GLFW.GLFW_GAMEPAD_BUTTON_B;
    public static final int X = GLFW.GLFW_GAMEPAD_BUTTON_X;
    public static final int Y = GLFW.GLFW_GAMEPAD_BUTTON_Y;
    public static final int LB = GLFW.GLFW_GAMEPAD_BUTTON_LEFT_BUMPER;
    public static final int RB = GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_BUMPER;
    public static final int BACK = GLFW.GLFW_GAMEPAD_BUTTON_BACK;
    public static final int START = GLFW.GLFW_GAMEPAD_BUTTON_START;
    public static final int L3 = GLFW.GLFW_GAMEPAD_BUTTON_LEFT_THUMB;
    public static final int R3 = GLFW.GLFW_GAMEPAD_BUTTON_RIGHT_THUMB;
    public static final int DPAD_UP = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_UP;
    public static final int DPAD_RIGHT = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_RIGHT;
    public static final int DPAD_DOWN = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_DOWN;
    public static final int DPAD_LEFT = GLFW.GLFW_GAMEPAD_BUTTON_DPAD_LEFT;
    /** Gatillos tratados como botones virtuales. */
    public static final int LT = 15;
    public static final int RT = 16;

    public float lx, ly, rx, ry, lt, rt;
    private int down, prevDown;
    /** Solo pruebas (AutoTest): botones que se dan por pulsados ademas de los reales de este mando. */
    public volatile int simDown;
    /** Solo pruebas: stick izquierdo simulado (si no es 0, sustituye al del mando). */
    public volatile float simLx, simLy;

    public void update(GLFWGamepadState s, float triggerThreshold) {
        prevDown = down;
        lx = s.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_X);
        ly = s.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_Y);
        rx = s.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_X);
        ry = s.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_Y);
        // GLFW da los gatillos en [-1, 1] con -1 en reposo.
        lt = (s.axes(GLFW.GLFW_GAMEPAD_AXIS_LEFT_TRIGGER) + 1f) * 0.5f;
        rt = (s.axes(GLFW.GLFW_GAMEPAD_AXIS_RIGHT_TRIGGER) + 1f) * 0.5f;

        int d = 0;
        for (int i = 0; i <= GLFW.GLFW_GAMEPAD_BUTTON_LAST; i++) {
            if (s.buttons(i) == GLFW.GLFW_PRESS) d |= 1 << i;
        }
        if (simLx != 0f || simLy != 0f) {
            lx = simLx;
            ly = simLy;
        }
        if (lt > triggerThreshold) d |= 1 << LT;
        if (rt > triggerThreshold) d |= 1 << RT;
        down = d | simDown;
    }

    public int downBits() { return down; }

    /** Mando virtual de pruebas: no hay hardware, el estado es solo simDown. */
    public void tickSim() {
        prevDown = down;
        down = simDown;
        lx = simLx;
        ly = simLy;
    }
    public boolean isDown(int code) { return (down & (1 << code)) != 0; }
    public boolean pressed(int code) { return isDown(code) && (prevDown & (1 << code)) == 0; }
    public boolean released(int code) { return !isDown(code) && (prevDown & (1 << code)) != 0; }
}
