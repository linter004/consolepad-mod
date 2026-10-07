package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

/**
 * Ayuda de pruebas, solo si se arranca con la variable de entorno CONSOLEPAD_AUTOTEST=<nombre del mundo>.
 * Diagnostico del paso 3: abre el mundo, mete a P2, lo teletransporta a 24 bloques de P1, deja a P1 sin entrada
 * 30 s (comprobando que no se mueve) y hace andar a P2 registrando cada tick posiciones, colisiones, entrada y bloques.
 * Sin la variable no hace nada.
 * Importante: no tocar la ventana del juego mientras corre; cualquier entrada humana (por ejemplo /tp) contamina las medidas.
 */
public final class AutoTest {
    private static final String WORLD = System.getenv("CONSOLEPAD_AUTOTEST");
    private static final int IDLE_TICKS = 20 * 30;
    private static final int WALK_TICKS = 60;

    private static int state;
    private static int ticks;
    private static Vec3 p1Start;
    private static double p1MaxDeviation;
    private static boolean teleported;
    private static Vec3 target;

    private AutoTest() {}

    private static ServerPlayer serverP2(Minecraft mc) {
        return mc.getSingleplayerServer() == null ? null
                : mc.getSingleplayerServer().getPlayerList().getPlayerByName(SecondaryPlayers.secondaryName(mc));
    }

    private static String fmt(Vec3 v) {
        return v == null ? "null" : String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
    }

    private static String blockAt(ClientLevel level, BlockPos pos) {
        return level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().getPath();
    }

    public static void tick() {
        if (WORLD == null) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        switch (state) {
            case 0 -> {
                if (mc.screen instanceof TitleScreen && ticks > 60) {
                    ConsolePad.LOGGER.info("[autotest] abriendo el mundo '{}'", WORLD);
                    next();
                    mc.createWorldOpenFlows().openWorld(WORLD, () -> ConsolePad.LOGGER.error("[autotest] no se pudo abrir el mundo"));
                }
            }
            case 1 -> {
                if (mc.player != null && mc.level != null && mc.screen == null && ticks > 200) {
                    ConsolePad.LOGGER.info("[autotest] mundo cargado; metiendo a P2");
                    SecondaryPlayers.join(mc);
                    next();
                }
            }
            case 2 -> teleportAway(mc);
            case 3 -> idle(mc);
            case 4 -> walk(mc);
            case 5 -> farActions(mc);
            case 6 -> adjacent(mc);
            default -> { }
        }
    }

    /** P2 entra, se asienta y se teletransporta a 24 bloques de P1, en la superficie. */
    private static void teleportAway(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        if (c == null || c.player == null || c.level == null || p2 == null || ticks < 100) return;
        if (!teleported) {
            Vec3 p1 = mc.player.position();
            ServerLevel level = (ServerLevel) p2.level();
            ConsolePad.LOGGER.info("[autotest] P1 {} (onGround={}) P2(servidor) {}; teletransportando a P2 a 24 bloques de P1",
                    fmt(p1), mc.player.onGround(), fmt(p2.position()));
            mc.getSingleplayerServer().execute(() -> {
                int x = (int) Math.floor(p1.x) + 24;
                int z = (int) Math.floor(p1.z);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
                target = new Vec3(x + 0.5, y, z + 0.5);
                p2.teleportTo(level, target.x, target.y, target.z, 0f, 0f);
            });
            teleported = true;
            ticks = 0;
            return;
        }
        // espera a que P2 este en el destino, con chunk cargado en su cliente y en el suelo
        if (target != null && ticks > 100 && p2.position().distanceTo(target) < 3.0 && p2.onGround()
                && c.level.hasChunkAt(c.player.blockPosition())) {
            p1Start = mc.player.position();
            ConsolePad.LOGGER.info("[autotest] P2 en destino {} (servidor) / {} (cliente de P2). Fase 1: 30 s con P1 y P2 sin entrada; P1 inicial {}",
                    fmt(p2.position()), fmt(c.player.position()), fmt(p1Start));
            next();
        }
    }

    /** 30 s sin entrada: la posicion de P1 no debe cambiar (salvo gravedad). */
    private static void idle(Minecraft mc) {
        double dev = mc.player.position().distanceTo(p1Start);
        p1MaxDeviation = Math.max(p1MaxDeviation, dev);
        if (ticks % 20 == 0) {
            PlayerContext c = SecondaryPlayers.context();
            ConsolePad.LOGGER.info("[diag] idle +{} s: P1 {} (onGround={}, desviacion {}) P2 servidor {} cliente {}",
                    ticks / 20, fmt(mc.player.position()), mc.player.onGround(), String.format("%.3f", dev),
                    fmt(serverP2(mc).position()), fmt(c.player.position()));
        }
        if (ticks >= IDLE_TICKS) {
            ConsolePad.LOGGER.info("[autotest] fase 1 terminada: desviacion maxima de P1 en 30 s = {} bloques (P1 {} -> {})",
                    String.format("%.3f", p1MaxDeviation), fmt(p1Start), fmt(mc.player.position()));
            SecondaryTick.scriptActive = true;
            SecondaryTick.scriptForward = 1f;
            next();
            ConsolePad.LOGGER.info("[autotest] Fase 2: P2 anda {} ticks hacia +z con registro por tick", WALK_TICKS);
        }
    }

