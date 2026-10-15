# Tráiler corto (30 s) · guion

Pensado para grabarse con el juego real (OBS, 1080p60) y subirse a la ficha de Modrinth/CurseForge y a YouTube. Sin música con derechos: usar una pista libre (por ejemplo CC0) o ninguna. No hace falta voz: texto en pantalla en inglés y español (una línea de cada).

| Seg. | Plano | Texto en pantalla |
|---|---|---|
| 0–3 | Pantalla de título con el botón «Mandos»; llegan dos mandos al escritorio | **ConsolePad** · *Minecraft Java, con mandos* |
| 3–7 | Entrar en un mundo; sale «¿Cuántos jugadores sois?»; se elige 4 | *Pregunta cuántos sois y ya está / Asks how many of you there are* |
| 7–13 | Cuatro cuadrantes: cada uno camina, pica, construye | *Cada jugador, su pantalla / One screen each* |
| 13–17 | Inventario de consola con LB/RB cambiando de categoría en un jugador y crafteo en otro | *Menús estilo Bedrock / Bedrock-style menus* |
| 17–21 | El punto del compañero sobre la barra de experiencia; rueda rápida «Ir con alguien» | *Indicador del compañero y rueda rápida / Partner indicator and quick wheel* |
| 21–25 | Ajustes del jugador y pantalla de controles con el diagrama | *Todo configurable por jugador / Everything per player* |
| 25–30 | Vista de 2 jugadores con skins distintas; logotipo y «Disponible para NeoForge 1.21.1» | **ConsolePad** · *Modrinth · CurseForge* |

Pasos para grabarlo:

1. `tools/run-autotest.sh` no se usa (toca la ventana). Lanzar `./gradlew runClient` con el mundo «New World» y mandos reales.
2. Poner la interfaz en escala automática y el margen del HUD en 3 %.
3. Grabar cada plano por separado (5–10 s) y montar en cualquier editor.
4. Exportar a 1080p, sin audio o con pista libre; miniatura: `split-4-players.png`.
