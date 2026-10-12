#!/usr/bin/env python3
"""Extract the project's fix archive into the repository.

Used by `.github/workflows/extract-fix-zip.yml`. The archive sits at the
repository root, is named `fix.<something>` and may be a **zip**, a **7z**, a
**rar** or a **tar** (optionally gzipped, bzipped or xz-compressed). It carries
the project's own relative paths (e.g. `app/src/main/java/com/ansu/anime/...`),
so every entry is written straight to the matching folder and sub-folder: an
existing file is overwritten, a missing one is created, and nothing is ever
deleted.

Which archives are applied:

  * with `ANSU_BEFORE` set (a push), only the archives **that push** brought in —
    an old `fix.zip` lying in the repository is never re-applied behind your back
    by a later commit;
  * without it (a manual run), every fix archive on `main` right now;
  * names given on the command line override both, for local use.

Python rather than `unzip`/`tar -x`/`7z x` on the repository so every entry is
checked *before* it is written: a path that escapes the repository or points into
`.git` aborts the run instead of overwriting something outside the checkout. Tar
members that are links, devices or FIFOs are skipped — those are the ones that
could point somewhere else on disk.

7z and rar are read through a 7-Zip binary (`7z`, `7za` or `7zz`) into a
temporary directory, from which the files are copied one by one under the same
checks as every other format.

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
import shutil
import subprocess
import sys
import tarfile
import tempfile
import zipfile

WORKFLOWS_PREFIX = ".github/workflows/"

# Suffixes an archive may carry. Order does not matter: ".tar.gz" is matched as a
# whole and a file ending in ".tar" never ends in ".tar.gz".
TAR_SUFFIXES = (".tar", ".tar.gz", ".tgz", ".tar.bz2", ".tbz2", ".tar.xz", ".txz")
ARCHIVE_SUFFIXES = (".zip", ".7z", ".7zip", ".rar") + TAR_SUFFIXES


def fail(message):
    """Stop the run; GitHub turns the prefix into a readable annotation."""
    sys.exit(f"::error::{message}")


def notice(message):
    print(f"::notice::{message}")


def allow_workflows():
    return os.environ.get("ANSU_ALLOW_WORKFLOWS", "").strip().lower() in ("1", "true", "yes")


def is_archive(name):
    """A root-level file whose name says 'fix' and 'archive'."""
    lowered = name.lower()
    return lowered.startswith("fix.") and lowered.endswith(ARCHIVE_SUFFIXES)


def discover(root):
    """Every fix archive sitting at the repository root, sorted."""
    return sorted(
        name
        for name in os.listdir(root)
        if is_archive(name) and os.path.isfile(os.path.join(root, name))
    )


def git(root, *args):
    return subprocess.run(["git", *args], cwd=root, capture_output=True, text=True)


def changed_archives(root, before, sha):
    """Only the fix archives the push brought in (never an older leftover)."""
    if set(before) <= {"0"}:
        # A ref's first push carries an all-zero `before`; there is nothing to
        # diff against, so fall back to the tip commit instead of applying
        # everything that happens to be lying around.
        notice("This push has no usable previous commit — comparing HEAD~1 instead.")
        before = "HEAD~1"

    proc = git(root, "diff", "--name-only", "--diff-filter=ACMR", before, sha, "--", ":(glob)fix.*")
    if proc.returncode != 0:
        # A force push leaves `before` out of this clone. Comparing the tip
        # commit still applies what the newest commit carried.
        notice("Cannot diff against the previous commit — comparing HEAD~1 instead.")
        before = "HEAD~1"
        proc = git(root, "diff", "--name-only", "--diff-filter=ACMR", before, sha, "--", ":(glob)fix.*")
        if proc.returncode != 0:
            fail(f"cannot list the files of this push: {proc.stderr.strip()}")

    names = set()
    for line in proc.stdout.splitlines():
        name = os.path.basename(line.strip())
        if name and is_archive(name) and os.path.isfile(os.path.join(root, name)):
            names.add(name)
    return sorted(names)


def read_zip(path):
    entries, skipped_links = [], 0
    try:
        with zipfile.ZipFile(path) as archive:
            members = [member for member in archive.infolist() if not member.is_dir()]
            if not members:
                fail(f"{os.path.basename(path)} contains no files")
            for member in members:
                entries.append((member.filename, archive.read(member)))
    except zipfile.BadZipFile as error:
        fail(f"{os.path.basename(path)} is not a zip Anisu can read ({error})")
    return entries, skipped_links


def read_tar(path):
    entries, skipped_links = [], 0
    try:
        with tarfile.open(path, "r:*") as archive:
            for member in archive.getmembers():
                if member.isdir():
                    continue
                if not member.isfile():
                    # Symlinks, hard links, devices, FIFOs: the entries that could
                    # point somewhere outside the checkout. Never written.
                    skipped_links += 1
                    continue
                source = archive.extractfile(member)
                if source is None:
                    skipped_links += 1
                    continue
                entries.append((member.name, source.read()))
    except tarfile.ReadError as error:
        fail(f"{os.path.basename(path)} is not a tar Anisu can read ({error})")
    if not entries:
        fail(f"{os.path.basename(path)} contains no files")
    return entries, skipped_links


def seven_zip_binary():
    for candidate in ("7z", "7za", "7zz"):
        found = shutil.which(candidate)
        if found:
            return found
    # A plain Windows machine usually has 7-Zip installed but not on PATH.
    for variable in ("ProgramFiles", "ProgramFiles(x86)"):
        base = os.environ.get(variable)
        if base:
            found = os.path.join(base, "7-Zip", "7z.exe")
            if os.path.isfile(found):
                return found
    return None


def read_seven(path):
    """7z / rar: unpack into a temp dir, then copy the files out one by one."""
    binary = seven_zip_binary()
    if binary is None:
        fail(
            f"{os.path.basename(path)} is a 7z/rar archive and no 7-Zip binary is "
            "installed here — use .zip or .tar.gz, or install 7-Zip"
        )

    with tempfile.TemporaryDirectory(prefix="ansu-fix-") as temp:
        proc = subprocess.run([binary, "x", "-y", f"-o{temp}", path], capture_output=True, text=True)
        if proc.returncode != 0:
            tail = [line for line in (proc.stderr + "\n" + proc.stdout).splitlines() if line.strip()][-4:]
            fail(f"{os.path.basename(path)} could not be read by {binary}: {' | '.join(tail)}")

        entries = []
        for folder, _, files in os.walk(temp):
            for file in files:
                full = os.path.join(folder, file)
                name = os.path.relpath(full, temp).replace(os.sep, "/")
                with open(full, "rb") as handle:
                    entries.append((name, handle.read()))

    if not entries:
        fail(f"{os.path.basename(path)} contains no files")
    return entries, 0


def read_archive(path):
    lowered = path.lower()
    if lowered.endswith(".zip"):
        return read_zip(path)
    if lowered.endswith(TAR_SUFFIXES):
        return read_tar(path)
    return read_seven(path)


def write_entries(root, entries, workflows_allowed, source):
    """Validate every path, then write. Only ever writes, never removes."""
    written, skipped_workflows = 0, 0
    for name, data in entries:
        name = name.replace("\\", "/")
        if name.startswith("./"):
            name = name[2:]

        if name.startswith(WORKFLOWS_PREFIX) and not workflows_allowed:
            skipped_workflows += 1
            continue

        parts = name.split("/")
        if name.startswith("/") or ".." in parts or parts[0] == ".git":
            fail(f"{source} has an unsafe path: {name}")

        target = os.path.realpath(os.path.join(root, name))
        if target == root or not target.startswith(root + os.sep):
            fail(f"{source} has an unsafe path: {name}")

        os.makedirs(os.path.dirname(target), exist_ok=True)
        with open(target, "wb") as destination:
            destination.write(data)
        print(f"extracted {name}")
        written += 1
    return written, skipped_workflows


def select_archives(root):
    """Explicit names, else the push's new archives, else everything present."""
    requested = sys.argv[1:]
    if requested:
        selected = []
        for name in requested:
            if not is_archive(name) or not os.path.isfile(os.path.join(root, name)):
                fail(f"{name} is not a fix archive at the repository root")
            selected.append(name)
        return selected

    before = os.environ.get("ANSU_BEFORE", "").strip()
    if not before:
        # A manual run works on whatever is on main right now.
        return discover(root)

    sha = os.environ.get("ANSU_SHA", "").strip() or "HEAD"
    return changed_archives(root, before, sha)


