import re
from pathlib import Path
from collections import Counter
import sys

app = Path(sys.argv[1])
js = app.read_text(encoding="utf-8", errors="ignore")
paths = re.findall(r'path:"([^"]+)"', js) + re.findall(r"path:'([^']+)'", js)
titles = re.findall(r'title:"([^"]+)"', js) + re.findall(r"title:'([^']+)'", js)
meta_titles = re.findall(r'title:\s*"([^"]+)"', js)
zh = re.findall(r"[\u4e00-\u9fff]{2,24}", js)
c = Counter(zh)
print("=== PATHS ===")
for p in dict.fromkeys(paths):
    print(p)
print("=== TITLES ===")
for t in dict.fromkeys(titles + meta_titles):
    print(t)
print("=== TOP ZH ===")
for w, n in c.most_common(120):
    print(f"{n}\t{w}")
