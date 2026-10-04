#!/usr/bin/env python3
"""Write the `fix.zip` report that is published on the `apk-nightly` branch.

Used by `.github/workflows/apk-nightly.yml`. The extraction workflow
(`extract-fix-zip.yml`) commits whatever a pushed `fix.zip` contained as
"extract fix.zip and updated the app" and then starts this nightly build itself,
so the APK published by the run that reaches this step is the one built from that
very commit.

The report records that next to the APKs, in two forms:

* `fix-report.json` — machine readable, one entry per file the extraction changed;
* `fix-report.md`   — the same information as plain text, for reading on the
  branch page without downloading anything.

Both list the extraction commit (with the bot as its author), the commit that
carried the `fix.zip` (the human who pushed it), every added or updated file with
its line counts, and which build published it.

The `apk-nightly` branch is rewritten on every run, so a run that is *not* an
extraction carries the previous report over instead of dropping it: the details of
the last fix stay readable until the next one lands. The `apkBuiltFromFix` field
says whether the APK published alongside it was actually built from that fix.

Usage: fix-report.py <output-directory>

Reads these from the environment (all optional, set by the workflow):

    GITHUB_REPOSITORY, GITHUB_RUN_ID, GITHUB_SERVER_URL,
    VERSION_NAME, VERSION_CODE, VARIANT, ISO

Exits non-zero with a `::error::` annotation only on an unexpected failure, so a
broken report is visible instead of silently publishing nothing.
"""

import json
import os
import subprocess
import sys

# The exact subject `extract-fix-zip.yml` commits its result with. A build whose
# tip carries this subject is the one produced by an extraction.
FIX_SUBJECT = "extract fix.zip and updated the app"

REPORT_JSON = "fix-report.json"
REPORT_MD = "fix-report.md"

STATUS_LABELS = {
    "A": "added",
    "M": "modified",
    "D": "deleted",
    "R": "renamed",
    "C": "copied",
    "T": "type changed",
    "U": "unmerged",
}


def git(*args):
    """Run git and return the completed process (stdout decoded as text)."""
    return subprocess.run(
        ["git", *args], capture_output=True, text=True, check=False
    )


def commit_info(sha):
    """The fields of one commit the report shows."""
    fmt = "%H%n%h%n%s%n%an%n%ae%n%aI%n%cn%n%ce%n%cI"
    fields = git("log", "-1", f"--format={fmt}", sha).stdout.split("\n")
    if len(fields) < 9:
        sys.exit(f"::error::Could not read commit {sha} — is the checkout complete?")
    return {
        "sha": fields[0],
        "short": fields[1],
        "message": fields[2],
        "author": {"name": fields[3], "email": fields[4]},
        "authoredAt": fields[5],
        "committedBy": {"name": fields[6], "email": fields[7]},
        "committedAt": fields[8],
    }


def diff_rows(sha, *columns):
    """`git diff-tree` rows as lists of tab-separated fields."""
    out = git(
        "diff-tree", "--no-commit-id", "-r", "--no-renames", *columns, sha
    ).stdout
    return [line.split("\t") for line in out.splitlines() if line.strip()]


def changed_files(sha):
    """Every file the commit touched, with its status and line counts."""
    status = {}
    for fields in diff_rows(sha, "--name-status"):
        if len(fields) >= 2:
            status[fields[-1]] = fields[0]

    stats = {}
    for fields in diff_rows(sha, "--numstat"):
        if len(fields) < 3:
            continue
        additions = None if fields[0] == "-" else int(fields[0])
        deletions = None if fields[1] == "-" else int(fields[1])
        stats[fields[-1]] = (additions, deletions)

    files = []
    for path in sorted(status):
        additions, deletions = stats.get(path, (None, None))
        files.append(
            {
                "path": path,
                "status": status[path],
                "label": STATUS_LABELS.get(status[path][0], status[path]),
                "additions": additions,
                "deletions": deletions,
            }
        )
    return files


def build_report():
    """Assemble the report for the extraction commit at HEAD."""
    sha = git("rev-parse", "HEAD").stdout.strip()
    commit = commit_info(sha)

    parents = git("rev-parse", f"{sha}^@").stdout.split()
    # The extraction commit is made on top of the push that carried fix.zip, so
    # its first parent is the commit the human pushed.
    fix_zip_commit = commit_info(parents[0]) if parents else None

    repo = os.environ.get("GITHUB_REPOSITORY", "")
    server = os.environ.get("GITHUB_SERVER_URL", "https://github.com").rstrip("/")
    run_id = os.environ.get("GITHUB_RUN_ID", "")
    variant = os.environ.get("VARIANT", "both")

    def commit_url(value):
        return f"{server}/{repo}/commit/{value}" if repo else value

    commit["url"] = commit_url(commit["sha"])
    if fix_zip_commit:
        fix_zip_commit["url"] = commit_url(fix_zip_commit["sha"])

    apks = {
        "both": ["Ansu-nightly.apk", "Ansu-nightly-debug.apk"],
        "release": ["Ansu-nightly.apk"],
        "debug": ["Ansu-nightly-debug.apk"],
    }.get(variant, [])

    files = changed_files(sha)
    return {
        "type": "fix-zip",
        "apkBuiltFromFix": True,
        "commit": commit,
        "fixZipCommit": fix_zip_commit,
        "changes": {
            "fileCount": len(files),
            "additions": sum(f["additions"] or 0 for f in files),
            "deletions": sum(f["deletions"] or 0 for f in files),
            "files": files,
        },
        "build": {
            "runId": run_id,
            "runUrl": f"{server}/{repo}/actions/runs/{run_id}" if repo else "",
            "workflow": "apk-nightly.yml",
            "versionName": os.environ.get("VERSION_NAME", ""),
            "versionCode": os.environ.get("VERSION_CODE", ""),
            "variant": variant,
            "apk": apks,
            "publishedAt": os.environ.get("ISO", ""),
        },
    }


