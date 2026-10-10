package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.mixin.GameRendererAccessor;
import dev.consolepad.mixin.MinecraftInvoker;
import dev.consolepad.mixin.ParticleEngineAccessor;
import dev.consolepad.mixin.OptionsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;

/**
 * Cambia temporalmente player/level/gameMode/cameraEntity de Minecraft al contexto de un jugador extra.
 * Solo en el hilo principal. P1 se restaura siempre (finally) y se verifica despues.
 */
public final class ContextSwitcher {
    private static volatile boolean active;
    private static long runs;
    private static long violations;

    private ContextSwitcher() {}

    /** True mientras se procesa un paquete de un jugador extra (los mixins "guarda" lo consultan). */
    public static boolean isSecondaryActive() {
        return active;
    }

    public static LevelRenderer rendererOr(Minecraft mc, LevelRenderer original) {
        PlayerContext c = current; // los hilos de mallas tambien llaman: la variable se lee una vez
        return active && c != null ? c.renderer : original;
    }

    /** Mano en primera persona del jugador cuyo contexto esta activo (la de GameRenderer si no hay contexto extra). */
    public static net.minecraft.client.renderer.ItemInHandRenderer handOr(net.minecraft.client.renderer.ItemInHandRenderer original) {
        PlayerContext c = current;
        return active && c != null && c.hand != null ? c.hand : original;
    }

    /** Camara del jugador cuyo contexto esta activo (la mainCamera de GameRenderer es la de P1): cada uno tiene su posicion, rotacion y altura de ojos suavizada. */
    public static net.minecraft.client.Camera cameraOr(net.minecraft.client.Camera original) {
        PlayerContext c = current; // un hilo de mallas (SectionRenderDispatcher) llama a getMainCamera mientras el hilo de render cambia de contexto
        return active && c != null && !SplitView.legacyCamera && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread() ? c.camera : original;
    }

    /** Mapa de luz del jugador activo (efectos propios como vision nocturna u oscuridad). */
    public static net.minecraft.client.renderer.LightTexture lightOr(net.minecraft.client.renderer.LightTexture original) {
        PlayerContext c = current;
        return active && c != null ? c.lightTexture(Minecraft.getInstance()) : original;
    }

    private static volatile PlayerContext current;

    /** Contexto activo (null fuera de un contexto extra). */
    public static PlayerContext current() {
        return active ? current : null;
    }

