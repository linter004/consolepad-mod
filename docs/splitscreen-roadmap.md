# Hoja de ruta de la pantalla dividida

Complementa `docs/splitscreen-plan.md` (diseño y prototipo, pasos 1-7). Primero funcionar, después pulir: el aspecto es el de Minecraft normal.
**[V]** = verificado con una medida numérica del autotest (`AutoTest`, `CONSOLEPAD_AUTOTEST="New World"`, lanzado con `tools/run-autotest.sh <salida>`). **[S]** = supuesto o sin medir (solo implementado o leído en las fuentes). Estado a 2026-10-08.

## 0. Estado

| Pieza | Estado |
|---|---|
| 2-4 jugadores locales por conexiones en memoria, contexto por jugador, tick y entrada por mando | [V] 0 fallos de restauración de contexto en todo el autotest |
| Pantalla dividida: 2 = mitades, 3 = mitad + dos cuartos, 4 = cuadrantes; aspecto por región; las regiones cubren la ventana sin solapes | [V] 854×480 y 1920×1080 |
| 4a Mano y objeto propios de cada jugador; HUD completo por jugador (mira, hotbar, vida, armadura, comida, burbujas, barra y nivel de experiencia, nombre del objeto, efectos) | [V] 10/10 capas por jugador, 0 con datos del jugador equivocado |
| 4b Soltar, cambio de mano, perspectiva propia, sprint, agacharse, abrir inventario con los bindings de P1 | [V] |
| 4c Inventario, cofre y mesa de crafteo por jugador, en su región, con cursor propio (clic, arrastre, mover rápido, scroll, cerrar) | [V] inventario, cofre, mesa; horno y demás usan la misma ruta [S] |
| 4d Menú de pausa por jugador sin pausar el mundo; "Salir" saca solo a ese jugador | [V] incluido el D-pad en inventario y menú |
| 4e Entrar con Start+Back y salir manteniéndolo 2 s | [V] simulado, también P3/P4 por atajo; con mando físico [S] |
| 4f 3 y 4 jugadores: regiones, HUD, inventarios escalados a su región (también el de P1, con el ratón reescalado), FPS | [V] |
| 4g Avisos propios (chat, barra de acciones, título) y FOV y sensibilidad por jugador desde su menú | [V] |
| Segunda tanda (2026-10-08): Start/B cierran el menu del propio jugador, desenfoque y objeto del cursor recortados a su region, pantalla de muerte por jugador, chat y avisos en la region de cada uno, escala de interfaz propia (automatica o 1-4), maniqui de P2-P4, menu ampliado (ajustes, controles remapeables, reparto lado a lado/apilado) | [V] `startCloseTest`, `uiClipTest`, `deathTest`, `chatTest`, `scaleTest`, `menusTest`, `mannequinTest` |
| Aislamiento P1 ↔ extras | [V] clic de ratón, teclas de sprint/agacharse, FOV al correr, títulos, inventario, chat |
| Tanda de consola (2026-10-08): aviso "Unirse como el jugador N" y Start para entrar, ayuda de botones en el HUD (contextual, iconos por tipo de mando) y barra de ayuda en pantallas, menú de pausa del mod para P1 solo, velocidad del cursor y navegación por casillas con el stick, teclado en pantalla (chat de P1 y de P2-P4, cartel, campos de texto), etiqueta con nombre configurable, doble toque para correr, sensibilidad X/Y, invertir eje Y, zona segura/opacidad/ocultar HUD y mano, y HUD de P2-P4 con viñeta, calabaza, portal, barras de jefe, marcador y lista de jugadores | [V] `joinPromptTest`, `hintsTest`, `soloMenuTest`, `snapNavTest`, `keyboardTest`, `labelTest`, `moveTest`, `hudOptionsTest`, `hudBoardsTest` |

FPS (distancia 8, sin vsync, 854×480): 1 jugador ≈ 3300, 2 jugadores ≈ 1600, 3 jugadores ≈ 765, 4 jugadores ≈ 408. El coste es casi proporcional al número de jugadores.

## 1. Pendientes (con el motivo)

- **Cabalgadura y depuración en P2-P4:** barra de salto y vida del vehículo, y la pantalla de depuración (F3) siguen siendo solo de P1. Motivo: pocas veces se usan con mando y cada una es otra capa del `Gui` con estado propio; se hacen igual que las barras de jefe (invoker + estado por jugador) si se piden.
- **Pantallas distintas del contenedor para P2-P4** (avances, estadísticas, ajustes de vanilla, libro, bloque de comandos): se ignoran a propósito. Sí se admiten el cartel (con teclado en pantalla), el chat propio (`ExtraChatScreen`) y las del mod. P1 solo con mando sigue sin "Avances", "Estadísticas" ni "Abrir a LAN" en su menú de pausa del mod (mismo motivo: otra fila de menú por cada una).
- **Compensar el estrechamiento horizontal al dividir:** el FOV vertical se mantiene (70°) y el horizontal baja de ≈102° a ≈64° con dos jugadores. Es estético; va al final.
- **Rendimiento:** con 4 jugadores el coste de GPU es ~4×; se puede bajar la distancia de renderizado. No se ha optimizado.
- **Teclado en pantalla:** distribución fija en español (sin teclas de acento muertas ni emojis); no hay deslizar ni predictivo. Para la búsqueda del inventario creativo (tanda 2) ya sirve el mismo teclado.
- **Etiqueta con nombre:** solo cambia el nombre para mostrar (etiqueta sobre la cabeza, chat, avisos, lista de jugadores), nunca el perfil del servidor (nombre y UUID siguen siendo `Dev_N`), así que los datos de cada jugador no se tocan. La skin propia por jugador va a la tanda 2.
- **Estado de cliente por jugador:** ver `docs/splitscreen-state-audit.md` (cámara, mapa de luz, lluvia y niebla ya por jugador; pendientes: oscurecimiento por barras de jefe, animación del tótem por región, efectos de pantalla completa, partículas ambientales y sonido por jugador).
- **`No value with id -1` al entrar P3 y P4 a la vez:** visto una vez, no reproducido en ~35 entradas; instrumentado para cuando vuelva.
- Sonido por jugador, partículas por jugador, Sodium/Iris y servidores externos quedan fuera.

## 2. Qué se ha comprobado solo con simulación (hay que probar con mandos reales)

Ver la lista de pruebas manuales al final de `routing-log.md` (sección "Estado actual").
