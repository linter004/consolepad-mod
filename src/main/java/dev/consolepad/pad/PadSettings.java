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
    /** Curva de respuesta del stick de la camara (lineal, suave = la de siempre, con aceleracion). */
    public LookControl.Curve lookCurve = LookControl.Curve.SMOOTH;
    /** Multiplicador de la sensibilidad de la camara al apuntar con arco, ballesta o catalejo (1 = la misma). */
    public float aimSensitivity = 1f;
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
    /** Informacion extra en la region de este jugador: coordenadas, direccion a la que mira y FPS. */
    public boolean showCoords = false;
    public boolean showDirection = false;
    public boolean showFps = false;
    /** Color de la etiqueta con el nombre sobre la cabeza (indice de PlayerLabels.COLORS; 0 = sin color). */
    public int labelColor = 0;
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

    /** Agacharse, correr y atacar: true = una pulsacion alterna, false = vale mientras se mantiene el boton. */
    public boolean sneakToggle = true;
    public boolean sprintToggle = true;
    public boolean attackToggle = false;
    /** Accesibilidad: los botones de mantener (agacharse, correr, atacar, usar) pasan a alternar. */
    public boolean toggleHolds = false;

    /** Si la accion se alterna con una pulsacion (true) o se mantiene (false). */
    public boolean isToggle(PadAction a) {
        return switch (a) {
            case SNEAK_TOGGLE -> sneakToggle || toggleHolds;
            case SPRINT_TOGGLE -> sprintToggle || toggleHolds;
            case ATTACK -> attackToggle || toggleHolds;
            case USE -> toggleHolds;
            default -> false;
        };
    }

    /** Lo propio de cada jugador que ni copiar ni restablecer tocan (quien es, no como juega). */
    private static boolean isIdentity(String field) {
        return field.equals("playerName") || field.equals("labelColor");
    }

    /** Copia todos los ajustes de otro jugador (botones incluidos, copia profunda) salvo su nombre y su color de etiqueta. */
    public void copyFrom(PadSettings o) {
        if (o == this) return;
        for (java.lang.reflect.Field f : PadSettings.class.getDeclaredFields()) {
            int mod = f.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(mod) || isIdentity(f.getName())) continue;
            try {
                if (f.getName().equals("bindings")) {
                    Map<Integer, PadAction> mine = bindings;
                    Map<Integer, PadAction> theirs = new HashMap<>(o.bindings);
                    mine.clear();
                    mine.putAll(theirs);
                } else {
                    f.set(this, f.get(o));
                }
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    /** Valores de fabrica (conserva el nombre y el color de etiqueta). */
    public void resetToDefaults() {
        copyFrom(new PadSettings());
    }

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
