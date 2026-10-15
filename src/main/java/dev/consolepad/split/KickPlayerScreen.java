package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * "Sacar a un jugador" desde el menu de pausa de P1: una fila por cada jugador extra que esta dentro (con su nombre y color). Pulsarla lo saca de la partida
 * (su inventario se guarda, como al salir el solo). Sirve sobre todo para quien se quedo sin mando y no puede salir por si mismo.
 */
public final class KickPlayerScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final List<Button> buttons = new ArrayList<>();
    private Button back;

    public KickPlayerScreen(Screen parent) {
        super(0, Component.literal("Sacar a un jugador"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        buttons.clear();
        Minecraft mc = Minecraft.getInstance();
        int rows = 0;
        List<Integer> slots = new ArrayList<>();
        for (int s = 1; s <= SecondaryPlayers.MAX_EXTRA; s++) {
            if (SecondaryPlayers.isActive(s)) slots.add(s);
        }
        rows = slots.size() + 1;
        int i = 0;
        for (int s : slots) {
            final int slotToKick = s;
            Component label = Component.literal("Sacar a ").append(PlayerLabels.styled(s, PlayerLabels.labelOf(s))).append(" (P" + (s + 1) + ")");
            buttons.add(addRow(i++, rows, label, b -> {
                SecondaryPlayers.leave(mc, slotToKick);
                this.onClose();
            }));
        }
        back = addRow(i, rows, Component.literal("Atrás"), b -> this.onClose());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    /** Pruebas: la fila del primer jugador extra de la lista (null si no hay). */
    public Button firstKick() {
        return buttons.isEmpty() ? null : buttons.get(0);
    }

    public int rowCount() {
        return buttons.size();
    }

    public int[] firstKickCenter() {
        return buttons.isEmpty() ? new int[] {0, 0} : center(buttons.get(0));
    }

    public int[] backCenter() {
        return center(back);
    }
}
