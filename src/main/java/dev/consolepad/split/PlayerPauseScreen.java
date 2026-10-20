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
    private Button resume, settingsButton, controls, layout, options, quit, kick;
    /** Ultima caja {x, y, ancho, alto} donde se dibujo la vista previa del jugador (vacia si no cupo); para las pruebas. */
    public static final int[][] lastPreview = new int[4][4];

    public PlayerPauseScreen(int slot) {
        super(slot, I18n.lit("Pausa · ").append(PlayerLabels.styled(slot, PlayerLabels.labelOf(slot))));
    }

    private static Component layoutLabel() {
        return I18n.lit("Reparto: " + (SplitSettings.orientation() == SplitSettings.Orientation.SIDE_BY_SIDE ? I18n.tr("lado a lado") : I18n.tr("apilado")));
    }

    @Override
    protected void init() {
        Minecraft mc = Minecraft.getInstance();
        boolean canKick = slot == 0 && SecondaryPlayers.anyActive(); // P1 puede sacar a los extra (sobre todo a quien se quedo sin mando)
        int rows = (slot == 0 ? 6 : 5) + (canKick ? 1 : 0);
        int i = 0;
        resume = addRow(i++, rows, I18n.lit("Volver a la partida"), b -> this.onClose());
        settingsButton = addRow(i++, rows, I18n.lit("Ajustes del jugador"), b -> mc.setScreen(new PlayerSettingsScreen(slot, this)));
        controls = addRow(i++, rows, I18n.lit("Controles"), b -> mc.setScreen(new PlayerControlsScreen(slot, this)));
        layout = addRow(i++, rows, layoutLabel(), b -> {
            SplitSettings.toggleOrientation(); // global: se recalcula todo en el siguiente fotograma
            SplitSettings.rememberWorldOrientation(WorldEntry.worldId(mc), SplitSettings.orientation()); // y se recuerda para este mundo
            b.setMessage(layoutLabel());
        });
        if (canKick) kick = addRow(i++, rows, I18n.lit("Sacar a un jugador"), b -> mc.setScreen(new KickPlayerScreen(this)));
        if (slot == 0) {
            options = addRow(i++, rows, I18n.lit("Opciones del juego"), b -> mc.setScreen(new OptionsScreen(this, mc.options)));
            quit = addRow(i, rows, I18n.lit("Guardar y salir al título"), b -> {
                b.active = false;
                if (mc.level != null) mc.level.disconnect();
                if (mc.isLocalServer()) mc.disconnect(new GenericMessageScreen(Component.translatable("menu.savingLevel")));
                else mc.disconnect();
                mc.setScreen(new TitleScreen());
            });
        } else {
            quit = addRow(i, rows, I18n.lit(I18n.fmt("Salir de la partida (P{0})", slot + 1)), b -> SecondaryPlayers.leave(mc, slot));
        }
    }

    /** Vista previa del jugador con su skin a la izquierda de los botones, si sobra sitio (con cuatro jugadores las regiones son estrechas y no sale). */
    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        lastPreview[slot] = new int[4];
        Minecraft mc = Minecraft.getInstance();
        int gap = (this.width - ROW_W) / 2;
        if (mc.player == null || gap < 52 || this.height < 110) return;
        int x1 = 6, x2 = gap - 6, y1 = 28, y2 = Math.min(footerY() - 18, y1 + 130);
        if (y2 - y1 < 60) return;
        float e = openProgress();
        graphics.pose().pushPose();
        graphics.pose().translate(0, (1 - e) * 10, 0); // sube con el menu
        try {
            int scale = (int) Math.min((y2 - y1) * 0.43, (x2 - x1) * 0.62);
            net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventoryFollowsMouse(graphics, x1, y1, x2, y2, scale, 0.0625f, mouseX, mouseY, mc.player);
            graphics.drawCenteredString(this.font, PlayerLabels.styled(slot, PlayerLabels.labelOf(slot)), (x1 + x2) / 2, y2 + 4, 0xFFFFFF);
        } finally {
            graphics.pose().popPose();
        }
        lastPreview[slot] = new int[] {x1, y1, x2 - x1, y2 - y1};
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

    /** Pruebas: el boton de sacar a un jugador (null si no se muestra) y su centro. */
    public Button kickButton() {
        return kick;
    }

    public int[] quitCenter() {
        return center(quit);
    }

    public int slot() {
        return slot;
    }
}
