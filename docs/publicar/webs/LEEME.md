# Paquete para Modrinth y CurseForge (ConsolePad 0.13.0-beta)

Todo lo de esta carpeta está listo para copiar y pegar. **Crear las cuentas, aceptar los términos de cada web y pulsar «enviar» lo haces tú**; yo no puedo ni debo hacerlo por ti. Los nombres exactos de los botones y campos pueden cambiar un poco con el tiempo.

## Qué hay aquí

| Archivo | Para qué |
|---|---|
| `icono-512.png` | Icono del proyecto (Modrinth: «Icon»; CurseForge: «Logo»). Dibujo propio, generado con `tools/MakeIcon.java` |
| `modrinth-descripcion.md` | Descripción larga en Markdown (inglés y español) |
| `curseforge-descripcion.html` | La misma descripción en HTML (el editor de CurseForge tiene modo «Source») |
| `changelog-0.13.0-beta.md` | Registro de cambios para la subida del archivo (inglés y español) |
| `../../screenshots/*.png` | Capturas de la galería |
| `build/libs/consolepad-0.13.0-beta.jar` | El archivo a subir (también está en la release de GitHub). SHA-256: `aa68ec57630ece9ff37c6dc9091863d86d63e8dd993cf30f6571daefa9909900` |

## Datos de la ficha (iguales en las dos webs)

| Campo | Valor |
|---|---|
| Nombre | `ConsolePad` |
| Dirección / slug | `consolepad` |
| Resumen (máx. 250 caracteres) | `Controller support and 2–4 player local split screen for Minecraft Java, Bedrock-style: each player gets their own HUD, menus, inventory and settings.` |
| Cargador | **NeoForge** (no «Forge»: el mod no funciona en Minecraft Forge) |
| Versión de Minecraft | **1.21.1** |
| Java | 21 |
| Entorno | **Solo cliente** (en Modrinth: cliente «requerido», servidor «no soportado») |
| Canal / tipo de versión | **Beta** |
| Número de versión | `0.13.0-beta` |
| Código fuente | https://github.com/linter004/consolepad-mod |
| Incidencias | https://github.com/linter004/consolepad-mod/issues |
| Dependencias | ninguna obligatoria (Sodium, Iris y Controlify son compatibles, no necesarios) |
| Categorías | Modrinth: **Utility** y **Game mechanics**. CurseForge: **Miscellaneous** (o la de «Utility & QoL» si aparece) |
| Licencia | **All Rights Reserved** (decidida el 2026-10-20; está en la lista de licencias de las dos webs) |

### Orden de la galería (con título)

1. `split-2-players.png` — Two players, one screen (imagen destacada)
2. `split-4-players.png` — Four players, each in their own colour
3. `console-crafting.png` — Console-style crafting
4. `controls-diagram.png` — Controls screen with controller diagram
5. `player-settings.png` — Per-player settings
6. `companion.png` — Partner marker on the experience bar
7. `pause.png` — Per-player pause menu

## Modrinth (modrinth.com)

1. Entra con tu cuenta (se puede con GitHub) → **Create a project** → tipo **Mod**.
2. Rellena nombre, slug, resumen y visibilidad. Sube `icono-512.png`.
3. **Description**: pega `modrinth-descripcion.md`.
4. **Settings → Tags**: las dos categorías; **Environment**: cliente requerido / servidor no soportado.
5. **Settings → Links**: código fuente e incidencias (arriba). **License**: la que decidas.
6. **Gallery**: sube las 7 capturas en el orden de arriba y marca la primera como destacada.
7. **Versions → Create version**: sube el jar, número `0.13.0-beta`, título «ConsolePad 0.13.0-beta», canal **Beta**, cargador **NeoForge**, versión de juego **1.21.1**, changelog = `changelog-0.13.0-beta.md`.
8. **Submit for review**. Modrinth revisa cada proyecto nuevo antes de hacerlo público (suele tardar de unas horas a unos días).

## CurseForge (curseforge.com / authors.curseforge.com)

1. Entra con tu cuenta → **Create project** → juego **Minecraft** → clase **Mods**.
2. Nombre, resumen y licencia. Sube `icono-512.png` como logo (pide un cuadrado; 512×512 vale).
3. **Description**: abre el editor en modo **Source/HTML** y pega `curseforge-descripcion.html`.
4. **Categorías**: Miscellaneous (o la más parecida a «Utility & QoL»). **Links**: código fuente e incidencias.
5. **Images**: sube las capturas en el orden de arriba.
6. **Files → Upload file**: el jar, tipo **Beta**, versión **1.21.1**, cargador **NeoForge**, **Java 21**, entorno **cliente**, changelog = `changelog-0.13.0-beta.md`.
7. Envía el proyecto a aprobación. CurseForge lo revisa antes de publicarlo (de horas a unos días).

## La licencia (decidida: All Rights Reserved)

Se queda como está (`license` del `mods.toml` y README), así que el jar de la release de GitHub es el mismo que se sube a las webs. Con esta licencia, **CurseForge te preguntará si se permite incluir el mod en modpacks y bajo qué condiciones**: eso lo decides tú en el formulario (lo habitual es «pedir permiso»). Se puede abrir más adelante a MIT o LGPL (lo contrario no se puede deshacer), y entonces habría que sacar un jar nuevo. Las otras opciones que se barajaron:

| Opción | Qué significa | Efecto en lo ya publicado |
|---|---|---|
| **All Rights Reserved** (la actual) | Nadie puede copiar, modificar ni redistribuir sin tu permiso. Las dos webs la aceptan. En CurseForge tendrás que elegir si se permite incluirlo en modpacks | Ninguno: el jar de la release ya es el que se sube |
| **MIT** | Cualquiera puede usarlo, modificarlo y redistribuirlo, también en modpacks, con tu aviso de autor | Hay que añadir `LICENSE`, cambiar el `mods.toml` y el README → **jar nuevo** (0.13.1-beta) y release nueva |
| **LGPL-3.0** | Se puede usar y empaquetar; quien modifique el mod tiene que publicar sus cambios con la misma licencia | Igual que MIT: jar nuevo |
| **GPL-3.0** | Igual que LGPL pero más estricta con quien lo incorpore | Igual: jar nuevo |

Si cambias de licencia, aprovecho el jar nuevo para corregir también la descripción del `mods.toml` (hoy dice «con base para pantalla dividida», que ya se queda corta).
