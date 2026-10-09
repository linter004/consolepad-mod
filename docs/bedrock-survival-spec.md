# Especificación: menús de supervivencia de Minecraft Bedrock con mando de Xbox

Objetivo: que los menús de supervivencia de ConsolePad (inventario con crafteo, mesa de crafteo, horno, cofre) se parezcan a los de Bedrock con mando. El **modo creativo no se toca**. Las pantallas se dibujan con las texturas y el estilo de Java: aquí solo se copia la **disposición y el comportamiento**, ninguna textura ni recurso de Bedrock entra en el mod.

Leyenda: **[V]** = visto en una captura (primera mano); **[S]** = supuesto o deducido; **[W]** = dicho por una fuente web.

## Fuentes usadas

**Capturas (vistas con la herramienta de lectura de imágenes).** Ocho capturas de Bedrock en español con mando de Xbox que ya estaban en `docs/reference/` (carpeta ignorada por git; el archivo no dice de dónde salen, parecen del propio usuario): `crafteo.png`, `crafteo-supervivencia.png`, `crafteo-supervivencia 2.png`, `mesa de crafto.png`, `horno.png`, `cofre.png`, `creativo.png`, `creativo-pestanas 1.png` (más `creativo-pestanas 2/3`, no usadas). No he descargado ninguna imagen nueva: la búsqueda web no dio capturas utilizables (ver abajo).

