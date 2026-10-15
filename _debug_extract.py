# -*- coding: utf-8 -*-
import html
from pathlib import Path
raw = Path(r"D:\yanjing\97 projects\物小智-项目\_tmp_doubao.html").read_text(encoding="utf-8")
content = html.unescape(raw)
idx = content.find("# MKS")
Path(r"D:\yanjing\97 projects\物小智-项目\_extract_debug\debug.txt").write_text(
    f"idx={idx}\n"
    f"snippet={repr(content[idx-200:idx+200]) if idx>=0 else 'NOT FOUND'}\n"
    f"content_block count={content.count('content_block')}\n"
    f"text_block count={content.count('text_block')}\n",
    encoding="utf-8",
)
