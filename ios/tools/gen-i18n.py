#!/usr/bin/env python3
"""Генерирует ios/FIXXE/Resources/i18n.json из Android-ресурсов app/src/main/res/values*/strings.xml.
Единственный источник истины для строк — strings.xml. Запуск: python3 ios/tools/gen-i18n.py"""
import os, re, json, xml.etree.ElementTree as ET

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
RES = os.path.join(ROOT, 'app/src/main/res')
OUT = os.path.join(ROOT, 'ios/FIXXE/Resources/i18n.json')

def tag_for(dirname):
    if dirname == 'values': return 'en'
    t = dirname[len('values-'):]
    return t[2:] if t.startswith('b+') else t   # values-b+he -> he

def unescape(s):
    if s is None: return ''
    s = s.replace('\\n', '\n').replace("\\'", "'").replace('\\"', '"').replace('\\@', '@')
    return s

def read(path):
    root = ET.parse(path).getroot()
    out = {}
    for el in root.findall('string'):
        name = el.get('name')
        # собираем текст вместе с вложенной разметкой (у нас её нет, но на будущее)
        text = ''.join(el.itertext())
        out[name] = unescape(text)
    return out

data = {}
for d in sorted(os.listdir(RES)):
    if not (d == 'values' or d.startswith('values-')): continue
    p = os.path.join(RES, d, 'strings.xml')
    if not os.path.exists(p): continue
    data[tag_for(d)] = read(p)

# en первым, остальные — в порядке как раньше
ordered = {'en': data.pop('en')}
ordered.update(data)
with open(OUT, 'w', encoding='utf-8') as f:
    json.dump(ordered, f, ensure_ascii=False, separators=(',', ':'))
print(f'i18n.json: {len(ordered)} languages, {len(ordered["en"])} keys -> {OUT}')
