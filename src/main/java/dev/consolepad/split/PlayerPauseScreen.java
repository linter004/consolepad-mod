package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.GenericMessageScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.network.chat.Component;

/**
 * Menu de pausa de un jugador local (P1 a P4) con la pantalla dividida: no pausa nada (con extras dentro el mundo no se pausa nunca).
 * Reanudar, ajustes del jugador, controles, reparto de la pantalla (global) y salir: P1 guarda y vuelve al titulo (y con el salen todos);
 * un extra sale solo el (el PauseScreen de vanilla desconectaria a P1 y pararia el servidor).
 */
public final class PlayerPauseScreen extends PlayerMenuScreen {
    private Button resume, settingsButton, controls, layout, options, quit;

    public PlayerPauseScreen(int slot) {
        super(slot, Component.literal("Jugador " + (slot + 1)));
    }

    private static Component layoutLabel() {
        return Component.literal("Reparto: " + (SplitSettings.orientation() == SplitSettings.Orientation.SIDE_BY_SIDE ? "lado a lado" : "apilado"));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        int rows = slot == 0 ? 6 : 5;
        int i = 0;
        resume = addRow(i++, rows, Component.literal("Volver a la partida"), b -> this.onClose());
        settingsButton = addRow(i++, rows, Component.literal("Ajustes del jugador"), b -> mc.setScreen(new PlayerSettingsScreen(slot, this)));
        controls = addRow(i++, rows, Component.literal("Controles"), b -> mc.setScreen(new PlayerControlsScreen(slot, this)));
        layout = addRow(i++, rows, layoutLabel(), b -> {
            SplitSettings.toggleOrientation(); // global: se recalcula todo en el siguiente fotograma
            b.setMessage(layoutLabel());
        });
        if (slot == 0) {
            options = addRow(i++, rows, Component.literal("Opciones del juego"), b -> mc.setScreen(new OptionsScreen(this, mc.options)));
            quit = addRow(i, rows, Component.literal("Guardar y salir al título"), b -> {
                b.active = false;
                if (mc.level != null) mc.level.disconnect();
                if (mc.isLocalServer()) mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                else mc.disconnect();
                mc.setScreen(new TitleScreen());
            });
        } else {
            quit = addRow(i, rows, Component.literal("Salir de la partida (P" + (slot + 1) + ")"), b -> SecondaryPlayers.leave(mc, slot));
        }
    }

    @Override
    public boolean isPauseScreen() {
        return slot == 0; // como el de vanilla; con extras dentro Minecraft no pausa de todos modos
    }

    public boolean resumeHovered() {
        return resume.isHovered();
    }

    public int[] resumeCenter() {
        return center(resume);
    }

    public int[] settingsCenter() {
        return center(settingsButton);
    }

    public int[] controlsCenter() {
        return center(controls);
    }

    public int[] layoutCenter() {
        return center(layout);
    }

    public int[] quitCenter() {
        return center(quit);
    }

    public int slot() {
        return slot;
    }
}
