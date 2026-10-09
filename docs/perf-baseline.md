# Rendimiento de la pantalla dividida: linea base (2026-10-09)

Prueba: `CONSOLEPAD_AUTOTEST_CASES=46 CONSOLEPAD_PERF_TAG=<etiqueta> tools/run-autotest.sh run/perf/<etiqueta>.log` (casilla 46 de `AutoTest`, `perfTest`;
`CONSOLEPAD_PERF_NS=1,2,3,4` elige los numeros de jugadores). Las lineas `[perf]` del log son los resultados.

**Escena:** ventana 1920x1080 (framebuffer 1920x1080), escala de interfaz 4 (automatica), sin vsync ni limite de FPS, distancia de renderizado 12
(la del usuario), bosque (`minecraft:forest`, x=-26 z=106), lluvia, 60 animales (vacas, ovejas, cerdos, gallinas) alrededor, todos los jugadores a
4 bloques entre si mirando en 4 direcciones (el caso de sofa: comparten casi todos los chunks). Se calientan 30 s (carga + mallas), y se miden 300 ticks
(~14,8 s). Equipo: AMD Radeon RX 7600, 15,9 GB de RAM, JVM de IntelliJ (JBR 21) con el maximo de monton por defecto (4064 MB).

## Que es medido y que es supuesto

| Dato | Como | Estado |
|---|---|---|
| FPS, ms por fotograma | tiempo entre inicios de `GameRenderer.render` (`PerfProbe`) | **medido** |
| CPU del hilo principal por fotograma | `render()` (sin el intercambio de buffers) y `Minecraft.tick()` por fotograma, `System.nanoTime` | **medido** |
| CPU por pasada | tiempo de cada segmento (P1..P4, HUD y pantallas = "post") | **medido** |
| GPU por fotograma y por pasada | consultas `GL_TIME_ELAPSED` consecutivas (solo cuenta el tiempo ocupado de la GPU) | **medido** (el controlador puede agrupar trabajo; ruido ~10 %) |
| Memoria del monton de Java | usada tras dos `System.gc()` justo antes de medir; maximo = `maxMemory` | **medido** (solo monton y memoria directa de NIO) |
| Memoria de GPU / nativa fuera del monton | no medida | **supuesto**: crece con las secciones dibujadas (buffers de malla por jugador) |
| Secciones y entidades por jugador | `LevelRenderer.getSectionStatistics/getEntityStatistics` de cada jugador | **medido** |
| Variacion entre repeticiones | 4 ejecuciones de N=1: 418-467 FPS | **medido** (±6 %) |

## Linea base (distancia 12, sin ninguna optimizacion)

| Jugadores | FPS | ms/fotograma | CPU render (ms) | CPU tick (ms/fotograma) | GPU (ms) | Cuello | Monton usado (MB) | + por jugador (MB) | Memoria directa (MB) |
|---|---|---|---|---|---|---|---|---|---|
| 1 | 418 | 2,39 | 2,09 | 0,17 | 1,02 | CPU | 510 | - | 19 |
| 2 | 148 | 6,78 | 6,05 | 0,66 | 2,37 | CPU | 589 | +79 | 27 |
| 3 | 97 | 10,36 | 8,99 | 1,34 | 2,63 | CPU | 665 | +76 | 34 |
| 4 | 78 | 12,83 | 10,78 | 2,05 | 4,58 | CPU | 758 | +93 | 43 |

(CPU tick incluye ~0,8 ms/fotograma de un `System.gc()` de la propia medicion; corregido en las mediciones siguientes.)

Por pasada (CPU / GPU en ms): 1 jugador P1 1,95/0,92; 4 jugadores P1 2,69/0,80, P2 2,65/0,58, P3 2,65/2,61, P4 2,18/0,46; HUD y pantallas 0,13 -> 0,58 / 0,10 -> 0,13.

Secciones dibujadas (visibles de 15000 reservadas): P1 539, P2 386-502, P3 368-374, P4 368. Entidades dibujadas: 21-88 por jugador (de ~235 en el nivel).

## Conclusion de la linea base

