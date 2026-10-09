package dev.consolepad.mixin;

import net.minecraft.client.gui.components.StateSwitchingButton;
import net.minecraft.client.gui.screens.recipebook.RecipeBookPage;
import net.minecraft.client.gui.screens.recipebook.RecipeButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(RecipeBookPage.class)
public interface RecipeBookPageAccessor {
    @Accessor("buttons")
    List<RecipeButton> consolepad$buttons();

    @Accessor("forwardButton")
    StateSwitchingButton consolepad$forward();

    @Accessor("backButton")
    StateSwitchingButton consolepad$back();

    @Accessor("totalPages")
    int consolepad$totalPages();

    @Accessor("currentPage")
    int consolepad$currentPage();
}
