#!/bin/bash
# Uso: tools/run-autotest.sh <archivo-de-salida>
# Lanza runClient con el autotest, espera "[autotest] terminado" (max 20 min), cierra el juego a la fuerza
# y restaura run/config/consolepad desde una copia hecha antes. NO tocar la ventana mientras corre.
cd "$(dirname "$0")/.."
OUT="${1:-autotest.log}"
BK="$(mktemp -d)"
cp -r run/config/consolepad "$BK/"
export JAVA_HOME=C:/Users/<usuario>/.jdks/jbr-21.0.11 CONSOLEPAD_AUTOTEST="New World"
./gradlew runClient > "$OUT" 2>&1 &
GP=$!
for i in $(seq 1 240); do
  grep -aq "\[autotest\] terminado\|BUILD FAILED\|BUILD SUCCESSFUL" "$OUT" 2>/dev/null && break
  sleep 5
done
sleep 2
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'fml.modFolders=consolepad' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
sleep 3
cp -r "$BK/consolepad/." run/config/consolepad/
echo "autotest finalizado; salida en $OUT"