- **El cuello de botella es la CPU en todos los casos.** La GPU gasta 1,0 ms con 1 jugador y 4,6 ms con 4, es decir, el 35-45 % del fotograma o menos:
  la GPU espera a la CPU. A 1080p con un RX 7600 (pasadas de ~960x540) no hay casos limitados por la GPU, asi que el paso 2c (resolucion de dibujo por
  jugador) no aporta aqui; se deja en el roadmap con estos datos.
- Cada pasada de mundo cuesta ~2-3 ms de CPU (recorrer y emitir ~540 secciones y ~50 entidades), casi lineal en el numero de jugadores: de 1 a 4 jugadores
  el fotograma pasa de 2,4 a 12,8 ms (x5,4). El resto (HUD, tick) crece mucho menos.
- Memoria: cada jugador extra gasta ~80-90 MB de monton (su `ClientLevel` con los chunks recibidos, su `LevelRenderer` con la rejilla de 15000 secciones y
  sus `RenderBuffers`) y ~8 MB de memoria directa. Con 4 jugadores, 758 MB de 4064: no hay presion de memoria.
- Sospecha de fallo ocasional: una de 5 ejecuciones lanzo `NullPointerException` en `LevelRenderer.getSectionStatistics` (P1, `viewArea` nulo) con 3 jugadores;
  se atribuye a la lectura de estadisticas de la prueba (ya protegida) y no se ha vuelto a ver.

## Paso 2a: distancia de vision automatica (`PerfLimits`, `OptionsLimitsMixin`, `IntegratedServerLimitsMixin`)

La distancia efectiva baja con el numero de jugadores dibujados: 2 = 75 %, 3 = 60 %, 4 = 50 % de la que el usuario tiene en las opciones (minimo 6 chunks).
Con 12 en las opciones: 9, 7 y 6. El servidor integrado usa la misma para enviar chunks y simular. Se apaga con `"autoViewDistance": false` en
`run/config/consolepad/split.json`; las opciones guardadas no se tocan. (Primer intento: la reduccion se aplicaba dos veces porque el servidor ya manda la
suya al cliente; corregido para aplicarla solo a la distancia pedida.)

| Jugadores | Distancia | FPS antes -> despues | ms/fotograma | CPU render (ms) | GPU (ms) | Monton usado (MB) |
|---|---|---|---|---|---|---|
| 1 | 12 -> 12 | 418 -> 466 (ruido) | 2,39 -> 2,15 | 2,09 -> 1,85 | 1,02 -> 0,94 | 510 -> 499 |
| 2 | 12 -> 9 | 148 -> 186 | 6,78 -> 5,38 | 6,05 -> 4,80 | 2,37 -> 1,47 | 589 -> 540 |
| 3 | 12 -> 7 | 97 -> 162 | 10,36 -> 6,19 | 8,99 -> 5,42 | 2,63 -> 1,40 | 665 -> 561 |
| 4 | 12 -> 6 | 78 -> 129 | 12,83 -> 7,77 | 10,78 -> 6,61 | 4,58 -> 1,45 | 758 -> 583 |

Secciones dibujadas por jugador con 4: 539/386/374/368 -> 178/163/148/137. Memoria por jugador extra: ~80-90 MB -> ~20-40 MB (el servidor tambien manda menos chunks).

Prueba completa (`tools/run-autotest.sh`): 355 resultados, 0 FALLO. Hallazgo colateral: una carrera real en `ContextSwitcher.cameraOr` (un hilo de mallas llamaba a
`GameRenderer.getMainCamera` mientras el hilo de render cambiaba de contexto: `NullPointerException` y cierre del juego); ahora lee `current` una vez y solo
devuelve la camara de un extra en el hilo de render.

## Paso 2b: limites por jugador (`PerfLimits.entityScale/fancyWeather`, `LevelRendererMixin`)

Con 3 y 4 jugadores (ajuste `"playerLimits": false` en split.json para apagarlo): la distancia de dibujo de entidades se multiplica por 0,8 y 0,65 (sobre la
escala de vanilla, que ya baja con la distancia de vision), y la lluvia pasa a la version "rapida" (radio 5 en vez de 10 y la cuarta parte de gotas; las gotas
de los extra son las unicas particulas propias que crean). Las particulas generales no se tocan: solo existe el `ParticleEngine` de P1 y su coste es pequeno en la escena.

