# ConsolePad — Fase 3: plan de pantalla dividida local (2–4 jugadores)

Informe de diseño. No hay cambios de código asociados.

**Leyenda**

- **[V] Verificado:** lo he leído en las fuentes de NeoForge 21.1.92 / Minecraft 1.21.1 (mappings Mojang + Parchment), concretamente en `build/moddev/artifacts/neoforge-21.1.92-sources.jar`. Cito la clase y la línea aproximada de ese jar.
- **[S] Suposición:** es razonamiento, experiencia o estimación que no he comprobado en código. Hay que validarlo con el prototipo.

---

## 0. Resumen y recomendación

Para cada jugador extra (P2–P4) se recomienda abrir **una conexión en memoria adicional contra el servidor integrado**, igual que hace el propio juego para el jugador 1.

- **Qué tiene cada jugador:** su propio `ClientPacketListener`, `ClientLevel`, `LocalPlayer`, `MultiPlayerGameMode`, `LevelRenderer` y `Camera`.
- **Cómo se cambia de jugador:** Minecraft accede al jugador por campos globales de `Minecraft`. Un mecanismo de **cambio de contexto** sustituye esos campos mientras se procesa, se actualiza (tick) o se dibuja cada jugador.
- **Cómo se dibuja:** el mundo se dibuja una vez por jugador en su región de la ventana.
- **Pausa:** el servidor integrado no se pausa mientras haya más de un jugador local.

Es la opción más cara en memoria. A cambio, es la única en la que el servidor trata a cada jugador como un jugador real. Eso incluye chunks alrededor de cada uno, inventario, guardado de datos y estadísticas, sin reimplementar nada de la lógica de juego.

---

## 1. Cómo tener un segundo jugador local

### Hechos verificados del código

- **[V] El jugador 1 ya entra por una conexión en memoria.** `Minecraft.doWorldLoad` (aprox. `Minecraft.java:2115`) hace lo siguiente:
  1. Llama a `singleplayerServer.getConnection().startMemoryChannel()`.
  2. Llama a `Connection.connectToLocalServer(address)`.
  3. Llama a `initiateServerboundPlayConnection(..., new ClientHandshakePacketListenerImpl(...))`.
  4. Envía `ServerboundHelloPacket(name, uuid)`.
- **[V] Cada llamada a `startMemoryChannel()` crea un `LocalServerChannel` nuevo.** Lo hace con su propio `ServerBootstrap` (`ServerConnectionListener.java:123`). Cada conexión entrante recibe un `MemoryServerHandshakePacketListenerImpl`, así que se puede llamar otra vez para el jugador 2.
- **[V] Las conexiones en memoria no se autentican contra Mojang.** En `ServerLoginPacketListenerImpl.handleHello` (aprox. línea 119):
  - Si el nombre coincide con el perfil del dueño (`getSingleplayerProfile()`), usa ese perfil.
  - Si no, con `isMemoryConnection()` se llama a `startClientVerification(UUIDUtil.createOfflineProfile(name))`.
  - Por tanto, P2 obtiene un perfil *offline* con UUID derivado del nombre, y sus datos se guardan en `playerdata/<uuid>.dat`.
- **[V] El servidor integrado admite 8 jugadores.** `IntegratedPlayerList` llama a `super(..., 8)`, así que 4 jugadores locales caben.
- **[V] No se pueden repetir nombres.** `PlayerList.getPlayerForLogin` desconecta al jugador existente con el mismo perfil (`DUPLICATE_LOGIN_DISCONNECT_MESSAGE`). P2 necesita un nombre distinto, por ejemplo `<nombreP1>_2`. El nombre debe pasar `StringUtil.isValidPlayerName`: 16 caracteres como máximo y caracteres válidos.
- **[V] Al iniciar sesión, el cliente pisa los campos globales.** `ClientPacketListener.handleLogin` (aprox. líneas 408–449) asigna `minecraft.gameMode`, crea `ClientLevel`, llama a `minecraft.setLevel(...)`, asigna `minecraft.player` y `minecraft.cameraEntity`. `handleRespawn` (aprox. líneas 1167–1204) hace lo mismo. Una segunda conexión sin aislar **sustituiría al jugador 1**.
- **[V] Los paquetes se procesan en el hilo principal.** Pasan por `PacketUtils.ensureRunningOnSameThread`, que reencola `packet.handle(processor)` con `executor.executeIfPossible(...)`. Ese es el punto donde envolver el procesamiento de los paquetes de P2 con su contexto.
- **[V] La conexión de juego se procesa desde `MultiPlayerGameMode.tick()`.** Lo hace con `this.connection.getConnection().tick()` (línea 281), llamado desde `Minecraft.tick()`. Cada contexto necesita que su `gameMode.tick()` se ejecute.

