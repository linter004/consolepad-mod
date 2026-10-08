package dev.consolepad.mixin;

import net.minecraft.client.gui.Gui;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Estado del HUD que es de un jugador y no puede compartirse entre las mitades (parpadeo de vida, nombre del objeto). */
@Mixin(Gui.class)
public interface GuiStateAccessor {
    @Accessor("title")
    net.minecraft.network.chat.Component consolepad$getTitle();

    @Accessor("lastHealth")
    int consolepad$getLastHealth();

    @Accessor("lastHealth")
    void consolepad$setLastHealth(int v);

    @Accessor("displayHealth")
    int consolepad$getDisplayHealth();

    @Accessor("displayHealth")
    void consolepad$setDisplayHealth(int v);

    @Accessor("lastHealthTime")
    long consolepad$getLastHealthTime();

    @Accessor("lastHealthTime")
    void consolepad$setLastHealthTime(long v);

    @Accessor("healthBlinkTime")
    long consolepad$getHealthBlinkTime();

    @Accessor("healthBlinkTime")
    void consolepad$setHealthBlinkTime(long v);

    @Accessor("toolHighlightTimer")
    int consolepad$getToolHighlightTimer();

    @Accessor("toolHighlightTimer")
    void consolepad$setToolHighlightTimer(int v);

    @Accessor("lastToolHighlight")
    ItemStack consolepad$getLastToolHighlight();

    @Accessor("lastToolHighlight")
    void consolepad$setLastToolHighlight(ItemStack v);
}
