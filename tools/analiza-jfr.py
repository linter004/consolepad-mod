"""Resume un perfil de JFR (tools/perfil-jfr.sh): uso  python tools/analiza-jfr.py run/perf/<etiqueta>.jfr [hilo=Render thread] [filtro de pila=GameRenderer.render]
Imprime, de las muestras del hilo (con el filtro en su pila): las funciones que mas tiempo propio gastan, y el tiempo "con todo lo de dentro" de las funciones clave.
Necesita el JDK 21 (jfr.exe): C:/Users/<usuario>/.jdks/jbr-21.0.11/bin/jfr.exe."""
import collections
import re
import subprocess
import sys

jfr = sys.argv[1]
thread = sys.argv[2] if len(sys.argv) > 2 else "Render thread"
need = sys.argv[3] if len(sys.argv) > 3 else "GameRenderer.render"
JFR = "C:/Users/<usuario>/.jdks/jbr-21.0.11/bin/jfr.exe"
text = subprocess.run([JFR, "print", "--events", "jdk.ExecutionSample", "--stack-depth", "96", jfr], capture_output=True, text=True, encoding="utf-8", errors="replace").stdout

blocks = text.split("jdk.ExecutionSample {")[1:]
frame_re = re.compile(r"^\s+([\w.$<>\[\]/]+)\(.*?\)\s+line:")
samples = []
for b in blocks:
    m = re.search(r'sampledThread = "([^"]*)"', b)
    if not m or m.group(1) != thread:
        continue
    st = b.split("stackTrace = [", 1)
    if len(st) < 2:
        continue
    frames = []
    for line in st[1].splitlines():
        fm = frame_re.match(line)
        if fm:
            frames.append(fm.group(1))
        elif line.strip().startswith("]"):
            break
    if frames and any(need in f for f in frames):
        samples.append(frames)

n = len(samples)
print(f"muestras de '{thread}' con '{need}' en la pila: {n}")
if not n:
    sys.exit(0)


def short(f):
    parts = f.split(".")
    return ".".join(parts[-2:]) if len(parts) >= 2 else f


own = collections.Counter(short(s[0]) for s in samples)
print("\n== Mas tiempo propio (parte superior de la pila) ==")
for f, c in own.most_common(28):
    print(f"{100.0 * c / n:5.1f} %  {f}")

incl = collections.Counter()
for s in samples:
    seen = set()
    for f in s:
        k = short(f)
        if k not in seen:
            seen.add(k)
            incl[k] += 1
keys = ["LevelRenderer.renderLevel", "LevelRenderer.setupRender", "LevelRenderer.renderEntities", "LevelRenderer.renderSectionLayer", "LevelRenderer.renderSnowAndRain",
        "LevelRenderer.renderClouds", "LevelRenderer.renderSky", "ParticleEngine.render", "ParticleEngine.tick", "GameRenderer.renderLevel", "Frustum.cubeInFrustum",
        "EntityRenderDispatcher.render", "BlockEntityRenderDispatcher.render", "ClientLevel.tickEntities", "LightTexture.updateLightTexture", "Gui.render", "SplitHud.render",
        "SplitRender.renderLevels", "ContextSwitcher.run", "SecondaryTick.tick", "Minecraft.tick", "ClientLevel.animateTick", "ChunkRenderDispatcher.uploadAllPendingUploads",
        "SectionRenderDispatcher.uploadAllPendingUploads", "SectionOcclusionGraph.update", "SectionRenderDispatcher.runTask", "LevelRenderer.cullTerrain", "LevelRenderer.applyFrustum"]
print("\n== Tiempo con todo lo de dentro (funciones clave) ==")
for k in keys:
    if incl[k]:
        print(f"{100.0 * incl[k] / n:5.1f} %  {k}")
if len(sys.argv) > 4 and sys.argv[4] == "incl":  # python tools/analiza-jfr.py f.jfr main GameRenderer.render incl : las 50 funciones con mas tiempo con todo lo de dentro
    print("\n== Mas tiempo con todo lo de dentro (50 primeras) ==")
    for f, c in incl.most_common(50):
        print(f"{100.0 * c / n:5.1f} %  {f}")
elif len(sys.argv) > 4:  # python tools/analiza-jfr.py f.jfr main GameRenderer.render ServerChunkCache.getChunk : las cadenas de llamadas mas comunes que llegan a esa funcion
    target = sys.argv[4]
    chains = collections.Counter()
    for smp in samples:
        for i, f in enumerate(smp):
            if target in f:
                chains[" <- ".join(short(x) for x in smp[i:i + 9])] += 1
                break
    print(f"\n== Cadenas de llamadas que llegan a '{target}' ==")
    for c, k in chains.most_common(6):
        print(f"{100.0 * k / n:5.1f} %  {c}")
print("\n== Funciones de ConsolePad con mas tiempo (con todo lo de dentro) ==")
cp = collections.Counter()
for s in samples:
    seen = set()
    for f in s:
        if "consolepad" in f:
            k = short(f)
            if k not in seen:
                seen.add(k)
                cp[k] += 1
for f, c in cp.most_common(18):
    print(f"{100.0 * c / n:5.1f} %  {f}")
