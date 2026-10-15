package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Un perfil: usarlo en este jugador, copiarle los ajustes del jugador, o borrarlo (hay que pulsar dos veces). */
public final class ProfileScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final Profiles.Profile profile;
    private boolean deleteArmed;
    private String message = "";
    private Button use, copy, delete;

    public ProfileScreen(int slot, Profiles.Profile profile, Screen parent) {
        super(slot, Component.literal(profile.name));
        this.parent = parent;
        this.profile = profile;
    }

    @Override
    protected void init() {
        int rows = 4;
        use = addRow(0, rows, Component.literal("Usar en el jugador " + (slot + 1)), b -> {
            String err = Profiles.use(slot, profile);
            message = err == null ? "Perfil puesto" + (slot >= 1 ? (SecondaryPlayers.isActive(slot) ? "" : ": entrará con su cuenta") : "") : err;
            if (err == null) this.onClose();
        });
        copy = addRow(1, rows, Component.literal("Guardar mis ajustes en el perfil"), b -> {
            ConsolePad.PADS.slot(slot).settings.profileId = profile.id; // sin cambiar de cuenta: solo se guarda
            Profiles.sync(slot);
            message = "Ajustes guardados";
        });
        delete = addRow(2, rows, Component.literal(deleteArmed ? "¿Seguro? Pulsa otra vez para borrar" : "Borrar perfil"), b -> {
            if (!deleteArmed) {
                deleteArmed = true;
                b.setMessage(Component.literal("¿Seguro? Pulsa otra vez para borrar"));
                return;
            }
            Profiles.delete(profile);
            this.onClose();
        });
        addRow(3, rows, Component.literal("Atrás"), b -> this.onClose());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        String info = "Skin: " + Profiles.labelOf(profile) + " · cuenta " + profile.id;
        graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(info, this.width - 8), this.width / 2, 20, 0xFFA0A0A0);
        if (!message.isEmpty()) graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(message, this.width - 8), this.width / 2, this.height - 12, 0xFFFFD84A);
    }

    public int[] useCenter() {
        return center(use);
    }

    public int[] copyCenter() {
        return center(copy);
    }

    public int[] deleteCenter() {
        return center(delete);
    }
}
