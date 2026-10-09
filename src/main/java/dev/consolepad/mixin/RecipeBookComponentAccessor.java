package dev.consolepad.mixin;

import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeBookTabButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** Estado interno del libro de recetas de vanilla que ConsoleCraft necesita para navegarlo con el mando. */
@Mixin(RecipeBookComponent.class)
public interface RecipeBookComponentAccessor {
    @Accessor("tabButtons")
    List<RecipeBookTabButton> consolepad$tabs();

    @Accessor("selectedTab")
    RecipeBookTabButton consolepad$selectedTab();

    @Accessor("recipeBookPage")
    RecipeBookPage consolepad$page();

    @Accessor("filterButton")
    StateSwitchingButton consolepad$filter();

    @Accessor("menu")
    net.minecraft.world.inventory.RecipeBookMenu<?, ?> consolepad$menu();
}
