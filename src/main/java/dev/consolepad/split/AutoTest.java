package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Ayuda de pruebas, solo si se arranca con la variable de entorno CONSOLEPAD_AUTOTEST=<nombre del mundo>: abre ese
 * mundo, mete a P2 y le hace andar, saltar y romper un bloque con entrada simulada, leyendo su posicion en el
 * servidor. Despues deja el estado en el log cada 30 s durante 2 minutos. Sin la variable no hace nada.
 */
public final class AutoTest {
    private static final String WORLD = System.getenv("CONSOLEPAD_AUTOTEST");
    private static final int REPORT_EVERY = 20 * 30;
    private static final int STABILITY_TICKS = 20 * 120;

    private static final int WALK_TICKS = 40;
    private static final int JUMP_TICKS = 30;
    private static final int BREAK_TICKS = 200;

    private static int state;
    private static int ticks;
    private static Vec3 p1Start;
    private static Vec3 p2Start;
    private static double p2MaxY;
    private static BlockPos target;
    private static boolean targetWasAir;

    private AutoTest() {}

    private static ServerPlayer serverP2(Minecraft mc) {
        return mc.getSingleplayerServer() == null ? null
                : mc.getSingleplayerServer().getPlayerList().getPlayerByName(SecondaryPlayers.secondaryName(mc));
    }

    private static String fmt(Vec3 v) {
        return v == null ? "null" : String.format("(%.2f, %.2f, %.2f)", v.x, v.y, v.z);
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
            case 2 -> { // P2 asentado: nivel, jugador y chunks cargados
                PlayerContext c = SecondaryPlayers.context();
                if (ticks > 100 && c != null && c.player != null && c.level != null
                        && c.level.hasChunkAt(c.player.blockPosition()) && serverP2(mc) != null) {
                    p1Start = mc.player.position();
                    p2Start = serverP2(mc).position();
                    ConsolePad.LOGGER.info("[autotest] PASO 3 andar: P1 {} P2(servidor) {}", fmt(p1Start), fmt(p2Start));
                    SecondaryTick.scriptActive = true;
                    SecondaryTick.scriptForward = 1f;
                    next();
                }
            }
            case 3 -> {
                if (ticks >= WALK_TICKS) {
                    SecondaryTick.scriptForward = 0f;
                    Vec3 p2 = serverP2(mc).position();
                    ConsolePad.LOGGER.info("[autotest] andar: P2 {} -> {} (desplazamiento horizontal {} bloques); P1 {} -> {} (debe ser igual)",
                            fmt(p2Start), fmt(p2), String.format("%.2f", Math.hypot(p2.x - p2Start.x, p2.z - p2Start.z)),
                            fmt(p1Start), fmt(mc.player.position()));
                    p2Start = p2;
                    p2MaxY = p2.y;
                    next();
                }
            }
            case 4 -> { // saltar: A pulsada los primeros 5 ticks
                SecondaryTick.scriptJump = ticks <= 5;
                p2MaxY = Math.max(p2MaxY, serverP2(mc).position().y);
                if (ticks >= JUMP_TICKS) {
                    SecondaryTick.scriptJump = false;
                    ConsolePad.LOGGER.info("[autotest] saltar: P2 y {} -> maxima {} (subida {} bloques)",
                            String.format("%.2f", p2Start.y), String.format("%.2f", p2MaxY), String.format("%.2f", p2MaxY - p2Start.y));
                    next();
                }
            }
            case 5 -> { // romper el bloque de debajo: mirar hacia abajo y atacar
                PlayerContext c = SecondaryPlayers.context();
                if (ticks == 1) {
                    target = serverP2(mc).blockPosition().below();
                    targetWasAir = serverP2(mc).level().getBlockState(target).isAir();
                    c.player.setXRot(90f);
                    SecondaryTick.scriptAttack = true;
                    ConsolePad.LOGGER.info("[autotest] romper: bloque {} = {} (mirando hacia abajo, atacando)", target.toShortString(),
                            serverP2(mc).level().getBlockState(target).getBlock());
                }
                boolean airNow = serverP2(mc).level().getBlockState(target).isAir();
                if ((airNow && !targetWasAir) || ticks >= BREAK_TICKS) {
                    SecondaryTick.scriptAttack = false;
                    SecondaryTick.scriptActive = false;
                    ConsolePad.LOGGER.info("[autotest] romper: tras {} ticks el bloque {} es ahora {} (roto: {})", ticks, target.toShortString(),
                            serverP2(mc).level().getBlockState(target).getBlock(), airNow && !targetWasAir);
                    next();
                }
            }
            case 6 -> {
                if (ticks % REPORT_EVERY == 0 || ticks == STABILITY_TICKS) {
                    PlayerContext c = SecondaryPlayers.context();
                    ConsolePad.LOGGER.info("[autotest] estabilidad +{} s: P2 conectado={} nivel={} jugador={}; fallos de restauracion={}; P1={} P2(servidor)={}",
                            ticks / 20, SecondaryPlayers.isActive(), c != null && c.level != null, c != null && c.player != null,
                            ContextSwitcher.violations(), fmt(mc.player.position()),
                            serverP2(mc) == null ? "null" : fmt(serverP2(mc).position()));
                }
                if (ticks == STABILITY_TICKS) {
                    ConsolePad.LOGGER.info("[autotest] terminado");
                    next();
                }
            }
            default -> { }
        }
    }

    private static void next() {
        state++;
        ticks = 0;
    }
}
