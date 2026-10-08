# Hoja de ruta de la pantalla dividida (lo que falta)

Complementa `docs/splitscreen-plan.md` (diseño y prototipo, pasos 1-7). Primero funcionar, después pulir: el aspecto es el de Minecraft normal.
**[V]** = verificado con una medida numérica del autotest (`AutoTest`, `CONSOLEPAD_AUTOTEST="New World"`, `tools/run-autotest.sh`). **[S]** = supuesto, leído en las fuentes de 1.21.1 pero sin comprobar en ejecución.

## 0. Estado de partida

| Pieza | Estado |
|---|---|
| Login de P2 por conexión en memoria, contexto por jugador (`PlayerContext`, `ContextSwitcher`) | [V] 0 fallos de restauración tras >2 millones de paquetes de P2 |
| Tick y entrada de P2 con su mando (stick, saltar, atacar, usar, hotbar LB/RB) y bindings de P1 como base | [V] |
| Dos viewports (aspecto por región, `LevelRendererMixin` para el viewport) | [V] 0 muestras fuera de tolerancia a 854×480 y 1920×1080 |
| HUD: mira y hotbar de P2 en su mitad | [V] solo esas dos piezas |
| Sin pausa global con P2 dentro (`isPublished` fingido) | [V] mundo y servidor siguen con `PauseScreen` abierto |
| Salida y reentrada de P2, cierre al salir P1 del mundo | [V] `.dat` guardado, inventario conservado, 0 errores |
| FOV propio por jugador | [V] el efecto de correr de P1 ya no llega a P2 |
| Clic izquierdo del ratón de P1 no hace atacar a P2 | [V] 0 inicios de ataque, 0 swing en P2 |
| **Mano y objeto en mano de P2** | [V] **roto**: la mitad derecha dibuja el objeto de P1 (`ItemInHandRenderer` único, solo lo ticka P1) → 4a |

Coste medido (distancia 8, sin vsync): dividido ≈ 1,8-1,9× el de un jugador. Con 3-4 jugadores se espera ~3×/~4× [S].

## 1. Orden de ejecución y criterio de cada paso

Cada paso: implementar, `./gradlew build`, ampliar el autotest con una medida numérica, `runClient`, un commit y una línea en `routing-log.md`.

### 4a. HUD completo y mano de cada jugador
- **Mano:** `ItemInHandRenderer` propio por contexto (`PlayerContext.hand`), elegido por `@Redirect` del campo `GameRenderer.itemInHandRenderer` en `renderItemInHand` y en `Minecraft.startUseItem` (animación de "usar"); se ticka en el tick de P2 (su `tick()` lee `minecraft.player`). [S: nombres leídos en fuentes]
- **HUD de P2:** invocar por capas las piezas privadas de `Gui` dentro del contexto de P2: vida, armadura, comida, burbujas, barra y nivel de experiencia, nombre del objeto seleccionado, efectos. El estado propio de `Gui` que no puede compartirse (`lastHealth`, `displayHealth`, `lastHealthTime`, `healthBlinkTime`, `toolHighlightTimer`, `lastToolHighlight`) se guarda por jugador y se intercambia alrededor de cada dibujo. [S]
- **Medida:** el autotest registra, por capa y por mitad, el jugador al que pertenecen los datos y el ancho de interfaz usado; la mano de cada mitad debe ser el objeto de su jugador. Criterio: 0 capas con datos del jugador equivocado.
- Fuera: viñeta, calabaza, portal y demás superposiciones de cámara, cabalgaduras (barra de salto, vida del vehículo), bossbars, scoreboard, título (se quedan en P1). Pendiente por tamaño.

### 4b. Acciones restantes de P2 con los bindings de P1
Soltar, perspectiva, sprint, agacharse, inventario (abrir), cambio de mano. `SecondaryTick` ya lee `cfg.bindings`; faltan los casos del `switch` (hoy `default -> {}`) y `PadInput` (sprint, agacharse). El sprint de P2 lee `options.keySprint` (anulado por `KeyMappingGuardMixin`) → hay que dar la señal por `PadInput`. [S]
- **Medida:** simular cada botón en el slot 1 y comprobar el estado de P2 (sprinting, shiftKeyDown, mano cambiada, objeto soltado, `cameraType` propio) y que P1 no cambia.
- La perspectiva (F5) de P2 necesita `cameraType` por jugador: la cámara usa `options.getCameraType()` global → [S] requiere guardarlo en el contexto y redirigir en `Camera.setup`. Si resulta frágil, queda como pendiente.