### Opciones

| Opción | Descripción | Ventajas | Riesgos |
|---|---|---|---|
| **A. Conexión en memoria extra por jugador (recomendada)** | P2..P4 hacen el mismo login que P1 por `startMemoryChannel()`, cada uno con su propio estado de cliente. | El servidor los trata como jugadores reales: chunks, inventario, muerte y reaparición, logros, `playerdata`. Cada jugador ve a los demás como `RemotePlayer`, que es el camino normal. | Hay que aislar todo el estado global del cliente (sección 3). Memoria: N `ClientLevel` y N cachés de chunks y mallas. Muchos mods asumen un solo `LocalPlayer`. |
| **B. Un solo cliente; P2 como `ServerPlayer` "falso" controlado por paquetes del mod** | Una única conexión. El mod crea en el servidor un `ServerPlayer` con conexión ficticia y le envía la entrada de P2 con un payload propio. | Un único `ClientLevel` y una sola caché de chunks: menos memoria. | **[S]** El servidor manda los chunks de P2 a la conexión de P2, que es ficticia. La caché del cliente sigue centrada en P1, así que si P2 se aleja, P1 no tiene sus chunks. Además, P2 en el cliente sería un `RemotePlayer` sin predicción local: movimiento con lag y sin inventario ni pantallas propias. Habría que reimplementar mucho. |
| **C. Varias instancias del juego** | Lanzar 2–4 procesos de Minecraft y colocar sus ventanas en mosaico (LAN o mismo servidor). | Cero cambios en el motor. Máxima compatibilidad con otros mods. | No es "una ventana". Unos 2–4 GB de RAM por instancia. Cada instancia necesita su cuenta o un servidor offline. El foco de teclado y ratón queda en una sola ventana, aunque los mandos se leen igual por GLFW. Es más un lanzador que un mod. |

**Conclusión: la opción A.** La B parece barata pero obliga a reescribir la mitad del cliente para P2. La C es un buen plan B si la A resulta inviable con los mods del usuario.

### Clases de 1.21.1 que intervienen (opción A)

- **Conexión:** `ServerConnectionListener.startMemoryChannel`, `Connection.connectToLocalServer`, `Connection.initiateServerboundPlayConnection`, `ServerboundHelloPacket`.
- **Login:** `ClientHandshakePacketListenerImpl` (habrá que usar una subclase o mixin para que no cambie la pantalla ni `minecraft.pendingConnection` de P1), `ServerLoginPacketListenerImpl`, `MemoryServerHandshakePacketListenerImpl`, `PlayerList.placeNewPlayer` y `getPlayerForLogin`.
- **Estado de cliente por jugador:** `ClientPacketListener`, `ClientLevel`, `LocalPlayer`, `MultiPlayerGameMode`, `ClientChunkCache`.
- **Planificación:** `PacketUtils.ensureRunningOnSameThread` y `Minecraft` como `BlockableEventLoop`.

---

## 2. Dibujar el mundo una vez por jugador

### Hechos verificados

