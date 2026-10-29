package dev.consolepad.pad;

import dev.consolepad.ConsolePad;
import dev.consolepad.split.SplitSettings;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWGamepadState;

/**
 * Detecta mandos y los asigna a slots de jugador (hasta 4).
 * Fase 1: solo el slot 0 controla al jugador real; los demas quedan asignados para la pantalla dividida.
 */
public final class PadManager {
    public static final int MAX_PLAYERS = 4;
    private static final long SCAN_INTERVAL_NANOS = 500_000_000L;

    public static final class Device {
        public final int jid;
        public final String name;
        public final PadType type;
        public final PadState state = new PadState();

        Device(int jid, String name) {
            this.jid = jid;
            this.name = name == null ? "Gamepad " + jid : name;
            this.type = PadType.detect(this.name);
        }
    }

    public static final class Slot {
        public final int index;
        public PadSettings settings = new PadSettings();
        public Device device;
        /** El mando de este slot se desconecto y no ha vuelto (lo mira PadLost para pausar al jugador); lostJid = el que era. */
        public boolean lost;
        public int lostJid = -1;
        /** Solo pruebas: el mando (virtual o real) que simulateDisconnect quito del slot y simulateReconnect devuelve. */
        Device hidden;

        Slot(int index) { this.index = index; }

        /** Familia de iconos que ve este jugador: la que elija en sus ajustes o, si no, la de su mando (Xbox si no tiene). */
        public PadType iconType() {
            int st = settings.iconStyle;
            if (st >= 1 && st <= PadType.values().length) return PadType.values()[st - 1];
            return device == null ? PadType.XBOX : device.type;
        }
    }

    /** Diagnostico opcional (variable de entorno CONSOLEPAD_PADLOG=1): registra los cambios de estado de cada mando. */
    public static final boolean PADLOG = "1".equals(System.getenv("CONSOLEPAD_PADLOG"));
    private final int[] lastLoggedBits = new int[GLFW.GLFW_JOYSTICK_LAST + 1];
    private final Slot[] slots = new Slot[MAX_PLAYERS];
    private final java.util.List<Device> virtualDevices = new java.util.ArrayList<>();
    private final Device[] devices = new Device[GLFW.GLFW_JOYSTICK_LAST + 1];
    private final GLFWGamepadState buffer = GLFWGamepadState.calloc();
    private long lastScan = 0L;

    public PadManager() {
        for (int i = 0; i < MAX_PLAYERS; i++) slots[i] = new Slot(i);
        // El mapeado del jugador 1 es el de partida de los demas: un slot sin archivo propio copia los bindings actuales
        // del slot 0 (no los de fabrica). Sensibilidad, zonas muertas, etc. quedan con los valores por defecto del slot.
        slots[0].settings = PadSettingsStore.load(0, new PadSettings());
        for (int i = 1; i < MAX_PLAYERS; i++) {
            PadSettings seed = new PadSettings();
            seed.bindings.clear();
            seed.bindings.putAll(slots[0].settings.bindings);
            slots[i].settings = PadSettingsStore.load(i, seed);
        }
    }

    /** Solo pruebas (AutoTest): rellena un slot vacio con un mando sin hardware, controlado por PadState.simDown. */
    public void attachVirtual(int slotIndex) {
        Slot s = slots[slotIndex];
        if (s.device != null) return;
        Device d = new Device(-1, "Virtual (prueba)");
        s.device = d;
        s.lost = false;
        virtualDevices.add(d);
    }

    /** Solo pruebas (AutoTest): quita el mando virtual de un slot (como desenchufarlo); el siguiente attachVirtual es un mando nuevo. */
    public void detachVirtual(int slotIndex) {
        Slot s = slots[slotIndex];
        if (s.device != null && virtualDevices.remove(s.device)) s.device = null;
    }

