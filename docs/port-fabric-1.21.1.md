# Prueba de portar a Fabric 1.21.1 (2026-10-25)

Rama `port-fabric` (worktree `../consolepad-port-fabric`, sin commits): `fabric-loom` 1.15.5 (los 1.16+ piden Java 25 para Gradle y los 1.19 Gradle 9.8), Minecraft 1.21.1 con mapeos oficiales de Mojang (los mismos nombres que en NeoForge, asi que los mixins no se renombran), Fabric Loader 0.20.1, Fabric API 0.116.17+1.21.1. Solo se compilo; nada se ha ejecutado. `master` sin tocar.

## Resultado de `compileJava`
- **144 errores distintos en 28 de 154 archivos** (de ellos 31 en pruebas): el codigo del mod tiene **113 errores en 23 archivos**. Mucho menos que 1.21.11 (459 en 80). Pueden salir mas al arreglar estos.
- **Mixins:** 5 de 44 no compilan (`RegistrySyncMixin`, `ChatGuardsMixin`, `CreativeScreenAccessor`, `GameRendererMixin`, `ParticleEngineMixin`). Los demas apuntan a clases de vanilla y compilan; su aplicacion (y los `@At`/`@Redirect`) solo se ve al arrancar.
- **De NeoForge** (17 archivos importan `net.neoforged`): eventos (`ClientTickEvent`, `RenderLevelStageEvent`, `RenderFrameEvent`, `RegisterClientCommandsEvent`, `PlayerEvent`, `LivingEntityUseItemEvent`, `AttackEntityEvent`, `BlockEvent`, `ExplosionEvent`), `@Mod` y el contenedor de mods (`ModList`, `FMLPaths`, `IEventBus`), la sincronizacion de registros (`RegistrySnapshot`, `FrozenRegistrySyncCompletedPayload`, `ClientPayloadHandler`, `IPayloadContext`) y `CreativeTabsScreenPage`. Casi todo tiene equivalente en Fabric API (`ClientTickEvents`, `WorldRenderEvents`, `ClientCommandRegistrationCallback`, `FabricLoader`, entrypoint `ClientModInitializer`); lo de `RegistrySync` y `CreativeTabsScreenPage` no.
- **Metodos que NeoForge anade a vanilla** y que en Fabric no existen: `AbstractContainerScreen.getGuiLeft/getGuiTop/getSlotUnderMouse` (~130 usos: hay que sustituirlos por accessors de `leftPos/topPos/hoveredSlot`), `Gui.leftHeight/rightHeight/getHighlightTip`, `Player.refreshDisplayName/refreshTabListName`, `Particle.getRenderBoundingBox`, `CreativeModeInventoryScreen.hasSearchBar`, `InventoryScreen.renderEntityInInventoryFollowsAngle`.

## Riesgos que no se ven compilando
- Las partes de render (`RenderLevelStageEvent`, reparto en regiones) estan hechas con mixins propios sobre vanilla, pero el evento de etapa del mundo es de NeoForge: en Fabric hay que usar `WorldRenderEvents` o un mixin, y revisar el orden.
- Los tres juegos de `Mixin` que tocan Sodium/Iris/Controlify se comportan distinto en Fabric (el mismo mod existe para los dos cargadores).
- Hay que montar la distribucion: `fabric.mod.json`, refmap, jar para Fabric ademas del de NeoForge (dos artefactos, o un proyecto multicargador).

## Estimacion
Sacar un jar de Fabric que compile: unas horas a 1-2 dias (sustituir eventos y los metodos de NeoForge). Que funcione en pantalla dividida: pasar el autotest completo en Fabric, con 1-3 dias mas y los riesgos de mixins en el primer arranque. Mantener dos cargadores obliga a pasar el autotest dos veces por cambio.

## Porte hecho (2026-10-25) — rama `port-fabric`
La rama `port-fabric` (worktree `../consolepad-port-fabric`) tiene el porte completo a Fabric 1.21.1; `master` sigue siendo NeoForge. Jar: `build/libs/consolepad-fabric-0.15.0-beta.jar` (con `fabric.mod.json` y `consolepad.refmap.json`).
- **Compila sin errores** y arranca en el entorno de Fabric (`./gradlew runClient`, Loom 1.15.5, Fabric API 0.116.17).
- **Autotest completo en Fabric: 866 OK, 2 FALLO** (particulas sueltas junto al otro jugador, la conocida de la hoja de ruta, y el resumen de la casilla 49 que depende de ella). La casilla 49 sola: 156 OK, 0 FALLO.
- **Cambios respecto a NeoForge:**
  - Eventos de NeoForge -> Fabric API (`ClientTickEvents`, `WorldRenderEvents`, `ClientCommandRegistrationCallback`, `ScreenEvents`, `PlayerBlockBreakEvents`, `AttackEntityCallback`, `ServerTickEvents`, `ClientPlayConnectionEvents`, `ServerPlayConnectionEvents`).
  - Sin evento en Fabric, mixins nuevos: fotograma (`GameRendererFrameMixin`), nombre visible y de la lista de jugadores (`PlayerDisplayNameMixin`, `PlayerTabNameMixin`), terminar de comer o beber (`UseFinishRumbleMixin`), explosiones (`ExplosionRumbleMixin`).
  - Lo que NeoForge anade a vanilla se lee con accessors: `getGuiLeft/getGuiTop/getSlotUnderMouse` (`ScreenFields`), `KeyMapping.getKey`, `LevelRenderer.getTicks`, el HUD por piezas (`GuiParts` con `GuiInvoker`), las paginas de pestanas del creativo (en vanilla hay una sola), el recorte de particulas por campo de vision (en vanilla no existe, asi que no hace falta).
  - Quitados: `RegistrySyncMixin` (sincronizacion de registros de NeoForge). La reflexion por nombre de campos de vanilla (mesa de corte, telar, anzuelo) se sustituyo por accessors, porque en produccion los nombres de Fabric son otros.
  - `RenderSystemMixin` lleva `remap = false` (esa clase no esta ofuscada).
- **Sin probar:** el jar en un lanzador real de Minecraft con Fabric (solo el entorno de desarrollo), Sodium/Iris/Controlify en Fabric, y la sincronizacion de registros cuando hay mods de Fabric que anaden bloques.
- **Mantenerlo:** son dos ramas con el mismo codigo salvo estos archivos; cada cambio de `master` hay que fusionarlo en `port-fabric` y pasar el autotest en las dos.
