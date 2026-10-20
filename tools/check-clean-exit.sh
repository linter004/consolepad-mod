#!/bin/bash
# Uso: tools/check-clean-exit.sh <log>
# Cuenta lo que ensucia el cierre: lineas de nivel ERROR del juego y excepciones, sin el ruido conocido de log4j (no puede borrar latest.log/debug.log
# porque otra instancia los tiene abiertos) y sin el aviso intermitente del motor de sonido de vanilla al parar una fuente ya liberada
# ("Stop: Invalid name parameter", visto tambien antes del sonido por jugador). Si el log es de un autotest, solo se juzga lo que pasa DESPUES de
# "[autotest] P1 sale del mundo" (el cierre): los jugadores 3 y 4 que salen a mitad de la prueba de rendimiento dejan a veces un
# ClosedChannelException del servidor con paquetes en vuelo, y no es del cierre. Imprime "cierre limpio" o las lineas encontradas; sale con 0 si esta limpio.
LOG="${1:?falta el log}"
START=$(grep -an "\[autotest\] P1 sale del mundo" "$LOG" | tail -1 | cut -d: -f1)
if [ -n "$START" ]; then SRC=$(tail -n +"$START" "$LOG"); else SRC=$(cat "$LOG"); fi
BAD=$(printf '%s\n' "$SRC" | grep -aE "/ERROR\]|Exception" | grep -avE "Unable to delete file|Failed to delete file|OpenAlUtil\]: Stop: Invalid name parameter" | cut -c1-220)
if [ -z "$BAD" ]; then echo "cierre limpio: 0 ERROR/Exception en $LOG"; exit 0; fi
echo "$BAD" | head -20
echo "cierre sucio: $(echo "$BAD" | wc -l) lineas en $LOG"
exit 1
