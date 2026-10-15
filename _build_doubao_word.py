# -*- coding: utf-8 -*-
"""从豆包分享页提取实验指导书，生成三个 Word 文档（含公式）。"""
from __future__ import annotations

import html
import json
import re
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path

URL = "https://www.doubao.com/thread/xegT3x0WTIqpVJ26P"
OUT_DIR = Path(r"D:\yanjing\00 资料\智能体\26立项--物小智")
WORK = Path(r"D:\yanjing\97 projects\物小智-项目\_doubao_word_tmp")
UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
)

TITLE = "MKS‑NHY牛顿环仪器 详细实验指导书"
BLOCK_ID = "10173410505179650"


def download_html() -> str:
    req = urllib.request.Request(URL, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read().decode("utf-8", errors="replace")


def normalize_embedded_json(raw_html: str) -> str:
    return html.unescape(raw_html.replace("\\&quot;", '"').replace("&quot;", '"'))


def extract_json_string(content: str, pos: int) -> str:
    raw_chars: list[str] = []
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
    for old, new in (
        ("\\\\dfrac", "\\dfrac"),
        ("\\\\lambda", "\\lambda"),
        ("\\\\ge", "\\ge"),
        ("\\\\text{", "\\text{"),
        ("\\\\mathrm{", "\\mathrm{"),
        ("\\\\cdot", "\\cdot"),
    ):
        text = text.replace(old, new)
    return text


def extract_guide_text(raw_html: str) -> str:
    content = normalize_embedded_json(raw_html)
    preferred = content.find(f'"block_id":"{BLOCK_ID}"')
    if preferred >= 0:
        tpos = content.find('"text":"', preferred)
        if tpos >= 0:
            raw_json = extract_json_string(content, tpos + len('"text":"'))
            if TITLE in raw_json:
                return decode_doubao_text(raw_json)

    for m in re.finditer(r'"text"\s*:\s*"', content):
        raw_json = extract_json_string(content, m.end())
        if TITLE not in raw_json:
            continue
        try:
            text = decode_doubao_text(raw_json)
        except json.JSONDecodeError:
            continue
        if "## 实验一" in text:
            return text

    raise RuntimeError("未在豆包页面中找到实验指导书正文")


def split_sections(text: str) -> dict[str, str]:
    exp1_start = text.find("## 实验一")
    exp2_start = text.find("## 实验二")
    exp3_start = text.find("## 补充实验三")
    ccd_start = text.find("## MKS‑NHY示教CCD系统")
    if min(exp1_start, exp2_start, exp3_start, ccd_start) < 0:
        raise RuntimeError("正文结构不完整，缺少实验分节")

    preamble = text[:exp1_start].rstrip()
    return {
        "实验一-牛顿环法测定平凸透镜曲率半径.docx": preamble + "\n\n" + text[exp1_start:exp2_start].rstrip(),
        "实验二-空气劈尖干涉测量薄片厚度.docx": preamble + "\n\n" + text[exp2_start:exp3_start].rstrip(),
        "实验三-读数显微镜练习-微小长度直接测量.docx": preamble
        + "\n\n"
        + text[exp3_start:ccd_start].rstrip()
        + "\n\n"
        + text[ccd_start:].rstrip(),
    }


def pandoc_to_docx(md_path: Path, docx_path: Path) -> None:
    cmd = [
        "pandoc",
        str(md_path),
        "-o",
        str(docx_path),
        "-f",
        "markdown+tex_math_dollars+smart",
        "--standalone",
    ]
    subprocess.run(cmd, check=True, capture_output=True, text=True, encoding="utf-8")


def apply_word_styles(docx_path: Path) -> None:
    from docx import Document
    from docx.enum.text import WD_LINE_SPACING
    from docx.oxml.ns import qn
    from docx.shared import Pt

    doc = Document(str(docx_path))

    section = doc.sections[0]
    section.page_height = Pt(841.9)
    section.page_width = Pt(595.3)

    def set_run_font(run, western: str, east_asia: str, size_pt: float, bold: bool = False) -> None:
        run.font.name = western
        run.font.size = Pt(size_pt)
        run.font.bold = bold
        r = run._element.rPr
        if r is not None:
            r.rFonts.set(qn("w:eastAsia"), east_asia)

    for style_name, east, size, bold in (
        ("Normal", "宋体", 12, False),
        ("Heading 1", "黑体", 16, True),
        ("Heading 2", "黑体", 14, True),
        ("Heading 3", "黑体", 12, True),
        ("Quote", "宋体", 12, False),
    ):
        try:
            st = doc.styles[style_name]
        except KeyError:
            continue
        st.font.name = "Times New Roman"
        st.font.size = Pt(size)
        st.font.bold = bold
        st._element.rPr.rFonts.set(qn("w:eastAsia"), east)

    for para in doc.paragraphs:
        pf = para.paragraph_format
        pf.line_spacing_rule = WD_LINE_SPACING.MULTIPLE
        pf.line_spacing = 1.5
        pf.space_after = Pt(0)
        pf.space_before = Pt(0)

        style = para.style.name if para.style else ""
        if style.startswith("Heading"):
            for run in para.runs:
                set_run_font(run, "Times New Roman", "黑体", 16 if "1" in style else 14, True)
        elif style == "Quote":
            for run in para.runs:
                set_run_font(run, "Times New Roman", "宋体", 12, run.bold)
        else:
            for run in para.runs:
                set_run_font(run, "Times New Roman", "宋体", 12, run.bold)

    doc.save(str(docx_path))


def main() -> None:
    if WORK.exists():
        shutil.rmtree(WORK)
    WORK.mkdir(parents=True)
    OUT_DIR.mkdir(parents=True, exist_ok=True)

    raw_html = download_html()
    (WORK / "page.html").write_text(raw_html, encoding="utf-8")

    guide = extract_guide_text(raw_html)
    (WORK / "full.md").write_text(guide, encoding="utf-8")

    sections = split_sections(guide)
    for filename, body in sections.items():
        md_path = WORK / filename.replace(".docx", ".md")
        docx_path = OUT_DIR / filename
        md_path.write_text(body, encoding="utf-8")
        pandoc_to_docx(md_path, docx_path)
        apply_word_styles(docx_path)
        print(f"OK {docx_path}")

    # 删除上次生成的 markdown 文档
    for old_md in OUT_DIR.glob("实验*.md"):
        old_md.unlink(missing_ok=True)

    shutil.rmtree(WORK)
    print("done")


if __name__ == "__main__":
    try:
        main()
    except subprocess.CalledProcessError as e:
        print(e.stderr or e.stdout or str(e), file=sys.stderr)
        sys.exit(1)
