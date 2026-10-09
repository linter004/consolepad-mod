package dev.consolepad.split;

import com.google.gson.Gson;
import dev.consolepad.ConsolePad;
import dev.consolepad.pad.ButtonLayouts;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;

/**
 * Segunda tanda de ajustes por jugador (casilla 48 del autotest): comprobaciones numericas de cada ajuste. La mayoria son logica pura sobre
 * PadSettings y las clases auxiliares (no hace falta jugar); las que necesitan el juego (HUD, aviso de mando desconectado...) son fases aparte.
 * Cada comprobacion escribe una linea "[autotest] RESULTADO ... -> OK/FALLO".
 */
public final class Settings2Test {
    private static final Gson GSON = new Gson();
    private static boolean allOk = true;
    private static int phase;
    private static int calls;

    private Settings2Test() {}

    static void check(String name, boolean ok) {
        allOk &= ok;
        ConsolePad.LOGGER.info("[autotest] RESULTADO {} -> {}", name, ok ? "OK" : "FALLO");
    }

    static boolean near(float a, float b) {
        return Math.abs(a - b) < 0.001f;
    }

    /** Lo llama la casilla 48 una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        calls++;
        switch (phase) {
            case 0 -> {
                layoutsTest();
                phase = 1;
                calls = 0;
            }
            case 1 -> {
                if (stickSwapTest()) {
                    phase = 99;
                }
            }
            default -> {
                ConsolePad.LOGGER.info("[autotest] RESULTADO ajustes por jugador (tanda 2) en conjunto -> {}", allOk ? "OK" : "FALLO");
                phase = 0;
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- paso 1: disposiciones de botones

    private static void layoutsTest() {
        PadSettings s = new PadSettings();
        check("disposicion de fabrica = Bedrock", ButtonLayouts.detect(s) == ButtonLayouts.Layout.BEDROCK && !s.swapSticks);
        ButtonLayouts.apply(s, ButtonLayouts.Layout.JAVA);
        check("Java: B agacharse, R3 soltar, A saltar, RT atacar (" + ButtonLayouts.name(s) + ")",
                s.bindings.get(PadState.B) == PadAction.SNEAK_TOGGLE && s.bindings.get(PadState.R3) == PadAction.DROP
                        && s.bindings.get(PadState.A) == PadAction.JUMP && s.bindings.get(PadState.RT) == PadAction.ATTACK
                        && ButtonLayouts.detect(s) == ButtonLayouts.Layout.JAVA && !s.swapSticks);
        ButtonLayouts.apply(s, ButtonLayouts.Layout.LEFTY);
        check("Zurdo: LT ataca, RT usa, RB/LB y L3/R3 intercambiados, sticks cambiados (" + ButtonLayouts.name(s) + ")",
                s.bindings.get(PadState.LT) == PadAction.ATTACK && s.bindings.get(PadState.RT) == PadAction.USE
                        && s.bindings.get(PadState.LB) == PadAction.HOTBAR_NEXT && s.bindings.get(PadState.RB) == PadAction.HOTBAR_PREV
                        && s.bindings.get(PadState.L3) == PadAction.SNEAK_TOGGLE && s.bindings.get(PadState.R3) == PadAction.SPRINT_TOGGLE
                        && ButtonLayouts.detect(s) == ButtonLayouts.Layout.LEFTY && s.swapSticks);
        int n = s.bindings.size();
        check("todas las disposiciones tienen los mismos botones (" + n + ")",
                ButtonLayouts.bindings(ButtonLayouts.Layout.BEDROCK).size() == n && ButtonLayouts.bindings(ButtonLayouts.Layout.JAVA).size() == n);
        // Un remapeo individual encima de una disposicion la marca personalizada; el siguiente clic del ciclo vuelve a Bedrock.
        s.bindings.put(PadState.X, PadAction.JUMP);
        check("tras remapear un boton la disposicion es personalizada y el ciclo vuelve a Bedrock",
                ButtonLayouts.detect(s) == null && ButtonLayouts.name(s).equals("personalizada") && ButtonLayouts.next(s) == ButtonLayouts.Layout.BEDROCK);
        // Se guarda y se carga: ida y vuelta por JSON con los mismos botones y sticks.
        ButtonLayouts.apply(s, ButtonLayouts.Layout.LEFTY);
        PadSettings back = GSON.fromJson(GSON.toJson(s), PadSettings.class);
        check("la disposicion zurdo sobrevive al guardado en JSON (" + ButtonLayouts.name(back) + ")", ButtonLayouts.detect(back) == ButtonLayouts.Layout.LEFTY);
    }

    /** Con sticks intercambiados, el estado que leen el juego, los menus y el teclado trae el stick derecho como izquierdo. Necesita un mando virtual libre en el slot 3. */
    private static boolean stickSwapTest() {
        PadManager pads = ConsolePad.PADS;
        PadManager.Slot sl = pads.slot(3);
        if (calls == 1) {
            if (sl.device != null) {
                check("sticks intercambiados con el estado del mando (SALTADO: el slot 3 tiene un mando real)", true);
                return true;
            }
            pads.attachVirtual(3);
            sl.settings.swapSticks = true;
            sl.device.state.simLx = 0.8f;
            sl.device.state.simRy = -0.6f;
            return false;
        }
        if (calls < 4) return false;
        PadState st = sl.device.state;
        boolean ok = near(st.rx, 0.8f) && near(st.ly, -0.6f) && near(st.lx, 0f) && near(st.ry, 0f);
        sl.settings.swapSticks = false;
        st.simLx = 0f;
        st.simRy = 0f;
        pads.detachVirtual(3);
        check("sticks intercambiados: simulado izq 0.8 / der -0.6 se lee como rx=" + st.rx + " ly=" + st.ly + " lx=" + st.lx + " ry=" + st.ry, ok);
        return true;
    }
}