- **[V] `GameRenderer` tiene una sola `Camera`.** Es el campo `mainCamera` (`GameRenderer.java:129`). `renderLevel(DeltaTracker)` (aprox. línea 1231) la configura con `minecraft.cameraEntity` y llama a `levelRenderer.renderLevel(...)`.
- **[V] El aspecto de la proyección sale de la ventana completa.** `getProjectionMatrix` usa `window.getWidth() / window.getHeight()` (aprox. línea 980). En pantalla dividida hay que sustituirlo por el aspecto del viewport, con un `@ModifyArg` o `@WrapOperation`. Si no, la imagen sale deformada.
- **[V] `LevelRenderer` guarda estado ligado a una cámara y a un jugador:**
  - `viewArea`, que es la rejilla de secciones alrededor del jugador.
  - `sectionOcclusionGraph`, `visibleSections`, `cullingFrustum`, `lastCameraSectionX/Y/Z` y `prevCamX/Y/Z`.
  - En `setupRender` (aprox. línea 815) recentra `viewArea` con **`minecraft.player`**, no con la cámara, e invalida el grafo de oclusión cuando la cámara cambia de celda de 8 bloques.

  Si se dibujan dos jugadores lejanos con un solo `LevelRenderer`, la rejilla y el grafo se recalculan dos veces por frame: rendimiento desastroso y parpadeo.
- **[V] `LevelRenderer` está atado a un único `ClientLevel`.** Lo fija `setLevel` (aprox. línea 683), y crea su propio `SectionRenderDispatcher(level, renderer, ...)`.
- **[V] Los campos de `Minecraft` que hay que sustituir son finales.** `levelRenderer`, `gameRenderer`, `gui` y `particleEngine` son `public final`, y `mainRenderTarget` es `private final` (`Minecraft.java:278–309`). Sustituirlos requiere accessors con `@Mutable`.

### Diseño propuesto

1. **Un `LevelRenderer` por jugador.** Con la opción A cada jugador ya tiene su propio `ClientLevel`, así que tener un renderer por jugador es natural. P1 usa el de vanilla. P2..P4 crean el suyo con `new LevelRenderer(mc, entityRenderDispatcher, blockEntityRenderDispatcher, renderBuffers)` y `setLevel(ctx.level)`. **[S]** Hay que confirmar que comparten sin problemas `RenderBuffers` y los dispatchers. Lo más prudente es un `RenderBuffers` por jugador.
2. **Bucle de dibujo.** En cada frame, para cada jugador:
   1. Activar su contexto: `player`, `level`, `cameraEntity`, `levelRenderer`, `hitResult` y `gameMode`.
   2. Fijar el viewport y scissor de su región, con `RenderSystem.viewport` y `enableScissor`, sobre `mainRenderTarget`.
   3. Llamar a `gameRenderer.renderLevel(deltaTracker)` con el aspecto de su región.
   4. Restaurar el estado.

   El HUD se dibuja después, también por región (sección 3).
   - **[S] Alternativa más robusta:** un `TextureTarget` por jugador, del tamaño de su región, dibujado y luego copiado a la ventana. Aísla efectos de post-procesado como la visión de criaturas en espectador o la desaturación. Cuesta algo más de VRAM y una copia por frame.
3. **Cámara.** La `mainCamera` de `GameRenderer` se reconfigura en cada llamada a `renderLevel` con `camera.setup(...)`, así que puede reutilizarse en secuencia. **[S]** El *bobbing*, el FOV dinámico (`fovModifier`) y `itemInHandRenderer` son estado de `GameRenderer`. Hay que guardarlos y restaurarlos por jugador, o aceptar pequeños fallos en el prototipo.

### Coste de rendimiento esperado **[S]**

Son estimaciones sin medir; hay que medirlas en el paso 4 del prototipo.

- **CPU de render** (recorrido de secciones, entidades y llamadas de dibujo): escala casi linealmente con N. Con 2 jugadores, el render del mundo cuesta ×1,8–2. Con 4, ×3,5–4.
- **GPU:** la tasa de relleno total es parecida, porque cada viewport es más pequeño. Los vértices y las llamadas de dibujo se multiplican por N.
- **Generación de mallas de chunks:** cada `LevelRenderer` compila sus propias secciones. Si los jugadores están juntos, se compila lo mismo N veces. Si están separados, se compila más mundo.
- **Memoria:** N cachés de chunks del cliente más N juegos de mallas en VRAM. Con distancia 12 calculo unos 300–600 MB extra por jugador entre RAM y VRAM.
- **Recomendación práctica:** distancia de renderizado efectiva por jugador de 8 con 2 jugadores y de 6 con 4. En un PC donde un jugador va a 120 FPS, espero unos 60–70 FPS con 2 jugadores y unos 30–40 con 4.

