# Texto de la ficha (Modrinth y CurseForge)

Borrador listo para pegar. **No se publica nada hasta que el usuario diga «publica».** Ambas webs admiten Markdown; las imágenes se suben desde `docs/screenshots/`.

Datos de la ficha:

- Nombre: **ConsolePad**
- Resumen (una línea, máx. 256): *Controller support and 2–4 player local split screen for Minecraft Java, Bedrock-style: each player gets their own HUD, menus, inventory and settings.*
- Categorías: Utility, Game mechanics (Modrinth) · Miscellaneous, Player Transport (CurseForge: elegir «Miscellaneous»)
- Cargador: NeoForge · Versión: 1.21.1 · Entorno: solo cliente
- Licencia: pendiente de decidir (hoy «todos los derechos reservados»; Modrinth exige elegir una antes de publicar)
- Imágenes de galería, en este orden: `split-2-players.png`, `split-4-players.png`, `console-crafting.png`, `controls-diagram.png`, `player-settings.png`, `companion.png`, `pause.png`

---

## English

# ConsolePad

**Play Minecraft Java like on a console: a controller for everyone, and up to four players on one screen.**

Plug in your controllers, open a single-player world, and ConsolePad asks "How many players?" Everyone is in, each with their own part of the screen: their own camera, HUD, hotbar, pause menu, inventory, containers, chat and settings. Nothing is shared by accident, and nothing changes in how the game looks: it stays vanilla.

![Two players](split-2-players.png)

### Highlights

- **2 to 4 players, one screen.** Side by side or stacked, remembered per world. Players join with Start and leave with Start + Back, without affecting the others.
- **Xbox, PlayStation and Nintendo controllers**, with button prompts in the matching style.
- **Bedrock-style menus:** console crafting with LB/RB categories, creative tabs on the shoulder buttons, a virtual cursor with D-pad navigation, and an on-screen keyboard.
- **Co-op comforts:** a partner dot on your experience bar (like Bedrock's locator bar) with name and distance, a quick actions wheel ("Go to a player", "Bring a player", time, weather...), and per-player settings (sensitivity, field of view, render distance, HUD margin and opacity, left-handed layout, remapping).
- **Rumble per player** on Xbox-compatible controllers: damage, hits, breaking, eating, drawing a bow, explosions.
- **Console touches:** contextual button prompts ("Open", "Ride", "Trade"...), per-player auto-jump, a paper doll while you sprint or sneak, riding with a jump bar for everyone, and items spread one by one with X and the D-pad.
- **Sound and particles for every player:** each sound is heard by the player closest to it, and torches, lava and portals animate around all of them.
- **Profiles:** each player keeps their own name, colour, skin (18 defaults or your own PNG), inventory and respawn point.
- **Smart performance:** view distance and entity drawing shrink automatically with more players.
- Works with **Sodium** and **Iris** (shaders pause while several players are in).

### Install

1. Minecraft **1.21.1**, **NeoForge 21.1.x**, **Java 21**.
2. Drop the jar in `mods`.
3. Start a single-player world with two or more controllers connected.

### Honest limits (beta)

Local worlds only (single player, LAN, servers without authentication). Rumble needs XInput (Windows). Ambient particles are missing near players 2–4. Please report bugs on the issue tracker.

*Unofficial mod. Not affiliated with, endorsed by or associated with Mojang Studios or Microsoft.*

---

## Español

# ConsolePad

**Juega a Minecraft Java como en una consola: un mando para cada uno y hasta cuatro jugadores en una pantalla.**

Conecta los mandos, abre un mundo de un jugador y ConsolePad pregunta «¿Cuántos jugadores sois?». Entráis todos, cada uno con su parte de la pantalla: su cámara, HUD, barra de objetos, menú de pausa, inventario, contenedores, chat y ajustes. Nada se comparte por accidente y el juego no cambia de aspecto: sigue siendo vanilla.

### Lo mejor

- **De 2 a 4 jugadores en una pantalla.** Lado a lado o apilados, recordado por mundo. Se entra con Start y se sale con Start + Atrás, sin afectar a los demás.
- **Mandos Xbox, PlayStation y Nintendo**, con iconos del estilo de cada uno.
- **Menús al estilo Bedrock:** crafteo de consola con categorías en LB/RB, pestañas del creativo en los gatillos, cursor virtual con cruceta y teclado en pantalla.
- **Comodidades cooperativas:** punto del compañero sobre tu barra de experiencia (como la barra de localización de Bedrock) con nombre y distancia, rueda de acciones rápidas («Ir con alguien», «Traer a alguien», hora, clima...) y ajustes por jugador (sensibilidad, campo de visión, distancia de visión, margen y opacidad del HUD, zurdos, remapeo).
- **Vibración por jugador** en mandos compatibles con Xbox: daño, golpes, romper bloques, comer, tensar el arco y explosiones.
- **Detalles de consola:** avisos de botones contextuales («Abrir», «Montar», «Comerciar»...), salto automático por jugador, muñeco del jugador al correr o agacharte, cabalgaduras con barra de salto para todos y objetos repartidos de uno en uno con X y la cruceta.
- **Sonido y partículas para todos:** cada sonido lo oye el jugador más cercano y las antorchas, la lava y los portales se animan alrededor de todos.
- **Perfiles:** cada jugador conserva su nombre, color, skin (18 de fábrica o tu PNG), inventario y punto de reaparición.
- **Rendimiento inteligente:** con más jugadores baja sola la distancia de visión y el dibujo de entidades.
- Funciona con **Sodium** e **Iris** (los shaders se apagan mientras hay varios jugadores).

### Instalación

1. Minecraft **1.21.1**, **NeoForge 21.1.x**, **Java 21**.
2. Copia el jar a `mods`.
3. Abre un mundo de un jugador con dos o más mandos conectados.

### Límites (beta)

Solo mundos locales (un jugador, LAN, servidores sin autenticación). La vibración necesita XInput (Windows). Faltan las partículas ambientales cerca de P2 a P4. Avisa de los errores en el apartado de incidencias.

*Mod no oficial. No está afiliado a Mojang ni a Microsoft.*
