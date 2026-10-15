package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/** Una entrada de la rueda: nombre y comando (sin la barra), con "Guardar" y, si ya existia, "Borrar". Guarda una rueda propia del jugador. */
public final class WheelEntryScreen extends PlayerMenuScreen {
    private final Screen parent;
    private final int index;
    private EditBox label, command;
    private Button save, delete;
    private String message = "";

    /** index = -1: entrada nueva. */
    public WheelEntryScreen(int slot, int index, Screen parent) {
        super(slot, Component.literal(index < 0 ? "Entrada nueva" : "Entrada de la rueda"));
        this.parent = parent;
        this.index = index;
    }

    @Override
    protected void init() {
        List<QuickWheel.Entry> list = QuickWheel.listFor(settings());
        boolean has = index >= 0 && index < list.size();
        String l = label != null ? label.getValue() : has ? list.get(index).label() : "";
        String c = command != null ? command.getValue() : has ? list.get(index).command() : "";
        int x = this.width / 2 - ROW_W / 2;
        label = new EditBox(this.font, x, 34, ROW_W, 16, Component.literal("Nombre"));
        label.setMaxLength(14);
        label.setValue(l);
        addRenderableWidget(label);
        command = new EditBox(this.font, x, 66, ROW_W, 16, Component.literal("Comando"));
        command.setMaxLength(120);
        command.setValue(c);
        addRenderableWidget(command);
        setInitialFocus(label);
        save = addRenderableWidget(Button.builder(Component.literal("Guardar"), b -> save()).bounds(x, 90, ROW_W / 2 - 2, ROW_H).build());
        delete = addRenderableWidget(Button.builder(Component.literal(index >= 0 ? "Borrar" : "Atrás"), b -> {
            if (index >= 0) delete();
            else this.onClose();
        }).bounds(x + ROW_W / 2 + 2, 90, ROW_W / 2 - 2, ROW_H).build());
        if (index >= 0) addRenderableWidget(Button.builder(Component.literal("Atrás"), b -> this.onClose()).bounds(x, 114, ROW_W, ROW_H).build());
    }

    private void store(List<QuickWheel.Entry> list) {
        List<PadSettings.WheelEntry> out = new ArrayList<>();
        for (QuickWheel.Entry e : list) out.add(new PadSettings.WheelEntry(e.label(), e.command()));
        settings().wheel = out; // lista nueva: nunca se modifica una que ya se haya copiado a otros ajustes
        ConsolePad.PADS.saveSettings();
    }

    /** Guarda la entrada. Devuelve null si va bien o por que no. */
    public String save() {
        String l = label.getValue().trim(), c = command.getValue().trim();
        if (c.startsWith("/")) c = c.substring(1).trim();
        if (l.isEmpty() || c.isEmpty()) {
            message = "Escribe un nombre y un comando";
            return message;
        }
        List<QuickWheel.Entry> list = new ArrayList<>(QuickWheel.listFor(settings()));
        QuickWheel.Entry e = new QuickWheel.Entry(l, c);
        if (index >= 0 && index < list.size()) {
            list.set(index, e);
        } else if (list.size() < QuickWheel.MAX_ENTRIES) {
            list.add(e);
        } else {
            message = "La rueda ya tiene " + QuickWheel.MAX_ENTRIES + " entradas";
            return message;
        }
        store(list);
        this.onClose();
        return null;
    }

    private void delete() {
        List<QuickWheel.Entry> list = new ArrayList<>(QuickWheel.listFor(settings()));
        if (index >= 0 && index < list.size() && list.size() > 1) {
            list.remove(index);
        } else {
            message = "La rueda necesita al menos una entrada";
            return;
        }
        store(list);
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
        graphics.drawCenteredString(this.font, "Nombre", this.width / 2, 24, 0xFFA0A0A0);
        graphics.drawCenteredString(this.font, "Comando (sin la barra)", this.width / 2, 56, 0xFFA0A0A0);
        if (!message.isEmpty()) graphics.drawCenteredString(this.font, this.font.plainSubstrByWidth(message, this.width - 8), this.width / 2, this.height - 12, 0xFFFFD84A);
    }

    /** Pruebas. */
    public void setLabel(String s) {
        label.setValue(s);
    }

    public void setCommand(String s) {
        command.setValue(s);
    }

    public void pressDelete() {
        delete.onPress();
    }
}
