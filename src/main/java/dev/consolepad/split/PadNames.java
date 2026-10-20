package dev.consolepad.split;

import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadState;
import dev.consolepad.pad.PadType;

/** Nombres que ve el jugador: de las acciones del juego y de los botones de su tipo de mando. */
public final class PadNames {
    private PadNames() {}

    public static String action(PadAction a) {
        return switch (a) {
            case JUMP -> I18n.tr("Saltar");
            case ATTACK -> I18n.tr("Atacar");
            case USE -> I18n.tr("Usar");
            case DROP -> I18n.tr("Soltar");
            case SWAP_HANDS -> I18n.tr("Cambiar de mano");
            case INVENTORY -> I18n.tr("Inventario");
            case PERSPECTIVE -> I18n.tr("Perspectiva");
            case HOTBAR_PREV -> I18n.tr("Barra anterior");
            case HOTBAR_NEXT -> I18n.tr("Barra siguiente");
            case SPRINT_TOGGLE -> I18n.tr("Correr");
            case SNEAK_TOGGLE -> I18n.tr("Agacharse");
            case PICK_BLOCK -> I18n.tr("Copiar bloque");
            case PLAYER_LIST -> I18n.tr("Jugadores");
            case CHAT -> I18n.tr("Chat");
            case PAUSE -> I18n.tr("Pausa");
            case QUICK_WHEEL -> I18n.tr("Acciones");
        };
    }

    public static String button(int code, PadType type) {
        if (code == PadState.DPAD_UP) return I18n.tr("Cruceta arriba");
        if (code == PadState.DPAD_DOWN) return I18n.tr("Cruceta abajo");
        if (code == PadState.DPAD_LEFT) return I18n.tr("Cruceta izquierda");
        if (code == PadState.DPAD_RIGHT) return I18n.tr("Cruceta derecha");
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
        if (c == PadState.BACK) return I18n.tr("Vista");
        if (c == PadState.START) return I18n.tr("Menú");
        return I18n.fmt("Botón {0}", c);
    }

    private static String ps(int c) {
        if (c == PadState.A) return I18n.tr("Cruz");
        if (c == PadState.B) return I18n.tr("Círculo");
        if (c == PadState.X) return I18n.tr("Cuadrado");
        if (c == PadState.Y) return I18n.tr("Triángulo");
        if (c == PadState.LB) return "L1";
        if (c == PadState.RB) return "R1";
        if (c == PadState.LT) return "L2";
        if (c == PadState.RT) return "R2";
        if (c == PadState.BACK) return I18n.tr("Share");
        if (c == PadState.START) return I18n.tr("Options");
        return I18n.fmt("Botón {0}", c);
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
        return I18n.fmt("Botón {0}", c);
    }
}
