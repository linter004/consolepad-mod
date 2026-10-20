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
    /** Factor de la sensibilidad de la camara mientras la mira esta sobre un ser vivo (1 = sin ayuda): frena un poco el giro para acertar mas facil. */
    public float targetSlowdown = 0.8f;
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
    /** Compensacion del campo de vision al dividir la pantalla (0 = ninguna, 0.5 = media, 1 = total): sube el FOV vertical en regiones estrechas. */
    public float fovComp = 0.5f;
    /** Distancia de chunks de este jugador (4 a 32); 0 = la de las opciones del juego. La automatica por numero de jugadores es un tope (PerfLimits). */
    public int viewDistance = 0;
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
    /** Punto sobre la barra de experiencia que apunta hacia los demas jugadores cuando estan lejos (como la barra de localizacion de Bedrock), con su nombre y distancia. */
    public boolean showCompanions = true;
    /** Ademas del cuadrado que marca la direccion del companero, mostrar su nombre y la distancia encima de los corazones (por defecto no: solo la direccion). */
    public boolean showCompanionDistance = false;
    /** Ademas del compañero, un segundo marcador en la misma barra hacia la cama o el punto de reaparicion propio (por defecto no). */
    public boolean showBedMarker = false;
    /** Salto automatico de este jugador al subir bloques de uno en uno: 0 = como la opcion del juego, 1 = si, 2 = no. */
    public int autoJump = 0;
    /** Muñeco del jugador en una esquina mientras corre, se agacha, nada o planea (como en Bedrock). */
    public boolean paperDoll = true;
    /** Teclado en pantalla: false = solo se abre al pulsar Y en una pantalla de texto (chat, carteles...) o A sobre un campo; true = se abre solo al entrar en ellas. */
    public boolean autoKeyboard = false;
    /** Cursor del mando y casilla seleccionada con el color del jugador (azul, rojo, verde, amarillo o el de su etiqueta). */
    public boolean playerColors = true;
    /** Destello rojo suave en los bordes de su pantalla con 3 corazones o menos, al ritmo del latido. */
    public boolean lowHealthFlash = true;
    /** Brillo propio de este jugador (0 a 1); un valor negativo = el de las opciones del juego. */
    public float brightness = -1f;
    /** Fuerza de la vibracion del mando de este jugador (0 = apagada, 1 = maxima). */
    public float vibration = 0.7f;
    /** Fuerza por tipo de evento, que se multiplica por la general: daño (y latido con poca vida), acciones (golpear, romper, comer, arco, pesca) y entorno (explosiones). */
    public float vibDamage = 1f, vibActions = 1f, vibWorld = 1f;
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
    /** Disposicion Nintendo: los botones A y B, y X e Y, se intercambian (el A de Nintendo esta a la derecha, el de Xbox abajo); vale en el juego y en los menus. */
    public boolean swapFace = false;
    /** Soltar objetos mantenido: ticks entre un objeto y el siguiente (2 = rapido, como Q mantenida en el teclado). */
    public int dropInterval = 2;

    /** Agacharse, correr y atacar: true = una pulsacion alterna, false = vale mientras se mantiene el boton. */
    public boolean sneakToggle = true;
    public boolean sprintToggle = true;
    public boolean attackToggle = false;
    /** Accesibilidad: ayuda de botones un 50 % mayor, y menos efectos de pantalla (vineta, balanceo y sacudida de la camara, FOV al correr). */
    public boolean bigHints = false;
    public boolean reduceEffects = false;
    /** Accesibilidad: los botones de mantener (agacharse, correr, atacar, usar) pasan a alternar. */
    public boolean toggleHolds = false;
    /** Iconos de botones de este jugador: 0 = los de su mando, 1 = Xbox, 2 = PlayStation, 3 = Nintendo (el orden de PadType + 1). */
    public int iconStyle = 0;

    /** Entrada de la rueda de acciones rapidas propia del jugador. */
    public static final class WheelEntry {
        public String label = "";
        public String command = "";

        public WheelEntry(String label, String command) {
            this.label = label;
            this.command = command;
        }
    }

    /** Rueda propia del jugador (null = la comun, config/consolepad/wheel.json). La lista no se modifica: se sustituye entera. */
    public java.util.List<WheelEntry> wheel = null;
    /** Perfil local que usa este jugador (id = nombre de cuenta con el que entra, ver dev.consolepad.split.Profiles); vacio = ninguno. Es de identidad: no se copia. */
    public String profileId = "";
    /** La rueda de acciones rapidas ya se ofrecio a este jugador (los ajustes guardados de antes no la traen: se le da la cruceta izquierda una sola vez si estaba libre). */
    public boolean wheelOffered = false;

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
        return field.equals("playerName") || field.equals("labelColor") || field.equals("profileId");
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
        m.put(PadState.DPAD_LEFT, PadAction.QUICK_WHEEL);
        m.put(PadState.DPAD_RIGHT, PadAction.CHAT);
        m.put(PadState.BACK, PadAction.PLAYER_LIST);
        m.put(PadState.RT, PadAction.ATTACK);
        m.put(PadState.LT, PadAction.USE);
        return m;
    }
}
