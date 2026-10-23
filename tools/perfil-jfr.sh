#!/bin/bash
# Uso: tools/perfil-jfr.sh <etiqueta> [jugadores]   (por defecto 4)
# Perfil de CPU del juego con el grabador de Java (JFR): lanza la prueba de rendimiento (casilla 46) con el numero de jugadores indicado y los ajustes de fabrica,
# graba una muestra cada 2 ms y, al terminar la prueba, vuelca la grabacion con jcmd (el cierre forzado del autotest la perderia) a run/perf/<etiqueta>.jfr.
# Despues: python tools/analiza-jfr.py run/perf/<etiqueta>.jfr. Esconde Sodium e Iris como run-autotest.sh. NO tocar la ventana mientras corre.
cd "$(dirname "$0")/.."
PROJ="$(basename "$PWD")"
TAG="${1:-perfil}"
NS="${2:-4}"
mkdir -p run/perf
JDK=C:/Users/<usuario>/.jdks/jbr-21.0.11
export JAVA_HOME=$JDK
BK="$(mktemp -d)"
cp -r run/config/consolepad "$BK/"
HID=0
if ls run/mods/*.jar > /dev/null 2>&1; then mkdir -p run/mods.off && mv run/mods/*.jar run/mods.off/ && HID=1; fi
export CONSOLEPAD_AUTOTEST="New World" CONSOLEPAD_AUTOTEST_CASES=46 CONSOLEPAD_PERF_NS="$NS" CONSOLEPAD_PERF_TAG="$TAG"
export JAVA_TOOL_OPTIONS="-XX:StartFlightRecording:name=cp,settings=profile,jdk.ExecutionSample#period=2ms,disk=true,maxsize=300M"
OUT="run/perf/$TAG.log"
./gradlew runClient > "$OUT" 2>&1 &
for i in $(seq 1 240); do
  grep -aq "\[autotest\] terminado\|BUILD FAILED\|BUILD SUCCESSFUL" "$OUT" 2>/dev/null && break
  sleep 5
done
PID=$(powershell.exe -NoProfile -Command "(Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'fml.modFolders=consolepad.*$PROJ' } | Select-Object -First 1).ProcessId" | tr -d '\r')
if [ -n "$PID" ]; then "$JDK/bin/jcmd" "$PID" JFR.dump name=cp filename="$(pwd)/run/perf/$TAG.jfr" > /dev/null 2>&1; fi
sleep 2
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'fml.modFolders=consolepad.*$PROJ' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
sleep 3
cp -r "$BK/consolepad/." run/config/consolepad/
if [ "$HID" = 1 ]; then mv run/mods.off/*.jar run/mods/; fi
unset JAVA_TOOL_OPTIONS
echo "perfil listo: run/perf/$TAG.jfr (pid $PID); resultados [perf] en $OUT"
