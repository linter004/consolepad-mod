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
  - (c) el uso semanal pasa del 75 % en `/usage`;
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

### Reglas aprendidas de la tanda de ajustes por jugador (2026-10-09)

- **Si `git status` muestra cambios que no son tuyos, hay otra sesion en la misma carpeta:** trabaja en un worktree (`git worktree add -b <rama> ../<carpeta> HEAD` y copia `run/`), no lances el autotest en la carpeta compartida y fusiona al final (`git merge master`). `tools/run-autotest.sh` solo mata el juego de su carpeta; no imprimir nunca la linea de comandos de un proceso `java` (son miles de caracteres).
- **Un ajuste nuevo de `PadSettings` se guarda solo (Gson) y lo copian `copyFrom`/`resetToDefaults` por reflexion:** la prueba de la casilla 48 cambia todos los campos uno a uno, asi que un campo nuevo que no se copie la hace fallar. Los de identidad (nombre, color de etiqueta) se listan en `PadSettings.isIdentity`.
- **Un efecto por jugador se decide con el contexto activo** (`ContextSwitcher.current()`, P1 si no hay) en tick/HUD y con `SplitRender.passSlot` en la pasada de dibujo del mundo; los contadores de lo omitido (`ReduceEffects`) permiten medirlo.
- **En el autotest puede haber mandos reales (slots 0 y 1):** para probar una desconexion usar `PadManager.simulateDisconnect/simulateReconnect`, que esconden el mando del slot sin desenchufar nada.
- **Probar el apuntar con arco necesita `keyUse` pulsada** (si no, `Minecraft.handleKeybinds` suelta el objeto al instante) y el arco ya en la mano del cliente antes de tensarlo.
- **Los parches de Python con comillas triples y apostrofes se escriben con Write a un archivo y se ejecutan con `python archivo.py`;** los `replace(..., 1)` que no encuentran el texto fallan en silencio: aserta que el texto esta.

### Reglas aprendidas del cierre de la v1 (2026-10-10)

- **Un efecto "que no se ve" en un jugador extra puede tener VARIOS filtros en cadena** (aqui `ParticleEngineMixin` y `SplitLevelRenderer.addParticle`): comprobar cada paso de la ruta (crear -> anadir -> dibujar) con una medida antes de dar el arreglo por bueno.
- **Las particulas pertenecen a un nivel** (`Particle.level`): el motor es uno, `ContextSwitcher` le cambia el nivel al entrar en un contexto y la pasada de dibujo filtra por `Minecraft.level`. Las pruebas de particulas cuentan solo la clase que miden (`TerrainParticle`): la lluvia (`WaterDropParticle`) y los efectos de otros jugadores aparecen cerca de cualquiera.
- **Un paquete de un extra puede procesarse despues de que su jugador salga:** `PlayerContext.released` lo descarta. `tools/check-clean-exit.sh <log>` es el juez del cierre; `CONSOLEPAD_EXIT_PLAYERS=1|4` hace que la casilla 49 cierre el mundo con ese numero de jugadores.
- **Las pruebas que cambian el inventario o dejan objetos sueltos deben guardar y restaurar las 36 casillas y quitar los objetos:** el mundo "New World" es compartido y otras pruebas (`actionsTest`) cuentan la tierra de la barra.

### Reglas aprendidas de la 0.12.0 (2026-10-15)