    public static void run(PlayerContext ctx, Runnable body) {
        Minecraft mc = Minecraft.getInstance();
        if (active) { // anidado (p. ej. paquetes dentro de un bundle): ya estamos en el contexto
            body.run();
            return;
        }
        LocalPlayer p1Player = mc.player;
        ClientLevel p1Level = mc.level;
        MultiPlayerGameMode p1GameMode = mc.gameMode;
        Entity p1Camera = mc.cameraEntity;
        HitResult p1Hit = mc.hitResult;
        Entity p1Cross = mc.crosshairPickEntity;
        MinecraftInvoker inv = (MinecraftInvoker) mc;
        int p1Miss = inv.consolepad$getMissTime();
        int p1Delay = inv.consolepad$getRightClickDelay();
        GameRendererAccessor gr = (GameRendererAccessor) mc.gameRenderer;
        net.minecraft.client.gui.screens.Screen p1Screen = mc.screen;
        OptionsAccessor opts = (OptionsAccessor) mc.options;
        net.minecraft.client.CameraType p1CameraType = opts.consolepad$getCameraType();
        float p1Fov = gr.consolepad$getFov();
        float p1OldFov = gr.consolepad$getOldFov();

        ParticleEngineAccessor particles = (ParticleEngineAccessor) mc.particleEngine;
        ClientLevel p1ParticleLevel = particles.consolepad$getLevel();
        active = true;
        current = ctx;
        particles.consolepad$setLevel(ctx.level); // las particulas que cree este jugador nacen en su mundo
        mc.player = ctx.player;
        mc.level = ctx.level;
        mc.gameMode = ctx.gameMode;
        mc.cameraEntity = ctx.cameraEntity;
        mc.hitResult = ctx.hitResult;
        mc.crosshairPickEntity = ctx.crosshairPickEntity;
        inv.consolepad$setMissTime(ctx.missTime);
        inv.consolepad$setRightClickDelay(ctx.rightClickDelay);
        mc.screen = ctx.screen;
        opts.consolepad$setCameraType(ctx.cameraType);
        gr.consolepad$setFov(ctx.fov);
        gr.consolepad$setOldFov(ctx.oldFov);
        try {
            body.run();
        } finally {
            // Lo que el manejador dejo (el login crea nivel, jugador y gameMode de P2) se guarda en su contexto.
            ctx.player = mc.player;
            ctx.level = mc.level;
            ctx.gameMode = mc.gameMode;
            ctx.cameraEntity = mc.cameraEntity;
            ctx.hitResult = mc.hitResult;
            ctx.crosshairPickEntity = mc.crosshairPickEntity;
            ctx.missTime = inv.consolepad$getMissTime();
            ctx.rightClickDelay = inv.consolepad$getRightClickDelay();
            ctx.screen = mc.screen;
            mc.screen = p1Screen;
            ctx.cameraType = opts.consolepad$getCameraType();
            opts.consolepad$setCameraType(p1CameraType);
            ctx.fov = gr.consolepad$getFov();
            ctx.oldFov = gr.consolepad$getOldFov();
            gr.consolepad$setFov(p1Fov);
            gr.consolepad$setOldFov(p1OldFov);
            particles.consolepad$setLevel(p1ParticleLevel);
            mc.hitResult = p1Hit;
            mc.crosshairPickEntity = p1Cross;
            inv.consolepad$setMissTime(p1Miss);
            inv.consolepad$setRightClickDelay(p1Delay);
            mc.player = p1Player;
            mc.level = p1Level;
            mc.gameMode = p1GameMode;
            mc.cameraEntity = p1Camera;
            active = false;
            current = null;
            runs++;
            verify(mc, p1Player, p1Level, p1GameMode, p1Camera, p1Hit, p1Cross, p1Miss, p1Delay, inv);
        }
    }

    private static void verify(Minecraft mc, LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, Entity camera,
                               HitResult p1Hit, Entity p1Cross, int p1Miss, int p1Delay, MinecraftInvoker inv) {
        boolean ok = mc.player == player && mc.level == level && mc.gameMode == gameMode && mc.cameraEntity == camera
                && mc.hitResult == p1Hit && mc.crosshairPickEntity == p1Cross
                && inv.consolepad$getMissTime() == p1Miss && inv.consolepad$getRightClickDelay() == p1Delay;
        if (!ok) {
            violations++;
            ConsolePad.LOGGER.error("[split] P1 SIN RESTAURAR tras un paquete de P2: player={} level={} gameMode={} camera={}",
                    mc.player == player, mc.level == level, mc.gameMode == gameMode, mc.cameraEntity == camera);
        } else if (runs <= 3 || runs % 500 == 0) {
            ConsolePad.LOGGER.info("[split] paquete de P2 n.º {} procesado: player/level/gameMode de P1 restaurados (fallos: {})",
                    runs, violations);
        }
    }

    /**
     * Vigilante (cada tick y cada frame): fuera de un contexto, Minecraft no debe apuntar al jugador o nivel de P2.
     * Detecta accesos que dejaron a P1 sin restaurar por cualquier otro camino.
     */
    public static void watchdog(PlayerContext p2) {
        if (p2 == null || active) return;
        Minecraft mc = Minecraft.getInstance();
        if ((p2.player != null && mc.player == p2.player) || (p2.level != null && mc.level == p2.level)
                || (p2.gameMode != null && mc.gameMode == p2.gameMode)) {
            violations++;
            ConsolePad.LOGGER.error("[split] vigilante: Minecraft apunta al contexto de P2 fuera de un contexto activo");
        }
    }

    public static long violations() {
        return violations;
    }

    public static long runs() {
        return runs;
    }
}
