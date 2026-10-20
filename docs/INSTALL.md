# Instalación de ConsolePad

ConsolePad añade soporte de mandos estilo consola y pantalla dividida local de 2 a 4 jugadores a Minecraft Java.

## Requisitos

- **Minecraft:** 1.21.1.
- **NeoForge:** 21.1.x (probado con 21.1.92; el mod pide `[21,)`).
- **Java:** 21.
- Mandos compatibles con GLFW (Xbox/XInput, PlayStation y Nintendo). Un mando por jugador; el primero conectado es P1, el segundo P2, etc.

## Instalar

1. Instala NeoForge 21.1.x para Minecraft 1.21.1 con su instalador y crea un perfil en el lanzador.
2. Copia `consolepad-0.13.0-beta.jar` (`build/libs/` al compilar el proyecto con `./gradlew build`) a la carpeta `mods` de la instancia (`%APPDATA%\.minecraft\mods` en Windows).
3. Arranca el perfil de NeoForge. En la lista de mods debe aparecer **ConsolePad 0.13.0-beta**.

## Cómo se unen los jugadores

- Abre un mundo en **un jugador** (mundo local). P1 puede jugar con teclado y ratón o con su mando.
- Con otro mando conectado, P1 ve abajo en el centro el aviso **«Unirse como el jugador N»**. **Start** en ese mando mete al jugador (también vale Start + Back).
- Para salir: mantén **Start + Back** durante 2 segundos, o usa **Salir de la partida** en su menú de pausa. Salir no afecta a los demás.
- La pantalla se reparte sola: 2 jugadores = mitades, 3 = una mitad y dos cuartos, 4 = cuadrantes. El reparto (lado a lado o apilado) se cambia desde el menú de pausa.
- Cada jugador tiene su menú de pausa (**Start**), con sus ajustes, controles y disposición de botones.

## Dónde están los ajustes

Carpeta `config/consolepad/` de la instancia:

- `slot0.json` a `slot3.json`: ajustes de cada jugador (botones, sensibilidad, curva de cámara, distancia de chunks, accesibilidad...). Se editan desde el menú de pausa de cada jugador; se guardan al cerrar el juego.
- `split.json`: ajustes globales de la pantalla dividida (entre ellos `adaptivePerformance`, `targetFps`, `savingMode`, `soundPerPlayer`, `soundOcclusion`, `askPlayers` y `padLostLeaveSeconds`).

Cierra el juego antes de editar los archivos a mano: se reescriben al salir.

### Desactivar la distancia automática y los límites

En `config/consolepad/split.json`:

```json
{
  "autoViewDistance": false,
  "playerLimits": false
}
```

- `autoViewDistance`: con más jugadores se reduce la distancia de visión (2 jugadores = 75 %, 3 = 60 %, 4 = 50 %). Con `false`, cada jugador usa la que elija. Si está en `true`, esa distancia es un tope para la que cada jugador elige en sus ajustes.
- `playerLimits`: con 3 o 4 jugadores se acorta el dibujo de entidades y la lluvia pasa a la versión rápida. Con `false` se dibuja todo.

Los demás valores del archivo (`orientation`, `promptStyle`) no hace falta tocarlos.

## Lista de comprobación para una instalación limpia

1. [ ] Minecraft 1.21.1 con NeoForge 21.1.x y Java 21.
2. [ ] `consolepad-0.13.0-beta.jar` en `mods`, sin otra copia ni versión anterior.
3. [ ] El juego arranca y **ConsolePad** aparece en la lista de mods.
4. [ ] Con un mando conectado, P1 se mueve y gira con él y Start abre su menú de pausa.
5. [ ] En un mundo de un jugador, con un segundo mando, aparece «Unirse como el jugador 2»; Start lo mete y la pantalla se divide.
6. [ ] P2 tiene su propio HUD, inventario (Y) y menú de pausa (Start); lo que hace P2 no afecta a la pantalla de P1.
7. [ ] Start + Back mantenido 2 s saca a P2 y la pantalla vuelve a ser de P1.
8. [ ] Se crea `config/consolepad/` con `slot0.json`, `slot1.json`... al cerrar el juego.
9. [ ] Si hay tirones con 3 o 4 jugadores, comprueba que `autoViewDistance` y `playerLimits` están en `true`.

## Problemas conocidos

- **Sodium e Iris:** el juego arranca y la pantalla dividida de 4 jugadores se dibuja bien con Sodium 0.8.13 e Iris 1.8.14 en NeoForge 21.1.248. Los shaders de Iris no funcionan con la pantalla dividida (se ve bien solo la última pasada): ConsolePad los apaga mientras haya jugadores extra y los enciende al quedar uno; con `"keepShaders": true` en `config/consolepad/split.json` se dejan puestos. Otros mods de renderizado, sin verificar.
- **Solo mundos locales, LAN o servidores sin autenticación:** los jugadores extra se conectan por dentro del juego. En un servidor con autenticación no pueden entrar.
- **Cierre del mundo:** las tres excepciones (`NullPointerException` en `handleSoundEvent` y `hurtTo` al salir un jugador extra con paquetes todavía en cola) están corregidas; el cierre con 1 y con 4 jugadores no deja ningún `ERROR` en el log.
- **Un fallo raro del servidor al entrar un jugador extra** (`IndexOutOfBoundsException` al preparar un chunk, visto 1 vez en unas 20 pruebas, justo cuando cambiaba la distancia de visión): el informe queda en `crash-reports`. Si te pasa, guarda el informe y avisa.
- **Vibración solo en mandos XInput (Windows).** El sonido por jugador mueve los sonidos de los demás al oyente de P1 (OpenAL tiene uno solo): se oye lo que pasa junto a cada jugador, con la distancia y la dirección de ese jugador (se apaga con `"soundPerPlayer": false` en `split.json`).
- **Un jugador extra con el mando desconectado** queda en pausa con un aviso; a los 30 s sale solo, o P1 lo saca antes desde su menú de pausa («Sacar a un jugador»).
- **Skins:** todos los jugadores locales usan el perfil del servidor; se distinguen por la etiqueta con nombre (y color) sobre la cabeza.
- El resto de pendientes y límites están en `docs/splitscreen-roadmap.md`.