- **Todo lo que una prueba mide debe fijarlo ella al empezar, porque el usuario juega en el mismo mundo y en la misma carpeta `run/`:** perfil y margen del HUD de P1, libro de recetas abierto (estado por jugador guardado en el mundo), reparto de pantalla (recordado por mundo), modo de juego, escala de interfaz y foco de la ventana (`pauseOnLostFocus` se apaga ANTES de abrir el mundo). Un fallo que aparece solo en la pasada completa y no suelto suele ser de estado: repetir una cadena corta de casillas (`CONSOLEPAD_AUTOTEST_CASES=40,41,...`) y registrar region, orientacion y jugadores dentro.
- **Al subir de version hay que cambiar tambien `version` en `src/main/resources/META-INF/neoforge.mods.toml`** (es literal, no se rellena solo con `mod_version`): si no, el jar se anuncia con una version vieja en la lista de mods.
- **Otros juegos abiertos de otros mods no molestan al autotest ni los cierra `run-autotest.sh`** (solo mata el de esta carpeta, por `fml.modFolders=consolepad`); comprobarlo sin imprimir lineas de comandos. No compilar (`./gradlew build`) mientras un juego de pruebas esta corriendo (ClassNotFound).
- **Leer los datos de una medida ANTES de que la propia prueba cambie el estado** (apagar a P2 y volver a llamar a `render` ponia a cero los contadores que se leian despues).

### Reglas aprendidas de la prueba de shaders (2026-10-17)

- **Sodium e Iris (incluso 0.6.13 y 1.8.12) empaquetan modulos de Fabric API que exigen NeoForge 21.1.169 o mas; el entorno de desarrollo usa 21.1.92 y el juego no carga** ("Mod fabric_api_base requires neoforge ..."). Para probarlos sin tocar `gradle.properties`: `ORG_GRADLE_PROJECT_neo_version=21.1.248` en el entorno (esta en la cache de Gradle) y los jars en `run/mods`. Con jars de otros mods en `run/mods` cualquier arranque normal falla: quitarlos y restaurar `run/config/iris.properties` y `split.json` al terminar.
- **Las capturas de `CONSOLEPAD_SHOTS=<ruta relativa>` salen en `run/<ruta>`** (la carpeta de trabajo del juego es `run/`).
- **Resultado con shaders (Complementary Reimagined) y `keepShaders=true`:** P1 correcto, P2 a P4 lavados o blancos. No ofrecer "shaders en pantalla dividida" hasta que cada vista tenga su propio framebuffer.

### Reglas aprendidas de los ajustes tras probar la 0.13.0 (2026-10-17)

- **Lo que se dibuja encima del HUD de P1 va a `Companions.OVERLAY_Z` (1200), no a 300:** el HUD de P1 lo dibujan las capas de NeoForge (`LayeredDraw`), que se separan 200 unidades de z cada una, y la barra de experiencia quedaba por encima del punto del companero. En P2 a P4 el HUD se dibuja a mano a z = 0 y no lo ve. Un fallo "solo en P1" asi se comprueba leyendo el pixel (`AutoTest.probe`, `CompanionPixelTest`), no con la posicion.
- **Las acciones de P2 a P4 se tratan una por una en `SecondaryTick`** (`switch (act)`): una accion de `PadAction` sin caso ahi no hace nada (asi pasaba con `PICK_BLOCK`). Al anadir una accion nueva a P1, repasar ese `switch`. Los metodos privados de `Minecraft` se llaman con un `@Invoker` de `MinecraftInvoker`.
- **Un ajuste con valor guardado en `split.json` manda sobre el valor por defecto nuevo** (`language` quedo en "auto"): con el juego cerrado, cambiarlo tambien en `run/config/consolepad/split.json`.
- **`java` por defecto es el 8:** para `java Archivo.java` (utilidades sueltas como recortar capturas) usar `C:/Users/<usuario>/.jdks/jbr-21.0.11/bin/java`, desde la carpeta del archivo y con rutas de Windows (`C:/...`).

### Reglas aprendidas de los shaders propios (2026-10-21)

