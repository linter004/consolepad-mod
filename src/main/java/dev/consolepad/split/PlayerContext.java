package dev.consolepad.split;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

/** Estado de cliente de un jugador local extra: lo que vanilla guarda en campos globales de Minecraft. */
public final class PlayerContext {
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

    public static final class HudState {
        public int lastHealth, displayHealth, toolHighlightTimer;
        public long lastHealthTime, healthBlinkTime;
        public net.minecraft.world.item.ItemStack lastToolHighlight = net.minecraft.world.item.ItemStack.EMPTY;
    }
    /** Renderer latente: recibe los avisos de su ClientLevel sin tocar el de P1. Se sustituye en el paso 4. */
    public final LevelRenderer renderer;

    PlayerContext(LevelRenderer renderer) {
        this.renderer = renderer;
    }
}
