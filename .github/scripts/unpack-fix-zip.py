#!/usr/bin/env python3
"""Unpack `fix.zip` from the repository root into the repository.

Used by `.github/workflows/extract-fix-zip.yml`. The archive carries the project's
own relative paths (e.g. `app/src/main/java/com/ansu/anime/...`), so every entry is
written straight to the matching folder and sub-folder.

Python rather than `unzip` so every entry is checked *before* it is written: a path
that escapes the repository or points into `.git` aborts the run instead of
overwriting something outside the checkout.

Exits non-zero (with a GitHub `::error::` annotation) on anything unexpected, so a
bad archive fails the workflow instead of silently doing half the job.
"""

import os
import sys
import zipfile

ZIP = "fix.zip"


def main():
    if not os.path.isfile(ZIP):
        sys.exit(f"::error::{ZIP} was not found at the repository root")

    root = os.path.realpath(os.getcwd())

    with zipfile.ZipFile(ZIP) as archive:
        members = [m for m in archive.infolist() if not m.is_dir()]
        if not members:
            sys.exit(f"::error::{ZIP} contains no files")

        for member in members:
            name = member.filename.replace("\\", "/")
            if name.startswith("./"):
                name = name[2:]
            target = os.path.realpath(os.path.join(root, name))

            if name.startswith("/") or name.split("/")[0] == ".git":
                sys.exit(f"::error::{ZIP} has an unsafe path: {name}")
            if target == root or not target.startswith(root + os.sep):
                sys.exit(f"::error::{ZIP} has an unsafe path: {name}")

            os.makedirs(os.path.dirname(target), exist_ok=True)
            with archive.open(member) as source, open(target, "wb") as destination:
                destination.write(source.read())
            print(f"unpacked {name}")

    print(f"{ZIP}: {len(members)} file(s) unpacked")


if __name__ == "__main__":
    main()
