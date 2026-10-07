package dev.consolepad.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenInvoker {
    @Invoker("slotClicked")
    void consolepad$slotClicked(Slot slot, int slotId, int mouseButton, ClickType type);
}
