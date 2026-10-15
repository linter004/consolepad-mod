#!/bin/bash
# Uso: tools/run-cleancheck.sh <archivo-de-salida>
# Simula un primer arranque: aparta run/config/consolepad y run/options.txt, lanza runClient con CONSOLEPAD_CLEANCHECK=1, espera "[cleancheck] terminado"
# (max 4 min), cierra el juego a la fuerza y restaura lo apartado. NO lanzar a la vez que run-autotest.sh (comparten la carpeta run/).
cd "$(dirname "$0")/.."
PROJ="$(basename "$PWD")"
OUT="${1:-cleancheck.log}"
BK="$(mktemp -d)"
[ -d run/config/consolepad ] && cp -r run/config/consolepad "$BK/consolepad" && rm -rf run/config/consolepad
[ -f run/options.txt ] && cp run/options.txt "$BK/options.txt" && rm -f run/options.txt
export JAVA_HOME=C:/Users/alvar/.jdks/jbr-21.0.11 CONSOLEPAD_CLEANCHECK=1
./gradlew runClient > "$OUT" 2>&1 &
for i in $(seq 1 48); do
  grep -aq "\[cleancheck\] terminado\|BUILD FAILED" "$OUT" 2>/dev/null && break
  sleep 5
done
sleep 3
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'fml.modFolders=consolepad.*$PROJ' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
sleep 3
rm -rf run/config/consolepad
[ -d "$BK/consolepad" ] && cp -r "$BK/consolepad" run/config/consolepad
[ -f "$BK/options.txt" ] && cp "$BK/options.txt" run/options.txt
echo "comprobacion de instalacion limpia finalizada; salida en $OUT"
grep -a "\[cleancheck\]" "$OUT" | cut -c1-220
