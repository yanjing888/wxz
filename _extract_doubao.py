# -*- coding: utf-8 -*-
import json
import re
from pathlib import Path

HTML_PATH = Path(r"D:\yanjing\97 projects\物小智-项目\_tmp_doubao.html")
OUT_DIR = Path(r"D:\yanjing\00 资料\智能体\26立项--物小智")
DEBUG_DIR = Path(r"D:\yanjing\97 projects\物小智-项目\_extract_debug")

content = HTML_PATH.read_text(encoding="utf-8")

# Try to parse embedded JSON
m = re.search(r"_ROUTER_DATA\s*=\s*(\{.*?\});", content, re.DOTALL)
if m:
    data = json.loads(m.group(1))
    (DEBUG_DIR / "router_data.json").write_text(
        json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8"
    )

# Find message blocks - look for text content in JSON strings
# Extract all long Chinese text segments
text_segments = re.findall(r'"text"\s*:\s*"((?:\\.|[^"\\])*)"', content)
print(f"Found {len(text_segments)} text segments")

# Also try content/text fields
for field in ["content", "message", "reply", "answer"]:
    segs = re.findall(rf'"{field}"\s*:\s*"((?:\\.|[^"\\])*)"', content)
    print(f"{field}: {len(segs)} segments")

# Decode escaped JSON strings and collect substantial text
decoded = []
for seg in text_segments:
    try:
        t = json.loads(f'"{seg}"')
    except Exception:
        t = seg.replace("\\n", "\n").replace('\\"', '"')
    if len(t) > 200 and any(k in t for k in ["实验", "步骤", "牛顿", "空气", "显微"]):
        decoded.append(t)

print(f"Relevant decoded segments: {len(decoded)}")
for i, t in enumerate(decoded[:5]):
    (DEBUG_DIR / f"segment_{i}.txt").write_text(t, encoding="utf-8")
    print(f"  seg {i}: len={len(t)}, start={t[:80]!r}")

# Find assistant reply blocks by looking for markdown-style headers
# Search for pattern: 实验一/二/三 with surrounding context
exp_pattern = re.compile(
    r"(实验[一二三][：:].*?)(?=实验[一二三][：:]|$)", re.DOTALL
)
# First get a large decoded blob from the page
all_text = []
for seg in text_segments:
    try:
        t = json.loads(f'"{seg}"')
    except Exception:
        continue
    all_text.append(t)

full = "\n".join(all_text)
(DEBUG_DIR / "all_text_joined.txt").write_text(full, encoding="utf-8")
print(f"Full joined text length: {len(full)}")

# Find experiment sections in full text
for marker in ["实验一", "实验二", "实验三"]:
    idx = full.find(marker)
    print(f"{marker} at {idx}")
