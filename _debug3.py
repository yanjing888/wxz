# -*- coding: utf-8 -*-
import html
from pathlib import Path
raw = Path(r"D:\yanjing\97 projects\物小智-项目\_tmp_doubao.html").read_text(encoding="utf-8")
content = html.unescape(raw.replace("\\&quot;", '"').replace("&quot;", '"'))
idx = content.find("content_block")
Path(r"D:\yanjing\97 projects\物小智-项目\_extract_debug\cb.txt").write_text(content[idx:idx+1200], encoding="utf-8")