    /** Solo pruebas (AutoTest): un mando virtual se "desconecta" como uno real (el slot queda perdido hasta que vuelva un mando). */
    public boolean simulateDisconnect(int slotIndex) {
        Slot s = slots[slotIndex];
        if (s.device == null) return false;
        s.hidden = s.device; // sigue en su lista (un mando real sigue "conectado" para GLFW, asi que no se reasigna)
        s.lostJid = s.device.jid;
        s.device = null;
        s.lost = true;
        return true;
    }

    /** Solo pruebas: el mando quitado por simulateDisconnect vuelve a su slot. */
    public boolean simulateReconnect(int slotIndex) {
        Slot s = slots[slotIndex];
        if (s.hidden == null) return false;
        s.device = s.hidden;
        s.hidden = null;
        s.lost = false;
        return true;
    }

    public void saveSettings() {
        for (Slot s : slots) {
            PadSettingsStore.save(s.index, s.settings);
            dev.consolepad.split.Profiles.sync(s.index); // el perfil que usa el jugador guarda sus ajustes
        }
    }

    public Slot slot(int index) { return slots[index]; }

    /** Todos los mandos conectados (reales y virtuales), con o sin jugador. */
    public java.util.List<Device> allDevices() {
        java.util.List<Device> out = new java.util.ArrayList<>();
        for (Device d : devices) if (d != null) out.add(d);
        out.addAll(virtualDevices);
        return out;
    }

    /** El jugador (slot) de un mando, o null si no tiene. */
    public Slot slotOfDevice(Device d) {
        return slotOf(d);
    }

    /**
     * Asigna un mando a un jugador: si el jugador ya tenia otro mando, los dos se intercambian (el otro pasa al jugador que tenia este, o queda sin
     * jugador si este no tenia). Un jugador que se queda sin mando se marca como "mando perdido" (PadLost lo pausa si esta dentro).
     * Los ajustes pertenecen al jugador, no al mando: se quedan donde estan.
     */
    public void assign(Device d, int slotIndex) {
        if (d == null || slotIndex < 0 || slotIndex >= MAX_PLAYERS) return;
        Slot target = slots[slotIndex];
        if (target.device == d) return;
        Slot from = slotOf(d);
        Device old = target.device;
        target.device = d;
        target.lost = false;
        target.hidden = null;
        if (from != null) {
            from.device = old;
            from.hidden = null;
            from.lost = old == null;
            if (old == null) from.lostJid = d.jid;
        }
        ConsolePad.LOGGER.info("Mando '{}' asignado al jugador {} (antes: {}); el jugador {} {}", d.name, slotIndex + 1,
                from == null ? "sin jugador" : "jugador " + (from.index + 1), slotIndex + 1, old == null ? "no tenia mando" : "tenia '" + old.name + "'");
    }

    /** Un mando de este fotograma con algun boton recien pulsado (para "pulsa un boton en el mando que quieres"), o null. */
    public Device firstPressedDevice() {
        for (Device d : allDevices()) {
            for (int code = 0; code <= PadState.RT; code++) {
                if (d.state.pressed(code)) return d;
            }
        }
        return null;
    }

    /** Verdadero si algun mando tiene un boton pulsado ahora. */
    public boolean anyDown() {
        for (Device d : allDevices()) if (d.state.downBits() != 0) return true;
        return false;
    }