- **Un shader sirve para la pantalla dividida si no guarda estado entre fotogramas (sin exposicion automatica, TAA, mapa de sombras ni texturas "anteriores") y saca las coordenadas de `gl_FragCoord.xy / vec2(viewWidth, viewHeight)`, no de las coordenadas de textura del cuadrado:** el cuadrado de los pasos composite/final solo cubre la region del jugador y las texturas de Iris tienen el tamano de la ventana. `tools/shaderpack/ConsolePadLite` (solo `final.vsh/fsh`, saturacion y contraste) se ve igual en las 4 regiones; Complementary Reimagined sale blanco en P2 a P4.
- **Iris acepta un pack en carpeta dentro de `run/shaderpacks/` (`shaderPack=ConsolePadLite` en `run/config/iris.properties`)** y usa los programas de vanilla para lo que el pack no define. Receta de prueba: Sodium 0.6.13 + Iris 1.8.12 en `run/mods`, `ORG_GRADLE_PROJECT_neo_version=21.1.248`, `keepShaders=true` en `split.json`, `CONSOLEPAD_SHOTS=<carpeta> tools/run-autotest.sh <log>`, y al terminar quitar los jars y restaurar `iris.properties` y `split.json`.

### Reglas aprendidas de jugar con shaders (2026-10-21)

- **"Arranca el juego" del usuario = con shaders y sin quitarlos al cerrar:** `tools/jugar.sh` instala (si falta) el kit de `run/shader-kit` (Sodium 0.6.13 + Iris 1.8.12) en `run/mods`, deja el pack `ConsolePadLite.zip` elegido en `run/config/iris.properties` y lanza con `ORG_GRADLE_PROJECT_neo_version=21.1.248` (sus modulos de Fabric API no cargan con el NeoForge 21.1.92 de desarrollo). No lanzar nunca `./gradlew runClient` a pelo con esos jars puestos.
- **El autotest va sin shaders:** `tools/run-autotest.sh` mueve los jars de `run/mods` a `run/mods.off` mientras corre y los devuelve al terminar (miden colores de pixel y la version de NeoForge cambia). `CONSOLEPAD_KEEP_MODS=1` los deja puestos (pruebas de shaders, con `ORG_GRADLE_PROJECT_neo_version=21.1.248`). Si una ejecucion se corta con los jars escondidos, `jugar.sh` los devuelve.
- **El oro (u otro objeto) que una prueba deja en el inventario de P2 en el mundo compartido falsea la siguiente pasada:** limpiar lo que se cuenta antes de preparar la prueba (asi se arreglo "mover rapido del cofre").

### Reglas aprendidas del perfil de CPU (2026-10-22)

- **Para saber donde se va el tiempo, perfilar antes de optimizar:** `tools/perfil-jfr.sh <etiqueta> [jugadores]` y `python tools/analiza-jfr.py run/perf/<etiqueta>.jfr main GameRenderer.render [funcion]`. El hilo del juego en JFR se llama `main` (no "Render thread"); el perfilador atribuye mal algunas llamadas (salian `ServerChunkCache.getChunk` inexistentes): confirmar con una linea de log antes de actuar. Comparar en las 4 pasadas (`cpuPorPasada`), no solo en FPS (el ruido entre pasadas es de +-15 %).
- **El `ParticleEngine` es uno y cada pasada recorre las particulas de todos los mundos:** cualquier trabajo por particula (caja de dibujo, prueba de campo de vision, ajustes) hay que filtrarlo por mundo ANTES (`ParticleEngineMixin`) y los ajustes leerlos una vez por fotograma, no por particula (`AdaptivePerf.savingChanged`).
- **Una copia en cache de un ajuste debe actualizarse al cambiarlo, no solo en el siguiente fotograma:** las pruebas lo cambian y lo leen al instante.

### Cambio (2026-10-23): shaders apagados por defecto

- **"Arranca el juego" ya NO lleva shaders** (el usuario pidio "quita los shader"): `tools/jugar.sh` no instala el kit salvo con `CONSOLEPAD_SHADERS=1`; `run/mods` queda vacio (los jars siguen en `run/shader-kit`). Esto sustituye a la regla de "jugar con shaders" de arriba.
