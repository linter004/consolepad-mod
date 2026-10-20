package dev.consolepad.split;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Elegir el nombre que ven los demas sobre la cabeza de este jugador (se escribe con el teclado en pantalla y se guarda en su slot). */
public final class PlayerNameScreen extends PlayerMenuScreen {
    private final Screen parent;
    private EditBox box;
    private Button done, reset;

    public PlayerNameScreen(int slot, Screen parent) {
        super(slot, I18n.lit("Nombre del jugador " + (slot + 1)));
        this.parent = parent;
    }

    @Override
    protected void init() {
        String keep = box == null ? (settings().playerName == null ? "" : settings().playerName) : box.getValue();
        box = new EditBox(this.font, this.width / 2 - ROW_W / 2, 26, ROW_W, 16, I18n.lit("Nombre"));
        box.setMaxLength(16);
        box.setValue(keep);
        this.addRenderableWidget(box);
        this.setInitialFocus(box);
        done = addRenderableWidget(Button.builder(I18n.lit("Hecho"), b -> save(box.getValue())).bounds(this.width / 2 - ROW_W / 2, 48, ROW_W / 2 - 2, ROW_H).build());
        reset = addRenderableWidget(Button.builder(I18n.lit("Por defecto"), b -> save("")).bounds(this.width / 2 + 2, 48, ROW_W / 2 - 2, ROW_H).build());
    }

    private void save(String name) {
        settings().playerName = name.trim();
        dev.consolepad.ConsolePad.PADS.saveSettings();
        PlayerLabels.refreshAll(Minecraft.getInstance());
        this.onClose();
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
    }

    public String text() {
        return box.getValue();
    }

    public int[] boxCenter() {
        return new int[] {box.getX() + box.getWidth() / 2, box.getY() + box.getHeight() / 2};
    }

    public int[] doneCenter() {
        return center(done);
    }
}