    public void poll() {
        for (Device d : virtualDevices) {
            d.state.tickSim();
            Slot vs = slotOf(d);
            if (vs != null && vs.settings.swapSticks) d.state.swapSticks();
            if (vs != null && vs.settings.swapFace) d.state.swapFaceButtons();
        }
        long now = System.nanoTime();
        if (now - lastScan > SCAN_INTERVAL_NANOS) {
            lastScan = now;
            scanConnections();
        }
        for (int jid = 0; jid < devices.length; jid++) {
            Device dev = devices[jid];
            if (dev == null) continue;
            if (!GLFW.glfwGetGamepadState(jid, buffer)) {
                disconnect(jid);
                continue;
            }
            Slot s = slotOf(dev);
            dev.state.update(buffer, s != null ? s.settings.triggerThreshold : 0.5f);
            if (s != null && s.settings.swapSticks) dev.state.swapSticks();
            if (s != null && s.settings.swapFace) dev.state.swapFaceButtons();
            if (PADLOG && dev.state.downBits() != lastLoggedBits[jid]) {
                lastLoggedBits[jid] = dev.state.downBits();
                ConsolePad.LOGGER.info("[padlog] jid={} slot={} '{}' botones=0x{} LT={} RT={} stickIzq=({}, {}) stickDer=({}, {})", jid,
                        s == null ? "-" : String.valueOf(s.index), dev.name, Integer.toHexString(dev.state.downBits()),
                        String.format("%.2f", dev.state.lt), String.format("%.2f", dev.state.rt),
                        String.format("%.2f", dev.state.lx), String.format("%.2f", dev.state.ly),
                        String.format("%.2f", dev.state.rx), String.format("%.2f", dev.state.ry));
            }
        }
    }

    private void scanConnections() {
        for (int jid = GLFW.GLFW_JOYSTICK_1; jid <= GLFW.GLFW_JOYSTICK_LAST; jid++) {
            boolean isPad = GLFW.glfwJoystickIsGamepad(jid);
            if (isPad && devices[jid] == null) connect(jid);
            else if (!isPad && devices[jid] != null) disconnect(jid);
        }
    }

    /**
     * P1 con teclado y raton: el mando que tuviera el slot 0 pasa al primer slot libre de P2 a P4 (o queda sin jugador). Al desactivarlo, el slot 0 recupera un mando
     * que no tenga jugador (los mandos de jugadores que ya estan dentro no se mueven).
     */
    public void applyP1Keyboard(boolean on) {
        Slot first = slots[0];
        if (on) {
            Device d = first.device;
            if (d == null) return;
            for (int i = 1; i < MAX_PLAYERS; i++) {
                if (slots[i].device == null) {
                    slots[i].device = d;
                    slots[i].lost = false;
                    first.device = null;
                    first.lost = false;
                    return;
                }
            }
            first.device = null; // sin hueco: queda sin jugador
            first.lost = false;
        } else if (first.device == null) {
            for (Device d : allDevices()) {
                if (slotOf(d) == null) {
                    first.device = d;
                    first.lost = false;
                    return;
                }
            }
        }
    }

    private void connect(int jid) {
        Device dev = new Device(jid, GLFW.glfwGetGamepadName(jid));
        devices[jid] = dev;
        // Un mando que vuelve recupera su jugador; si no, el primer jugador sin mando que lo habia perdido; si no, el primer slot libre.
        Slot target = null;
        boolean kb = SplitSettings.p1Keyboard();
        for (Slot s : slots) if (s.device == null && s.lost && s.lostJid == jid && !(kb && s.index == 0)) target = s;
        if (target == null) for (Slot s : slots) if (s.device == null && s.lost && !(kb && s.index == 0)) { target = s; break; }
        if (target == null) for (Slot s : slots) if (s.device == null && !(kb && s.index == 0)) { target = s; break; }
        if (target != null) {
            target.device = dev;
            target.lost = false;
            ConsolePad.LOGGER.info("Mando '{}' ({}) asignado al jugador {}", dev.name, dev.type, target.index + 1);
            return;
        }
        ConsolePad.LOGGER.info("Mando '{}' detectado pero no hay slots libres", dev.name);
    }

    private void disconnect(int jid) {
        Device dev = devices[jid];
        devices[jid] = null;
        if (dev == null) return;
        Slot s = slotOf(dev);
        if (s != null) {
            s.device = null;
            s.lost = true;
            s.lostJid = jid;
        }
        ConsolePad.LOGGER.info("Mando '{}' desconectado", dev.name);
    }

    private Slot slotOf(Device dev) {
        for (Slot s : slots) if (s.device == dev) return s;
        return null;
    }
}
