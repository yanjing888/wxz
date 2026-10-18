#!/usr/bin/env python3
"""Export the backend's canonical experiment knowledge to a Dify import pack."""

from __future__ import annotations

import json
import re
import argparse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

EXPERIMENTS = ROOT / "backend" / "src" / "main" / "resources" / "experiments"
KB_DOCUMENTS = {
    "teaching-knowledge.md": ("teaching", "teaching"),
    "visual-rules.md": ("correction_rules", "correction_rule"),
}

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


def export_dify_pack(export_root: Path, experiments: Path = EXPERIMENTS) -> list[Path]:
    # Validate all sources before writing, so an incomplete experiment fails visibly.
    documents = []
    for manifest in sorted(experiments.glob("*/manifest.json")):
        exp_code = manifest.parent.name
        for filename, (dataset, kb_type) in KB_DOCUMENTS.items():
            src = manifest.parent / filename
            text = src.read_text(encoding="utf-8")
            meta = parse_frontmatter(text)
            if meta.get("experiment_code", exp_code) != exp_code:
                raise ValueError(f"experiment_code does not match its folder: {src}")
            documents.append((src, text, meta, exp_code, dataset, kb_type))
    if not documents:
        raise ValueError(f"No experiment manifests found in {experiments}")

    written: list[Path] = []
    for src, text, meta, exp_code, dataset, kb_type in documents:
        doc_name = meta.get("doc") or f"{exp_code}/{src.name}"
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
                    "kb_type": meta.get("kb_type", kb_type),
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
        print(f"exported {out_md}")
    return written


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / "docs" / "kb" / "export")
    args = parser.parse_args()
    files = export_dify_pack(args.output)
    print(f"done: {len(files) // 2} documents with metadata; source files unchanged")


if __name__ == "__main__":
    main()
