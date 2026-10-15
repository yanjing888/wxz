# -*- coding: utf-8 -*-
import html
from pathlib import Path
raw = Path(r"D:\yanjing\97 projects\物小智-项目\_tmp_doubao.html").read_text(encoding="utf-8")
content = html.unescape(raw)
out = Path(r"D:\yanjing\97 projects\物小智-项目\_extract_debug\anchors.txt")
lines = []
lines.append(f"raw len={len(raw)}")
lines.append(f"unescaped len={len(content)}")
for s in ['content_block', 'text_block', '# MKS', 'MKS']:
    lines.append(f"{s!r}: raw={raw.find(s)} unesc={content.find(s)}")
idx = raw.find('content_block')
if idx >= 0:
    lines.append("RAW SNIPPET:")
    lines.append(raw[idx:idx+400])
idx2 = content.find('MKS')
if idx2 >= 0:
    lines.append("UNESC SNIPPET:")
    lines.append(repr(content[idx2-50:idx2+100]))
out.write_text("\n".join(lines), encoding="utf-8")
