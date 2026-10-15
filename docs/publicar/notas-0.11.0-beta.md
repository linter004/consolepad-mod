## English

**ConsolePad 0.11.0-beta** — the biggest update so far: co-op comforts, rumble, a Bedrock-style partner indicator, per-player sound, riding for everyone and a lot of polish. It covers everything since 0.9.1.

**Co-op and Bedrock-style**
- **Partner indicator:** like Bedrock's locator bar, a square in the partner's colour moves along your experience bar to show where the other player is (centre = ahead, an end = to that side or behind), with their name and distance above your hearts. Shown from 10 blocks away; off in player settings.
- **Quick wheel:** new default entries "Go to a player" and "Bring a player".
- **Contextual button prompts:** the use button says what it will do ("Open", "Sleep", "Ride", "Trade", "Feed", "Milk", "Shear", "Eat"...).
- **Per-player auto-jump** ("as the game / yes / no") and a **paper doll** (your character in a corner while you sprint, sneak, swim or glide).
- **Riding for every player:** jump bar and mount hearts in each HUD.
- **Sound per player:** each sound is played by the nearest local player, so what happens next to players 2 to 4 is heard (it is moved to player 1's listener keeping distance and direction). **Ambient particles** (torches, lava, portals) around every player.
- **Rumble per player** (Xbox-compatible controllers on Windows): damage, hits, breaking blocks, eating, drawing a bow, explosions.
- **Inventory:** hold X and move with the D-pad to spread one item per slot, as in Bedrock. D-pad targets for stonecutter recipes and loom patterns.
- Player 1 can **remove a player** from the pause menu; the pause menu shows your character with your skin.
- **Field of view compensation** for narrow split-screen regions (off / half / full) and **automatic performance** (with several players, if the frame rate stays under 40 FPS the view distance and entity drawing go down a bit and come back later; `adaptivePerformance` / `targetFps` in `split.json`).

**Earlier in this range (0.9.2 and 0.10.0)**
- Custom skins (64x64 PNG), in-game quick wheel editor with a wheel per player, mob sounds quieter through blocks, "How many players?" prompt when opening a world with several controllers, layout remembered per world, an extra player whose controller stays off for 30 s leaves the game, settings in categories (X / right click = previous value), aim assist, Bedrock-style category tabs.
- **Fixes:** player 1 no longer crashes when an extra player re-enters; a recipe button with no recipes no longer crashes the game; the title screen no longer stays black after leaving a world with extra players.

**Honest limits (beta)**
- Local worlds only (single player, LAN, servers without authentication).
- Rumble needs XInput (Windows); PlayStation and Nintendo controllers vibrate only if they arrive as XInput (for example through Steam Input). No gyroscope.
- Not tested with real PlayStation or Switch controllers, nor with the real Controlify mod. The feel of rumble and per-player sound was checked with automated tests, not with every setup.
- Iris shaders are turned off while extra players are in.

Requires Minecraft 1.21.1, NeoForge 21.1.x and Java 21. Put `consolepad-0.11.0-beta.jar` in `mods` and remove older versions. Guide: `docs/GUIDE.md`.

**SHA-256** of `consolepad-0.11.0-beta.jar`:
`4dd1704c48b1483d8940a559d9c6c7d585c66d4af4b5d611128d7e497f936376`

Not affiliated with Mojang or Microsoft. Button icons: "Free Input Prompts" by JulioCacko (CC0).

## Español

**ConsolePad 0.11.0-beta** — la mayor actualización hasta ahora: comodidades cooperativas, vibración, un indicador del compañero al estilo Bedrock, sonido por jugador, cabalgaduras para todos y mucho pulido. Recoge todo lo hecho desde la 0.9.1.

**Cooperativo y estilo Bedrock**
- **Indicador del compañero:** como la barra de localización de Bedrock, un cuadrado del color del compañero se mueve por tu barra de experiencia y marca dónde está el otro jugador (centro = delante, un extremo = a ese lado o detrás), con su nombre y distancia sobre tus corazones. Sale desde 10 bloques; se apaga en los ajustes del jugador.
- **Rueda rápida:** entradas nuevas de fábrica «Ir con alguien» y «Traer a alguien».
- **Avisos de botones contextuales:** el botón de usar dice qué hará («Abrir», «Dormir», «Montar», «Comerciar», «Dar de comer», «Ordeñar», «Esquilar», «Comer»...).
- **Salto automático por jugador** («como el juego / sí / no») y **muñeco del jugador** (tu personaje en una esquina mientras corres, te agachas, nadas o planeas).
- **Cabalgaduras para todos:** barra de salto y corazones de la montura en cada HUD.
- **Sonido por jugador:** cada sonido lo reproduce el jugador local más cercano, así que se oye lo que pasa junto a P2 a P4 (se mueve al oyente de P1 conservando distancia y dirección). **Partículas ambientales** (antorchas, lava, portales) alrededor de cada jugador.
- **Vibración por jugador** (mandos compatibles con Xbox en Windows): daño, golpes, romper bloques, comer, tensar el arco, explosiones.
- **Inventario:** mantén X y muévete con la cruceta para repartir un objeto por hueco, como en Bedrock. La cruceta llega a las recetas de la mesa de corte y a los patrones del telar.
- P1 puede **sacar a un jugador** desde el menú de pausa; la pausa muestra tu personaje con tu skin.
- **Compensación del campo de visión** en regiones estrechas (no / media / total) y **rendimiento automático** (con varios jugadores, si los FPS se quedan por debajo de 40 se baja un poco la distancia de visión y el dibujo de entidades, y se devuelve después; `adaptivePerformance` / `targetFps` en `split.json`).

**Antes en este tramo (0.9.2 y 0.10.0)**
- Skins propias (PNG 64x64), editor de la rueda en el juego con una rueda por jugador, sonido de mobs más bajo detrás de bloques, pregunta «¿Cuántos jugadores sois?» al abrir un mundo con varios mandos, reparto recordado por mundo, un jugador extra con el mando apagado 30 s sale de la partida, ajustes por categorías (X / clic derecho = valor anterior), ayuda de apuntado, pestañas de categoría estilo Bedrock.
- **Arreglos:** P1 ya no se cae cuando un jugador extra vuelve a entrar; un botón de receta sin recetas ya no tumba el juego; el título ya no se queda en negro al salir de un mundo con jugadores extra.

**Límites (beta)**
- Solo mundos locales (un jugador, LAN, servidores sin autenticación).
- La vibración necesita XInput (Windows); los mandos de PlayStation y Nintendo vibran solo si llegan como XInput (por ejemplo con Steam Input). Sin giroscopio.
- Sin probar con mandos reales de PlayStation o Switch ni con el Controlify real. La sensación de la vibración y del sonido por jugador se comprobó con pruebas automáticas, no en todas las configuraciones.
- Los shaders de Iris se apagan mientras hay jugadores extra.

Requiere Minecraft 1.21.1, NeoForge 21.1.x y Java 21. Copia `consolepad-0.11.0-beta.jar` a `mods` y quita versiones anteriores. Guía: `docs/GUIDE.md`.

**SHA-256** de `consolepad-0.11.0-beta.jar`:
`4dd1704c48b1483d8940a559d9c6c7d585c66d4af4b5d611128d7e497f936376`

Sin relación con Mojang ni Microsoft. Iconos de botones: «Free Input Prompts» de JulioCacko (CC0).
