#!/bin/bash
# Uso: tools/check-clean-exit.sh <log>
# Cuenta lo que ensucia el cierre: lineas de nivel ERROR del juego y excepciones, sin el ruido conocido de log4j (no puede borrar latest.log/debug.log
# porque otra instancia los tiene abiertos). Imprime "cierre limpio" o las lineas encontradas; sale con 0 si esta limpio.
LOG="${1:?falta el log}"
BAD=$(grep -aE "/ERROR\]|Exception" "$LOG" | grep -avE "Unable to delete file|Failed to delete file" | cut -c1-220)
if [ -z "$BAD" ]; then echo "cierre limpio: 0 ERROR/Exception en $LOG"; exit 0; fi
echo "$BAD" | head -20
echo "cierre sucio: $(echo "$BAD" | wc -l) lineas en $LOG"
exit 1