def render_markdown(report):
    """A readable rendering of the same report."""
    commit = report["commit"]
    fix_zip = report["fixZipCommit"] or {}
    changes = report["changes"]
    build = report["build"]
    repo = os.environ.get("GITHUB_REPOSITORY", "")

    lines = [
        "# fix.zip report",
        "",
        "Published automatically by the "
        f"[`Anisu Nightly APK`](https://github.com/{repo}/actions/workflows/apk-nightly.yml) "
        "workflow when a `fix.zip` was extracted into the project.",
        "",
    ]

    if report["apkBuiltFromFix"]:
        lines.append(
            "**The APKs next to this file were built from the fix below.**"
        )
    else:
        lines.append(
            "**This is the last fix that landed; the APKs next to it were built "
            "from a later commit.**"
        )
    lines.append("")

    lines += [
        "## Extraction",
        "",
        f"- **Commit:** [`{commit['short']}`]({commit['url']}) — {commit['message']}",
        f"- **Committed by:** {commit['committedBy']['name']} "
        f"<{commit['committedBy']['email']}> on {commit['committedAt']}",
    ]
    if fix_zip:
        lines.append(
            f"- **fix.zip pushed by:** {fix_zip['author']['name']} "
            f"<{fix_zip['author']['email']}> in "
            f"[`{fix_zip['short']}`]({fix_zip['url']}) on {fix_zip['authoredAt']}"
        )

    lines += [
        "",
        "## Build",
        "",
        f"- **Version:** `{build['versionName']}` (code `{build['versionCode']}`)",
        f"- **Variant(s):** {build['variant']}",
        f"- **APK(s):** {', '.join(f'`{a}`' for a in build['apk']) or '—'}",
        f"- **Published:** {build['publishedAt']}",
    ]
    if build["runUrl"]:
        lines.append(f"- **Workflow run:** {build['runUrl']}")
    else:
        lines.append(f"- **Workflow run id:** {build['runId']}")

    lines += [
        "",
        f"## Changed files ({changes['fileCount']}; "
        f"+{changes['additions']} / -{changes['deletions']})",
        "",
        "| Status | File | +/- |",
        "|---|---|---|",
    ]
    for entry in changes["files"]:
        if entry["additions"] is None:
            counts = "binary"
        else:
            counts = f"+{entry['additions']} / -{entry['deletions']}"
        lines.append(f"| {entry['label']} | `{entry['path']}` | {counts} |")
    if not changes["files"]:
        lines.append("| — | *no file changed* | — |")

    lines.append("")
    return "\n".join(lines)


def write_text(path, text):
    with open(path, "w", encoding="utf-8", newline="\n") as handle:
        handle.write(text)


def carry_previous(outdir):
    """Copy the report already on the apk-nightly branch, if there is one."""
    fetched = git("fetch", "--depth", "1", "origin", "apk-nightly")
    if fetched.returncode != 0:
        return False

    carried = False
    for name in (REPORT_JSON, REPORT_MD):
        shown = git("show", f"FETCH_HEAD:{name}")
        if shown.returncode == 0 and shown.stdout:
            write_text(os.path.join(outdir, name), shown.stdout)
            carried = True
    return carried


def main():
    if len(sys.argv) != 2:
        sys.exit(f"::error::usage: {os.path.basename(__file__)} <output-directory>")
    outdir = sys.argv[1]
    os.makedirs(outdir, exist_ok=True)

    subject = git("log", "-1", "--format=%s").stdout.strip()

    if subject != FIX_SUBJECT:
        # Not an extraction: keep the previous report so the last fix does not
        # disappear on the next hourly build, and mark that this APK is not its.
        if carry_previous(outdir):
            try:
                with open(
                    os.path.join(outdir, REPORT_JSON), encoding="utf-8"
                ) as handle:
                    report = json.load(handle)
                report["apkBuiltFromFix"] = False
                # Rewrite both so the readable file does not keep claiming the
                # APKs next to it were built from this fix.
                write_text(
                    os.path.join(outdir, REPORT_JSON),
                    json.dumps(report, indent=2, ensure_ascii=False) + "\n",
                )
                write_text(os.path.join(outdir, REPORT_MD), render_markdown(report))
            except (OSError, ValueError, KeyError):
                pass
            print("Kept the previous fix.zip report; this build is not an extraction.")
        else:
            print("This build is not a fix.zip extraction and there is no report yet.")
        return

    report = build_report()
    write_text(
        os.path.join(outdir, REPORT_JSON),
        json.dumps(report, indent=2, ensure_ascii=False) + "\n",
    )
    write_text(os.path.join(outdir, REPORT_MD), render_markdown(report))
    print(
        f"Wrote {REPORT_JSON} and {REPORT_MD} for extraction commit "
        f"{report['commit']['short']} ({report['changes']['fileCount']} file(s))."
    )


if __name__ == "__main__":
    main()
