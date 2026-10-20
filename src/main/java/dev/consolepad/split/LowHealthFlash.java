package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Destello rojo suave en los bordes de la region de un jugador con poca vida (3 corazones o menos), al ritmo del latido del mando (cada 24 ticks): un
 * resplandor de fondo constante y un pulso mas fuerte al "latir", mas intenso cuanto menos vida. Es por jugador y se apaga en sus ajustes.
 * Bedrock lo tiene y vanilla no; no tapa nada (solo los 14 pixeles de GUI del borde).
 */
public final class LowHealthFlash {
    public static final int EDGE = 14;
    /** Altura (z) del destello: por debajo de la barra de objetos de vanilla (z = -90 en P1) y del resto del HUD. */
    public static final float Z = -400f;
    /** Ultimo valor de opacidad y el maximo desde la ultima vez que se pidio borrarlo, por jugador (pruebas). */
    public static final float[] lastAlpha = new float[4], maxAlpha = new float[4];

    private LowHealthFlash() {}

    /** Opacidad del destello (0 a 0,4) para esa vida y ese instante (ticks de juego); 0 si la vida no es baja. */
    public static float alpha(float hp, long gameTime) {
        if (hp <= 0f || hp > 6f) return 0f;
        long phase = Math.floorMod(gameTime, 24L);
        float pulse = phase < 10 ? 1f - phase / 10f : 0f;
        float severity = hp <= 2f ? 1f : hp <= 4f ? 0.8f : 0.6f;
        return 0.12f + 0.28f * pulse * severity;
    }

    public static void resetForTest() {
        java.util.Arrays.fill(lastAlpha, 0f);
        java.util.Arrays.fill(maxAlpha, 0f);
    }

    /** Dibuja el destello en la region completa (w x h) del jugador, dentro de su contexto (mc.player es el suyo). */
    public static void render(Minecraft mc, GuiGraphics g, int slot, int w, int h) {
        lastAlpha[slot] = 0f;
        PadSettings cfg = ConsolePad.PADS.slot(slot).settings;
        if (!cfg.lowHealthFlash || !cfg.showHud || mc.options.hideGui || mc.player == null || mc.level == null) return;
        if (mc.player.isCreative() || mc.player.isSpectator()) return;
        float a = alpha(mc.player.getHealth(), mc.level.getGameTime());
        lastAlpha[slot] = a;
        maxAlpha[slot] = Math.max(maxAlpha[slot], a);
        if (a <= 0f) return;
        g.pose().pushPose();
        g.pose().translate(0, 0, Z); // detras de todo el HUD: lo que se dibuja despues (a z mayor) pasa la prueba de profundidad y queda encima; a z positiva el HUD de P2 a P4 (z = 0) desaparecia bajo el borde
        for (int i = 0; i < EDGE; i++) {
            float t = 1f - i / (float) EDGE;
            int al = Math.min(255, (int) (255 * a * t * t));
            if (al <= 0) continue;
            int c = (al << 24) | 0xCC1010;
            g.fill(0, i, w, i + 1, c);
            g.fill(0, h - 1 - i, w, h - i, c);
            g.fill(0, i + 1, i + 1, h - 1 - i, c);
            g.fill(w - 1 - i, i + 1, w - i, h - 1 - i, c);
        }
        g.pose().popPose();
    }

    /** Autocomprobacion sin mundo: null si bien. */
    public static String selfTest() {
        if (alpha(20f, 0) != 0f || alpha(0f, 0) != 0f || alpha(7f, 0) != 0f) return "con vida normal o muerto no debe dibujar";
        float beat = alpha(6f, 0), off = alpha(6f, 12), worse = alpha(2f, 0);
        if (!(off > 0f && beat > off && worse > beat)) return "el latido y la gravedad: " + beat + ", " + off + ", " + worse;
        if (alpha(6f, 24) != beat || alpha(6f, 12) != alpha(6f, 36)) return "debe repetirse cada 24 ticks";
        return null;
    }
}
