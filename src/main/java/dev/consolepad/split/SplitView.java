package dev.consolepad.split;

/** Tamano (en pixeles) de la region que se esta dibujando; 0 = sin pantalla dividida (se usa la ventana entera). */
public final class SplitView {
    public static volatile int width;
    public static volatile int height;
    /** Ancho de la interfaz (en unidades de GUI) de la region cuyo HUD se dibuja; 0 = el de la ventana. */
    public static volatile int guiWidth;
    /** Permite apagar la division en pruebas (medir FPS con un solo jugador). */
    public static volatile boolean enabled = true;

    private SplitView() {}
}
