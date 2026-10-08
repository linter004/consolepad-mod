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
| 4d Menú de pausa por jugador sin pausar el mundo; "Salir" saca solo a ese jugador | [V] D-pad de los menús de P2-P4 [S] |
| 4e Entrar con Start+Back y salir manteniéndolo 2 s | [V] simulado; con mando físico [S]; P3/P4 por atajo [S] (por comando y `join` [V]) |
| 4f 3 y 4 jugadores: regiones, HUD, inventarios escalados a su región (también el de P1, con el ratón reescalado), FPS | [V] |
| 4g Avisos propios (chat, barra de acciones, título) y FOV y sensibilidad por jugador desde su menú | [V] |
| Aislamiento P1 ↔ extras | [V] clic de ratón, teclas de sprint/agacharse, FOV al correr, títulos, inventario, chat |

FPS (distancia 8, sin vsync, 854×480): 1 jugador ≈ 3300, 2 jugadores ≈ 1600, 3 jugadores ≈ 765, 4 jugadores ≈ 408. El coste es casi proporcional al número de jugadores.

## 1. Pendientes (con el motivo)

- **Maniquí del jugador en el inventario de P2-P4:** el recuadro sale vacío (el de P1 sí se dibuja). No bloquea; causa sin identificar (probable skin/`PlayerInfo` del jugador extra o estado de render de entidad).
- **Escribir en el chat con mando:** hace falta un teclado en pantalla; el chat con historial por jugador tampoco existe (hoy: últimos 5 avisos por jugador, sin entrada de texto).
- **HUD de P2-P4 que sigue siendo solo de P1:** superposiciones de cámara (viñeta, calabaza, portal), cabalgadura (barra de salto, vida del vehículo), bossbars, marcador, depuración y lista de jugadores.
- **Pantallas distintas del contenedor para P2-P4** (avances, ajustes de vanilla, muerte): se ignoran a propósito; la muerte se resuelve reapareciendo solo a los 3 s. El menú de pausa de P2-P4 es propio y mínimo (Volver, Campo de visión, Sensibilidad, Salir).
- **D-pad en los menús y contenedores de P2-P4:** implementado, sin medida numérica.
- **Compensar el estrechamiento horizontal al dividir:** el FOV vertical se mantiene (70°) y el horizontal baja de ≈102° a ≈64° con dos jugadores. Es estético; va al final.
- **Rendimiento:** con 4 jugadores el coste de GPU es ~4×; se puede bajar la distancia de renderizado. No se ha optimizado.
- Sonido por jugador, partículas por jugador, Sodium/Iris y servidores externos quedan fuera.

## 2. Qué se ha comprobado solo con simulación (hay que probar con mandos reales)

Ver la lista de pruebas manuales al final de `routing-log.md` (sección "Estado actual").
