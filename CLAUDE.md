# CLAUDE.md

## Reglas de ahorro

- **Nivel por defecto:** Sonnet, esfuerzo medio. No cambies de modelo ni subas el esfuerzo por tu cuenta sin avisar antes.
- **Juez objetivo:** `./gradlew build` (y `./gradlew runClient` cuando aplique). Si pasa, la tarea está hecha en el nivel actual.
- **Escalado:** si la comprobación falla 2 veces seguidas, propón subir un escalón y espera confirmación:
  Sonnet medio → Sonnet alto → Opus medio.
- **Registro:** al terminar cada tarea, añade una entrada en `routing-log.md` con: tarea, nivel que la resolvió, intentos y coste estimado.
- **Aprendizaje:** cuando `routing-log.md` tenga 5 o más entradas, resume los patrones como reglas fijas en la sección "Reglas aprendidas" de este archivo.
- **Sin subagentes:** no uses subagentes ni equipos de agentes salvo petición explícita.

## Reglas aprendidas

_(Vacío hasta tener 5+ entradas en `routing-log.md`.)_
