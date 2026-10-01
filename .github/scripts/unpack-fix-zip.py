#!/usr/bin/env python3
"""Extract `fix.zip` from the repository root into the repository.

Used by `.github/workflows/extract-fix-zip.yml`. The archive carries the project's
own relative paths (e.g. `app/src/main/java/com/ansu/anime/...`), so every entry is
written straight to the matching folder and sub-folder: an existing file is
overwritten, a missing one is created, and nothing is ever deleted.

Python rather than `unzip` so every entry is checked *before* it is written: a path
that escapes the repository or points into `.git` aborts the run instead of
overwriting something outside the checkout.

`.github/workflows/**` is skipped unless `ANSU_ALLOW_WORKFLOWS` is truthy. GitHub
refuses to let the built-in `GITHUB_TOKEN` create or update workflow files — "without
`workflows` permission" — and *no* `permissions:` block lifts that, so a commit that
touches a workflow is rejected as a whole. Skipping those entries is what keeps an
archive that contains the entire repository from failing to apply. Set
`ANSU_ALLOW_WORKFLOWS=true` (the workflow does it when a token with the `workflows`
scope is configured) to write them as well.

Exits non-zero (with a GitHub `::error::` annotation) on anything unexpected, so a
bad archive fails the workflow instead of silently doing half the job.
"""

import os
import sys
import zipfile

ZIP = "fix.zip"
WORKFLOWS_PREFIX = ".github/workflows/"


def allow_workflows():
    return os.environ.get("ANSU_ALLOW_WORKFLOWS", "").strip().lower() in ("1", "true", "yes")


def main():
    if not os.path.isfile(ZIP):
        sys.exit(f"::error::{ZIP} was not found at the repository root")

    root = os.path.realpath(os.getcwd())
    workflows_allowed = allow_workflows()
    extracted = []
    skipped_workflows = []

    with zipfile.ZipFile(ZIP) as archive:
        members = [m for m in archive.infolist() if not m.is_dir()]
        if not members:
            sys.exit(f"::error::{ZIP} contains no files")

        for member in members:
            name = member.filename.replace("\\", "/")
            if name.startswith("./"):
                name = name[2:]

            if name.startswith(WORKFLOWS_PREFIX) and not workflows_allowed:
                skipped_workflows.append(name)
                continue

            target = os.path.realpath(os.path.join(root, name))

            if name.startswith("/") or name.split("/")[0] == ".git":
                sys.exit(f"::error::{ZIP} has an unsafe path: {name}")
            if target == root or not target.startswith(root + os.sep):
                sys.exit(f"::error::{ZIP} has an unsafe path: {name}")

            os.makedirs(os.path.dirname(target), exist_ok=True)
            with archive.open(member) as source, open(target, "wb") as destination:
                destination.write(source.read())
            extracted.append(name)
            print(f"extracted {name}")

    if skipped_workflows:
        # Said out loud on purpose: GitHub blocks the built-in token from writing them,
        # so they are left to be changed by hand rather than failing the whole commit.
        listed = ", ".join(skipped_workflows[:10])
        more = "" if len(skipped_workflows) <= 10 else f" (+{len(skipped_workflows) - 10} more)"
        print(
            "::notice::Skipped "
            f"{len(skipped_workflows)} workflow file(s) — the built-in token may not write "
            f"`.github/workflows/`: {listed}{more}"
        )

    print(f"{ZIP}: {len(extracted)} file(s) extracted, {len(skipped_workflows)} workflow file(s) skipped")


if __name__ == "__main__":
    main()
