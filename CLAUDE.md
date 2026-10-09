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

### Reglas aprendidas de la tanda de consola (entradas "Tanda de consola" de `routing-log.md`)

- **Un widget que no reacciona al cursor en P2-P4 suele ser el recorte:** `AbstractWidget.render` decide "encima" con `GuiGraphics.containsPointInScissor`, que compara con el recorte de la region en unidades de ventana; `GuiGraphicsMixin` lleva el punto con la pose. Cualquier otra comprobacion contra el recorte tiene el mismo problema.
- **El boton que abre una pantalla (cruceta del chat, A sobre un campo) sigue pulsado al abrirse:** el teclado en pantalla ignora un fotograma y toma las direcciones ya pulsadas como estado anterior (`skipFrame`, `dirPrev`), si no se mueve solo.
- **Ejes simulados:** `PadState.simLx/simLy/simRx/simRy` sustituyen al stick (un mando real conectado los pisaria cada fotograma). Las pruebas nuevas deben terminar con P2 dentro (`leaveTo(mc, 2)`): `leaveTest` lo necesita.
- **Un cartel abierto desde una prueba necesita `sign.setAllowedPlayerEditor(uuid)` en el servidor** (lo hace `SignBlock.openTextEdit`, no `ServerPlayer.openTextEdit`). `ServerBossEvent` esta en `net.minecraft.server.level`.
- **Si en una tanda filtrada todo lo de P1 falla a la vez y lo de los extra no, es el foco de la ventana** (`PadDriver` no hace nada con `!mc.isWindowActive()`): repetir sin tocar ni abrir nada mas.
- **Un mixin a una clase de Mixin no puede tener campos estaticos publicos usados desde fuera:** los contadores van en `SplitDiag`.

### Reglas aprendidas de la tercera tanda (estado compartido, giro, aviso)

- **Todo estado de `GameRenderer`/`Camera`/`LevelRenderer`/`Minecraft` que se actualice por tick o por fotograma y dependa del jugador debe ser por jugador:** se cambia al entrar en su contexto (`try/finally`) y se actualiza dentro de el. Lista y pendientes en `docs/splitscreen-state-audit.md`; al tocar render o tick de un extra, repasarla.
- **Las redirecciones de `GETFIELD` de un campo privado (camara, mapa de luz) necesitan un `@Accessor` para leer el valor original sin pasar por la redireccion;** y una interfaz de accessors puede tener metodos `static` para campos estaticos (FogRenderer).
- **El giro de camara de un extra se aplica por fotograma** (`SecondaryTick.frameLook`, con `dt` real), nunca por tick. Para medir continuidad se muestrea el angulo de la camara que se dibuja en cada fotograma (`AutoTest.sampleLook`).
- **Una espera sobre el archivo de salida de una tarea en segundo plano se cuelga del texto final, no de que exista:** el script puede haber escrito algo al empezar. Esperar a una linea concreta del log.
- **Los mandos virtuales de prueba tienen reloj propio en el aviso de unirse:** una prueba que lo mida debe desenchufar y volver a enchufar (`detachVirtual` + `attachVirtual`) para empezar de cero.
- **El estado del mundo "New World" lo cambia el usuario al jugar** (modo de juego de P1, posicion de P2, tiempo): una prueba que dependa de eso debe fijarlo ella y restaurarlo (supervivencia para el inventario de P1).
- **El usuario puede tener mandos reales conectados durante el autotest (hasta 3 en los slots 0 a 2):** una prueba no puede suponer que un slot tiene un mando virtual ni desenchufarlo; los mandos reales se quedan, y su estado se suma al simulado. Para volver a empezar el reloj de un aviso usar `JoinPrompt.forget`.
- **Con la ventana sin foco vanilla abre el menu de pausa de P1** (`pauseOnLostFocus`): el autotest lo desactiva mientras corre y `PadDriver` no exige foco si `AutoTest.running()`.


### Reglas aprendidas de la tanda Bedrock (iconos, crafteo estilo consola; entradas del 2026-10-09 de `routing-log.md`)

- **El crafteo por mando no necesita tocar el servidor:** el libro de recetas de vanilla coloca la receta con `handlePlaceRecipe` (`ServerboundPlaceRecipePacket`). `ConsoleCraft` solo lo maneja (accessors de `RecipeBookComponent`/`RecipeBookPage`) y recoge el resultado con un mover rapido. La marca "fabricable" del cliente puede ir retrasada: no condicionar la accion a ella.
- **El estado del libro es por jugador y se guarda en el servidor** (abierto/cerrado y filtro "solo fabricables"): una prueba debe fijarlo y restaurarlo (con el filtro activo P2 solo veia 4 recetas). Las recetas 3x3 no salen en el libro del inventario (cuadricula 2x2).
- **Los iconos de botones salen de `tools/PromptAtlas.java`** (`java tools/PromptAtlas.java` desde la raiz; sin Python/PIL). Un boton sin icono mide 0 y no dibuja; el orden de `ICONS` debe coincidir con `PadIcons.Icon`.
- **Pruebas de pantallas especiales:** un yunque sin bloque debajo cae y cierra la pantalla; el aldeano necesita `villager.setTradingPlayer(player)` antes de `openTradingScreen`; las opciones de encantamiento sin coste no son destino.
- **Los scripts de Python con comillas triples o apostrofes se escriben con Write** y se ejecutan con `python script.py args`, nunca con heredoc de shell. Cuidado con `cd` dentro de una cadena larga: `tools/run-autotest.sh` se busca desde la raiz.

### Reglas aprendidas de la tanda de rendimiento (2026-10-09)

- **Medir con `CONSOLEPAD_AUTOTEST_CASES=46 CONSOLEPAD_PERF_TAG=<x> [CONSOLEPAD_PERF_NS=1,2,3,4] [CONSOLEPAD_PERF_AUTORD=on|off] [CONSOLEPAD_PERF_LIMITS=on|off] tools/run-autotest.sh run/perf/<x>.log`;** las lineas `[perf]` son el resultado. Para A/B comparar en la misma ejecucion (el ruido entre ejecuciones es de ±15 %). Nunca lanzar dos `run-autotest.sh` a la vez: el segundo mata el juego del primero; un script viejo vivo hace lo mismo a los 20 min.
- **El cuello de botella es la CPU** (cada pasada de mundo ~2-3 ms): la palanca es dibujar menos (distancia, entidades, lluvia), no la resolucion. `Options.getEffectiveRenderDistance` es global y el servidor ya manda al cliente su distancia reducida: aplicar el factor a la distancia pedida y luego `min` con la del servidor.
- **Los hilos de mallas llaman a `GameRenderer.getMainCamera`:** cualquier redireccion por contexto debe leer el estado una vez y comprobar `RenderSystem.isOnRenderThread()`.
