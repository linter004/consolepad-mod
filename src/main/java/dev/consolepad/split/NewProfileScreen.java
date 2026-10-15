package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Perfil nuevo: nombre (con el teclado en pantalla), skin (una de las 18 por defecto; no se puede cambiar despues porque define la cuenta del perfil) y
 * "Crear" (parte de los ajustes actuales del jugador).
 */
public final class NewProfileScreen extends PlayerMenuScreen {
    private final Screen parent;
    private EditBox box;
    private Button skinBtn, create;
    private int skin = 6;
    private String message = "";

    public NewProfileScreen(int slot, Screen parent) {
        super(slot, Component.literal("Perfil nuevo"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String keep = box == null ? "" : box.getValue();
        box = new EditBox(this.font, this.width / 2 - ROW_W / 2, 30, ROW_W, 16, Component.literal("Nombre"));
        box.setMaxLength(12);
        box.setValue(keep);
        this.addRenderableWidget(box);
        this.setInitialFocus(box);
        skinBtn = addRenderableWidget(Button.builder(Component.literal("Skin: " + Profiles.skinChoices().get(Math.min(skin, Profiles.skinChoices().size() - 1))), b -> {
            skin = (skin + 1) % Profiles.skinChoices().size();
            b.setMessage(Component.literal("Skin: " + Profiles.skinChoices().get(skin)));
        }).bounds(this.width / 2 - ROW_W / 2, 52, ROW_W, ROW_H).build());
        create = addRenderableWidget(Button.builder(Component.literal("Crear"), b -> {
            Profiles.Profile p = Profiles.create(box.getValue(), skin, ConsolePad.PADS.slot(slot).settings.labelColor, ConsolePad.PADS.slot(slot).settings);
            if (p == null) {
                message = box.getValue().isBlank() ? "Escribe un nombre" : "No se pudo crear el perfil";
                return;
            }
            Minecraft.getInstance().setScreen(new ProfileScreen(slot, p, parent));
        }).bounds(this.width / 2 - ROW_W / 2, 76, ROW_W / 2 - 2, ROW_H).build());
        addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> this.onClose()).bounds(this.width / 2 + 2, 76, ROW_W / 2 - 2, ROW_H).build());
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean wantsKeyboard() {
        return true;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        graphics.drawCenteredString(this.font, "Nombre del perfil", this.width / 2, 20, 0xFFA0A0A0);
        if (!message.isEmpty()) graphics.drawCenteredString(this.font, message, this.width / 2, 102, 0xFFFFD84A);
    }

    /** Pruebas. */
    public void setName(String name) {
        box.setValue(name);
    }

    public void setSkin(int s) {
        skin = s;
        skinBtn.setMessage(Component.literal("Skin: " + Profiles.skinChoices().get(Math.min(skin, Profiles.skinChoices().size() - 1))));
    }

    public void pressCreate() {
        create.onPress();
    }
}
