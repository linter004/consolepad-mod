# ConsolePad - Fase 1 (NeoForge 1.21.1)

Capa de mandos para Minecraft Java con base para pantalla dividida 2-4 jugadores.

## Estado
- Deteccion de mandos (Xbox / PlayStation / Nintendo via GLFW) con asignacion a 4 slots de jugador.
- Slot 0 controla al jugador: movimiento analogico, camara, salto, atacar/usar, hotbar, inventario, pausa, sprint/sneak.
- Ajustes y bindings por jugador (PadSettings), todavia solo en memoria.
- Pantallas abiertas: solo se cierran con B / Start. Cursor virtual = fase 2.

## Probar
Requiere JDK 21. Copia estos archivos dentro de un MDK de NeoForge 1.21.1 (trae el gradle wrapper)
o ejecuta `gradle wrapper` si tienes Gradle instalado. Luego:

    ./gradlew runClient

Controles por defecto: stick izq = mover, stick der = camara, A = saltar, RT = atacar, LT = usar,
B = soltar, X = cambiar mano, Y = inventario, LB/RB = hotbar, L3 = correr, R3 = agacharse,
D-pad arriba = perspectiva, Start = pausa.
