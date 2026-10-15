package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * "¿Cuántos jugadores sois?" al entrar en un mundo con varios mandos conectados: un boton por cantidad (hasta los mandos que hay) y "No volver a preguntar".
 * Elegir n mete a los n - 1 jugadores extra de golpe (WorldEntry los conecta uno tras otro). Se puede cerrar con B para jugar solo.
 */
public final class PlayerCountScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final Button[] counts = new Button[4];

    public PlayerCountScreen(Screen parent) {
        super(0, Component.literal("¿Cuántos jugadores sois?"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int pads = WorldEntry.connectedPlayers();
        int rows = 5;
        for (int n = 1; n <= 4; n++) {
            final int count = n;
            String text = n == 1 ? "1 jugador (solo yo)" : n + " jugadores";
            if (n == pads) text += "  (mandos conectados)";
            counts[n - 1] = addRow(n - 1, rows, Component.literal(text), b -> choose(count));
            counts[n - 1].active = n <= Math.max(1, pads);
        }
        addRow(4, rows, Component.literal("No volver a preguntar"), b -> {
            SplitSettings.setAskPlayers(false);
            this.onClose();
        });
    }

    /** Elige cuantos jugadores son en total (pruebas: tambien se llama directamente). */
    public void choose(int n) {
        WorldEntry.requestPlayers(n);
        this.onClose();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    public Button count(int n) {
        return counts[n - 1];
    }
}
