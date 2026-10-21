## English

**ConsolePad 0.14.0-beta** — shaders that work with split screen. Replaces 0.13.0-beta.

**Shaders**
- Until now ConsolePad turned every Iris shader pack off while extra players were in, because most packs only look right for one full-screen view. Now it keeps a pack on when the pack says it is **safe for split screen**: it carries the file `shaders/consolepad-splitscreen-safe` (stateless pack, screen coordinates taken from the window position). Every other pack is still turned off with several players and back on when one is left. `"keepShaders": true` in `config/consolepad/split.json` keeps any pack on.
- New separate download, **ConsolePad Lite** (`ConsolePadLite.zip`): a tiny shader pack that adds richer colour (more saturation, a little contrast), with no shadows. It looks the same in every player's region. Put the zip in the `shaderpacks` folder and pick it in Iris (Iris and Sodium are needed; they are not included). Saturation, contrast and warmth can be changed in Iris's shader pack settings.
- **Light on the GPU:** one texture read and one multiply-add per pixel. Measured with 2 and 4 players, it costs the same as an empty pack: the extra GPU time compared with "shaders off" (about 1 ms with 2 players, 1.5 ms with 4) is Iris itself, not the pack.
- The shader code is open: `tools/shaderpack/ConsolePadLite` in the source repository, as a starting point for packs that work with split screen.

Requires Minecraft 1.21.1, NeoForge 21.1.x and Java 21. Put `consolepad-0.14.0-beta.jar` in `mods` and remove older versions. Beta limits as in 0.11.0: local worlds only, rumble needs XInput (Windows), not tested with real PlayStation or Switch controllers or the real Controlify mod. Shadows and realistic lighting are still not possible in split screen.

**SHA-256**
- `consolepad-0.14.0-beta.jar`: `3025ac604e9084c175563c19517603754073e75a1749079cf0ab0f10247681ee`
- `ConsolePadLite.zip`: `5160e2c05233b406557b612d168e56acbd6c6554004d4885e971cbc5d042a019`

Not affiliated with Mojang, Microsoft or the Iris team. Button icons: "Free Input Prompts" by JulioCacko (CC0).

## Español

**ConsolePad 0.14.0-beta** — shaders que funcionan con la pantalla dividida. Sustituye a la 0.13.0-beta.

**Shaders**
- Hasta ahora ConsolePad apagaba todos los packs de shaders de Iris mientras había jugadores extra, porque casi todos solo se ven bien en una vista a pantalla completa. Ahora deja puesto un pack cuando este declara que es **seguro para la pantalla dividida**: lleva el archivo `shaders/consolepad-splitscreen-safe` (pack sin estado entre fotogramas y con las coordenadas sacadas de la posición en la ventana). Los demás se siguen apagando con varios jugadores y vuelven al quedar uno. `"keepShaders": true` en `config/consolepad/split.json` deja puesto cualquier pack.
- Descarga nueva aparte, **ConsolePad Lite** (`ConsolePadLite.zip`): un pack de shaders muy pequeño que da más color (más saturación y un poco de contraste), sin sombras. Se ve igual en la región de cada jugador. Copia el zip a la carpeta `shaderpacks` y elígelo en Iris (hacen falta Iris y Sodium; no van incluidos). La saturación, el contraste y la calidez se cambian en los ajustes del pack de Iris.
- **Ligero para la GPU:** una lectura de textura y una multiplicación-suma por píxel. Medido con 2 y 4 jugadores cuesta lo mismo que un pack vacío: el tiempo de GPU extra frente a «shaders apagados» (alrededor de 1 ms con 2 jugadores y 1,5 ms con 4) es de Iris, no del pack.
- El código del shader es abierto: `tools/shaderpack/ConsolePadLite` en el repositorio, como punto de partida para packs que funcionen con la pantalla dividida.

Requiere Minecraft 1.21.1, NeoForge 21.1.x y Java 21. Pon `consolepad-0.14.0-beta.jar` en `mods` y quita las versiones anteriores. Límites de la beta como en la 0.11.0: solo mundos locales, la vibración necesita XInput (Windows), sin probar con mandos reales de PlayStation o Switch ni con el Controlify real. Las sombras y la iluminación realista siguen sin ser posibles en pantalla dividida.

**SHA-256**
- `consolepad-0.14.0-beta.jar`: `3025ac604e9084c175563c19517603754073e75a1749079cf0ab0f10247681ee`
- `ConsolePadLite.zip`: `5160e2c05233b406557b612d168e56acbd6c6554004d4885e971cbc5d042a019`

No afiliado a Mojang, Microsoft ni al equipo de Iris. Iconos de botones: «Free Input Prompts» de JulioCacko (CC0).
