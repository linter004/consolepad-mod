# ConsolePad 0.9.0-beta

**[Español](#español) · [English](#english)**

> Unofficial mod. Not affiliated with, endorsed by or associated with Mojang Studios or Microsoft. "Minecraft" is a trademark of Mojang Studios.
> Mod no oficial. No está afiliado a Mojang ni a Microsoft, ni cuenta con su respaldo.

![Two players, split screen](docs/screenshots/split-2-players.png)

---

## English

### What is it

A **Bedrock-style, controller-first experience for Minecraft Java** (NeoForge 1.21.1): gamepad support for every player and **local split screen for 2 to 4 players**. Each player has their own HUD, pause menu, inventory and screens, cursor, controller and settings, inside their own part of the screen.

This is a **beta**: it works end to end in my tests, but it has had little testing outside them. Please report problems.

### Features

- Xbox, PlayStation and Nintendo controllers (through GLFW), one per player, with prompts in the matching button style.
- Split screen for 2, 3 or 4 players (side by side or stacked), each with its own camera, field of view, HUD, chat, notices and boss bars.
- Per-player pause menu, inventory, containers, trading, anvil, enchanting, signs, on-screen keyboard and virtual cursor with D-pad navigation.
- Console-style crafting: recipe book with categories, LB/RB to change category, one button to craft.
- Per-player settings: sensitivity, dead zones, camera curve, field of view, render distance, HUD margin and opacity, accessibility options, button layouts (default, Java, left-handed) and individual remapping.
- Automatic reduction of view distance and entity drawing with more players, to keep the frame rate (can be turned off).
- Players join with their controller (Start) and leave without affecting the others.

### Screenshots

| 4 players | Console-style crafting |
|---|---|
| ![4 players](docs/screenshots/split-4-players.png) | ![Crafting](docs/screenshots/console-crafting.png) |

![Player settings](docs/screenshots/player-settings.png)

### Default controls

| Action | Button |
|---|---|
| Move / look | Left stick / right stick |
| Jump | A |
| Attack / break | RT |
| Use / place | LT |
| Drop item | B |
| Swap hands | X |
| Inventory | Y |
| Change hotbar slot | LB / RB |
| Sprint | L3 |
| Sneak | R3 |
| Change perspective | D-pad up |
| Pause menu | Start |
| Leave (joined players) | Hold Start + Back for 2 s |

Everything can be remapped per player from the pause menu.

### Requirements

- Minecraft **1.21.1** with **NeoForge 21.1.x** (tested with 21.1.92) and **Java 21**.
- One controller per player. P1 can also use keyboard and mouse.

### Installation

See [docs/INSTALL.md](docs/INSTALL.md) (in Spanish). In short: put `consolepad-0.9.0-beta.jar` in the `mods` folder of a NeoForge 1.21.1 instance, open a single-player world, connect a second controller and press Start on it.

### Known issues

- **Sodium and Iris are unverified.** The mod replaces parts of world rendering per player; renderer mods may not work. Try without them.
- **Local worlds only** (single player, LAN, or servers without authentication): extra players connect from inside the game and cannot join an authenticated server.
- No ambient particles (torches, lava, portals) near players 2 to 4; no controller rumble or per-player volume (sound is player 1's).
- An extra player whose controller disconnects stays paused with a notice until it returns.
- All local players use the host profile (same skin); a name tag tells them apart.
- More in [docs/splitscreen-roadmap.md](docs/splitscreen-roadmap.md).

### Credits

- Button icons: "Free Input Prompts" by **JulioCacko**, released under **CC0 1.0** (public domain). See [CREDITS](CREDITS).
- Built on [NeoForge](https://neoforged.net/).

### License

Not decided yet; until then, all rights reserved.

---

## Español

### Qué es

Una **experiencia estilo Bedrock con mando para Minecraft Java** (NeoForge 1.21.1): soporte de mandos para todos los jugadores y **pantalla dividida local de 2 a 4 jugadores**. Cada jugador tiene su propio HUD, menú de pausa, inventario y pantallas, cursor, mando y ajustes, dentro de su parte de la pantalla.

Es una **beta**: funciona de principio a fin en mis pruebas, pero se ha probado poco fuera de ellas. Avisa de los problemas que encuentres.

### Características

- Mandos Xbox, PlayStation y Nintendo (vía GLFW), uno por jugador, con iconos del estilo de cada mando.
- Pantalla dividida para 2, 3 o 4 jugadores (lado a lado o apilada), cada uno con su cámara, campo de visión, HUD, chat, avisos y barras de jefe.
- Menú de pausa, inventario, contenedores, comercio, yunque, encantamiento, carteles, teclado en pantalla y cursor virtual con navegación por cruceta, todo por jugador.
- Crafteo estilo consola: libro de recetas con categorías, LB/RB para cambiar de categoría y un botón para fabricar.
- Ajustes por jugador: sensibilidad, zonas muertas, curva de cámara, campo de visión, distancia de visión, margen y opacidad del HUD, accesibilidad, disposiciones de botones (de fábrica, Java, zurdo) y remapeo individual.
- Reducción automática de distancia de visión y dibujo de entidades con más jugadores, para mantener los fotogramas (se puede desactivar).
- Los jugadores entran con su mando (Start) y salen sin afectar a los demás.

### Capturas

Ver arriba: 2 jugadores, 4 jugadores, crafteo estilo consola y ajustes del jugador.

### Controles por defecto

| Acción | Botón |
|---|---|
| Moverse / mirar | Stick izquierdo / stick derecho |
| Saltar | A |
| Atacar / romper | RT |
| Usar / colocar | LT |
| Soltar objeto | B |
| Cambiar de mano | X |
| Inventario | Y |
| Cambiar de casilla | LB / RB |
| Correr | L3 |
| Agacharse | R3 |
| Cambiar perspectiva | Cruceta arriba |
| Menú de pausa | Start |
| Salir (jugadores unidos) | Mantener Start + Back 2 s |

Todo se puede remapear por jugador desde el menú de pausa.

### Requisitos

- Minecraft **1.21.1** con **NeoForge 21.1.x** (probado con 21.1.92) y **Java 21**.
- Un mando por jugador. P1 también puede usar teclado y ratón.

### Instalación

Ver [docs/INSTALL.md](docs/INSTALL.md). En resumen: copia `consolepad-0.9.0-beta.jar` a la carpeta `mods` de una instancia de NeoForge 1.21.1, abre un mundo de un jugador, conecta un segundo mando y pulsa Start en él.

### Problemas conocidos

- **Sodium e Iris sin verificar.** El mod sustituye partes del dibujo del mundo para cada jugador; con mods que cambian el renderizador puede no funcionar. Prueba sin ellos.
- **Solo mundos locales, LAN o servidores sin autenticación:** los jugadores extra se conectan por dentro del juego y no pueden entrar en un servidor con autenticación.
- Sin partículas ambientales (antorchas, lava, portales) cerca de P2 a P4; sin vibración del mando ni volumen por jugador (el sonido es el de P1).
- Un jugador extra con el mando desconectado queda en pausa con un aviso hasta que el mando vuelva.
- Todos los jugadores locales usan el perfil del anfitrión (misma skin); se distinguen por una etiqueta con nombre.
- Más en [docs/splitscreen-roadmap.md](docs/splitscreen-roadmap.md).

### Créditos

- Iconos de botones: «Free Input Prompts» de **JulioCacko**, publicado bajo **CC0 1.0** (dominio público). Ver [CREDITS](CREDITS).
- Construido sobre [NeoForge](https://neoforged.net/).

### Licencia

Sin decidir todavía; hasta entonces, todos los derechos reservados.

### Aviso

Este mod no está afiliado a Mojang ni a Microsoft. «Minecraft» es una marca de Mojang Studios.
