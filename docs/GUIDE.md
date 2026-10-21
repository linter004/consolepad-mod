# ConsolePad · Guide / Guía

**[English](#english) · [Español](#español)**

---

## English

### 1. Install

1. Install **NeoForge 21.1.x** for Minecraft **1.21.1** (Java 21) and create a launcher profile.
2. Put the ConsolePad jar in the `mods` folder of that profile (`%APPDATA%\.minecraft\mods` on Windows). Keep only one version.
3. Start the profile. **ConsolePad** must show in the mod list. On the first start a welcome screen explains how players join; close it with "Got it".

### 2. First game

1. Connect your controllers (one per player) **before** opening the world, or from the title screen's **Controllers** button (top left), where each player presses A on their controller to claim a quarter of the screen.
2. Open a single-player world. With two or more controllers connected, the game asks **"How many players?"**; pick a number and everyone comes in one after another.
3. Without the prompt: press **Start** on another controller to join, hold **Start + Back** for 2 s to leave. Leaving never affects the others.
4. Player 1 can use keyboard and mouse or a controller; the rest use controllers.

### 3. Controls (default)

Move/look: sticks · Jump: A · Attack: RT · Use: LT · Drop: B · Swap hands: X · Inventory: Y · Hotbar: LB/RB · Sprint: L3 · Sneak: R3 · Perspective: D-pad up · Pause menu: Start · Quick wheel: hold D-pad left.

In any menu: left stick or D-pad moves the cursor, A clicks, B goes back, X right-clicks, and in tabbed screens LB/RB (and LT/RT in the creative list) change category.

### 4. Make it yours

Open the pause menu (Start) and choose **Player settings**. They are per player and grouped in pages:

- *Camera and aiming:* sensitivity, camera curve, aim assist, field of view.
- *Movement and buttons:* dead zones, layout (default, Java, left-handed), toggle or hold for sneak and sprint.
- *Menus and cursor:* cursor speed, button prompts and their style (Xbox, PlayStation, Nintendo).
- *Screen and interface:* interface scale, view distance, HUD margin and opacity, partner indicator and its distance, coordinates, FPS.
- *Player and controllers:* name, label colour, profile, assign controllers, rumble strength, quick wheel editor.

On any value button, **X** (or right click) goes to the previous value.

### 5. Quick wheel

Hold the D-pad left button, pick an entry with the right stick, release to run it as that player's command (commands need permission: cheats on in a single-player world). Default entries include day/night, weather, game mode, healing and **Go to a player / Bring a player**. Edit them per player in settings → "Quick wheel".

### 6. Rumble

Rumble works on controllers Windows sees as XInput (Xbox-compatible; PlayStation and Nintendo controllers when they come through Steam Input or similar). It is per player, off or soft/normal/strong in the settings, and fires when you take damage or die.

### 6b. Also worth knowing

- The use button's label tells you what it will do ("Open", "Sleep", "Ride"...).
- Settings include auto-jump per player, the paper doll, the partner indicator on the experience bar, field of view compensation and rumble strength.
- In inventories, hold X and move with the D-pad to spread one item per slot.
- Player 1 can remove another player from the pause menu ("Remove a player").
- Sounds are heard by the player closest to them; with several players the game lowers distance by itself if the frame rate drops under 40 FPS.

- Press LB and RB together to jump to the previous hotbar slot. Player settings also have a Nintendo layout (A/B and X/Y swapped), "Mark my bed", a strength for each kind of rumble and a Saving mode for weak PCs.

### 7. Troubleshooting

| Problem | What to do |
|---|---|
| A controller is not detected | Reconnect it; open the title screen's **Controllers** button to see what the game detects. |
| Choppy with 3 or 4 players | Keep `autoViewDistance` and `playerLimits` on in `config/consolepad/split.json` and lower render distance in the player's settings. |
| Shaders turn off | Normal: most Iris shader packs cannot split. They return when one player is left. The **ConsolePad Lite** pack stays on with several players (`"keepShaders": true` keeps any pack on). |
| A player's controller disconnects | That player pauses; after 30 s they leave the game and their inventory is saved. |
| No rumble | The controller must be XInput on Windows; check the strength is not "no". |

Settings live in `config/consolepad/` (close the game before editing by hand).

---

## Español

### 1. Instalar

1. Instala **NeoForge 21.1.x** para Minecraft **1.21.1** (Java 21) y crea un perfil en el lanzador.
2. Copia el jar de ConsolePad a la carpeta `mods` de ese perfil (`%APPDATA%\.minecraft\mods` en Windows). Deja solo una versión.
3. Arranca el perfil. **ConsolePad** debe salir en la lista de mods. La primera vez sale una bienvenida que explica cómo entran los jugadores; se cierra con «Entendido».

### 2. Primera partida

1. Conecta los mandos (uno por jugador) **antes** de abrir el mundo, o desde el botón **Mandos** del título (arriba a la izquierda), donde cada jugador pulsa A en su mando para quedarse con un cuarto de la pantalla.
2. Abre un mundo de un jugador. Con dos o más mandos conectados, el juego pregunta **«¿Cuántos jugadores sois?»**; elige el número y entran todos uno tras otro.
3. Sin la pregunta: pulsa **Start** en otro mando para entrar y mantén **Start + Atrás** 2 s para salir. Salir no afecta a los demás.
4. El jugador 1 puede usar teclado y ratón o mando; los demás, mando.

### 3. Controles (de fábrica)

Moverse/mirar: sticks · Saltar: A · Atacar: RT · Usar: LT · Soltar: B · Cambiar de mano: X · Inventario: Y · Barra: LB/RB · Correr: L3 · Agacharse: R3 · Perspectiva: cruceta arriba · Menú de pausa: Start · Rueda rápida: mantener cruceta izquierda.

En cualquier menú: stick izquierdo o cruceta mueve el cursor, A pulsa, B vuelve, X es clic derecho, y en las pantallas con pestañas LB/RB (y LT/RT en la lista del creativo) cambian de categoría.

### 4. Hazlo tuyo

Abre el menú de pausa (Start) y elige **Ajustes del jugador**. Son por jugador y están en páginas:

- *Cámara y apuntado:* sensibilidad, curva de cámara, ayuda de apuntado, campo de visión.
- *Movimiento y botones:* zonas muertas, disposición (de fábrica, Java, zurdo), mantener o alternar para agacharse y correr.
- *Menús y cursor:* velocidad del cursor, iconos de botones y su estilo (Xbox, PlayStation, Nintendo).
- *Pantalla e interfaz:* escala, distancia de visión, margen y opacidad del HUD, indicador del compañero y su distancia, coordenadas, FPS.
- *Jugador y mandos:* nombre, color de etiqueta, perfil, asignar mandos, fuerza de la vibración, editor de la rueda rápida.

En cualquier botón de valor, **X** (o clic derecho) vuelve al valor anterior.

### 5. Rueda rápida

Mantén la cruceta izquierda, elige una entrada con el stick derecho y suelta para ejecutarla como comando de ese jugador (los comandos piden permiso: trucos activados en un mundo de un jugador). Entre las de fábrica están día/noche, clima, modo de juego, curarte e **Ir con alguien / Traer a alguien**. Se editan por jugador en ajustes → «Rueda de acciones».

### 6. Vibración

Funciona en los mandos que Windows ve como XInput (compatibles con Xbox; los de PlayStation y Nintendo si llegan por Steam Input o similar). Es por jugador, con fuerza no/suave/normal/fuerte en los ajustes, y salta al recibir daño o morir.

### 6b. Y además

- El texto del botón de usar dice qué hará («Abrir», «Dormir», «Montar»...).
- Los ajustes incluyen salto automático por jugador, muñeco del jugador, indicador del compañero sobre la barra de experiencia, compensación del campo de visión y fuerza de la vibración.
- En los inventarios, mantén X y muévete con la cruceta para repartir un objeto por hueco.
- P1 puede sacar a otro jugador desde el menú de pausa («Sacar a un jugador»).
- Cada sonido lo oye el jugador más cercano; con varios jugadores el juego baja solo la distancia si los FPS caen de 40.

- Pulsa LB y RB a la vez para saltar a la casilla anterior. Los ajustes del jugador incluyen una disposición Nintendo (A/B y X/Y intercambiados), «Marcar mi cama», una fuerza para cada tipo de vibración y un Modo ahorro para PC modestos.

- **Idioma de los menús:** empiezan en español; en los ajustes del jugador, «Idioma de los menús» los cambia a inglés o al idioma de Minecraft (automático).
- Los ajustes del jugador incluyen **Colores del jugador** (cursor y casilla seleccionada con tu color), **Destello con poca vida**, **Brillo** propio, y para todos **Línea entre jugadores** y **Aviso de quién duerme**.

- **Teclado en pantalla:** en el chat, los carteles y los nombres se abre con **Y** (o con **A** sobre un campo de texto) y se cierra con **B**. «Teclado automático» (ajustes del jugador) lo abre solo al entrar.
- **Cama:** los jugadores 2 a 4 tienen su botón «Salir de la cama»; **B** también los levanta.

### 7. Si algo falla

| Problema | Qué hacer |
|---|---|
| Un mando no se detecta | Vuelve a conectarlo; el botón **Mandos** del título muestra lo que ve el juego. |
| Tirones con 3 o 4 jugadores | Deja `autoViewDistance` y `playerLimits` activos en `config/consolepad/split.json` y baja la distancia de visión en los ajustes del jugador. |
| Los shaders se apagan | Es normal: casi todos los packs de Iris no se pueden dividir. Vuelven al quedar un jugador. El pack **ConsolePad Lite** se queda puesto con varios jugadores (`"keepShaders": true` deja puesto cualquier pack). |
| Se desconecta el mando de un jugador | Ese jugador queda en pausa; a los 30 s sale de la partida y su inventario se guarda. |
| No vibra | El mando debe ser XInput en Windows; comprueba que la fuerza no esté en «no». |

Los ajustes están en `config/consolepad/` (cierra el juego antes de editarlos a mano).
