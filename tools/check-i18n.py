#!/usr/bin/env python3
"""Comprobacion estatica de idiomas: lista los textos con aspecto de espanol que NO pasan por I18n.tr / I18n.lit / I18n.fmt en las clases de interfaz
(texto de pantallas, ayudas, avisos). Uso: python tools/check-i18n.py  (desde la raiz). Sale con 0 si no hay ninguno.
Tambien comprueba que cada texto de I18n.tr/lit/fmt tiene traduccion en assets/consolepad/i18n/en.json."""
import json, os, re, sys

sys.stdout.reconfigure(encoding='utf-8')
root = 'src/main/java/dev/consolepad'
skip = re.compile(r'(Test|AutoTest|ShotsTest|CleanCheck|PerfProbe|SplitDiag|I18n|GuiDiagMixin|WelcomeScreen|Compat|PadManager|LookControl)\.java$')
accent = re.compile(r'[áéíóúñÁÉÍÓÚÑ¿¡]')
words = re.compile(r'\b(jugador|jugadores|mando|mandos|ajustes|volver|salir|guardar|partida|pausa|botón|boton|botones|menú|pantalla|reparto|opciones|elige|pulsa|mantén|atrás|cerrar|aceptar|restablecer|cancelar|nuevo|borrar|editar|perfil|nombre|sensibilidad|distancia|velocidad|fuerza|vibración|marcar|cama|compañero|sí|rápido|suave|lento|fuerte|barra|mostrar|ayuda)\b', re.I)
lit = re.compile(r'"((?:[^"\\\n]|\\.)*)"')
wrapped_call = re.compile(r'I18n\.(?:tr|lit|fmt)\(\s*$')
LOG_OR_CODE = re.compile(r'static final String\[\]|LETTERS|SYMBOLS|lastWhy|"pantalla/|LOGGER|^\s*@|case "|getResourceAsStream|\.matches\(|Pattern\.|equals\(|check\(|Verdict|selfTest|return "[a-z]')

# 1) literales con aspecto de espanol sin envolver
bad = []
for dp, dn, fn in os.walk(root):
    for f in sorted(fn):
        if not f.endswith('.java') or skip.search(f):
            continue
        p = os.path.join(dp, f)
        for i, line in enumerate(open(p, encoding='utf-8'), 1):
            st = line.strip()
            if st.startswith(('//', '*', '/*')) or LOG_OR_CODE.search(line):
                continue
            code = re.sub(r'\s//.*$', '', line)
            for m in lit.finditer(code):
                s = m.group(1)
                if len(s) < 3 or not (accent.search(s) or words.search(s)):
                    continue
                before = code[:m.start()]
                if wrapped_call.search(before) or re.search(r'I18n\.fmt\([^;]*$', before) or re.search(r'I18n\.(?:tr|lit)\([^;]*\?[^;]*$', before):
                    continue
                bad.append(f"{os.path.relpath(p, root)}:{i}: {s[:90]}")
for b in bad:
    print('SIN ENVOLVER', b)

# 2) cada texto envuelto tiene traduccion
en = json.load(open('src/main/resources/assets/consolepad/i18n/en.json', encoding='utf-8'))
call = re.compile(r'I18n\.(?:tr|lit|fmt)\(\s*"((?:[^"\\]|\\.)*)"')
missing = []
for dp, dn, fn in os.walk(root):
    for f in sorted(fn):
        if not f.endswith('.java') or skip.search(f):
            continue
        txt = open(os.path.join(dp, f), encoding='utf-8').read()
        for m in call.finditer(txt):
            k = m.group(1)
            if not (accent.search(k) or words.search(k)) or len(k) < 3:
                continue
            if k in en or k + ' ' in en or k.strip() in en or k.rstrip(': ') in en or (k.rstrip(': ') + ': ') in en:
                continue
            missing.append(f"{f}: {k[:90]}")
for k in missing:
    print('SIN TRADUCCION', k)
print(f"{len(bad)} sin envolver, {len(missing)} sin traduccion")
sys.exit(1 if bad or missing else 0)
