package dev.consolepad.pad;

import dev.consolepad.ConsolePad;
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

        Slot(int index) { this.index = index; }
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
        virtualDevices.add(d);
    }

    /** Solo pruebas (AutoTest): quita el mando virtual de un slot (como desenchufarlo); el siguiente attachVirtual es un mando nuevo. */
    public void detachVirtual(int slotIndex) {
        Slot s = slots[slotIndex];
        if (s.device != null && virtualDevices.remove(s.device)) s.device = null;
    }

    public void saveSettings() {
        for (Slot s : slots) PadSettingsStore.save(s.index, s.settings);
    }

    public Slot slot(int index) { return slots[index]; }

    public void poll() {
        for (Device d : virtualDevices) {
            d.state.tickSim();
            Slot vs = slotOf(d);
            if (vs != null && vs.settings.swapSticks) d.state.swapSticks();
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

    private void connect(int jid) {
        Device dev = new Device(jid, GLFW.glfwGetGamepadName(jid));
        devices[jid] = dev;
        for (Slot s : slots) {
            if (s.device == null) {
                s.device = dev;
                ConsolePad.LOGGER.info("Mando '{}' ({}) asignado al jugador {}", dev.name, dev.type, s.index + 1);
                return;
            }
        }
        ConsolePad.LOGGER.info("Mando '{}' detectado pero no hay slots libres", dev.name);
    }

    private void disconnect(int jid) {
        Device dev = devices[jid];
        devices[jid] = null;
        if (dev == null) return;
        Slot s = slotOf(dev);
        if (s != null) s.device = null;
        ConsolePad.LOGGER.info("Mando '{}' desconectado", dev.name);
    }

    private Slot slotOf(Device dev) {
        for (Slot s : slots) if (s.device == dev) return s;
        return null;
    }
}
