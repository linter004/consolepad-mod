package dev.consolepad.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerInvoker {
    @Invoker("onMove")
    void consolepad$onMove(long window, double x, double y);

    @Invoker("onPress")
    void consolepad$onPress(long window, int button, int action, int modifiers);

    @Invoker("onScroll")
    void consolepad$onScroll(long window, double xOffset, double yOffset);
}
