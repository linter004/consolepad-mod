#!/bin/bash
# Uso: tools/run-autotest.sh <archivo-de-salida>
# Lanza runClient con el autotest, espera "[autotest] terminado" (max 20 min), cierra el juego a la fuerza
# y restaura run/config/consolepad desde una copia hecha antes. NO tocar la ventana mientras corre.
cd "$(dirname "$0")/.."
PROJ="$(basename "$PWD")"
OUT="${1:-autotest.log}"
BK="$(mktemp -d)"
cp -r run/config/consolepad "$BK/"
# Sodium e Iris (los jars de run/mods, para jugar con shaders) se esconden mientras corre el autotest: alteran los colores que miden las pruebas y exigen otra version
# de NeoForge que la de desarrollo. Vuelven al terminar. CONSOLEPAD_KEEP_MODS=1 los deja puestos (para probar los shaders con el autotest).
HID=0
if [ -z "$CONSOLEPAD_KEEP_MODS" ] && ls run/mods/*.jar > /dev/null 2>&1; then
  mkdir -p run/mods.off && mv run/mods/*.jar run/mods.off/ && HID=1
fi
export JAVA_HOME=C:/Users/<usuario>/.jdks/jbr-21.0.11 CONSOLEPAD_AUTOTEST="New World"
./gradlew runClient > "$OUT" 2>&1 &
GP=$!
for i in $(seq 1 240); do
  grep -aq "\[autotest\] terminado\|BUILD FAILED\|BUILD SUCCESSFUL" "$OUT" 2>/dev/null && break
  sleep 5
done
sleep 2
powershell.exe -NoProfile -Command "Get-CimInstance Win32_Process -Filter \"Name='java.exe'\" | Where-Object { \$_.CommandLine -match 'fml.modFolders=consolepad.*$PROJ' } | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force }"
sleep 3
cp -r "$BK/consolepad/." run/config/consolepad/
if [ "$HID" = 1 ]; then mv run/mods.off/*.jar run/mods/; fi
echo "autotest finalizado; salida en $OUT"
