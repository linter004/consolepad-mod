package dev.consolepad;

import com.mojang.logging.LogUtils;
import com.mojang.brigadier.Command;
import dev.consolepad.client.PadDriver;
import dev.consolepad.split.AutoTest;
import dev.consolepad.split.ContextSwitcher;
import dev.consolepad.split.SecondaryPlayers;
import dev.consolepad.split.SecondaryTick;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

@Mod(value = ConsolePad.MODID, dist = Dist.CLIENT)
public class ConsolePad {
    public static final String MODID = "consolepad";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ConsolePad(IEventBus modBus, ModContainer container) {
        PadDriver driver = new PadDriver();
        AutoTest.init(driver.pads());
        NeoForge.EVENT_BUS.addListener(driver::onFrame);

        // Fase 3, paso 1: jugador local extra. Atajo de mando (Start+Back en el slot 1) en PadDriver; comando para probar con un solo mando.
        NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e) -> {
            SecondaryPlayers.tick();
            SecondaryTick.tick(driver.pads().slot(1));
            AutoTest.tick();
        });
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre e) -> ContextSwitcher.watchdog(SecondaryPlayers.context()));
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Post e) -> AutoTest.frameEnd());
        NeoForge.EVENT_BUS.addListener((RegisterClientCommandsEvent e) -> e.getDispatcher().register(
                Commands.literal("consolepad").then(Commands.literal("join2").executes(ctx -> {
                    SecondaryPlayers.join(Minecraft.getInstance());
                    return Command.SINGLE_SUCCESS;
                }))));
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e) -> {
            Minecraft mc = Minecraft.getInstance();
            String name = e.getEntity().getName().getString();
            if (!name.equals(SecondaryPlayers.secondaryName(mc))) return;
            // Hilo del servidor: lee Minecraft.player en el hilo principal, donde se usa.
            mc.execute(() -> LOGGER.info("Jugador 2 '{}' dentro del servidor. Minecraft.player sigue siendo '{}' (P1: {})",
                    name, mc.player == null ? "null" : mc.player.getName().getString(),
                    mc.player != null && !mc.player.getName().getString().equals(name)));
        });
        LOGGER.info("ConsolePad cargado");
    }
}
