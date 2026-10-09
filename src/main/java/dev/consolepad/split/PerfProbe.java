package dev.consolepad.split;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.TreeMap;

/**
 * Medicion de rendimiento por fotograma (solo mientras {@link #recording}; autotest). Reparte el fotograma en segmentos
 * consecutivos ("pre", una pasada de mundo por jugador "P1".."P4", "post" = HUD y pantallas) y de cada uno anota el tiempo de CPU
 * del hilo principal (System.nanoTime) y el de GPU (consulta GL_TIME_ELAPSED, que cuenta solo el tiempo que la GPU trabaja).
 * Las consultas no se anidan: cada segmento cierra la anterior. Los resultados se leen unos fotogramas despues.
 */
public final class PerfProbe {
    public static volatile boolean recording;

    private static boolean gpuChecked, gpuOk;
    private static final ArrayDeque<int[]> pending = new ArrayDeque<>(); // {id, indice de nombre}
    private static final ArrayDeque<Integer> free = new ArrayDeque<>();
    private static final java.util.List<String> names = new java.util.ArrayList<>();
    private static int openId = -1, openName = -1;
    private static String cpuName;
    private static long cpuStart;

    private static final Map<String, Long> cpuNs = new TreeMap<>();
    private static final Map<String, Long> gpuNs = new TreeMap<>();
    private static long frames, frameWallNs, renderNs, tickNs, lastFrameBegin, frameBeginNs, tickBegin;
    private static long ticks, tickMaxNs, startNs;

    private PerfProbe() {}

    private static boolean gpu() {
        if (!gpuChecked) {
            gpuChecked = true;
            try {
                var caps = GL.getCapabilities();
                gpuOk = caps.OpenGL33 || caps.GL_ARB_timer_query;
            } catch (RuntimeException e) {
                gpuOk = false;
            }
        }
        return gpuOk;
    }

    public static boolean gpuAvailable() {
        return gpu();
    }

    public static synchronized void start() {
        cpuNs.clear();
        gpuNs.clear();
        frames = frameWallNs = renderNs = tickNs = ticks = 0;
        lastFrameBegin = 0;
        tickMaxNs = 0;
        startNs = System.nanoTime();
        tickBegin = startNs; // el tick en curso (con el System.gc previo) no cuenta
        recording = true;
    }

    /** Cierra la medicion y recoge las consultas pendientes (espera al resultado de cada una). */
    public static synchronized void stop() {
        recording = false;
        endSegment();
        poll(true);
    }

    private static int nameIndex(String n) {
        int i = names.indexOf(n);
        if (i < 0) {
            names.add(n);
            i = names.size() - 1;
        }
        return i;
    }

    private static void endSegment() {
        long now = System.nanoTime();
        if (cpuName != null) cpuNs.merge(cpuName, now - cpuStart, Long::sum);
        cpuName = null;
        if (openId >= 0) {
            GL33C.glEndQuery(GL33C.GL_TIME_ELAPSED);
            pending.add(new int[] {openId, openName});
            openId = -1;
        }
    }

    /** Empieza un segmento nuevo (cierra el anterior). name == null solo cierra. */
    public static synchronized void segment(String name) {
        if (!recording) return;
        endSegment();
        if (name == null) return;
        cpuName = name;
        cpuStart = System.nanoTime();
        if (gpu()) {
            Integer id = free.poll();
            if (id == null) id = GL15C.glGenQueries();
            openId = id;
            openName = nameIndex(name);
            GL33C.glBeginQuery(GL33C.GL_TIME_ELAPSED, id);
        }
    }

    private static void poll(boolean wait) {
        while (!pending.isEmpty()) {
            int[] q = pending.peek();
            if (!wait && GL15C.glGetQueryObjecti(q[0], GL15C.GL_QUERY_RESULT_AVAILABLE) == 0) return;
            pending.poll();
            long ns = GL33C.glGetQueryObjecti64(q[0], GL15C.GL_QUERY_RESULT);
            gpuNs.merge(names.get(q[1]), ns, Long::sum);
            free.add(q[0]);
        }
    }

    /** Inicio de GameRenderer.render. */
    public static synchronized void frameBegin() {
        if (!recording) return;
        long now = System.nanoTime();
        if (lastFrameBegin != 0) {
            frameWallNs += now - lastFrameBegin;
            frames++;
        }
        lastFrameBegin = now;
        frameBeginNs = now;
        poll(false);
        segment("pre");
    }

    /** Fin de GameRenderer.render (el intercambio de buffers queda fuera). */
    public static synchronized void frameEnd() {
        if (!recording) return;
        segment(null);
        renderNs += System.nanoTime() - frameBeginNs;
    }

    public static void tickBegin() {
        tickBegin = System.nanoTime();
    }

    public static synchronized void tickEnd() {
        if (!recording || tickBegin == 0) return;
        long d = System.nanoTime() - tickBegin;
        tickNs += d;
        tickMaxNs = Math.max(tickMaxNs, d);
        ticks++;
        tickBegin = 0;
    }

    /** Resumen de la ultima medicion: ms por fotograma. */
    public record Result(long tickCalls, double tickMaxMs, double wallMs, int frames, double frameMs, double fps, double renderCpuMs, double tickCpuMsPerFrame, double gpuMs,
                         Map<String, Double> cpuByPass, Map<String, Double> gpuByPass) {
        /** "GPU", "CPU" o "otro" (espera de intercambio, limite de FPS...). */
        public String bottleneck() {
            if (gpuMs >= 0.9 * frameMs) return "GPU";
            if (renderCpuMs + tickCpuMsPerFrame >= 0.85 * frameMs) return "CPU";
            return gpuMs > renderCpuMs + tickCpuMsPerFrame ? "GPU (parcial)" : "CPU (parcial)";
        }
    }

    public static synchronized Result result() {
        double f = Math.max(1, frames);
        Map<String, Double> cpu = new TreeMap<>(), g = new TreeMap<>();
        double gpuTotal = 0;
        for (var e : cpuNs.entrySet()) cpu.put(e.getKey(), e.getValue() / 1e6 / f);
        for (var e : gpuNs.entrySet()) {
            g.put(e.getKey(), e.getValue() / 1e6 / f);
            gpuTotal += e.getValue() / 1e6 / f;
        }
        double frameMs = frameWallNs / 1e6 / f;
        return new Result(ticks, tickMaxNs / 1e6, (System.nanoTime() - startNs) / 1e6, (int) frames, frameMs, frameMs > 0 ? 1000.0 / frameMs : 0, renderNs / 1e6 / f, tickNs / 1e6 / f, gpuTotal, cpu, g);
    }
}
