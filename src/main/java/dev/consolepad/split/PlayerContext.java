package dev.consolepad.split;

import dev.consolepad.pad.PadAction;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

import java.util.EnumSet;

/** Estado de cliente de un jugador local extra: lo que vanilla guarda en campos globales de Minecraft. */
public final class PlayerContext {
    /** Slot de mando y numero de jugador menos uno: 1 = P2, 2 = P3, 3 = P4. */
    public final int slotIndex;
    public LocalPlayer player;
    public ClientLevel level;
    public MultiPlayerGameMode gameMode;
    public Entity cameraEntity;
    public HitResult hitResult;
    public Entity crosshairPickEntity;
    public int missTime;
    public int rightClickDelay;
    /** FOV suavizado de GameRenderer (fov/oldFov): efecto de correr, arco, etc. Cada jugador el suyo. */
    public float fov = 1.0F;
    public float oldFov = 1.0F;
    /** Mano en primera persona propia (objeto, altura, animacion de equipar/usar): el de GameRenderer es unico y solo lo ticka P1. */
    public net.minecraft.client.renderer.ItemInHandRenderer hand;
    /** Estado del HUD que es del jugador (parpadeo de vida, nombre del objeto): SplitHud lo intercambia con el de Gui. */
    public final HudState hud = new HudState();
    /** Perspectiva (F5) de este jugador: Minecraft la guarda en Options, que es global. */
    public net.minecraft.client.CameraType cameraType = net.minecraft.client.CameraType.FIRST_PERSON;
    /** Pantalla de este jugador (inventario, contenedores...) y su cursor virtual, en unidades de GUI logicas de su pantalla. */
    public net.minecraft.client.gui.screens.Screen screen;
    public double cursorX, cursorY;
    public final boolean[] buttonDown = new boolean[2];
    public final boolean[] dpadActive = new boolean[4];
    /** Direcciones (arriba, abajo, izquierda, derecha) del D-pad o del stick en modo casillas en el fotograma anterior. */
    public final boolean[] dirPrev = new boolean[4];
    public final float[] dpadHeld = new float[4], dpadNext = new float[4];
    public double scrollAcc;
    /** Avisos propios: chat y mensajes del servidor que recibe este jugador, mensaje sobre la barra y titulo (el chat/avisos de P1 son los de vanilla). */
    public final java.util.ArrayDeque<Notice> notices = new java.util.ArrayDeque<>();
    public net.minecraft.network.chat.Component overlay;
    public int overlayTime;
    public net.minecraft.network.chat.Component title;
    public int titleTime;
    public volatile int noticesDrawn;
    /** Lineas de chat dibujadas en el ultimo fotograma (0 con una pantalla de contenedor abierta: el historial se oculta para no cruzarse con ella). */
    public volatile int chatLinesDrawn;
    /** El jugador ya salio (SecondaryPlayers.forget): los paquetes que aun estaban en cola del hilo de red se descartan, no hay nivel ni jugador con que procesarlos. */
    public volatile boolean released;
    /** Ancho (unidades de su HUD) de la linea de avisos mas ancha dibujada: debe caber en su region. */
    public volatile int noticeMaxWidth;
    /** Estado de la entrada de su mando entre ticks. */
    public final InputState input = new InputState();
    /** Region de la ventana que le toca, en unidades de GUI (origen arriba a la izquierda) y en pixeles (origen abajo a la izquierda); la fija SplitLayout. */
    public int gx, gy, gw, gh;
    public int px, py, pw, ph;
    /** Escala de interfaz elegida para su region (entera, pixeles por unidad de GUI) y su relacion con la de la ventana. */
    public int guiScaleEff = 1;
    public float hudRatio = 1f;
    /** Tamano de su region en unidades de su propia interfaz: lo que ve el HUD (guiWidth/guiHeight). */
    public int hudW, hudH;
    /** Lo que queda de su region dentro de la zona segura (los avisos se ajustan a esto); 0 = aun sin calcular. */
    public int safeW, safeH;
    /** Relacion (respecto a la escala de la ventana) con la que se dibujan sus pantallas: la de su HUD, reducida si no cabria un inventario. */
    public float screenScale = 1f;
    /** Tamano logico de sus pantallas (unidades antes de aplicar screenScale). */
    public int logicalW, logicalH;
    /** Renderer latente: recibe los avisos de su ClientLevel sin tocar el de P1. Se sustituye en el paso 4. */
    public final LevelRenderer renderer;

