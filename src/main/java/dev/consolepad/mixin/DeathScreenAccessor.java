package dev.consolepad.mixin;

import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** La causa de la muerte que la DeathScreen de vanilla recibio, para repetirla en la pantalla de muerte de un jugador extra. */
@Mixin(DeathScreen.class)
public interface DeathScreenAccessor {
    @Accessor("causeOfDeath")
    Component consolepad$getCauseOfDeath();

    @Accessor("hardcore")
    boolean consolepad$isHardcore();
}