    private static void walk(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        LocalPlayer p = c.player;
        ClientLevel level = c.level;
        Direction dir = p.getDirection();
        BlockPos feetAhead = p.blockPosition().relative(dir);
        String input = p.input instanceof PadInput in
                ? String.format("fwd=%.2f left=%.2f", in.forwardImpulse, in.leftImpulse) : "sin PadInput";
        ConsolePad.LOGGER.info("[diag] andar t={} P2 cliente {} servidor {} colision={} suelo={} vel={} entrada[{}] delante({}): pies={} cabeza={} suelo={} | P1 {} (desv {})",
                ticks, fmt(p.position()), fmt(serverP2(mc).position()), p.horizontalCollision, p.onGround(), fmt(p.getDeltaMovement()),
                input, dir.getName(), blockAt(level, feetAhead), blockAt(level, feetAhead.above()), blockAt(level, feetAhead.below()),
                fmt(mc.player.position()), String.format("%.3f", mc.player.position().distanceTo(p1Start)));
        if (ticks >= WALK_TICKS) {
            SecondaryTick.scriptForward = 0f;
            SecondaryTick.scriptActive = false;
            ConsolePad.LOGGER.info("[autotest] fase 2 terminada: P2 cliente {} servidor {}; P1 {} (desviacion total desde el inicio de la fase 1: {})",
                    fmt(p.position()), fmt(serverP2(mc).position()), fmt(mc.player.position()),
                    String.format("%.3f", mc.player.position().distanceTo(p1Start)));
            next();
            p1Start = mc.player.position();
            p1MaxDeviation = 0; // la fase 3 mide solo su propio tramo (antes arrastraba el maximo de la fase 1)
            ConsolePad.LOGGER.info("[autotest] Fase 3: P2 lejos de P1 salta y rompe el bloque de debajo; se vigila a P1");
        }
    }

    private static BlockPos breakTarget;
    private static boolean breakWasAir;

    /** P2 lejos de P1: saltar y romper un bloque no debe mover a P1 (descarta fuga de contexto en esas acciones). */
    private static void farActions(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        ServerPlayer p2 = serverP2(mc);
        SecondaryTick.scriptActive = true;
        double dev = mc.player.position().distanceTo(p1Start);
        p1MaxDeviation = Math.max(p1MaxDeviation, dev);
        if (ticks <= 5) SecondaryTick.scriptJump = true;
        else if (ticks == 6) SecondaryTick.scriptJump = false;
        if (ticks == 40) {
            breakTarget = p2.blockPosition().below();
            breakWasAir = p2.level().getBlockState(breakTarget).isAir();
            c.player.setXRot(90f);
            SecondaryTick.scriptAttack = true;
        }
        if (ticks > 40) {
            boolean air = p2.level().getBlockState(breakTarget).isAir();
            if ((air && !breakWasAir) || ticks >= 240) {
                SecondaryTick.scriptAttack = false;
                SecondaryTick.scriptActive = false;
                ConsolePad.LOGGER.info("[autotest] fase 3 terminada: bloque {} roto={}; desviacion maxima de P1 durante saltar y romper = {} (P1 {})",
                        breakTarget.toShortString(), air && !breakWasAir, String.format("%.3f", p1MaxDeviation), fmt(mc.player.position()));
                next();
                teleportNextToP1(mc);
            }
        }
    }

    private static void teleportNextToP1(Minecraft mc) {
        ServerPlayer p2 = serverP2(mc);
        Vec3 p1 = mc.player.position();
        p1Start = p1;
        ServerLevel level = (ServerLevel) p2.level();
        mc.getSingleplayerServer().execute(() -> p2.teleportTo(level, p1.x + 0.6, p1.y, p1.z, 0f, 0f));
        ConsolePad.LOGGER.info("[autotest] Fase 4: P2 teletransportado pegado a P1 ({}), ambos sin entrada", fmt(p1));
    }

    /** P2 pegado a P1, sin entrada: si P1 se mueve aqui es empuje de colisiones, no entrada ni fuga. */
    private static void adjacent(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context();
        if (ticks % 10 == 0) {
            ConsolePad.LOGGER.info("[diag] pegados t={}: P1 {} (desv {}) P2 servidor {} cliente {}", ticks, fmt(mc.player.position()),
                    String.format("%.3f", mc.player.position().distanceTo(p1Start)), fmt(serverP2(mc).position()), fmt(c.player.position()));
        }
        if (ticks >= 100) {
            ConsolePad.LOGGER.info("[autotest] fase 4 terminada: P1 {} -> {} (desviacion {}); P2 {}", fmt(p1Start), fmt(mc.player.position()),
                    String.format("%.3f", mc.player.position().distanceTo(p1Start)), fmt(serverP2(mc).position()));
            ConsolePad.LOGGER.info("[autotest] terminado");
            next();
        }
    }

    private static void next() {
        state++;
        ticks = 0;
    }
}
