#!/usr/bin/env python3
"""Sync KB markdown from docs/ to backend resources and Dify export pack."""

from __future__ import annotations

import json
import re
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

# (source under docs/, backend relative path, dataset, default experiment_code)
KB_FILES: list[tuple[str, str, str, str]] = [
    ("visual-rules/general.md", "experiments/general/visual-rules.md", "correction_rules", "general"),
    ("visual-rules/newton_rings.md", "experiments/newton_rings/visual-rules.md", "correction_rules", "newton_rings"),
    ("visual-rules/tensile_steel.md", "experiments/tensile_steel/visual-rules.md", "correction_rules", "tensile_steel"),
    ("teaching-knowledge/newton_rings.md", "experiments/newton_rings/teaching-knowledge.md", "teaching", "newton_rings"),
    ("teaching-knowledge/tensile_steel.md", "experiments/tensile_steel/teaching-knowledge.md", "teaching", "tensile_steel"),
    ("teaching-knowledge/general.md", "experiments/general/teaching-knowledge.md", "teaching", "general"),
]

FRONTMATTER_RE = re.compile(r"^---\s*\n(.*?)\n---\s*\n", re.DOTALL)


def parse_frontmatter(text: str) -> dict[str, str]:
    match = FRONTMATTER_RE.match(text)
    if not match:
        return {}
    meta: dict[str, str] = {}
    for line in match.group(1).splitlines():
        if ":" not in line:
            continue
        key, value = line.split(":", 1)
        meta[key.strip()] = value.strip().strip('"').strip("'")
    return meta


def sync_to_backend() -> list[Path]:
    backend_root = ROOT / "backend" / "src" / "main" / "resources"
    written: list[Path] = []
    for rel_src, rel_dst, _dataset, _exp in KB_FILES:
        src = ROOT / "docs" / rel_src
        dst = backend_root / rel_dst
        if not src.exists():
            print(f"skip missing source: {src}")
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        written.append(dst)
        print(f"synced {src.relative_to(ROOT)} -> {dst.relative_to(ROOT)}")
    return written


def export_dify_pack() -> list[Path]:
    export_root = ROOT / "docs" / "kb" / "export"
    written: list[Path] = []
    for rel_src, _rel_dst, dataset, default_exp in KB_FILES:
        src = ROOT / "docs" / rel_src
        if not src.exists():
            continue
        text = src.read_text(encoding="utf-8")
        meta = parse_frontmatter(text)
        exp_code = meta.get("experiment_code", default_exp)
        kb_type = meta.get("kb_type", "correction_rule" if dataset == "correction_rules" else "teaching")
        doc_name = meta.get("doc") or rel_src.replace("\\", "/")
        section_key = meta.get("sectionKey") or exp_code

        out_dir = export_root / dataset
        out_dir.mkdir(parents=True, exist_ok=True)
        out_md = out_dir / f"{exp_code}.md"
        out_md.write_text(text, encoding="utf-8")
        written.append(out_md)

        meta_path = out_dir / f"{exp_code}.metadata.json"
        meta_path.write_text(
            json.dumps(
                {
                    "experiment_code": exp_code,
                    "kb_type": kb_type,
                    "doc": doc_name,
                    "sectionKey": section_key,
                    "dataset": dataset,
                },
                ensure_ascii=False,
                indent=2,
            )
            + "\n",
            encoding="utf-8",
        )
        written.append(meta_path)
        print(f"exported {out_md.relative_to(ROOT)}")
    return written


def main() -> None:
    sync_to_backend()
    export_dify_pack()
    print("done")


if __name__ == "__main__":
    main()
