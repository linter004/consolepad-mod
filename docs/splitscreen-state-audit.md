# Auditoría del estado de cliente compartido entre jugadores (2026-10-08)

Motivo: con 2 a 4 jugadores, `GameRenderer`, `Camera`, `LevelRenderer` y `Minecraft` guardan estado que vanilla da por único (el de P1). Todo lo que cambia por tick o por fotograma y depende del jugador debe ser por jugador: se cambia al entrar en su contexto (`ContextSwitcher.run`, siempre con `try/finally`) y se actualiza dentro de él. Fuentes leídas: `GameRenderer.tick/renderLevel`, `Camera.tick/setup`, `LevelRenderer.tick/tickRain`, `LightTexture`, `FogRenderer`, `Minecraft.tick`, `Gui`.

**Hipótesis del usuario: confirmada.** `GameRenderer.mainCamera` es única y `Camera.tick()` suaviza `eyeHeight` hacia el ojo de la *última entidad* con la que se hizo `setup` (la pasada de P1, que se dibuja la última). Resultado: lo que cambia la altura de ojos de P1 (élitros: ojos a 0,4; agacharse: 1,27) lo veían todos, y agacharse en P2 a P4 no cambiaba su vista. Medido con la cámara compartida: P2 agachado (ojos de su jugador 1,27) pero la cámara que dibuja su región seguía en 1,62. Además `LevelRenderer.tick()` y `tickRain()` solo corrían para P1: los renderers de P2 a P4 nunca avanzaban (`ticks` parado = lluvia y nubes congeladas, sin gotas de lluvia junto a ellos).

| Estado | Dónde | Antes | Ahora |
|---|---|---|---|
| Cámara (posición, rotación, altura de ojos suavizada, entidad) | `GameRenderer.mainCamera`, `Camera.tick` | única, la seguía P1 | una `Camera` por `PlayerContext` (`ContextSwitcher.cameraOr`, redirección de `GETFIELD` en `renderLevel/tick/resetData/getMainCamera`), tick propio en `SecondaryTick`, altura de ojos inicial real |
| Mapa de luz (visión nocturna, oscuridad, parpadeo del bloque) | `GameRenderer.lightTexture`, `LightTexture.tick/updateLightTexture` | único, solo se recalculaba con los datos de P1 | un `LightTexture` por contexto (`lightOr`), tick propio, se cierra al salir el jugador |
| Tick del `LevelRenderer` (nubes, lluvia, progreso de rotura) y `tickRain` | `Minecraft.tick`, `GameRenderer.tick` | solo P1 | `SplitLevelRenderer.tickWeather` por jugador, con su cámara |
| Gotas de lluvia/humo de lluvia | `LevelRenderer.tickRain` → `ClientLevel.addParticle` | los extra descartaban todas sus partículas | solo las gotas de su lluvia pasan al `ParticleEngine` compartido (`SplitView.particleBypass`); el resto de partículas de un extra se siguen descartando porque el servidor ya manda sus efectos a todos y saldrían duplicadas |
| Animación del tótem | `GameRenderer.displayItemActivation` | el tótem de P2 se dibujaba sobre P1 | se ignora cuando lo pide un extra (por jugador queda pendiente) |
| Niebla (color y transición entre biomas, estáticos) | `FogRenderer` | un solo estado para todas las pasadas | cada pasada carga y guarda el suyo (`SplitRender.loadFog/storeFog`); la transición de bioma solo se usa bajo el agua |
| FOV suavizado, mano en primera persona, tipo de cámara, `hitResult`, pantalla | `GameRenderer`, `Options`, `Minecraft` | ya era por jugador | sin cambios |
| Estado del `Gui` (vida, nombre del objeto, viñeta, catalejo), barras de jefe | `Gui` | por jugador desde la tanda anterior | sin cambios |

## Pendiente (con el motivo)
- **Oscurecimiento del mundo por barras de jefe** (`darkenWorldAmount`): un único valor de `GameRenderer`; solo reacciona a las barras de P1. Es un matiz visual poco frecuente.
- **Animación del tótem por región:** hoy no se dibuja para los extra; dibujarla exige moverla a `SplitHud` con su propio temporizador.
- **Efectos de pantalla completa de `GameRenderer.postEffect`** (visión de araña/creeper, espectador): solo P1.
- **Partículas ambientales (`ClientLevel.animateTick`: antorchas, lava, portales) cerca de cada extra:** no se generan; se generarían duplicadas con las de P1 si están cerca. Hay que decidir por distancia a P1.
- **Sonido por jugador** (elytra, lluvia, ambiente) y **partículas propias de los extra** (rotura, andar, golpes): siguen silenciados por los guardas.
