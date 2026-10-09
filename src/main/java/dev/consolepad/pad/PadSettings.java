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
    /** Sensibilidad vertical (grados por segundo); 0 = la misma que la horizontal. */
    public float lookSpeedY = 0f;
    /** Empujar dos veces seguidas el stick izquierdo hacia delante activa el sprint. */
    public boolean doubleTapSprint = true;
    /** HUD propio: zona segura (porcentaje de cada borde), opacidad (0 a 1), y si se ven el HUD y la mano. */
    public int hudMargin = 0;
    public float hudOpacity = 1f;
    public boolean showHud = true;
    public boolean showHand = true;
    /** Campo de vision de este jugador (grados); 0 = el de las opciones del juego. */
    public int fov = 0;
    public float triggerThreshold = 0.5f;
    /** Escala de interfaz de este jugador con la pantalla dividida: 0 = automatica (la que elegiria Minecraft para su region), 1 a 4 = fija. */
    public int guiScale = 0;
    /** Nombre que ven los demas sobre la cabeza de este jugador; vacio = "Jugador N". */
    public String playerName = "";
    /** Ayuda de botones del HUD (abajo a izquierda y derecha) y barra de ayuda en pantallas. */
    public boolean showHints = true;
    /** Navegacion en pantallas: false = cursor libre con el stick izquierdo; true = el stick salta de casilla en casilla (como el D-pad). */
    public boolean snapNav = false;
    /** Cursor virtual en pantallas: pixeles por segundo a inclinacion maxima. */
    public float cursorSpeed = 550f;
    public float cursorDeadzone = 0.2f;
    public float cursorExponent = 2.0f;
    /** Multiplicador de la velocidad del cursor mientras esta sobre un slot de inventario. */
    public float cursorSlotFactor = 0.5f;
    /** Pasos de scroll por segundo a inclinacion maxima del stick derecho. */
    public float scrollSpeed = 6f;

    /** Sticks intercambiados (zurdo): el izquierdo gira la camara y el derecho mueve, tambien en menus y teclado en pantalla. */
    public boolean swapSticks = false;

    public float lookSpeedY() {
        return lookSpeedY > 0f ? lookSpeedY : lookSpeed;
    }

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
