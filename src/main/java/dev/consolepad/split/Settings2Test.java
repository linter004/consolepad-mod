package dev.consolepad.split;

import com.google.gson.Gson;
import dev.consolepad.ConsolePad;
import dev.consolepad.pad.ButtonLayouts;
import dev.consolepad.pad.HoldToggle;
import dev.consolepad.pad.LookControl;
import dev.consolepad.pad.PadAction;
import dev.consolepad.pad.PadManager;
import dev.consolepad.pad.PadSettings;
import dev.consolepad.pad.PadState;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Segunda tanda de ajustes por jugador (casilla 48 del autotest): comprobaciones numericas de cada ajuste. La mayoria son logica pura sobre
 * PadSettings y las clases auxiliares (no hace falta jugar); las que necesitan el juego (tick de P2, mando de P1, HUD, aviso de mando
 * desconectado...) son fases aparte. Cada comprobacion escribe una linea "[autotest] RESULTADO ... -> OK/FALLO".
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

    // ---------------------------------------------------------------- secuencias por ticks

    /** Paso de una secuencia: {@code each} se ejecuta en cada tick del paso; en el ultimo se evalua {@code check} (si hay). */
    private record Step(String name, int ticks, Runnable each, BooleanSupplier check) {}

    private static List<Step> seq = new ArrayList<>();
    private static int seqIdx, seqTick;

    private static Step step(String name, int ticks, Runnable each, BooleanSupplier check) {
        return new Step(name, ticks, each, check);
    }

    private static void startSeq(Step... steps) {
        seq = Arrays.asList(steps);
        seqIdx = 0;
        seqTick = 0;
    }

    /** Avanza la secuencia un tick; true cuando termina. */
    private static boolean tickSeq() {
        if (seqIdx >= seq.size()) return true;
        Step st = seq.get(seqIdx);
        st.each().run();
        if (++seqTick >= st.ticks()) {
            if (st.check() != null) check(st.name(), st.check().getAsBoolean());
            seqIdx++;
            seqTick = 0;
        }
        return seqIdx >= seq.size();
    }

    private static int codeOf(PadSettings s, PadAction a) {
        for (var e : s.bindings.entrySet()) if (e.getValue() == a) return e.getKey();
        return -1;
    }

    /** Entrada simulada de P2: estas acciones pulsadas (las del servidor de pruebas de SecondaryTick). */
    private static void scriptP2(PadAction... actions) {
        SecondaryTick.scriptActive = true;
        EnumSet<PadAction> set = EnumSet.noneOf(PadAction.class);
        set.addAll(Arrays.asList(actions));
        SecondaryTick.scriptActions = set;
    }

    // ---------------------------------------------------------------- guion

    /** Lo llama la casilla 48 una vez por tick; true cuando ha terminado. */
    public static boolean run(Minecraft mc) {
        calls++;
        switch (phase) {
            case 0 -> {
                layoutsTest();
                holdToggleLogic();
                copyResetLogic();
                lookLogic();
                viewLogic();
                phase = 1;
                calls = 0;
            }
            case 1 -> {
                if (stickSwapTest()) {
                    phase = 2;
                    calls = 0;
                }
            }
            case 2 -> { // jugador 2 dentro: agacharse, correr y atacar en modo mantener / alternar
                if (!AutoTest.joinTo(mc, 2)) return false;
                if (seq.isEmpty()) holdToggleP2Setup();
                if (tickSeq()) {
                    SecondaryTick.reset();
                    resetModes(ConsolePad.PADS.slot(1).settings);
                    seq = new ArrayList<>();
                    phase = 3;
                    calls = 0;
                }
            }
            case 3 -> { // jugador 1: lo mismo con su mando (PadDriver)
                if (seq.isEmpty()) holdToggleP1Setup(mc);
                if (tickSeq()) {
                    resetModes(ConsolePad.PADS.slot(0).settings);
                    ConsolePad.PADS.slot(0).device.state.simDown = 0;
                    seq = new ArrayList<>();
                    phase = 4;
                    calls = 0;
                }
            }
            case 4 -> { // color de la etiqueta con el nombre
                if (labelColorTest(mc)) {
                    phase = 5;
                    calls = 0;
                }
            }
            case 5 -> { // apuntar con arco: P1 de verdad (el servidor le pone el arco en la mano)
                if (aimTest(mc)) {
                    phase = 6;
                    calls = 0;
                }
            }
            case 6 -> { // coordenadas, direccion y FPS solo en la region de cada jugador
                if (infoTest(mc)) {
                    phase = 7;
                    calls = 0;
                }
            }
            case 7 -> { // distancia de chunks propia de cada jugador, medida en el servidor y al dibujar
                if (viewTest(mc)) {
                    phase = 8;
                    calls = 0;
                }
            }
            case 8 -> { // accesibilidad: ayuda grande y menos efectos
                if (accessTest(mc)) {
                    phase = 9;
                    calls = 0;
                }
            }
            case 9 -> { // mando desconectado: pausa y aviso hasta que vuelva
                if (seq.isEmpty()) padLostSetup(mc);
                if (tickSeq()) {
                    seq = new ArrayList<>();
                    phase = 99;
                }
            }
            default -> {
                ConsolePad.LOGGER.info("[autotest] RESULTADO ajustes por jugador (tanda 2) en conjunto -> {}", allOk ? "OK" : "FALLO");
                phase = 0;
                seq = new ArrayList<>();
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

    // ---------------------------------------------------------------- paso 2: mantener o alternar

    private static void resetModes(PadSettings s) {
        s.sneakToggle = true;
        s.sprintToggle = true;
        s.attackToggle = false;
        s.toggleHolds = false;
    }

    private static void holdToggleLogic() {
        check("mantener/alternar (logica): alternar invierte con cada pulsacion y soltar no cambia",
                HoldToggle.next(true, false, true, true) && !HoldToggle.next(true, true, true, true)
                        && HoldToggle.next(true, true, false, false) && !HoldToggle.next(true, false, false, false));
        check("mantener/alternar (logica): mantener sigue al boton", HoldToggle.next(false, false, true, true) && !HoldToggle.next(false, true, false, false));
        PadSettings s = new PadSettings();
        check("por defecto agacharse y correr alternan, atacar y usar se mantienen",
                s.isToggle(PadAction.SNEAK_TOGGLE) && s.isToggle(PadAction.SPRINT_TOGGLE) && !s.isToggle(PadAction.ATTACK) && !s.isToggle(PadAction.USE)
                        && !s.isToggle(PadAction.JUMP));
        s.toggleHolds = true;
        s.sneakToggle = false;
        check("accesibilidad: todo lo de mantener pasa a alternar",
                s.isToggle(PadAction.SNEAK_TOGGLE) && s.isToggle(PadAction.ATTACK) && s.isToggle(PadAction.USE) && !s.isToggle(PadAction.JUMP));
    }

    private static void holdToggleP2Setup() {
        PlayerContext c = SecondaryPlayers.context(1);
        PlayerContext.InputState in = c.input;
        PadSettings s = ConsolePad.PADS.slot(1).settings;
        resetModes(s);
        PadAction sneak = PadAction.SNEAK_TOGGLE, sprint = PadAction.SPRINT_TOGGLE, attack = PadAction.ATTACK;
        startSeq(
                step("P2 agacharse alternar: pulsa = agachado", 4, () -> scriptP2(sneak), () -> in.sneakLatch),
                step("P2 agacharse alternar: suelta y sigue agachado", 4, () -> scriptP2(), () -> in.sneakLatch),
                step("P2 agacharse alternar: pulsa otra vez", 4, () -> scriptP2(sneak), null),
                step("P2 agacharse alternar: suelta y se levanta", 4, () -> scriptP2(), () -> !in.sneakLatch),
                step("P2 agacharse mantener: pulsado = agachado", 4, () -> {
                    s.sneakToggle = false;
                    scriptP2(sneak);
                }, () -> in.sneakLatch),
                step("P2 agacharse mantener: soltar = de pie", 4, () -> scriptP2(), () -> !in.sneakLatch),
                step("P2 correr alternar: pulsa", 4, () -> scriptP2(sprint), null),
                step("P2 correr alternar: suelta y sigue corriendo", 4, () -> scriptP2(), () -> in.sprintLatch && in.sprintHeld),
                step("P2 correr alternar: pulsa otra vez", 4, () -> scriptP2(sprint), null),
                step("P2 correr alternar: suelta y para", 4, () -> scriptP2(), () -> !in.sprintLatch && !in.sprintHeld),
                step("P2 correr mantener: pulsado = corre", 4, () -> {
                    s.sprintToggle = false;
                    scriptP2(sprint);
                }, () -> in.sprintLatch && in.sprintHeld),
                step("P2 correr mantener: soltar = para", 4, () -> scriptP2(), () -> !in.sprintLatch && !in.sprintHeld),
                step("P2 atacar mantener (por defecto): pulsado no deja nada activo", 4, () -> scriptP2(attack), () -> !in.attackOn),
                step("P2 atacar mantener: soltado no queda activo", 4, () -> scriptP2(), () -> !in.attackOn),
                step("P2 atacar alternar: pulsa", 4, () -> {
                    s.attackToggle = true;
                    scriptP2(attack);
                }, null),
                step("P2 atacar alternar: suelta y sigue atacando", 4, () -> scriptP2(), () -> in.attackOn),
                step("P2 atacar alternar: pulsa otra vez", 4, () -> scriptP2(attack), null),
                step("P2 atacar alternar: suelta y deja de atacar", 4, () -> scriptP2(), () -> !in.attackOn),
                step("P2 accesibilidad: con agacharse en mantener, pulsar y soltar lo deja activado", 4, () -> {
                    s.attackToggle = false;
                    s.sneakToggle = false;
                    s.toggleHolds = true;
                    scriptP2(sneak);
                }, null),
                step("P2 accesibilidad: sigue agachado tras soltar", 4, () -> scriptP2(), () -> in.sneakLatch),
                step("P2 accesibilidad: otra pulsacion lo apaga", 4, () -> scriptP2(sneak), null),
                step("P2 accesibilidad: apagado tras soltar", 4, () -> scriptP2(), () -> !in.sneakLatch));
    }

    private static void holdToggleP1Setup(Minecraft mc) {
        PadManager pm = ConsolePad.PADS;
        pm.attachVirtual(0);
        PadSettings s = pm.slot(0).settings;
        PadState st = pm.slot(0).device.state;
        resetModes(s);
        int sneakBit = 1 << codeOf(s, PadAction.SNEAK_TOGGLE);
        int attackBit = 1 << codeOf(s, PadAction.ATTACK);
        mc.setScreen(null);
        mc.player.setXRot(-90f); // mira al cielo: atacar no rompe nada
        Runnable pressSneak = () -> st.simDown = sneakBit;
        Runnable up = () -> st.simDown = 0;
        var o = mc.options;
        startSeq(
                step("P1 agacharse alternar: pulsa", 4, pressSneak, null),
                step("P1 agacharse alternar: suelta y sigue agachado", 4, up, () -> o.keyShift.isDown()),
                step("P1 agacharse alternar: pulsa otra vez", 4, pressSneak, null),
                step("P1 agacharse alternar: suelta y se levanta", 4, up, () -> !o.keyShift.isDown()),
                step("P1 agacharse mantener: pulsado = agachado", 4, () -> {
                    s.sneakToggle = false;
                    st.simDown = sneakBit;
                }, () -> o.keyShift.isDown()),
                step("P1 agacharse mantener: soltar = de pie", 4, up, () -> !o.keyShift.isDown()),
                step("P1 atacar mantener: pulsado = keyAttack", 4, () -> st.simDown = attackBit, () -> o.keyAttack.isDown()),
                step("P1 atacar mantener: soltar suelta", 4, up, () -> !o.keyAttack.isDown()),
                step("P1 atacar alternar: pulsa", 4, () -> {
                    s.attackToggle = true;
                    st.simDown = attackBit;
                }, null),
                step("P1 atacar alternar: suelta y sigue atacando", 4, up, () -> o.keyAttack.isDown()),
                step("P1 atacar alternar: pulsa otra vez", 4, () -> st.simDown = attackBit, null),
                step("P1 atacar alternar: suelta y para", 4, up, () -> !o.keyAttack.isDown()));
    }

    // ---------------------------------------------------------------- paso 3: copiar y restablecer

    /** Cambia cada ajuste simple de unos ajustes (booleanos, numeros, textos) para comprobar que copiar los trae todos, incluidos los que se anadan despues. */
    private static void scramble(PadSettings s) {
        for (java.lang.reflect.Field f : PadSettings.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) || f.getName().equals("bindings")) continue;
            try {
                Class<?> t = f.getType();
                if (t == boolean.class) f.setBoolean(s, !f.getBoolean(s));
                else if (t == int.class) f.setInt(s, f.getInt(s) + 3);
                else if (t == float.class) f.setFloat(s, f.getFloat(s) + 0.37f);
                else if (t == String.class) f.set(s, "x" + f.get(s));
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
    }

    private static void copyResetLogic() {
        PadSettings a = new PadSettings();
        scramble(a);
        ButtonLayouts.apply(a, ButtonLayouts.Layout.LEFTY);
        a.playerName = "Ana";
        a.labelColor = 3;
        PadSettings b = new PadSettings();
        b.playerName = "Beto";
        b.labelColor = 5;
        b.copyFrom(a);
        check("copiar: conserva el nombre y el color propios (" + b.playerName + ", " + b.labelColor + ")", b.playerName.equals("Beto") && b.labelColor == 5);
        PadSettings aKeep = GSON.fromJson(GSON.toJson(a), PadSettings.class);
        aKeep.playerName = b.playerName;
        aKeep.labelColor = b.labelColor;
        check("copiar: trae TODOS los demas ajustes (comparacion completa en JSON, campos cambiados uno a uno)", GSON.toJson(aKeep).equals(GSON.toJson(b)));
        a.bindings.put(PadState.X, PadAction.JUMP);
        check("copiar: los botones son una copia profunda (cambiar el original no cambia la copia)", ButtonLayouts.detect(b) == ButtonLayouts.Layout.LEFTY);
        b.resetToDefaults();
        PadSettings def = new PadSettings();
        def.playerName = b.playerName;
        def.labelColor = b.labelColor;
        check("restablecer: todo vuelve a fabrica salvo el nombre y el color (" + b.playerName + ")",
                GSON.toJson(def).equals(GSON.toJson(b)) && ButtonLayouts.detect(b) == ButtonLayouts.Layout.BEDROCK && !b.swapSticks);
        check("copiar/restablecer: el JSON de ajustes guarda y carga igual", GSON.toJson(GSON.fromJson(GSON.toJson(aKeep), PadSettings.class)).equals(GSON.toJson(aKeep)));
    }

    // ---------------------------------------------------------------- paso 4: color de la etiqueta

    private static Integer colorOf(net.minecraft.world.entity.player.Player p) {
        if (p == null) return -2;
        // el nombre completo puede ir envuelto: se busca el primer fragmento con color
        return p.getDisplayName().visit((style, text) -> style.getColor() == null ? java.util.Optional.empty() : java.util.Optional.of(style.getColor().getValue()),
                net.minecraft.network.chat.Style.EMPTY).orElse(-1);
    }

    private static boolean labelColorTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        PadSettings s0 = ConsolePad.PADS.slot(0).settings, s1 = ConsolePad.PADS.slot(1).settings;
        if (calls == 1) {
            s1.labelColor = 1;
            s0.labelColor = 4;
            PlayerLabels.refreshAll(mc);
            return false;
        }
        if (calls < 12) return false;
        if (calls == 12) return labelColorCheck(mc, c, s0, s1);
        if (calls < 24) return false;
        int back = colorOf(mc.level.getPlayerByUUID(c.player.getUUID()));
        check("etiqueta sin color: vuelve al color normal (" + back + ")", back == -1);
        return true;
    }

    private static boolean labelColorCheck(Minecraft mc, PlayerContext c, PadSettings s0, PadSettings s1) {
        int seenByP1 = colorOf(mc.level.getPlayerByUUID(c.player.getUUID()));
        int seenByP2 = colorOf(c.level.getPlayerByUUID(mc.player.getUUID()));
        check("etiqueta con color: P1 ve a P2 en rojo " + Integer.toHexString(seenByP1) + " y P2 ve a P1 en verde " + Integer.toHexString(seenByP2),
                seenByP1 == PlayerLabels.COLOR_RGB[1] && seenByP2 == PlayerLabels.COLOR_RGB[4]);
        s1.labelColor = 0;
        s0.labelColor = 0;
        PlayerLabels.refreshAll(mc);
        return false;
    }

    // ---------------------------------------------------------------- paso 5: curva de la camara y sensibilidad al apuntar

    private static void lookLogic() {
        PadSettings s = new PadSettings();
        float[] o = new float[2];
        LookControl.State st = new LookControl.State();
        float dt = 0.1f, stick = 0.6f;
        float scaled = (stick - s.lookDeadzone) / (1f - s.lookDeadzone);
        float k = dt / 0.15f;
        s.lookCurve = LookControl.Curve.SMOOTH;
        LookControl.compute(s, stick, 0f, false, 1f, dt, st, o);
        float smooth = o[0];
        check("curva suave (la de siempre): giro " + smooth + " esperado " + (float) Math.pow(scaled, s.lookExponent) * s.lookSpeed * k,
                near(smooth, (float) Math.pow(scaled, s.lookExponent) * s.lookSpeed * k));
        s.lookCurve = LookControl.Curve.LINEAR;
        LookControl.compute(s, stick, 0f, false, 1f, dt, st, o);
        float linear = o[0];
        check("curva lineal: giro " + linear + " esperado " + scaled * s.lookSpeed * k + " (y mayor que la suave a media inclinacion)",
                near(linear, scaled * s.lookSpeed * k) && linear > smooth);
        s.lookCurve = LookControl.Curve.ACCEL;
        st = new LookControl.State();
        LookControl.compute(s, 1f, 0f, false, 1f, dt, st, o);
        float first = o[0];
        for (int i = 0; i < 6; i++) LookControl.compute(s, 1f, 0f, false, 1f, dt, st, o);
        float last = o[0];
        check("curva con aceleracion: a fondo el giro sube de " + first + " a " + last + " (x" + last / first + ", maximo x" + LookControl.ACCEL_MAX + " tras " + LookControl.ACCEL_RAMP + " s)",
                last > first * 1.5f && near(last / (s.lookSpeed * k), LookControl.ACCEL_MAX) && near(LookControl.accelFactor(0f), 1f));
        LookControl.compute(s, 0f, 0f, false, 1f, dt, st, o);
        check("curva con aceleracion: al soltar el stick vuelve a empezar (tiempo a fondo " + st.held + ")", st.held == 0f && o[0] == 0f && o[1] == 0f);
        s.lookCurve = LookControl.Curve.SMOOTH;
        LookControl.compute(s, stick, 0f, true, 1f, dt, st, o);
        float normal = o[0];
        s.aimSensitivity = 0.5f;
        LookControl.compute(s, stick, 0f, true, 1f, dt, st, o);
        float aimed = o[0];
        LookControl.compute(s, stick, 0f, false, 1f, dt, st, o);
        check("sensibilidad al apuntar al 50 %: " + normal + " -> " + aimed + " (sin apuntar sigue igual: " + o[0] + ")",
                near(aimed, normal * 0.5f) && near(o[0], smooth));
        s.invertY = true;
        LookControl.compute(s, 0f, 0.8f, false, 1f, dt, st, o);
        float inv = o[1];
        s.invertY = false;
        LookControl.compute(s, 0f, 0.8f, false, 1f, dt, st, o);
        check("eje Y invertido sigue funcionando con las curvas (" + inv + " frente a " + o[1] + ")", near(inv, -o[1]) && inv != 0f);
    }

    private static net.minecraft.world.item.ItemStack aimPrev;

    private static boolean aimTest(Minecraft mc) {
        var server = mc.getSingleplayerServer();
        net.minecraft.server.level.ServerPlayer sp = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (calls == 1) {
            mc.setScreen(null);
            aimPrev = sp.getMainHandItem().copy();
            server.execute(() -> sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BOW)));
            return false;
        }
        if (calls == 10) { // el arco ya esta en la mano del cliente: ahora se tensa (con la tecla de usar pulsada: si no, el cliente lo suelta al instante)
            mc.options.keyUse.setDown(true);
            server.execute(() -> sp.startUsingItem(net.minecraft.world.InteractionHand.MAIN_HAND));
            return false;
        }
        if (calls < 20) return false;
        if (calls == 20) {
            check("apuntando: P1 tensando un arco cuenta como apuntar", LookControl.isAiming(mc.player));
            mc.options.keyUse.setDown(false);
            server.execute(() -> {
                sp.stopUsingItem();
                sp.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, aimPrev);
            });
            return false;
        }
        if (calls < 35) return false;
        check("apuntando: soltado el arco ya no cuenta como apuntar", !LookControl.isAiming(mc.player));
        return true;
    }

    // ---------------------------------------------------------------- paso 6: informacion extra por jugador

    private static boolean infoTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        PadSettings s0 = ConsolePad.PADS.slot(0).settings, s1 = ConsolePad.PADS.slot(1).settings;
        if (calls == 1) {
            mc.setScreen(null);
            s1.showCoords = true;
            s1.showDirection = true;
            s0.showFps = true;
            return false;
        }
        if (calls < 25) return false;
        String t0 = PlayerInfo.lastText[0], t1 = PlayerInfo.lastText[1];
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("XYZ: (-?[0-9.]+) / (-?[0-9.]+) / (-?[0-9.]+)").matcher(t1);
        boolean coords = m.find() && Math.abs(Double.parseDouble(m.group(1)) - c.player.getX()) < 0.6
                && Math.abs(Double.parseDouble(m.group(2)) - c.player.getY()) < 0.6 && Math.abs(Double.parseDouble(m.group(3)) - c.player.getZ()) < 0.6;
        check("coordenadas de P2 en su region y son las suyas: '" + t1 + "' frente a " + c.player.blockPosition().toShortString(), coords);
        check("direccion de P2 en su region: '" + t1 + "'", t1.contains("Mira al"));
        check("P1 solo muestra lo suyo (FPS) y no las coordenadas de P2: '" + t0 + "'", t0.contains("FPS") && !t0.contains("XYZ") && !t0.contains("Mira al"));
        check("P3 (no esta dentro) no muestra nada: '" + PlayerInfo.lastText[2] + "'", PlayerInfo.lastText[2].isEmpty());
        s1.showCoords = false;
        s1.showDirection = false;
        s0.showFps = false;
        return true;
    }

    // ---------------------------------------------------------------- paso 7: distancia de chunks por jugador

    private static void viewLogic() {
        int oldCount = PerfLimits.playerCount;
        Boolean oldAuto = SplitSettings.autoViewDistanceOverride;
        PadSettings s = ConsolePad.PADS.slot(3).settings;
        int oldChosen = s.viewDistance;
        try {
            PerfLimits.playerCount = 2; // tope automatico con 2 jugadores: 75 % de 12 = 9
            SplitSettings.autoViewDistanceOverride = Boolean.TRUE;
            s.viewDistance = 0;
            int global = PerfLimits.viewDistanceFor(3, 12);
            s.viewDistance = 6;
            int low = PerfLimits.viewDistanceFor(3, 12);
            s.viewDistance = 16;
            int high = PerfLimits.viewDistanceFor(3, 12);
            check("distancia por jugador con la automatica activa (2 jugadores, opciones 12): global=" + global + " (9), elige 6 -> " + low + " (6), elige 16 -> " + high + " (tope 9)",
                    global == 9 && low == 6 && high == 9);
            SplitSettings.autoViewDistanceOverride = Boolean.FALSE;
            s.viewDistance = 16;
            int free = PerfLimits.viewDistanceFor(3, 12);
            s.viewDistance = 0;
            int none = PerfLimits.viewDistanceFor(3, 12);
            check("distancia por jugador sin la automatica: elige 16 -> " + free + " (16), global -> " + none + " (12)", free == 16 && none == 12);
            PerfLimits.playerCount = 4; // 50 % de 12 = 6
            SplitSettings.autoViewDistanceOverride = Boolean.TRUE;
            s.viewDistance = 8;
            int four = PerfLimits.viewDistanceFor(3, 12);
            check("con 4 jugadores el tope baja a 6 aunque elija 8 (" + four + ")", four == 6);
        } finally {
            PerfLimits.playerCount = oldCount;
            SplitSettings.autoViewDistanceOverride = oldAuto;
            s.viewDistance = oldChosen;
        }
    }

    private static int viewTrack(net.minecraft.server.level.ServerPlayer p) {
        return p.getChunkTrackingView() instanceof net.minecraft.server.level.ChunkTrackingView.Positioned pos ? pos.viewDistance() : -1;
    }

    private static boolean viewTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        PadSettings s0 = ConsolePad.PADS.slot(0).settings, s1 = ConsolePad.PADS.slot(1).settings;
        var server = mc.getSingleplayerServer();
        if (calls == 1) {
            mc.setScreen(null);
            s0.viewDistance = 0;
            s1.viewDistance = 4;
            return false;
        }
        if (calls == 60) {
            var sp1 = server.getPlayerList().getPlayer(mc.player.getUUID());
            var sp2 = server.getPlayerList().getPlayer(c.player.getUUID());
            int req = mc.options.renderDistance().get();
            int e0 = ViewDistances.effective[0], e1 = ViewDistances.effective[1];
            int[] inCtx = new int[1];
            ContextSwitcher.run(c, () -> inCtx[0] = mc.options.getEffectiveRenderDistance());
            int outside = mc.options.getEffectiveRenderDistance();
            check("distancia por jugador en el cliente: P1 " + e0 + " (la global/automatica de " + req + "), P2 " + e1 + " (4)",
                    e1 == 4 && e0 == PerfLimits.viewDistanceFor(0, req) && e0 > 4);
            check("distancia al dibujar: dentro del contexto de P2 " + inCtx[0] + " (4) y la de P1 fuera " + outside + " (" + e0 + ")", inCtx[0] == 4 && outside == e0);
            check("el servidor conoce la de cada uno: P1 pide " + sp1.requestedViewDistance() + " (" + e0 + "), P2 pide " + sp2.requestedViewDistance() + " (4)",
                    sp1.requestedViewDistance() == e0 && sp2.requestedViewDistance() == 4);
            check("el servidor le manda a P2 menos chunks que a P1: radio de seguimiento P1 " + viewTrack(sp1) + " (" + e0 + "), P2 " + viewTrack(sp2) + " (4)",
                    viewTrack(sp2) == 4 && viewTrack(sp1) == e0);
            s1.viewDistance = 0;
            return false;
        }
        if (calls == 120) {
            var sp2 = server.getPlayerList().getPlayer(c.player.getUUID());
            int e1 = ViewDistances.effective[1];
            check("al volver P2 a global su distancia vuelve a la automatica: cliente " + e1 + ", servidor " + sp2.requestedViewDistance() + ", seguimiento " + viewTrack(sp2),
                    e1 == ViewDistances.effective[0] && sp2.requestedViewDistance() == e1 && viewTrack(sp2) == e1);
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- paso 8: accesibilidad

    private static int[] boxA;

    private static boolean accessTest(Minecraft mc) {
        PlayerContext c = SecondaryPlayers.context(1);
        PadSettings s1 = ConsolePad.PADS.slot(1).settings;
        if (calls == 1) {
            mc.setScreen(null);
            s1.bigHints = false;
            s1.reduceEffects = false;
            return false;
        }
        if (calls == 20) {
            boxA = PadHints.lastBoxL[1].clone();
            s1.bigHints = true;
            return false;
        }
        if (calls == 40) {
            int[] b = PadHints.lastBoxL[1];
            check("ayuda de botones grande: caja de P2 " + boxA[2] + "x" + boxA[3] + " -> " + b[2] + "x" + b[3] + " (x1.5 si cabe)",
                    boxA[2] > 0 && b[2] > boxA[2] * 1.2f && b[3] > boxA[3] * 1.2f);
            s1.bigHints = false;
            Arrays.fill(ReduceEffects.skippedBob, 0);
            Arrays.fill(ReduceEffects.skippedVignette, 0);
            s1.reduceEffects = true;
            return false;
        }
        if (calls == 80) {
            check("menos efectos: balanceo omitido en P2 " + ReduceEffects.skippedBob[1] + " veces y en P1 " + ReduceEffects.skippedBob[0] + " (debe ser 0)",
                    ReduceEffects.skippedBob[1] > 0 && ReduceEffects.skippedBob[0] == 0);
            if (Minecraft.useFancyGraphics()) {
                check("menos efectos: vineta omitida en P2 " + ReduceEffects.skippedVignette[1] + " veces y en P1 " + ReduceEffects.skippedVignette[0] + " (debe ser 0)",
                        ReduceEffects.skippedVignette[1] > 0 && ReduceEffects.skippedVignette[0] == 0);
            }
            float[] fov = new float[2];
            c.player.setSprinting(true);
            s1.reduceEffects = false;
            ContextSwitcher.run(c, () -> fov[0] = c.player.getFieldOfViewModifier());
            s1.reduceEffects = true;
            ContextSwitcher.run(c, () -> fov[1] = c.player.getFieldOfViewModifier());
            c.player.setSprinting(false);
            check("menos efectos: FOV de P2 corriendo " + fov[0] + " sin la opcion y " + fov[1] + " con ella (1.0)", fov[0] > 1.001f && near(fov[1], 1f));
            s1.reduceEffects = false;
            return true;
        }
        return false;
    }

    // ---------------------------------------------------------------- paso 9: mando desconectado

    private static boolean lostSkipP1;

    private static void padLostSetup(Minecraft mc) {
        PadManager pads = ConsolePad.PADS;
        PlayerContext c = SecondaryPlayers.context(1);
        var server = mc.getSingleplayerServer();
        var sp1 = server.getPlayerList().getPlayer(mc.player.getUUID());
        var sp2 = server.getPlayerList().getPlayer(c.player.getUUID());
        mc.setScreen(null);
        lostSkipP1 = false;
        startSeq(
                step("P2 sin mando: se desconecta, su region muestra el aviso y es invulnerable (P1 no)", 15, () -> pads.simulateDisconnect(1),
                        () -> pads.slot(1).lost && pads.slot(1).device == null && c.screen instanceof PadLostScreen && sp2.isInvulnerable()
                                && !(mc.screen instanceof PadLostScreen) && !sp1.isInvulnerable()),
                step("P2 sin mando: sigue con el aviso mientras no vuelva y P1 sigue jugando", 15, () -> { },
                        () -> c.screen instanceof PadLostScreen && mc.screen == null),
                step("P2 vuelve el mando: el aviso se cierra solo y deja de ser invulnerable", 15, () -> pads.simulateReconnect(1),
                        () -> !pads.slot(1).lost && c.screen == null && !sp2.isInvulnerable()),
                step("P1 sin mando: se desconecta, ve el aviso y es invulnerable", 15, () -> {
                    if (!lostSkipP1 && !pads.simulateDisconnect(0)) lostSkipP1 = pads.slot(0).device != null; // mando real: no se toca
                }, () -> lostSkipP1 || (mc.screen instanceof PadLostScreen && sp1.isInvulnerable() && c.screen == null)),
                step("P1: seguir con teclado y raton cierra el aviso", 1, () -> {
                    if (mc.screen instanceof PadLostScreen) mc.screen.onClose();
                }, null),
                step("P1: el aviso descartado no reaparece mientras no haya mando", 20, () -> { }, () -> lostSkipP1 || mc.screen == null),
                step("P1 vuelve el mando: vuelve a ser vulnerable", 15, () -> pads.simulateReconnect(0),
                        () -> lostSkipP1 || (!pads.slot(0).lost && mc.screen == null && !sp1.isInvulnerable())),
                step("P1 se vuelve a desconectar: el aviso reaparece", 15, () -> {
                    if (!lostSkipP1) pads.simulateDisconnect(0);
                }, () -> lostSkipP1 || mc.screen instanceof PadLostScreen),
                step("P1 vuelve el mando otra vez: el aviso se cierra solo", 15, () -> pads.simulateReconnect(0),
                        () -> lostSkipP1 || (mc.screen == null && !sp1.isInvulnerable())));
    }
}
