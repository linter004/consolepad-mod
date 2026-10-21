"""Construye el zip del pack de shaders ConsolePad Lite: python tools/make-shaderpack.py (desde la raiz). Salida: build/shaderpack/ConsolePadLite.zip.
El zip es reproducible (orden fijo y fecha fija): el mismo contenido da siempre el mismo SHA-256."""
import hashlib
import os
import sys
import zipfile

SRC = os.path.join("tools", "shaderpack", "ConsolePadLite")
OUT_DIR = os.path.join("build", "shaderpack")
OUT = os.path.join(OUT_DIR, "ConsolePadLite.zip")

files = []
for root, _dirs, names in os.walk(SRC):
    for n in names:
        full = os.path.join(root, n)
        files.append((os.path.relpath(full, SRC).replace(os.sep, "/"), full))
files.sort()
if not any(rel == "shaders/consolepad-splitscreen-safe" for rel, _ in files):
    sys.exit("falta el marcador shaders/consolepad-splitscreen-safe")

os.makedirs(OUT_DIR, exist_ok=True)
with zipfile.ZipFile(OUT, "w", zipfile.ZIP_DEFLATED) as z:
    for rel, full in files:
        info = zipfile.ZipInfo(rel, date_time=(2026, 1, 1, 0, 0, 0))
        info.compress_type = zipfile.ZIP_DEFLATED
        info.external_attr = 0o644 << 16
        with open(full, "rb") as f:
            data = f.read().replace(b"\r\n", b"\n")  # el mismo texto con cualquier fin de linea
        z.writestr(info, data)
print(OUT, len(files), "archivos", hashlib.sha256(open(OUT, "rb").read()).hexdigest())
