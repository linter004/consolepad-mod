package dev.consolepad.split;

/** Tamano (en pixeles) de la region que se esta dibujando; 0 = sin pantalla dividida (se usa la ventana entera). */
public final class SplitView {
    /** Esquina inferior izquierda de la region (origen del viewport). */
    public static volatile int x;
    public static volatile int y;
    public static volatile int width;
    public static volatile int height;
    /** Ancho de la interfaz (en unidades de GUI) de la region cuyo HUD se dibuja; 0 = el de la ventana. */
    public static volatile int guiWidth;
    /** Alto de la interfaz (unidades de GUI) de la region que se dibuja; 0 = el de la ventana. */
    public static volatile int guiHeight;
    /** Region de P1 en unidades de GUI {x, y, ancho, alto} (origen arriba izquierda) y en pixeles (origen abajo izquierda); la fija SplitLayout. */
    public static volatile int[] p1Gui = {0, 0, 0, 0};
    public static volatile int[] p1Px = {0, 0, 0, 0};
    /** Permite apagar la division en pruebas (medir FPS con un solo jugador). */
    public static volatile boolean enabled = true;
    /** True mientras la pantalla de P1 se encaja en la mitad izquierda (hay un P2 dibujandose); la fija SplitHud. */
    public static volatile boolean p1HalfScreen;

    private SplitView() {}

    /**
     * Vuelve a fijar el viewport de la region en curso. RenderTarget.clear() deja el viewport al tamano de la ventana y
     * LevelRenderer.renderLevel no lo restaura (en vanilla da igual: la region es la ventana).
     */
    public static void restoreViewport() {
        if (width > 0) com.mojang.blaze3d.systems.RenderSystem.viewport(x, y, width, height);
    }
}
