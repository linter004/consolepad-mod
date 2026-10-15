package dev.consolepad.pad;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import dev.consolepad.ConsolePad;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Vibracion por jugador. GLFW no puede hacer vibrar un mando, asi que en Windows se llama a XInput (la libreria que Windows ya trae) por JNA, que Minecraft
 * incluye: sirve para todo mando que Windows ve como XInput (Xbox, y los de PlayStation o Nintendo cuando llegan como XInput por Steam Input o similares).
 * Cada jugador tiene su fuerza en sus ajustes ({@link PadSettings#vibration}); un impulso suelta solo y nunca queda un motor encendido (se apagan al soltar,
 * al perder el mando, al cerrar el juego y con la ventana sin foco). Si la libreria no esta (Linux, macOS) no hace nada y no da error.
 */
public final class Rumble {
    public interface XInput extends Library {
        int XInputSetState(int user, Vibration v);
    }

    @Structure.FieldOrder({"left", "right"})
    public static final class Vibration extends Structure {
        public short left;
        public short right;
    }

    private static final String XINPUT_GUID = "78696e707574"; // "xinput": GLFW pone este prefijo al GUID de los mandos XInput
    private static XInput lib;
    private static boolean libTried;

    private static final long[] until = new long[4];
    private static final float[] low = new float[4], high = new float[4];
    private static final int[] sentUser = {-1, -1, -1, -1};
    private static final boolean[] active = new boolean[4];
    /** Pruebas y diagnostico: cuantos impulsos se han pedido por jugador, la fuerza efectiva del ultimo (grave y agudo) y si se llego a enviar al mando. */
    public static final int[] pulses = new int[4];
    public static final float[] lastLow = new float[4], lastHigh = new float[4];
    public static final int[] sent = new int[4];

    private Rumble() {}

    private static XInput lib() {
        if (!libTried) {
            libTried = true;
            if (System.getProperty("os.name", "").toLowerCase().contains("win")) {
                for (String n : List.of("xinput1_4", "xinput1_3", "xinput9_1_0")) {
                    try {
                        lib = Native.load(n, XInput.class);
                        Runtime.getRuntime().addShutdownHook(new Thread(Rumble::stopAll, "consolepad-rumble-off"));
                        break;
                    } catch (Throwable ignored) {
                        // se prueba la siguiente version de la libreria
                    }
                }
                if (lib == null) ConsolePad.LOGGER.info("Vibracion: XInput no disponible, los mandos no vibraran");
            }
        }
        return lib;
    }

    /** Indice de usuario de XInput (0 a 3) del mando del slot, o -1 si no es un mando XInput real (los virtuales de prueba tampoco). */
    public static int xinputUser(int slot) {
        var dev = ConsolePad.PADS.slot(slot).device;
        if (dev == null || dev.jid < 0 || dev.jid > GLFW.GLFW_JOYSTICK_LAST || !GLFW.glfwJoystickPresent(dev.jid)) return -1;
        String guid = GLFW.glfwGetJoystickGUID(dev.jid);
        if (guid == null || !guid.startsWith(XINPUT_GUID)) return -1;
        int n = 0;
        for (int j = 0; j < dev.jid; j++) { // GLFW da los ids a los XInput en el orden de sus indices
            if (GLFW.glfwJoystickPresent(j)) {
                String g = GLFW.glfwGetJoystickGUID(j);
                if (g != null && g.startsWith(XINPUT_GUID)) n++;
            }
        }
        return n < 4 ? n : -1;
    }

    /** Pide un impulso al mando de un jugador: grave (motor grande) y agudo (motor pequeno) de 0 a 1, durante ms; se escala con la fuerza de sus ajustes. */
    public static void pulse(int slot, float lowMotor, float highMotor, int ms) {
        if (slot < 0 || slot >= 4) return;
        float k = ConsolePad.PADS.slot(slot).settings.vibration;
        float l = Math.max(0f, Math.min(1f, lowMotor * k)), h = Math.max(0f, Math.min(1f, highMotor * k));
        pulses[slot]++;
        lastLow[slot] = l;
        lastHigh[slot] = h;
        if (l <= 0f && h <= 0f) return;
        long end = System.currentTimeMillis() + ms;
        if (end >= until[slot]) { // un impulso nuevo mas fuerte o mas largo sustituye al anterior
            until[slot] = end;
            low[slot] = l;
            high[slot] = h;
        }
    }

    /** Una vez por tick de cliente: envia el estado al mando o lo apaga cuando el impulso termina. */
    public static void tick(boolean focused) {
        long now = System.currentTimeMillis();
        for (int slot = 0; slot < 4; slot++) {
            boolean want = focused && now < until[slot];
            if (want) {
                int user = xinputUser(slot);
                if (user < 0) {
                    until[slot] = 0;
                    continue;
                }
                send(slot, user, low[slot], high[slot]);
                active[slot] = true;
            } else if (active[slot]) {
                stop(slot);
            }
        }
    }

    private static void send(int slot, int user, float l, float h) {
        XInput x = lib();
        if (x == null) {
            until[slot] = 0;
            return;
        }
        Vibration v = new Vibration();
        v.left = (short) Math.round(l * 65535f);
        v.right = (short) Math.round(h * 65535f);
        try {
            x.XInputSetState(user, v);
            sentUser[slot] = user;
            sent[slot]++;
        } catch (Throwable t) {
            until[slot] = 0;
        }
    }

    private static void stop(int slot) {
        active[slot] = false;
        until[slot] = 0;
        int user = sentUser[slot] >= 0 ? sentUser[slot] : xinputUser(slot);
        sentUser[slot] = -1;
        if (user < 0 || lib == null) return;
        try {
            lib.XInputSetState(user, new Vibration());
        } catch (Throwable ignored) {
            // el mando ya no esta: no hay nada que apagar
        }
    }

    /** Apaga todos los motores (cerrar el juego, salir del mundo, perder el foco). */
    public static void stopAll() {
        for (int slot = 0; slot < 4; slot++) if (active[slot] || until[slot] != 0) stop(slot);
    }

    /** Pruebas: true si el slot tiene un impulso pendiente de apagar. */
    public static boolean isActive(int slot) {
        return active[slot];
    }
}
