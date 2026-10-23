#!/bin/bash
# Uso: tools/jugar.sh
# Abre el juego para jugar de verdad. Si hay Sodium e Iris en run/shader-kit (los jars) los pone en run/mods y deja puesto el pack ConsolePad Lite (zip hecho con
# tools/make-shaderpack.py), asi se juega con shaders; con ellos el entorno de desarrollo necesita NeoForge 21.1.248 (sus modulos de Fabric API lo exigen). Sin el kit,
# arranca como siempre. NO quita nada al cerrar. El autotest (tools/run-autotest.sh) esconde los jars mientras corre y los devuelve.
cd "$(dirname "$0")/.."
export JAVA_HOME=C:/Users/<usuario>/.jdks/jbr-21.0.11
mkdir -p run/mods run/shaderpacks
# Si un autotest se corto con los jars escondidos, se devuelven.
if [ "$CONSOLEPAD_SHADERS" = "1" ] && ls run/mods.off/*.jar > /dev/null 2>&1; then mv run/mods.off/*.jar run/mods/; fi
# Los shaders estan apagados por defecto (2026-10-23, peticion "quita los shader"): solo con CONSOLEPAD_SHADERS=1 se instala el kit.
if [ "$CONSOLEPAD_SHADERS" = "1" ] && ! ls run/mods/*.jar > /dev/null 2>&1 && ls run/shader-kit/*.jar > /dev/null 2>&1; then cp run/shader-kit/*.jar run/mods/; fi
if ls run/mods/*.jar > /dev/null 2>&1; then
  export ORG_GRADLE_PROJECT_neo_version=21.1.248
  if [ -f build/shaderpack/ConsolePadLite.zip ]; then cp build/shaderpack/ConsolePadLite.zip run/shaderpacks/ConsolePadLite.zip; fi
  if [ -f run/shaderpacks/ConsolePadLite.zip ] && ! grep -q "^shaderPack=ConsolePadLite.zip" run/config/iris.properties 2>/dev/null; then
    sed -i 's/^shaderPack=.*/shaderPack=ConsolePadLite.zip/; s/^enableShaders=.*/enableShaders=true/' run/config/iris.properties
  fi
  echo "jugando con shaders (Sodium + Iris + ConsolePad Lite, NeoForge 21.1.248)"
else
  echo "jugando sin shaders (no hay Sodium ni Iris en run/mods)"
fi
./gradlew runClient > run/play.log 2>&1