---

## 3. Qué duplicar por jugador y cómo sobrevivir al `Minecraft.getInstance().player` global

### Magnitud del problema

**[V]** En las fuentes de cliente de NeoForge más Minecraft hay **unas 330 referencias** a `minecraft.player` / `Minecraft.getInstance().player` / `mc.player`, repartidas en **unos 59 archivos**. Lo conté con grep sobre el jar de fuentes. Reescribirlas no es viable.

### Estrategia: contexto por jugador e intercambio de campos

Se define una clase `PlayerContext` con todo el estado de un jugador:

- **Campos de `Minecraft` que se intercambian:** `player`, `level`, `gameMode`, `cameraEntity`, `crosshairPickEntity`, `hitResult`, `screen`, `levelRenderer` (`@Mutable`), `missTime` y `rightClickDelay`.
- **Estado de `GameRenderer`:** FOV, bobbing y `itemInHandRenderer`.
- **Por jugador:** `particleEngine` (opcional) y su `PadManager.Slot`.

Un método `ContextSwitcher.run(ctx, Runnable)` guarda los campos actuales, pone los de `ctx`, ejecuta y restaura. Es lo mismo que el juego ya hace de forma implícita al tener un solo jugador.

Puntos donde activar el contexto:

| Momento | Dónde enganchar | Comprobación |
|---|---|---|
| Procesar paquetes de P_k | **[V]** El lambda reencolado en `PacketUtils.ensureRunningOnSameThread`. Si `processor` es el `ClientPacketListener` de P_k, se envuelve `packet.handle(processor)`, con `@WrapOperation` de MixinExtras (0.4.1, presente según el log de arranque). | Tras cada paquete de P2, `Minecraft.player` vuelve a ser el de P1. |
| Tick de cliente de P_k | Se replican, con el contexto de P_k, las partes de `Minecraft.tick()` que afectan a un jugador: `gameMode.tick()` (incluye `connection.tick()`), `level.tickEntities()`, `level.tick()`, el procesamiento de acciones (`startAttack`, `startUseItem` y `continueAttack`, privados, vía `@Invoker`) y `gui.tick`. | P2 se mueve, pica y usa objetos. |
| Dibujo de P_k | El bucle de la sección 2. | Las dos vistas son distintas y correctas. |

### Qué duplicar

- **Entrada:**
  - P1 conserva teclado, ratón y el mando del slot 0, con el `KeyboardInput` actual y el mixin ya existente.
  - **Para P2..P4 no se usan las `KeyMapping` globales.** Son estáticas y compartidas (`KeyMapping.ALL`), y el `PadDriver` actual las pulsa directamente. En su lugar, cada `LocalPlayer` extra recibe un `Input` propio (`PadInput extends Input`) alimentado por su slot.
  - Las acciones (atacar, usar, soltar, hotbar) se ejecutan llamando a `gameMode` y `player` dentro de su contexto, sin pasar por `KeyMapping`.
  - Esto exige refactorizar `PadDriver` para que trabaje por slot. Hoy solo atiende al slot 0 y escribe en campos estáticos (`analogForward` y similares).
- **HUD (`Gui`):** `Gui.render` dibuja con `GuiGraphics`, cuyo tamaño viene de `window.getGuiScaledWidth()` y `getGuiScaledHeight()`. **[S]** Para dibujarlo por región hay dos opciones:
  - Cambiar temporalmente el tamaño escalado que devuelve `Window`, con un mixin en esos getters, y trasladar y recortar la pose.
  - Hacerlo con una pose escalada y scissor.

  Hay una `Gui` por jugador, o la misma con su estado (títulos, mensajes de acción, chat) intercambiado. El chat puede ser común en la fase 3.
- **Pantallas e inventario:**
  - `minecraft.screen` es único. Cada contexto tiene su `screen`, que se dibuja en su región; `Screen.init` recibe el ancho y alto de la región.
  - El cursor virtual de la fase 2 pasa a ser un cursor por jugador, porque el cursor de GLFW es único: hay que dejar de usar `glfwSetCursorPos` para P2..P4 y enviar eventos sintéticos.
  - **El prototipo (sección 6) excluye las pantallas por jugador.**
