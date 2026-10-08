# CLAUDE.md

## Reglas de ahorro

- **Nivel por defecto:** Sonnet, esfuerzo medio. No cambies de modelo ni subas el esfuerzo por tu cuenta sin avisar antes.
- **Juez objetivo:** `./gradlew build` (y `./gradlew runClient` cuando aplique). Si pasa, la tarea está hecha en el nivel actual.
- **Escalado:** si la comprobación falla 2 veces seguidas, propón subir un escalón y espera confirmación:
  Sonnet medio → Sonnet alto → Opus medio.
- **Registro:** al terminar cada tarea, añade una entrada en `routing-log.md` con: tarea, nivel que la resolvió, intentos y coste estimado.
- **Aprendizaje:** cuando `routing-log.md` tenga 5 o más entradas, resume los patrones como reglas fijas en la sección "Reglas aprendidas" de este archivo.
- **Subagentes:** permitidos solo para (1) leer logs largos y diagnósticos con mucho texto, devolviendo un resumen corto, y (2) `.claude/agents/fixer-opus.md` (Opus medio), con permiso permanente del usuario únicamente para diagnosticar y arreglar algo que ya falló una vez. Nada más.

## Autonomía en la pantalla dividida (hasta que funcione de principio a fin)

Estas reglas se suman a las de ahorro; no las sustituyen. Objetivo: 2-4 jugadores locales, cada uno con su HUD, pausa, inventario y pantallas, cursor, mando y ajustes en su parte de la pantalla. Primero funcionar, después pulir (el aspecto es el de Minecraft normal). Hoja de ruta en `docs/splitscreen-roadmap.md`; plan base en `docs/splitscreen-plan.md`.

- **En cada paso:** explica en 3 líneas qué vas a hacer y empieza sin esperar OK. Implementa, compila, usa el autotest y haz **un commit por paso**, con tarea, nivel y resultado en `routing-log.md`. Comprobaciones numéricas con el autotest siempre que se pueda, no solo capturas.
- **Si la comprobación pasa**, sigue al paso siguiente sin pedir permiso.
- **Si falla una vez:** delega el diagnóstico y el arreglo en `fixer-opus`. No cambies el modelo de la sesión principal.
- **Para y espera solo si:**
  - (a) un paso falla incluso con el subagente;
  - (b) hay una fuga de contexto de P2 hacia P1 que no consigues cerrar;
  - (c) el uso semanal pasa del 50 % en `/usage`;
  - (d) el usuario debe probar algo jugando que no se pueda medir: dar una lista corta de qué probar;
  - (e) hay que usar algo que gaste créditos de pago.
- **Si algo es demasiado arriesgado o grande**, déjalo en el roadmap como pendiente con el motivo y sigue con lo siguiente.
- **Contexto pequeño:** logs largos y diagnósticos con mucho texto, a un subagente que devuelva un resumen corto; no pegar logs completos ni capturas innecesarias. Tras cada paso, el estado resumido va a `routing-log.md` de modo que una sesión nueva pueda continuar solo con ese archivo y los commits.
- **Fallos visuales:** se comprueban con medidas numéricas en el autotest (por ejemplo `SplitDiag` y `aspectTest`), no solo mirando capturas.
- **Autotest:** mientras corre, la ventana no debe tocarse.

## Reglas aprendidas

Resumen de las 9 primeras entradas de `routing-log.md` (2026-10-07):

