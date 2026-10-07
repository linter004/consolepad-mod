package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.mixin.MinecraftInvoker;
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
    private static boolean active;
    private static long runs;
    private static long violations;

    private ContextSwitcher() {}

    /** True mientras se procesa un paquete de un jugador extra (los mixins "guarda" lo consultan). */
    public static boolean isSecondaryActive() {
        return active;
    }

    public static LevelRenderer rendererOr(Minecraft mc, LevelRenderer original) {
        return active && current != null ? current.renderer : original;
    }

    private static PlayerContext current;

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

        active = true;
        current = ctx;
        mc.player = ctx.player;
        mc.level = ctx.level;
        mc.gameMode = ctx.gameMode;
        mc.cameraEntity = ctx.cameraEntity;
        mc.hitResult = ctx.hitResult;
        mc.crosshairPickEntity = ctx.crosshairPickEntity;
        inv.consolepad$setMissTime(ctx.missTime);
        inv.consolepad$setRightClickDelay(ctx.rightClickDelay);
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