**Web (solo texto, no pude ver imágenes de ellas):**
- Minecraft Wiki, [Controls](https://minecraft.wiki/w/Controls): enlaces por defecto de Xbox en el juego (X = crafteo, Y = inventario, LB/RB = objeto anterior/siguiente, stick izquierdo pulsado = correr, cruceta abajo = tirar objeto). **[W]** No da el mapa de botones de los menús.
- Minecraft Wiki, [Crafting Table](https://minecraft.wiki/w/Crafting_Table): en Bedrock el libro de recetas está desplegado a la izquierda de la cuadrícula y fabrica con un clic; desde 1.2.0 hay una pestaña de recetas aprendidas que rellena la cuadrícula al elegir un objeto. **[W]**
- Minecraft Wiki, [Recipe book](https://minecraft.wiki/w/Furnace_recipe_book): pestañas de categorías, buscador y alternar "todas / solo fabricables" (descrito sobre todo para Java). **[W]**
- Windows Central, [1.16.20.53 beta](https://www.windowscentral.com/nether-update-minecraft-gets-1162053-beta-changes-controllers): no pude leer el artículo (solo salió el pie de la página); el resumen de la búsqueda decía que sobre una receta no fabricable aparece el botón "Seleccionar receta" y que pulsar el stick derecho vacía la cuadrícula. **[W, sin verificar]**
- Una página de la comunidad de player.games (`control_test`) lista LB/RB = categorías, A = fabricar, B = cerrar, pero es un menú de creador, **no** el de Bedrock vanilla; **no la uso**.

Conclusión: **la fuente fiable son las capturas**. Lo que no se ve en una captura estática (qué hace cada botón al pulsarlo, animaciones) está marcado [S] y habrá que comprobarlo con un mando en Bedrock.

## 1. Estructura común de las pantallas con libro de recetas **[V]**

Una ventana ancha con **dos paneles** lado a lado (en la mesa y el horno; el inventario del jugador es el mismo con el libro cerrado):

```
 LB [cat1][cat2][cat3][cat4]   [buscar] RB      LT [libro+cofre][cofre] [?] RT
 ┌──────────────────────────────┐ ┌─────────────────────────────────────┐
 │ <título de la lista>  [filtro]│ │ Fabricar  ┌───┐ → ┌───┐             │
 │ ┌─┬─┬─┬─┬─┬─┬─┬─┬┐           │ │           │3x3│    │res│             │
 │ │ │ │ │ │ │ │ │ │▐ scroll   │ │ Inventario └───┘    └───┘             │
 │ └─┴─┴─┴─┴─┴─┴─┴─┴┘           │ │ [9 x 3 huecos] + barra rápida aparte │
 └──────────────────────────────┘ └─────────────────────────────────────┘
 LS  <libro de recetas / inventario>        X Coger la mitad  A Coger  Y Movimiento rápido  B Salir
```

- **Panel izquierdo = libro de recetas.** Pestañas de categoría arriba, con los iconos **LB** a su izquierda y **RB** a su derecha (LB/RB cambian de pestaña **[S]**: los iconos flanquean el grupo). La última pestaña es una **lupa** (buscar) separada del resto por un hueco. Debajo, el título de la lista ("Objetos", "Todas las recetas"), un interruptor pequeño a la derecha del título y una cuadrícula de recetas con barra de desplazamiento a la derecha. **[V]**
- **Panel derecho = crafteo + inventario.** Arriba, una fila de pestañas pequeñas con **LT** a la izquierda y **RT** a la derecha: [libro + cofre] (la vista seleccionada), [cofre] y [?] (ayuda). **[V]** Se supone que LT/RT las cambian **[S]**.
- **Barra de ayuda de botones abajo** (iconos de Xbox): a la izquierda `LS <texto>`; a la derecha `X Coger la mitad · A Coger · Y Movimiento rápido · B Salir`. **[V]** La izquierda dice "Libro de recetas" (inventario con libro abierto), "Abrir libro de recetas" (libro cerrado) o "Inventario" (mesa de crafteo). **[V]** Así que **pulsar el stick izquierdo (L3) abre/cierra el libro** y, en la mesa, vuelve al inventario **[S]**.
- El hueco seleccionado se marca con un fondo **verde** y el objeto con cursor muestra el tooltip oscuro de siempre ("Diamante"). **[V]**

## 2. Inventario de supervivencia (con crafteo 2×2) **[V]**

- Panel derecho: a la izquierda una **columna de 4 huecos de armadura** (casco, peto, pantalones, botas) con su icono fantasma; al lado, el **maniquí** del jugador sobre fondo negro; bajo él a la derecha el **hueco de la otra mano** (escudo fantasma). A la derecha del maniquí: título "Fabricar", **cuadrícula 2×2**, flecha y **hueco de resultado**.
- Debajo: **9×3 huecos del inventario** y, separada por un pequeño espacio, la **barra rápida de 9**.
- Libro cerrado (`crafteo.png`): solo este panel, centrado; barra de ayuda `LS Abrir libro de recetas` … `B Salir` (las demás acciones X/A/Y no aparecen hasta que hay cursor sobre un hueco **[S]**).
- Libro abierto (`crafteo-supervivencia 2.png`): el panel de recetas aparece **a la izquierda** y el conjunto se desplaza; barra con `LS Libro de recetas`, X, A, Y, B.
- Categorías del inventario (4 + lupa): **ladrillos (construcción), espada+casco (equipamiento), cama (objetos), hierba+planta (naturaleza), lupa (buscar)**; el título de la lista es "Objetos" y, junto a él, un interruptor con el icono de la mesa de crafteo (filtro "recetas de la mesa") **[V]**.

## 3. Mesa de crafteo **[V]**

Igual que el inventario pero: cuadrícula **3×3** sin armadura ni maniquí, el **título "Fabricar"** y el texto "Inventario" encima de los huecos; categorías iguales (construcción, equipamiento, objetos, naturaleza) más lupa; barra de ayuda con `LS Inventario` (L3 vuelve al inventario **[S]**).

## 4. Horno **[V]**

- Panel izquierdo con **tres categorías** (comida, bloques/lingotes, vidrio-piedra) más lupa, y abierto en la lupa: título "Todas las recetas", **campo de búsqueda** con dos botoncitos a su derecha (filtros).
- Muchas recetas salen **teñidas de rojo**: lo más probable es que sean las que **no se pueden hacer con lo que llevas** **[S]** (lo contrario ocurre en las otras capturas, sin tinte). En el crafteo también aparecen unas **"+" blancas** en la esquina de algunos objetos (la receta tiene varias combinaciones o es nueva) **[S]**, no seguro.
- Panel derecho: título "Horno", **hueco de entrada** arriba, **hueco de combustible** debajo con la llama fantasma, flecha de progreso y **hueco de salida**; debajo el inventario como siempre.

## 5. Cofre **[V]**

- **Sin libro de recetas ni pestañas.** Una sola ventana estrecha: título "Cofre" y **9×3 huecos**; debajo "Inventario" con 9×3 y la barra rápida de 9 separada.
- Barra de ayuda **solo con** `X Coger la mitad · A Coger · Y Movimiento rápido · B Salir` (sin `LS`).

## 6. Qué hace cada botón en estas pantallas

| Botón | Hace | Origen |
|---|---|---|
| A | Coger / soltar el objeto del hueco (pila entera) | [V] barra de ayuda |
| X | Coger la mitad (clic derecho) | [V] barra de ayuda |
| Y | Movimiento rápido (mover pila al otro inventario) | [V] barra de ayuda |
| B | Salir / cerrar | [V] barra de ayuda |
| LS (L3) | Abre/cierra el libro de recetas; en la mesa, vuelve al inventario | [V] texto; acción [S] |
| LB / RB | Pestaña anterior / siguiente de categorías del libro | [S] (iconos junto a las pestañas) |
| LT / RT | Pestaña anterior / siguiente de la fila de la derecha (libro+cofre, cofre, ?) | [S] |
| D-pad / stick izq. | Mover el cursor de hueco en hueco | [S] (convención de consola) |
| Stick der. | Desplazar la lista de recetas | [S] |
| Elegir una receta con A | Rellena la cuadrícula con los ingredientes del inventario; con el resultado en el hueco, A lo coge | [W, wiki: "rellena la receta"], detalle [S] |

## 7. Lo que el mod ya tiene y lo que falta (para los pasos 3 y 4)

Ya hecho en ConsolePad: cursor propio por jugador (stick/D-pad por casillas, A/X/Y/B), barra de ayuda de botones abajo. Falta: panel de libro de recetas a la izquierda con pestañas LB/RB, cuadrícula de recetas navegable, resaltado de lo que no se puede fabricar, filtro, lupa/búsqueda, L3 para abrir el libro, y las pestañas LT/RT de la derecha (primera versión: solo la fila de vista, sin "?").

**Cosas que no se pueden saber con las capturas** (hay que probar en Bedrock): si A sobre una receta la **coloca** o la **fabrica** de golpe; qué hace exactamente el interruptor junto al título; el significado de las "+"; cómo se ordenan las recetas (se dice que las fabricables primero **[W, sin verificar]**).
