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
    public final float[] dpadHeld = new float[4], dpadNext = new float[4];
    public double scrollAcc;
    /** Avisos propios: chat y mensajes del servidor que recibe este jugador, mensaje sobre la barra y titulo (el chat/avisos de P1 son los de vanilla). */
    public final java.util.ArrayDeque<Notice> notices = new java.util.ArrayDeque<>();
    public net.minecraft.network.chat.Component overlay;
    public int overlayTime;
    public net.minecraft.network.chat.Component title;
    public int titleTime;
    public volatile int noticesDrawn;
    /** Estado de la entrada de su mando entre ticks. */
    public final InputState input = new InputState();
    /** Region de la ventana que le toca, en unidades de GUI (origen arriba a la izquierda) y en pixeles (origen abajo a la izquierda); la fija SplitLayout. */
    public int gx, gy, gw, gh;
    public int px, py, pw, ph;
    /** Escala con la que se dibujan sus pantallas para que quepan en su region (1 = tamano normal). */
    public float screenScale = 1f;
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

    public static final class HudState {
        public int lastHealth, displayHealth, toolHighlightTimer;
        public long lastHealthTime, healthBlinkTime;
        public net.minecraft.world.item.ItemStack lastToolHighlight = net.minecraft.world.item.ItemStack.EMPTY;
    }

    /** Lo que SecondaryTick recuerda de un tick al siguiente. */
    public static final class InputState {
        public boolean prevAttack, prevUse, prevLb, prevRb;
        public boolean sprintLatch, sneakLatch, comboLatch;
        /** Sprint conmutado: lo lee KeyMappingGuardMixin cuando LocalPlayer.aiStep pregunta por keySprint. */
        public volatile boolean sprintHeld;
        public int deadTicks;
        public EnumSet<PadAction> prevDown = EnumSet.noneOf(PadAction.class);
        public EnumSet<PadAction> pressedNow = EnumSet.noneOf(PadAction.class);
    }
}
