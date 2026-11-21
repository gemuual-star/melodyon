#!/usr/bin/env python3
"""Ребрендинг Metrolist -> Melodyon. Идемпотентен. Запуск из корня репо: python3 scripts/melodyon/rebrand.py"""
import os, re, subprocess, sys
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..', '..'))
SKIP_DIRS = {'.git', 'metroproto', 'libopus', 'build', '.gradle', '.kotlin'}
SKIP_FILES = {'LICENSE', 'rebrand.py', 'RULES.md', 'PROGRESS.md', 'SPEC.md'}
# внешние идентификаторы (сторонние библиотеки/подмодули/рабочие бэкенды) — не трогаем
PROTECT = [
    'com.github.MetrolistGroup.innertubex', 'com.github.MetrolistGroup.quackjs',
    'com.metrolist.innertubex', 'com.metrolist.music.listentogether.proto',
    'MetrolistGroup/metroproto.git', 'raw.githubusercontent.com/MetrolistGroup/faraday',
]
REPL = [
    ('com.metrolist.music', 'com.melodyon.app'),
    ('com/metrolist/music', 'com/melodyon/app'),
    ('METROLIST', 'MELODYON'), ('Metrolist', 'Melodyon'), ('metrolist', 'melodyon'),
    ('MetroList', 'Melodyon'), ('metroList', 'melodyon'),
]
def is_text(p):
    try:
        with open(p, 'rb') as f: b = f.read(8192)
        if b'\0' in b: return False
        b.decode('utf-8'); return True
    except Exception: return False
def transform(s):
    for i, t in enumerate(PROTECT): s = s.replace(t, f'\x00P{i}\x00')
    for a, b in REPL: s = s.replace(a, b)
    for i, t in enumerate(PROTECT): s = s.replace(f'\x00P{i}\x00', t)
    return s
changed = 0
for root, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
    for f in files:
        if f in SKIP_FILES: continue
        p = os.path.join(root, f)
        if os.path.islink(p) or not is_text(p): continue
        s = open(p, encoding='utf-8').read()
        n = transform(s)
        if n != s:
            open(p, 'w', encoding='utf-8').write(n); changed += 1
print('files changed:', changed)
# переименование путей (глубокие сначала)
paths = []
for root, dirs, files in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in SKIP_DIRS]
    for name in dirs + files:
        if 'metrolist' in name.lower(): paths.append(os.path.join(root, name))
paths.sort(key=lambda p: -p.count(os.sep))
for p in paths:
    d, name = os.path.split(p)
    new = os.path.join(d, transform(name))
    if new != p and os.path.exists(p):
        if os.path.exists(new):
            subprocess.run(['bash','-c',f'cp -a "{p}"/. "{new}"/ && rm -rf "{p}"'], check=True)
        else:
            os.rename(p, new)
print('paths renamed:', len(paths))
