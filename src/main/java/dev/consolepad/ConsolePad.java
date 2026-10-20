package dev.consolepad;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;
import dev.consolepad.client.PadDriver;
import dev.consolepad.split.AutoTest;
import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SecondaryPlayers;
import dev.consolepad.split.SecondaryTick;
import dev.consolepad.split.SplitDiag;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@Mod(value = ConsolePad.MODID, dist = Dist.CLIENT)
public class ConsolePad {
    /** Ajustes y mandos de los 4 slots (para los mixins). */
    public static dev.consolepad.pad.PadManager PADS;
    public static final String MODID = "consolepad";
    /** La bienvenida sale una sola vez por sesion de juego (y solo si no se marco "no volver a mostrar"). */
    public static boolean welcomeShown;
    public static final Logger LOGGER = LogUtils.getLogger();

    public ConsolePad(IEventBus modBus, ModContainer container) {
        PadDriver driver = new PadDriver();
        PADS = driver.pads();
        AutoTest.init(driver.pads());
        NeoForge.EVENT_BUS.addListener(driver::onFrame);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.PlayerLabels::onNameFormat);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.RumbleEvents::onBreak);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.RumbleEvents::onAttack);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.RumbleEvents::onUseFinish);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.RumbleEvents::onExplosion);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.RumbleEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(dev.consolepad.split.PlayerLabels::onTabName);

        // Fase 3, paso 1: jugador local extra. Atajo de mando (Start+Back en el slot 1) en PadDriver; comando para probar con un solo mando.
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            SecondaryPlayers.tick();
            for (var extra : SecondaryPlayers.contexts()) SecondaryTick.tick(driver.pads().slot(extra.slotIndex), extra);
            AutoTest.tick();
        });
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut e) -> {
            SecondaryPlayers.shutdown();
            dev.consolepad.pad.Rumble.stopAll();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> {
            for (var extra : SecondaryPlayers.contexts()) ContextSwitcher.watchdog(extra);
            dev.consolepad.split.AdaptivePerf.frame();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> AutoTest.frameEnd());
        NeoForge.EVENT_BUS.addListener((RenderLevelStageEvent e) -> {
            SplitDiag.onStage(e);
            if (e.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) dev.consolepad.split.PerfLimits.applyEntityScale(net.minecraft.client.Minecraft.getInstance());
        });
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> {
            var root = Commands.literal("consolepad");
            for (int slot = 1; slot <= SecondaryPlayers.MAX_EXTRA; slot++) {
                final int s = slot;
                root.then(Commands.literal("join" + (slot + 1)).executes(ctx -> {
                    SecondaryPlayers.join(Minecraft.getInstance(), s);
                    return Command.SINGLE_SUCCESS;
                }));
                root.then(Commands.literal("leave" + (slot + 1)).executes(ctx -> {
                    SecondaryPlayers.leave(Minecraft.getInstance(), s);
                    return Command.SINGLE_SUCCESS;
                }));
            }
            e.getDispatcher().register(root);
        });
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            Minecraft mc = Minecraft.getInstance();
            String name = e.getEntity().getName().getString();
            if (!SecondaryPlayers.isSecondaryName(mc, name)) return;
            // Hilo del servidor: lee Minecraft.player en el hilo principal, donde se usa.
            mc.execute(() -> LOGGER.info("Jugador 2 '{}' dentro del servidor. Minecraft.player sigue siendo '{}' (P1: {})",
                    name, mc.player == null ? "null" : mc.player.getName().getString(),
                    mc.player != null && !mc.player.getName().getString().equals(name)));
        });
        // Bienvenida de la primera vez, sobre el titulo (nunca durante el autotest ni la generacion de capturas).
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            Minecraft mc = Minecraft.getInstance();
            dev.consolepad.split.WorldEntry.tick(mc);
            dev.consolepad.split.RumbleTick.tick(mc);
            dev.consolepad.split.PaperDoll.tick(mc);
            dev.consolepad.split.CleanCheck.tick(mc);
            if (mc.screen instanceof net.minecraft.client.gui.screens.TitleScreen && !welcomeShown && !AutoTest.running()
                    && !dev.consolepad.split.ShotsTest.ENABLED && !dev.consolepad.split.SplitSettings.welcomeSeen()) {
                welcomeShown = true;
                mc.setScreen(new dev.consolepad.split.WelcomeScreen(mc.screen));
            }
        });
        // Boton "Mandos" en el titulo: asignar mandos a jugadores antes de entrar en un mundo.
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.Init.Post e) -> {
            if (e.getScreen() instanceof net.minecraft.client.gui.screens.TitleScreen title) {
                e.addListener(net.minecraft.client.gui.components.Button.builder(dev.consolepad.split.I18n.lit("Mandos"),
                        b -> Minecraft.getInstance().setScreen(new dev.consolepad.split.PadSetupScreen(title))).bounds(4, 4, 56, 20).build());
            }
        });
        dev.consolepad.split.Compat.logOnce();
        LOGGER.info("ConsolePad cargado");
    }
}
