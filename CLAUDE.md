# CLAUDE.md

## Reglas de ahorro

- **Nivel por defecto:** Sonnet, esfuerzo medio. No cambies de modelo ni subas el esfuerzo por tu cuenta sin avisar antes.
- **Juez objetivo:** `./gradlew build` (y `./gradlew runClient` cuando aplique). Si pasa, la tarea está hecha en el nivel actual.
- **Escalado:** si la comprobación falla 2 veces seguidas, propón subir un escalón y espera confirmación:
  Sonnet medio → Sonnet alto → Opus medio.
- **Registro:** al terminar cada tarea, añade una entrada en `routing-log.md` con: tarea, nivel que la resolvió, intentos y coste estimado.
- **Aprendizaje:** cuando `routing-log.md` tenga 5 o más entradas, resume los patrones como reglas fijas en la sección "Reglas aprendidas" de este archivo.
- **Sin subagentes:** no uses subagentes ni equipos de agentes salvo petición explícita.

## Autonomía en el prototipo de pantalla dividida (pasos que quedan)

Estas reglas se suman a las de ahorro; no las sustituyen.

- **En cada paso:** explica en 3 líneas qué vas a hacer y empieza sin esperar mi OK. Implementa, compila, usa el autotest y haz un commit por paso, con la tarea y el nivel en `routing-log.md`.
- **Si la comprobación objetiva pasa**, pasa al siguiente paso del plan (`docs/splitscreen-plan.md`, sección 6) sin pedir permiso.
- **Para y espera solo si:**
  - (a) una comprobación falla: propón subir de nivel; no cambies de modelo sin mi confirmación;
  - (b) ves una fuga de contexto de P2 hacia P1;
  - (c) necesitas que yo mire algo jugando: dame una lista corta de qué probar;
  - (d) el uso semanal pasa del 40 % en `/usage`;
  - (e) hay que usar algo que gaste créditos de pago.
- **Fallos visuales:** se comprueban con medidas numéricas en el autotest (por ejemplo `SplitDiag` y `aspectTest`), no solo mirando capturas.

## Reglas aprendidas

Resumen de las 9 primeras entradas de `routing-log.md` (2026-10-07):

- **Sonnet medio basta para el código del mod.** Las 8 tareas de código (compilar, mixins, controles, cursor, D-pad, ajustes JSON) se resolvieron al primer intento sin escalar. No proponer subir de nivel para este tipo de tarea salvo 2 fallos seguidos del juez.
- **Opus solo para investigación o diseño amplio** (por ejemplo, el plan de pantalla dividida), y solo si lo pide el usuario.
- **Entorno:** Gradle necesita `JAVA_HOME=C:/Users/<usuario>/.jdks/jbr-21.0.11` (el Java por defecto es el 8). Las fuentes de MC para verificar nombres están en `build/moddev/artifacts/neoforge-21.1.92-sources.jar`: comprobar ahí los nombres antes de escribir mixins.
- **`build` no valida mixins.** Tras tocar mixins, lanzar `runClient` y revisar el log. Avisar de qué mixins solo se validan con una acción en el juego (entrar en un mundo, abrir un contenedor).
- **Ajustes guardados mandan sobre los valores por defecto.** Al cambiar un valor por defecto de `PadSettings`, actualizar también `run/config/consolepad/slot*.json` con el juego cerrado. Cerrar el juego a la fuerza evita que lo sobrescriba al salir.
- **Coste:** el gasto principal es esperar el arranque de `runClient` (unos 30 s si ya está descargado). Usar un Monitor corto con `grep` del log y pararlo al terminar.