- **Sonnet medio basta para el código del mod.** Las 8 tareas de código (compilar, mixins, controles, cursor, D-pad, ajustes JSON) se resolvieron al primer intento sin escalar. No proponer subir de nivel para este tipo de tarea salvo 2 fallos seguidos del juez.
- **Opus solo para investigación o diseño amplio** (por ejemplo, el plan de pantalla dividida), y solo si lo pide el usuario.
- **Entorno:** Gradle necesita `JAVA_HOME=C:/Users/<usuario>/.jdks/jbr-21.0.11` (el Java por defecto es el 8). Las fuentes de MC para verificar nombres están en `build/moddev/artifacts/neoforge-21.1.92-sources.jar`: comprobar ahí los nombres antes de escribir mixins.
- **`build` no valida mixins.** Tras tocar mixins, lanzar `runClient` y revisar el log. Avisar de qué mixins solo se validan con una acción en el juego (entrar en un mundo, abrir un contenedor).
- **Ajustes guardados mandan sobre los valores por defecto.** Al cambiar un valor por defecto de `PadSettings`, actualizar también `run/config/consolepad/slot*.json` con el juego cerrado. Cerrar el juego a la fuerza evita que lo sobrescriba al salir.
- **Coste:** el gasto principal es esperar el arranque de `runClient` (unos 30 s si ya está descargado). Usar un Monitor corto con `grep` del log y pararlo al terminar.

### Reglas aprendidas de la pantalla dividida (entradas 10 a 36 de `routing-log.md`, 2026-10-08)

- **Un fallo del autotest suele ser del test, no del mod.** De ~12 fallos, 7 fueron del test (criterios fijos sin control, `Level.getBlockEntity` fuera del hilo del servidor, supervivencia de noche, esperas de carga). Antes de tocar el mod, medir con una linea de log temporal.
- **Los fallos reales los destapo el autotest, no `build`:** guarda de teclas que no cubria `ToggleKeyMapping`, proxy que solo reenviaba `handle*` (titulos de P2 llegaban a P1), Start+Back abria el menu de P2, P2 activo con el mundo descargado. Cada hallazgo de aislamiento se cierra con una medida numerica por jugador.
- **Aislar por jugador:** estado global que se intercambia en `ContextSwitcher` (pantalla, camara, FOV) o en `SplitHud` (estado de `Gui`); los campos privados se tocan con accessors de mixin, no con `setCameraType` ni `setScreen` de vanilla (efectos secundarios sobre P1).
- **El autotest es determinista si fija dia y dificultad pacifica y respawnea a quien muera;** restaura la dificultad al salir. `tools/run-autotest.sh` hace copia y restaura `run/config/consolepad`; la salida va a un archivo y se lee con `grep`.
- **Las pruebas largas (>10 min) se lanzan en segundo plano** (`run_in_background`); el limite de una llamada en primer plano es 10 min.
- **Cuando fixer-opus no esta registrado en la sesion,** usar un agente general con `model: opus` y apuntarlo a `.claude/agents/fixer-opus.md`; y comprobar su resultado repitiendo el autotest, no solo leer su informe.

### Reglas aprendidas de la segunda tanda de pantalla dividida (entradas 63 en adelante de `routing-log.md`)

- **Un boton que cierra una pantalla desde el fotograma no debe disparar su accion de juego en el tick siguiente** (Start reabria el menu, B soltaba un objeto): `InputState.closeLatch` y `prevDown` sin vaciar mientras hay pantalla.
- **`GuiGraphics.enableScissor` ignora la pose**: todo lo que se dibuje trasladado/escalado a una region (maniqui del inventario, listas) necesita el mixin que lo transforma. Cada region se dibuja dentro de su propio `enableScissor`, asi desenfoque, objeto del cursor y tooltips no se salen.
- **Una prueba que depende de la escala de interfaz global debe fijarla** (en `options.txt` del usuario puede ser 1); el autotest la pone en Automatico y la restaura. Tras cambiar la escala o la orientacion las pantallas se recolocan: el cursor de la prueba hay que volver a ponerlo sobre el boton.
- **Para repetir solo una prueba**: `CONSOLEPAD_AUTOTEST_CASES=<casillas> tools/run-autotest.sh <salida>`; antes de lanzar, asegurarse de que el juego anterior ha muerto del todo (si no, el mundo sigue bloqueado y el autotest se queda en el titulo).
- **Los scripts largos con comillas se escriben con la herramienta Write**, no con heredocs de shell (rompen el comando).
