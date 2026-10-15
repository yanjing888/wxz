# -*- coding: utf-8 -*-
import html
import json
import re
from pathlib import Path

raw = Path(r"D:\yanjing\97 projects\物小智-项目\_tmp_doubao.html").read_text(encoding="utf-8")
content = html.unescape(raw.replace("\\&quot;", '"').replace("&quot;", '"'))

# Find all text fields containing the guide title
title = "MKS‑NHY牛顿环仪器 详细实验指导书"
hits = []
for m in re.finditer(r'"text"\s*:\s*"', content):
    start = m.end()
    raw_chars = []
    i = start
    while i < len(content):
        c = content[i]
        if c == '"':
            bs = 0
            j = i - 1
            while j >= start and content[j] == '\\':
                bs += 1
                j -= 1
            if bs % 2 == 0:
                break
        raw_chars.append(c)
        i += 1
    raw_json = ''.join(raw_chars)
    if title not in raw_json:
        continue
    try:
        text = json.loads(f'"{raw_json}"')
    except json.JSONDecodeError as e:
        hits.append((start, None, str(e), raw_json[:80]))
        continue
    hits.append((start, len(text), text.count("\n"), text[:80]))

out = Path(r"D:\yanjing\00 资料\智能体\26立项--物小智\_hits.txt")
out.write_text("\n".join(str(h) for h in hits), encoding="utf-8")
print(len(hits), "hits")