- **Opciones (`Options`):** se comparten. FOV, distancia de renderizado y sonido son globales. Lo específico de cada jugador (sensibilidad, bindings, inversión) ya está en `PadSettings` por slot.
- **Sonido:** **[S]** `SoundManager` tiene un solo oyente. Opciones: usar como oyente a P1, o el punto medio. En la fase 3 se deja en P1.
- **Partículas:** **[S]** `ParticleEngine` está ligado al nivel. Con varios `ClientLevel`, lo correcto es uno por contexto; en el prototipo se puede desactivar para P2..P4.

### Riesgos

- **[S]** Otros mods que guardan referencias al jugador o al nivel en campos estáticos verán al jugador equivocado durante el contexto de P2.
- **[S]** Los eventos de NeoForge (`ClientTickEvent`, `RenderLevelStageEvent`, `RenderGuiEvent`) se dispararán una vez por jugador o solo una, según dónde se enganche. Hay que decidirlo y documentarlo.
- **[S]** Los hilos de compilación de chunks (`SectionRenderDispatcher`) no deben leer `Minecraft.getInstance().level` mientras hay un contexto activo. Su código usa la `ClientLevel` que reciben al construirse ([V]: el constructor toma `ClientLevel level`), pero hay que vigilar otros accesos.

---

## 4. Pausa por jugador sin pausar a los demás

### Hechos verificados

- **[V]** `Minecraft.runTick` calcula `pause = hasSingleplayerServer() && (screen != null && screen.isPauseScreen() || overlay...) && !singleplayerServer.isPublished()` (aprox. `Minecraft.java:1232`). Un único `screen` decide la pausa global.
- **[V]** `IntegratedServer.tickServer` hace `paused = Minecraft.getInstance().isPaused()`. Si está pausado, llama a `tickPaused()`, que no actualiza el mundo, y además guarda la partida al entrar en pausa (`"Saving and pausing game..."`). Esto está en `IntegratedServer.java:94–110`.
- **[V]** Si el mundo está publicado en LAN (`isPublished()`), no se pausa nunca, y `pauseGame` abre un `PauseScreen(true)`.

### Diseño

- **Cuando hay más de un jugador local**, el juego se comporta como si el mundo estuviera publicado en LAN: **el servidor nunca se pausa**. Se implementa con un `@ModifyExpressionValue` sobre el cálculo de `pause` en `runTick`, que lo fuerza a `false` si `SplitScreen.activePlayers() > 1`.
- **La pausa de un jugador** es solo su `PauseScreen` en su región. Mientras está abierta, ese jugador no recibe entrada, y su personaje sigue en el mundo y puede recibir daño, como en consola.
- **[S] Opción configurable:** pausa real solo si *todos* los jugadores tienen abierta una pantalla de pausa.
- **[S]** El guardado automático al pausar ya no se produce. El guardado periódico del servidor sigue funcionando.

---

## 5. Compatibilidad y limitaciones

### Sodium / Iris **[S, sin verificar: no tengo su código]**

- **Sodium** sustituye casi todo el interior de `LevelRenderer`: su `SodiumWorldRenderer` y su propio grafo de secciones. Asume un único renderer activo y una cámara por frame. Lo esperable es que dos `LevelRenderer` con Sodium fallen o compartan estado. Hacerlo funcionar exige integración específica con su API, si la hay.
- **Iris** añade *pipelines* de shaders con sombras y búferes propios del tamaño de la ventana, pensados para una cámara. Con varias cámaras es casi seguro que se rompa.
- **Para la fase 3:** detectar Sodium o Iris (con `ModList.get().isLoaded("sodium")` o `"iris"`) y **desactivar la pantalla dividida** con un aviso en el log y en el menú. También puede declararse `type="incompatible"` en `neoforge.mods.toml`. La compatibilidad queda para una fase posterior.

### Limitaciones de red y de cuentas

