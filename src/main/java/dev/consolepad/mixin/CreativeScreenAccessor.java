package dev.consolepad.mixin;

import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import net.neoforged.neoforge.client.gui.CreativeTabsScreenPage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Pestanas del inventario creativo (para LB/RB y la flecha de la categoria actual). */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {
    @Accessor("selectedTab")
    static CreativeModeTab consolepad$selectedTab() {
        throw new AssertionError();
    }

    @Accessor("selectedTab")
    static void consolepad$setSelectedTab(CreativeModeTab tab) {
        throw new AssertionError();
    }

    /** El contenedor estatico de la lista de objetos del creativo: lo comparten todas las pantallas, asi que ContextSwitcher lo cambia por jugador. */
    @Accessor("CONTAINER")
    static net.minecraft.world.SimpleContainer consolepad$container() {
        throw new AssertionError();
    }

    @Accessor("currentPage")
    CreativeTabsScreenPage consolepad$currentPage();

    @Accessor("pages")
    java.util.List<CreativeTabsScreenPage> consolepad$pages();

    @Accessor("scrollOffs")
    float consolepad$scrollOffs();

    @Accessor("searchBox")
    net.minecraft.client.gui.components.EditBox consolepad$searchBox();

    @Invoker("selectTab")
    void consolepad$selectTab(CreativeModeTab tab);

    @Invoker("getTabX")
    int consolepad$tabX(CreativeModeTab tab);

    @Invoker("getTabY")
    int consolepad$tabY(CreativeModeTab tab);
}
