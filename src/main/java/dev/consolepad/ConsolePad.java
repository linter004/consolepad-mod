package dev.consolepad;

import com.mojang.logging.LogUtils;
import dev.consolepad.client.PadDriver;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(value = ConsolePad.MODID, dist = Dist.CLIENT)
public class ConsolePad {
    public static final String MODID = "consolepad";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ConsolePad(IEventBus modBus, ModContainer container) {
        PadDriver driver = new PadDriver();
        NeoForge.EVENT_BUS.addListener(driver::onFrame);
        LOGGER.info("ConsolePad cargado");
    }
}