- **Mundo local:** sí. Es el caso principal. **[V]** P2..P4 entran sin autenticación por conexión en memoria y obtienen un perfil offline.
- **Mundo local publicado en LAN:** sí. Los jugadores locales extra siguen por memoria, y los de otros PCs por TCP con su cuenta.
- **Servidores externos:**
  - Cada jugador extra necesitaría su propia conexión TCP y un perfil.
  - En servidores con `online-mode=true`, cada perfil requiere su cuenta de Microsoft autenticada. **No es viable con una sola cuenta.**
  - Solo serían posibles servidores *offline-mode* (sin autenticación), y fuera del alcance de la fase 3.
- **[S] Permisos:** solo P1 es el dueño (`isSingleplayerOwner` compara por nombre, **[V]**). Con trucos activados, los demás podrían no tener permisos de comando según `PlayerList.isOp`. A decidir.
- **[V] Identidad:** el nombre de P2 debe ser válido y distinto, y su UUID offline es estable si el nombre es estable. Conviene derivarlo de P1 (`<P1>_2`) para que cada jugador conserve su inventario entre sesiones.

---

## 6. Prototipo mínimo propuesto

**Alcance:** 2 jugadores, pantalla dividida vertical (izquierda y derecha), mundo local. Sin pantallas ni menús por jugador: si P2 abre el inventario, se ignora. HUD mínimo para P2 (punto de mira y hotbar) o ninguno. Sin Sodium ni Iris.

**Juez común en todos los pasos:** `./gradlew build` en verde y `runClient` sin excepciones ni errores de mixin en el log.

| # | Paso | Comprobación objetiva |
|---|---|---|
| 1 | **Login de P2.** Con un atajo (por ejemplo Start+Back en el mando del slot 1), llamar a `startMemoryChannel()`, `Connection.connectToLocalServer()` y enviar `ServerboundHelloPacket("<P1>_2", uuidOffline)`, con un `ClientHandshakePacketListenerImpl` propio que no toque la pantalla de P1. | En el log del servidor aparece `<P1>_2 joined the game`. P1 ve la entidad de P2 en su mundo. `Minecraft.player` sigue siendo P1, comprobado con una línea de log tras el login. |
| 2 | **Contexto por jugador.** `PlayerContext` y `ContextSwitcher`, más el mixin en `PacketUtils.ensureRunningOnSameThread` que envuelve los paquetes de P2. | Se crea un `ClientLevel` y un `LocalPlayer` para P2. Una verificación en modo debug confirma que tras cada paquete de P2 `minecraft.player/level/gameMode` son los de P1. 0 desconexiones en 5 minutos. |
| 3 | **Tick y entrada de P2.** Ejecutar el tick de P2 en su contexto (`gameMode.tick()`, `level.tick*`), con `PadInput` para su `LocalPlayer` alimentado por el slot 1 (stick, saltar, atacar y usar). | P1 ve a P2 moverse, saltar y romper un bloque. La posición de P2 en el servidor (`/tp` o `/data get entity <P1>_2 Pos`) cambia. El slot 0 no mueve a P2 ni el slot 1 a P1. |
| 4 | **Dos viewports.** `LevelRenderer` para P2, bucle de dibujo con viewport y scissor por mitad, y aspecto de proyección corregido. | Una captura muestra dos vistas distintas y sin deformar. Se anotan los FPS de F3 con distancia 8 para 1 jugador y para 2 jugadores, para comparar con la estimación de la sección 2. |
| 5 | **HUD mínimo de P2.** Punto de mira y hotbar en su mitad. | Captura con las dos hotbars. Cambiar de objeto en P2 no afecta a P1. |
| 6 | **Sin pausa global.** Forzar `pause = false` si hay 2 jugadores. | P1 abre el menú de pausa y P2 sigue moviéndose. En el log no aparece `Saving and pausing game...`. |
| 7 | **Salida limpia.** Al salir P2 o al salir del mundo, cerrar su conexión y liberar su `LevelRenderer` y su `ClientLevel`. | Sin excepciones al salir. Existe `world/playerdata/<uuid P2>.dat` y al volver a entrar P2 conserva su inventario. |

Fuera del prototipo: pantallas e inventario por jugador, chat, sonido por jugador, 3–4 jugadores (generalización del paso 4), Sodium e Iris, y servidores externos.
