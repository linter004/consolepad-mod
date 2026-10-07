# Routing log

| Fecha | Tarea | Nivel que la resolvió | Intentos | Coste estimado |
|-------|-------|-----------------------|----------|----------------|
| 2026-10-07 | Fase 1 ConsolePad: limpiar plantilla NeoGradle y compilar con ModDevGradle (`./gradlew build`) | Sonnet 5.5, esfuerzo medio | 1 (sin errores de compilación) | Bajo (~15 llamadas a herramientas; el grueso del tiempo fue descargar Gradle/MC) |
| 2026-10-07 | Validar mixins con `./gradlew runClient` | Sonnet 5.5, esfuerzo medio | 1 (arranca sin errores; mando PS4 detectado como jugador 1; `KeyboardInputMixin` validado en un mundo: el stick izquierdo mueve al jugador) ✅ completada | Bajo (~6 llamadas; espera de descarga de assets) |
| 2026-10-07 | Ajustar controles por defecto (PICK_BLOCK, PLAYER_LIST, CHAT) + `build` + `runClient` | Sonnet 5.5, esfuerzo medio | 1 (build OK; el juego arranca sin errores; controles nuevos sin probar a mano) | Bajo (~8 llamadas) |
| 2026-10-07 | Fase 2.1: cursor virtual en pantallas (invokers `MouseHandler`/`AbstractContainerScreen`) + `build` + `runClient` | Sonnet 5.5, esfuerzo medio | 1 (build OK; arranca sin errores de mixin; invoker de `AbstractContainerScreen` y manejo con mando sin probar a mano) | Medio (~12 llamadas) |
| 2026-10-07 | Fase 2.2: sensibilidad del juego aplicada a `lookSpeed` (curva vanilla, 50 % = 1) | Sonnet 5.5, esfuerzo medio | 1 (build OK; sin probar a mano) | Bajo (~3 llamadas) |
| 2026-10-07 | Fase 2.3: guardar/cargar `PadSettings` por slot en `config/consolepad/slotN.json` (Gson, guardado en shutdown hook) + `build` + `runClient` | Sonnet 5.5, esfuerzo medio | 1 (build OK; arranca sin errores; el archivo se escribe al cerrar el juego: pendiente de comprobar) | Medio (~8 llamadas) |
| 2026-10-07 | Ajuste 1: cursor menos sensible (`cursorSpeed` 450, curva exponente 2.0, `cursorSlotFactor` 0.5 sobre slots) | Sonnet 5.5, esfuerzo medio | 1 (build OK; sin probar a mano) | Bajo (~4 llamadas) |
| 2026-10-07 | Ajuste 2: navegacion D-pad en pantallas (salto a slot en contenedores; `Screen.keyPressed` con flechas en menus; repeticion 0,35 s / 0,12 s) + `build` + `runClient` | Sonnet 5.5, esfuerzo medio | 1 (build OK; arranca sin errores de mixin ni de lectura del JSON; sin probar a mano) | Medio (~10 llamadas) |
| 2026-10-07 | `cursorSpeed` 450 -> 550 (defecto y `slot0..3.json`) y A activa el elemento enfocado (Enter) en menus normales en modo foco | Sonnet 5.5, esfuerzo medio | 1 (build OK; arranca sin errores de mixin; mando detectado; sin probar a mano) | Bajo (~7 llamadas) |
