package dev.consolepad.mixin;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Acciones de jugador privadas de Minecraft y los contadores que cada jugador local necesita por separado. */
@Mixin(Minecraft.class)
public interface MinecraftInvoker {
    @Accessor("missTime")
    int consolepad$getMissTime();

    @Accessor("missTime")
    void consolepad$setMissTime(int value);

    @Accessor("rightClickDelay")
    int consolepad$getRightClickDelay();

    @Accessor("rightClickDelay")
    void consolepad$setRightClickDelay(int value);

    @Invoker("startAttack")
    boolean consolepad$startAttack();

    @Invoker("continueAttack")
    void consolepad$continueAttack(boolean leftClick);

    @Invoker("startUseItem")
    void consolepad$startUseItem();
}
