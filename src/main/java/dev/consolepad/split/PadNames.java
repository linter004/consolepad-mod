package dev.consolepad.split;

import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;

/** Nombres que ve el jugador: de las acciones del juego y de los botones de su tipo de mando. */
public final class PadNames {
    private PadNames() {}

    public static String action(PadAction a) {
        return switch (a) {
            case JUMP -> "Saltar";
            case ATTACK -> "Atacar";
            case USE -> "Usar";
            case DROP -> "Soltar";
            case SWAP_HANDS -> "Cambiar de mano";
            case INVENTORY -> "Inventario";
            case PERSPECTIVE -> "Perspectiva";
            case HOTBAR_PREV -> "Barra anterior";
            case HOTBAR_NEXT -> "Barra siguiente";
            case SPRINT_TOGGLE -> "Correr";
            case SNEAK_TOGGLE -> "Agacharse";
            case PICK_BLOCK -> "Copiar bloque";
            case PLAYER_LIST -> "Jugadores";
            case CHAT -> "Chat";
            case PAUSE -> "Pausa";
        };
    }

    public static String button(int code, PadType type) {
        if (code == PadState.DPAD_UP) return "Cruceta arriba";
        if (code == PadState.DPAD_DOWN) return "Cruceta abajo";
        if (code == PadState.DPAD_LEFT) return "Cruceta izquierda";
        if (code == PadState.DPAD_RIGHT) return "Cruceta derecha";
        if (code == PadState.L3) return "L3";
        if (code == PadState.R3) return "R3";
        return switch (type) {
            case PLAYSTATION -> ps(code);
            case NINTENDO -> nintendo(code);
            default -> xbox(code);
        };
    }

    private static String xbox(int c) {
        if (c == PadState.A) return "A";
        if (c == PadState.B) return "B";
        if (c == PadState.X) return "X";
        if (c == PadState.Y) return "Y";
        if (c == PadState.LB) return "LB";
        if (c == PadState.RB) return "RB";
        if (c == PadState.LT) return "LT";
        if (c == PadState.RT) return "RT";
        if (c == PadState.BACK) return "Atrás";
        if (c == PadState.START) return "Inicio";
        return "Botón " + c;
    }

    private static String ps(int c) {
        if (c == PadState.A) return "Cruz";
        if (c == PadState.B) return "Círculo";
        if (c == PadState.X) return "Cuadrado";
        if (c == PadState.Y) return "Triángulo";
        if (c == PadState.LB) return "L1";
        if (c == PadState.RB) return "R1";
        if (c == PadState.LT) return "L2";
        if (c == PadState.RT) return "R2";
        if (c == PadState.BACK) return "Share";
        if (c == PadState.START) return "Options";
        return "Botón " + c;
    }

    private static String nintendo(int c) {
        if (c == PadState.A) return "B";
        if (c == PadState.B) return "A";
        if (c == PadState.X) return "Y";
        if (c == PadState.Y) return "X";
        if (c == PadState.LB) return "L";
        if (c == PadState.RB) return "R";
        if (c == PadState.LT) return "ZL";
        if (c == PadState.RT) return "ZR";
        if (c == PadState.BACK) return "-";
        if (c == PadState.START) return "+";
        return "Botón " + c;
    }
}
