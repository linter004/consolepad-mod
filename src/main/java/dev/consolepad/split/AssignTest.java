package dev.consolepad.split;

import dev.consolepad.ConsolePad;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Asignar mandos (dentro de la casilla 21): con mandos virtuales en los slots 2 y 3 (los reales no se tocan; si estan ocupados por mandos reales, la parte
 * de pantalla se omite), mide el intercambio de mandos entre dos jugadores, la asignacion a un jugador sin mando (el que lo tenia queda "perdido") y la
 * pantalla: pulsar un jugador, pulsar un boton en otro mando y que se intercambien; el mismo mando cancela.
 */
public final class AssignTest {
    private static int t;
    private static boolean ok = true, ready, attached2, attached3, skip;
    private static PadManager.Device dA, dB;
    private static Screen prev;
    private static PadAssignScreen scr;
    private static PadSetupScreen setup;
    private static final PadManager.Device[] orig = new PadManager.Device[PadManager.MAX_PLAYERS];

    private AssignTest() {}

    private static void verdict(String name, boolean good) {
        ok &= good;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, good ? "OK" : "FALLO");
    }

    private static PadManager.Slot slot(int i) {
        return ConsolePad.PADS.slot(i);
    }

    private static void press(PadManager.Device d, int bits) {
        d.state.simDown = bits;
    }

    /** Una vuelta por tick; true al terminar. */
    public static boolean run(Minecraft mc) {
        t++;
        PadManager pads = ConsolePad.PADS;
        if (t == 1) {
            if (slot(2).device == null) {
                pads.attachVirtual(2);
                attached2 = true;
            }
            if (slot(3).device == null) {
                pads.attachVirtual(3);
                attached3 = true;
            }
            dA = slot(2).device;
            dB = slot(3).device;
            boolean virtB = isVirtual(dB); // pulsar botones solo se puede simular en un mando virtual
            // intercambio entre dos jugadores (vale con mandos reales: se devuelven al final)
            pads.assign(dA, 3);
            verdict("asignar mandos: el mando del jugador 3 pasa al 4 y el del 4 al 3 (intercambio)", slot(3).device == dA && slot(2).device == dB);
            pads.assign(dA, 2);
            verdict("asignar mandos: volver a asignarlo deja cada mando en su sitio", slot(2).device == dA && slot(3).device == dB);
            if (virtB) {
                // a un jugador sin mando: el que lo tenia queda perdido
                pads.detachVirtual(3);
                attached3 = true; // el que se crea despues es nuestro: al final se quita
                pads.assign(dA, 3);
                verdict("asignar mandos: a un jugador sin mando, el jugador 3 queda sin mando y 'perdido' y el 4 lo lleva (j3=" + slot(2).device + " perdido=" + slot(2).lost + ")",
                        slot(3).device == dA && slot(2).device == null && slot(2).lost && !slot(3).lost);
                pads.assign(dA, 2);
                verdict("asignar mandos: devolverlo deja el jugador 4 sin mando", slot(2).device == dA && slot(3).device == null && !slot(2).lost);
                pads.attachVirtual(3);
                dB = slot(3).device;
            } else {
                skip = true;
                ConsolePad.LOGGER.info("[autotest] asignar mandos: la parte de pantalla se omite (el slot 4 tiene un mando real)");
                ConsolePad.LOGGER.info("[autotest] RESULTADO asignar mandos -> {}", ok ? "OK" : "FALLO");
                finish(pads);
                return true;
            }
            prev = mc.screen;
            scr = new PadAssignScreen(0, null);
            mc.setScreen(scr);
        }
        if (skip) return true;
        if (t == 15) {
            scr.startListening(2); // como pulsar la fila del jugador 3
            verdict("asignar mandos: pulsar una fila pide un boton (escuchando al jugador " + (scr.listening() + 1) + ", la pantalla bloquea el cierre)", scr.listening() == 2 && scr.listeningForPad());
        }
        if (t == 30) press(dB, 1 << PadState.A); // el mando del jugador 4 pulsa un boton
        if (t == 36) press(dB, 0);
        if (t == 50) {
            verdict("asignar mandos: el mando que pulsa pasa al jugador esperado (j3 lleva el que era del 4=" + (slot(2).device == dB) + ", j4 el que era del 3=" + (slot(3).device == dA) + ")",
                    slot(2).device == dB && slot(3).device == dA && scr.listening() < 0);
            scr.startListening(2);
        }
        if (t == 65) press(dB, 1 << PadState.A); // el mando que ya lleva el jugador 3 cancela
        if (t == 71) press(dB, 0);
        if (t == 85) {
            verdict("asignar mandos: el mismo mando cancela sin cambiar nada", slot(2).device == dB && slot(3).device == dA && scr.listening() < 0);
            pads.assign(dA, 2); // deja cada mando en su sitio
            for (int i = 0; i < orig.length; i++) orig[i] = slot(i).device;
            setup = new PadSetupScreen(prev);
            mc.setScreen(setup);
        }
        if (t == 100) { // pantalla previa al mundo: el mando que pulsa A primero se queda el jugador 1
            PadManager.Device d4 = slot(3).device, d3 = slot(2).device;
            setup.pressA(d4);
            verdict("mandos antes del mundo: el primer mando que pulsa A se queda el jugador 1 (j1=" + (slot(0).device == d4) + ", cuadrante reclamado=" + setup.claimed(0) + ")",
                    slot(0).device == d4 && setup.claimed(0) && !setup.claimed(1));
            setup.pressA(d3);
            verdict("mandos antes del mundo: el segundo se queda el jugador 2 (j2=" + (slot(1).device == d3) + ")", slot(1).device == d3 && setup.claimed(1) && setup.claimed(0));
            setup.pressB(d3);
            verdict("mandos antes del mundo: B suelta el cuadrante pero el mando se queda (reclamado=" + setup.claimed(1) + ")", !setup.claimed(1) && slot(1).device == d3 && mc.screen == setup);
            setup.pressA(d4); // ya unido: continua
            verdict("mandos antes del mundo: A en un mando ya unido cierra la pantalla", mc.screen == prev);
            for (int i = 0; i < orig.length; i++) if (orig[i] != null) pads.assign(orig[i], i); // cada mando a su sitio
            ConsolePad.LOGGER.info("[autotest] RESULTADO asignar mandos -> {}", ok ? "OK" : "FALLO");
            finish(pads);
            return true;
        }
        return false;
    }

    private static void finish(PadManager pads) {
        if (attached2) pads.detachVirtual(2);
        if (attached3) pads.detachVirtual(3);
        attached2 = attached3 = ready = skip = false;
        t = 0;
        ok = true;
    }

    private static boolean isVirtual(PadManager.Device d) {
        return d != null && d.jid < 0;
    }
}