    PlayerContext(LevelRenderer renderer, int slotIndex) {
        this.renderer = renderer;
        this.slotIndex = slotIndex;
    }

    public String label() {
        return "P" + (slotIndex + 1);
    }

    /** Un aviso con el tick de cliente en que llego. */
    public record Notice(net.minecraft.network.chat.Component text, long tick) {}

    /** Camara propia (GameRenderer.mainCamera es una y solo sigue a P1) y su mapa de luz; ver ContextSwitcher.cameraOr/lightOr. */
    public final net.minecraft.client.Camera camera = new net.minecraft.client.Camera();
    public boolean cameraReady;
    private net.minecraft.client.renderer.LightTexture lightTexture;

    public net.minecraft.client.renderer.LightTexture lightTexture(net.minecraft.client.Minecraft mc) {
        if (lightTexture == null) lightTexture = new net.minecraft.client.renderer.LightTexture(mc.gameRenderer, mc);
        return lightTexture;
    }

    /** Libera la textura del mapa de luz (hilo de render, al salir el jugador). */
    public void closeLightTexture() {
        if (lightTexture != null) {
            lightTexture.close();
            lightTexture = null;
        }
    }

    /** Barras de jefe de este jugador (las que le llegan por su conexion). */
    private net.minecraft.client.gui.components.BossHealthOverlay bossOverlay;
    /** Marcado por el HUD: lo que se dibujo en el ultimo fotograma (autotest). */
    public volatile int lastBossBars;

    public net.minecraft.client.gui.components.BossHealthOverlay bossOverlay() {
        if (bossOverlay == null) bossOverlay = new net.minecraft.client.gui.components.BossHealthOverlay(net.minecraft.client.Minecraft.getInstance());
        return bossOverlay;
    }

    /** Sujeto a la bandera de la lista de jugadores: tecla mantenida y sin pantalla abierta. */
    public volatile boolean playerListHeld;

    public static final class HudState {
        public float scopeScale = 0.5F, vignetteBrightness = 1.0F;
        public int lastHealth, displayHealth, toolHighlightTimer;
        public long lastHealthTime, healthBlinkTime;
        public net.minecraft.world.item.ItemStack lastToolHighlight = net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** Lo que SecondaryTick recuerda de un tick al siguiente. */
    public static final class InputState {
        public boolean prevAttack, prevUse, prevLb, prevRb;
        public boolean sprintLatch, sneakLatch, comboLatch;
        /** Atacar y usar en modo alternar (PadSettings.isToggle): quedan activos hasta la siguiente pulsacion. */
        public boolean attackOn, useOn;
        /** Aceleracion del giro de camara (dev.consolepad.pad.LookControl). */
        public final dev.consolepad.pad.LookControl.State look = new dev.consolepad.pad.LookControl.State();
        /** Sprint por doble toque del stick hacia delante: dura mientras el stick siga hacia delante. */
        public boolean doubleTapSprint, fwdState;
        public int ticksSinceFwdTap = 1000;
        /** Botones (mascara de codigos de PadState) que acaban de cerrar una pantalla: hasta soltarlos no disparan su accion de juego (soltar, inventario, pausa...). */
        public int closeLatch;
        /** Sprint conmutado: lo lee KeyMappingGuardMixin cuando LocalPlayer.aiStep pregunta por keySprint. */
        public volatile boolean sprintHeld;
        public int deadTicks;
        public EnumSet<PadAction> prevDown = EnumSet.noneOf(PadAction.class);
        public EnumSet<PadAction> pressedNow = EnumSet.noneOf(PadAction.class);
    }
}