def main():
    root = os.path.realpath(os.getcwd())
    archives = select_archives(root)

    if not archives:
        if os.environ.get("ANSU_BEFORE", "").strip():
            notice("No new fix archive in this push — nothing to extract.")
        else:
            notice("No fix archive (fix.zip, fix.7z, fix.tar.gz, …) at the repository root — nothing to extract.")
        return

    workflows_allowed = allow_workflows()
    extracted = skipped_workflows = skipped_links = 0

    for name in archives:
        entries, links = read_archive(os.path.join(root, name))
        written, skipped = write_entries(root, entries, workflows_allowed, name)
        extracted += written
        skipped_workflows += skipped
        skipped_links += links
        detail = f"{written} file(s) extracted" + (f", {skipped} workflow file(s) skipped" if skipped else "")
        print(f"{name}: {detail}")

    if skipped_links:
        notice(f"Skipped {skipped_links} link/device member(s) — only regular files are written.")

    if skipped_workflows:
        # Said out loud on purpose: GitHub blocks the built-in token from writing them,
        # so they are left to be changed by hand rather than failing the whole commit.
        notice(
            f"Skipped {skipped_workflows} workflow file(s) — the built-in token may not write "
            "`.github/workflows/`"
        )

    print(f"{len(archives)} archive(s): {extracted} file(s) extracted")


if __name__ == "__main__":
    main()
