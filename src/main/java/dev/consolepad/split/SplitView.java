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
    /** Escala con la que se dibujan las pantallas de P1 para caber en su region (1 = normal; con 4 jugadores < 1). */
    public static volatile float p1ScreenScale = 1f;
    /** Tamano de la region de P1 en unidades de su propia interfaz (lo que ve su HUD) y relacion con la escala de la ventana. */
    public static volatile int p1HudW, p1HudH, p1LogicalW, p1LogicalH;
    public static volatile int p1GuiScaleEff = 1;
    public static volatile float p1HudRatio = 1f;

    /** Escala de pantalla de P1 que esta en vigor (1 si no hay pantalla dividida). */
    public static float p1Scale() {
        return p1HalfScreen ? p1ScreenScale : 1f;
    }

    /** Opacidad con la que se dibuja el HUD del jugador en curso (1 = opaco): RenderSystemMixin la aplica a todo setShaderColor. */
    public static volatile float hudAlpha = 1f;

    /** True mientras la lluvia de un jugador extra crea sus gotas en el ParticleEngine compartido (si no, ParticleEngineMixin las descarta). */
    public static volatile boolean particleBypass;

    /** Solo pruebas: vuelve a la camara compartida de vanilla (el comportamiento anterior) para medir el fallo antes del arreglo. */
    public static volatile boolean legacyCamera;

    private SplitView() {}

    /**
     * Vuelve a fijar el viewport de la region en curso. RenderTarget.clear() deja el viewport al tamano de la ventana y
     * LevelRenderer.renderLevel no lo restaura (en vanilla da igual: la region es la ventana).
     */
    public static void restoreViewport() {
        if (width > 0) com.mojang.blaze3d.systems.RenderSystem.viewport(x, y, width, height);
    }
}
