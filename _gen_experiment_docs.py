# -*- coding: utf-8 -*-
import html
import json
import re
from pathlib import Path

HTML_PATH = Path(r"D:\yanjing\97 projects\物小智-项目\_tmp_doubao.html")
OUT_DIR = Path(r"D:\yanjing\00 资料\智能体\26立项--物小智")

raw = HTML_PATH.read_text(encoding="utf-8")
content = html.unescape(raw.replace("\\&quot;", '"').replace("&quot;", '"'))


def extract_json_string_at(pos: int) -> str:
    raw_chars = []
    i = pos
    while i < len(content):
        c = content[i]
        if c == '"':
            bs = 0
            j = i - 1
            while j >= pos and content[j] == "\\":
                bs += 1
                j -= 1
            if bs % 2 == 0:
                break
        raw_chars.append(c)
        i += 1
    return "".join(raw_chars)


def decode_doubao_text(raw_json: str) -> str:
    text = json.loads(f'"{raw_json}"')
    if "\n" not in text and "\\n" in text:
        text = text.replace("\\n", "\n")
    text = re.sub(r"\\u([0-9a-fA-F]{4})", lambda m: chr(int(m.group(1), 16)), text)
    # LaTeX / escape cleanup from double-encoded JSON
    text = text.replace("\\\\dfrac", "\\dfrac")
    text = text.replace("\\\\lambda", "\\lambda")
    text = text.replace("\\\\ge", "\\ge")
    text = text.replace("\\\\text{", "\\text{")
    text = text.replace("\\\\mathrm{", "\\mathrm{")
    text = text.replace("\\\\cdot", "\\cdot")
    return text


title = "MKS‑NHY牛顿环仪器 详细实验指导书"
best_text = None

# Prefer assistant reply block id from the shared thread
preferred = content.find('"block_id":"10173410505179650"')
if preferred >= 0:
    tpos = content.find('"text":"', preferred)
    if tpos >= 0:
        raw_json = extract_json_string_at(tpos + len('"text":"'))
        if title in raw_json:
            best_text = decode_doubao_text(raw_json)

if not best_text:
    for m in re.finditer(r'"text"\s*:\s*"', content):
        raw_json = extract_json_string_at(m.end())
        if title not in raw_json:
            continue
        try:
            candidate = decode_doubao_text(raw_json)
        except json.JSONDecodeError:
            continue
        if "## 实验一" in candidate:
            best_text = candidate
            break

if not best_text or "## 实验一" not in best_text:
    raise SystemExit("extract failed")

text = best_text

full_path = OUT_DIR / "MKS-NHY牛顿环仪器-详细实验指导书（豆包对话完整回复）.md"
full_path.write_text(text, encoding="utf-8")

exp1_start = text.find("## 实验一")
exp2_start = text.find("## 实验二")
exp3_start = text.find("## 补充实验三")
ccd_start = text.find("## MKS‑NHY示教CCD系统")

preamble = text[:exp1_start].rstrip()
exp1_body = text[exp1_start:exp2_start].rstrip()
exp2_body = text[exp2_start:exp3_start].rstrip()
exp3_body = text[exp3_start:ccd_start].rstrip()
appendix = text[ccd_start:].rstrip()

files = [
    ("实验一-牛顿环法测定平凸透镜曲率半径.md", preamble + "\n\n" + exp1_body),
    ("实验二-空气劈尖干涉测量薄片厚度.md", preamble + "\n\n" + exp2_body),
    (
        "实验三-读数显微镜练习-微小长度直接测量.md",
        preamble + "\n\n" + exp3_body + "\n\n" + appendix,
    ),
]

for filename, body in files:
    (OUT_DIR / filename).write_text(body, encoding="utf-8")

Path(r"D:\yanjing\97 projects\物小智-项目\_gen_stats.txt").write_text(
    f"chars={len(text)}\nlines={text.count(chr(10))+1}\n",
    encoding="utf-8",
)
