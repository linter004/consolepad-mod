# Prueba de portar a 1.21.11 (2026-10-24)

Rama `port-1.21.11` (worktree `../consolepad-port-1.21.11`, sin commits): `minecraft_version=1.21.11`, `neo_version=21.11.48`, sin Parchment (no hay para 1.21.11), `[21.11,)` en el toml. El resto del codigo, sin tocar. Nada de esto afecta a `master`.

## Resultado de `compileJava`
- **459 errores distintos en 80 de los 154 archivos** (`-Xmaxerrs 100000`); javac aun puede destapar mas tras arreglar los de simbolos.
- **Mixins:** 14 de los 44 no compilan (los accessors/invokers y las clases que tocan tipos cambiados); otros 9 apuntan a clases o metodos que ya no existen (comprobado contra las fuentes de 1.21.11 con un script; el mismo script da 44/44 bien en 1.21.1). Se solapan: **unos 20 de 44 mixins hay que rehacer**. Los demas solo se validan al arrancar, y los `@At`/`@Redirect` por dentro no se pueden comprobar sin ejecutar.
- Los que no existen: `FogRenderer`, `ToastComponent`, `ClientPayloadHandler`, `Minecraft.useFancyGraphics`, `LevelRenderer.renderSnowAndRain/tickRain`, `RenderSystem.setShaderColor`, `GuiGraphics.renderTooltipInternal`, `Gui.renderExperienceBar`, `MouseHandler.onPress`.
- Causas mas repetidas: `GuiGraphics.pose()` ya no es `PoseStack` (`pushPose`/`popPose`, ~80 usos), `ResourceLocation` -> `Identifier`, `Input` como registro (`forwardImpulse`/`leftImpulse`), `PlayerSkin`/skins, `UseAnim`, `Window`, `NativeImage.getPixelRGBA`, recetas (`getRecipe`, libro de recetas), `GameRules`, `ServerLevel`/`serverLevel`, animales (`Cow`, `Sheep`, `Boat`...).
- Sin importancia para el juego: 196 de los errores estan en `AutoTest` y otros 150 en las demas pruebas.

## Riesgo que no se ve en el compilador
El dibujo del HUD, los menus y el recorte por region (`enableScissor` + pose, `GuiGraphicsMixin`, `SplitHud`) se apoya en que `GuiGraphics` dibuje en el momento. En 1.21.6+ el GUI se acumula en un estado de render y se dibuja despues, y el mundo usa otro sistema de dibujo: puede ser que la forma de repartir la pantalla entre jugadores haya que rehacerla, no solo adaptar nombres.

## Estimacion
Compilar de nuevo: unos 2-4 dias de trabajo con muchas pasadas del autotest. Hacer que la pantalla dividida se dibuje bien (HUD, menus, 4 regiones): incierto, de varios dias a semanas. Mantener dos ramas en paralelo ademas dobla las pruebas.
