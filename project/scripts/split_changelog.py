#!/usr/bin/env python3
"""Regenerate CHANGELOG.md from CHANGELOG.dev.md, the way PiBOH/vivi-music-de does it.

CHANGELOG.dev.md is the source: one bullet per change, a bold one-line head and the whole
account under it. This script writes CHANGELOG.md, where every entry is reduced to that head,
so the short file is never edited by hand and the two cannot drift.

Sections that only exist in CHANGELOG.md (releases cut before this script existed) are kept
verbatim, in their original order, after the generated ones.

Usage: python scripts/split_changelog.py
"""

from pathlib import Path
import re

ROOT = Path(__file__).resolve().parent.parent
DEV = ROOT / "CHANGELOG.dev.md"
SHORT = ROOT / "CHANGELOG.md"

HEADER = """# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## How this file is used

- Generated from `CHANGELOG.dev.md` by `scripts/split_changelog.py`. Never edit this file by hand: a change made here is gone at the next run.
- One line per change: the entry's head, read off the dev file. The whole story stays in `CHANGELOG.dev.md`.
"""


def sections(text):
    """[(heading_line, body)] split on top-level `## ` headings, preamble dropped."""
    found = []
    heading, body = None, []
    for line in text.splitlines():
        if line.startswith("## "):
            if heading is not None:
                found.append((heading, "\n".join(body).strip("\n")))
            heading, body = line, []
        elif heading is not None:
            body.append(line)
    if heading is not None:
        found.append((heading, "\n".join(body).strip("\n")))
    return found


def head_of(entry):
    """The one line CHANGELOG.md keeps: the bold head, else the first sentence."""
    text = entry.splitlines()[0].removeprefix("- ").strip()
    bold = re.search(r"\*\*.+?\*\*", text)
    if bold:
        head = text[: bold.end()]
        inner = bold.group(0)
        return head if inner[:-2].endswith((".", "!", "?", ":")) else head + "."
    sentence = re.split(r"(?<=[.!?])\s", text)[0].strip()
    return sentence


def reduce_body(body):
    """Drop an entry's continuation lines, keep everything around them."""
    out, entry = [], []

    def flush():
        if entry:
            out.append("- " + head_of("\n".join(entry)))
            entry.clear()

    for line in body.splitlines():
        if line.startswith("- "):
            flush()
            entry.append(line)
        elif entry and line.startswith("  ") and line.strip():
            entry.append(line)  # a continuation line of the entry above
        elif line.startswith("### "):
            flush()
            out.append(line)
        else:
            flush()
            out.append(line)
    flush()
    return "\n".join(out).strip("\n")


def main():
    dev = sections(DEV.read_text(encoding="utf-8"))
    old = sections(SHORT.read_text(encoding="utf-8")) if SHORT.exists() else []
    # Only `## [version]` sections are generated: anything else in the dev file is instructions.
    generated = [
        (heading, reduce_body(body)) for heading, body in dev if heading.startswith("## [")
    ]
    generated_headings = {heading for heading, _ in generated}
    # Only version sections are kept from the short file: its own instructions are not one.
    kept = [
        (heading, body)
        for heading, body in old
        if heading.startswith("## [") and heading not in generated_headings
    ]

    parts = [HEADER.rstrip("\n"), ""]
    for heading, body in generated + kept:
        parts += [heading, ""]
        if body.strip():
            parts += [body.strip("\n"), ""]
    SHORT.write_text("\n".join(parts).rstrip("\n") + "\n", encoding="utf-8", newline="\n")
    print(f"CHANGELOG.md written from {len(generated)} generated and {len(kept)} kept sections")


if __name__ == "__main__":
    main()