### 4c. Pantallas por jugador (inventario, cofres, crafteo, horno…)
La parte más grande y arriesgada. Diseño propuesto [S]:
- `PlayerContext.screen`; `ContextSwitcher` intercambia también `Minecraft.screen` y se retira el `ci.cancel()` de `setScreen` para P2 cuando tiene su propia pantalla.
- Una pantalla de P2 se inicia (`init`) con el tamaño de su mitad y se dibuja con `SplitView.guiWidth` y traslación de la pose, dentro de su contexto.
- Cursor propio por jugador: `PadDriver.screenFrame` hoy es solo de P1; se generaliza a un controlador de cursor por slot, con coordenadas relativas a la mitad.
- Clics, arrastre y mover rápido llaman a `mouseClicked/Released/Dragged` de la pantalla de P2 en su contexto; `gameMode.handleInventoryMouseClick` ya es de P2.
- Riesgos: `setScreen` toca el ratón (liberar/capturar), el narrador y la pausa; `Screen` usa `minecraft.getWindow()` para el tamaño (hay que sustituirlo por el de la mitad vía `GuiGraphics.guiWidth` y `Screen.width`); tooltips y textos en coordenadas absolutas.
- **Medida:** con P2 en un cofre, un clic simulado mueve un objeto en el contenedor de P2 y no en el de P1; `mc.screen` de P1 sigue siendo `null`; sin excepciones.

### 4d. Pausa y menú por jugador
- Ya [V]: con P2 dentro, el menú de pausa de P1 no pausa el mundo. Falta: el menú de pausa de P2 (parte de 4c, pantalla en su contexto) y que su "Volver" lo cierre solo a él; "Salir" de P2 = `leave()`.
- Con más de un jugador local el mundo no se pausa nunca (ya está así por `SecondaryPlayers.isActive()`).
- **Medida:** abrir `PauseScreen` en P2 con P1 libre: P1 se mueve y P2 está en el menú; P2 pulsa "Volver" y se cierra solo su pantalla.

### 4e. Entrada y salida de jugadores
- Entrada: Start+Back del slot 1 ya une a P2 [V la lógica; el atajo no se ha probado con mando real]. Falta: asignar slot libre a P3/P4 y elegir el mando libre.
- Salida: `leave()` y `/consolepad leave2` ya existen [V]. Falta atajo por mando (por ejemplo Start+Back mantenido 2 s) y que P1 no pueda salir sin cerrar a los demás [V parcial: el cierre al salir P1 ya está].
- **Medida:** autotest ya cubre salida/reentrada; se amplía al atajo.

### 4f. 3 y 4 jugadores
- Particiones: 2 → mitades verticales; 3 → mitad izquierda para P1 y dos cuartos a la derecha; 4 → cuadrantes. El aspecto y la proyección ya se calculan por región [V]. `SplitView.guiWidth` pasa a necesitar también alto y desplazamiento (`guiHeight`, `x`, `y`).
- `SecondaryPlayers` hoy es un único P2 (singleton): se generaliza a una lista de hasta 3 jugadores extra con su `Connection`, `PlayerContext` y slot.
- Coste de GPU: ~N× [S]; para 4 jugadores se recomienda distancia de renderizado 6-8.
- **Medida:** aspecto por región con 3 y 4 jugadores; FPS por número de jugadores.

### 4g. Chat, avisos y ajustes por jugador
- Chat y avisos: hoy silenciados para P2 por los mixins "guarda". Mostrar los mensajes de P2 en su mitad requiere un `ChatComponent` por jugador. Pendiente de tamaño; mínimo viable: mensajes del sistema de P2 en su barra de acciones.
- Ajustes por jugador: sensibilidad y mapeado ya son por slot (`PadSettings`, `slotN.json`) [V para el mapeado]. FOV por jugador: el valor base sale de `options.fov` global; por jugador requiere una redirección en `GameRenderer.getFov` [S].
- **Medida:** FOV base distinto por jugador visible en `fovTest`.

## 2. Pendientes ya identificados (no bloquean lo anterior)
- El rendimiento con 4 jugadores puede ser insuficiente: medir antes de optimizar.
- Compensar el estrechamiento horizontal al unir a P2 (el FOV horizontal baja de 102° a 64° con el vertical fijo en 70°) con un ajuste de FOV vertical por región. Es estético; va al final.
- Sonido por jugador, partículas por jugador, Sodium/Iris y servidores externos quedan fuera.
- Prueba con mandos reales: la entrada de dos mandos físicos solo se ha verificado con simulación; el "atacan los dos" visto jugando no se reproduce por software (ver `routing-log.md`).