Comparacion A/B en la misma ejecucion (distancia automatica activa en ambos): 3 jugadores 141 -> 176 FPS (CPU render 6,17 -> 5,07 ms); 4 jugadores 115 -> 169 FPS (7,35 -> 5,19 ms).
Prueba completa: 355 resultados, 0 FALLO.

## Paso 2c: resolucion de dibujo por jugador -> NO implementada (roadmap)

El paso 1 no muestra ningun caso limitado por la GPU: con la linea base la GPU gasta 1,0-4,6 ms de 2,4-12,8 ms por fotograma, y con las optimizaciones 1,0-1,5 ms de
2,7-5,4. Un `RenderTarget` reducido por jugador solo ayudaria en una GPU mucho mas lenta o a 4K. Se deja en el roadmap con una condicion: reabrirlo si alguien mide
`gpuMs >= 0,9 * frameMs` (la prueba lo marca como cuello "GPU").

## Paso 2d: memoria y CPU (analisis; nada arriesgado cambiado)

- **Niveles de cliente de los extra:** cada extra tiene su `ClientLevel`, su cache de chunks y su `LevelRenderer` (rejilla de secciones + mallas). Los chunks llegan
  por su propia conexion y se guardan una vez por jugador. Medido: ~80-90 MB de monton por extra a distancia 12 y ~17-36 MB con la distancia automatica. Compartir
  `ClientLevel` o mallas entre jugadores exigiria un nivel de cliente unico con entidades y jugadores por conexion: **riesgoso y no se hace**. La presion de memoria
  no existe (604 MB de 4064 MB con 4 jugadores).
- **Mallas repetidas:** los jugadores comparten casi todos los chunks en el sofa, asi que cada seccion se malla hasta 4 veces. No se ha medido por separado el coste de
  mallado (el recuento de hilos por nombre no dio datos, **supuesto**); la causa principal del coste de CPU medida es el recorrido y emision de ~150-550 secciones por pasada, que ya
  baja con la distancia automatica. Mallas compartidas: descartado por el mismo motivo.
- **JVM:** el GC cuesta ~1,5 % del tiempo (6-11 recolecciones y ~220 ms cada 14,8 s, medido, G1 por defecto, monton maximo 4064 MB = 1/4 de la RAM). No hay ganancia
  que esperar de cambiar de recolector. Recomendacion para el lanzador del usuario: `-Xms1G -Xmx4G` (mas de 4 GB no se usa) y dejar G1; si hay tirones por GC con packs de recursos
  pesados, probar `-XX:+UseZGC -XX:+ZGenerational` (Java 21). Con 4 jugadores y distancia 12 sin optimizar se usan ~760 MB, con la automatica ~600 MB.

## Resumen: antes y despues (1920x1080, distancia 12 en las opciones, escena de bosque con lluvia y 60 animales)

| Jugadores | FPS antes -> despues | ms/fotograma | CPU render (ms) | GPU (ms) | Monton (MB) | Distancia efectiva |
|---|---|---|---|---|---|---|
| 1 | 418 -> 371* | 2,39 -> 2,69* | 2,09 -> 2,37* | 1,02 -> 1,04 | 510 -> 534* | 12 -> 12 |
| 2 | 148 -> 185 | 6,78 -> 5,42 | 6,05 -> 4,80 | 2,37 -> 1,51 | 589 -> 563 | 12 -> 9 |
| 3 | 97 -> 201 | 10,36 -> 4,98 | 8,99 -> 4,43 | 2,63 -> 1,23 | 665 -> 580 | 12 -> 7 |
| 4 | 78 -> 189 | 12,83 -> 5,29 | 10,78 -> 4,62 | 4,58 -> 1,19 | 758 -> 604 | 12 -> 6 |

\* Con 1 jugador no cambia nada (los limites no actuan); las ejecuciones de 1 jugador dieron 371-505 FPS segun el momento del equipo (ruido de ±15 %).
