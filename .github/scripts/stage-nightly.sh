#!/usr/bin/env bash
#
# Stage ONE variant of the nightly build onto a fresh directory, ready to be
# pushed to the `apk-nightly` branch by `push-nightly.sh`.
#
# Used by `.github/workflows/apk-nightly.yml`. The release and the debug APK are
# built in parallel and published **independently**: each leg runs this script as
# soon as its build succeeds, so a failure on one side never holds back the other.
#
# Because a single leg knows nothing about the other one, this script first reads
# the branch as it stands. When the branch already carries a build with the *same*
# `versionCode` (i.e. the sibling variant of this very run), its APK is carried over
# so publishing one leg never drops the other. A branch carrying a build from an
# older run is replaced wholesale instead, so the two APKs can never belong to
# different runs.
#
# Usage: stage-nightly.sh <output-directory>
#
# Reads these from the environment (set by the workflow):
#
#   VARIANT            release | debug
#   VERSION_NAME, VERSION_CODE, ISO   the stamp computed by the `plan` job
#   GITHUB_REPOSITORY, GITHUB_TOKEN   to read the current branch
#   GITHUB_SHA, GITHUB_RUN_ID         written into nightly.json
#
# The APK this leg built is looked up under `artifacts/`.

set -euo pipefail

STAGE="${1:?usage: stage-nightly.sh <output-directory>}"
: "${VARIANT:?VARIANT is required}"
: "${VERSION_NAME:?VERSION_NAME is required}"
: "${VERSION_CODE:?VERSION_CODE is required}"
: "${ISO:?ISO is required}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

mkdir -p "$STAGE"

# The file this leg publishes, and the one its sibling would have published.
case "$VARIANT" in
  release) APK_NAME="Ansu-nightly.apk";       SIBLING="Ansu-nightly-debug.apk" ;;
  debug)   APK_NAME="Ansu-nightly-debug.apk"; SIBLING="Ansu-nightly.apk" ;;
  *) echo "::error::stage-nightly.sh got an unknown variant '$VARIANT'"; exit 1 ;;
esac

# `download-artifact@v4` flattens uploaded paths, so find the APK by name.
APK="$(find artifacts -type f -name '*.apk' | head -1 || true)"
if [ -z "$APK" ]; then
  echo "::error::No APK artifact was found for the $VARIANT build."
  exit 1
fi
cp "$APK" "$STAGE/$APK_NAME"

# Carry the sibling variant over, but only when it belongs to this same run.
EXISTING="$(mktemp -d)"
if git clone --quiet --depth 1 --branch apk-nightly \
    "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" \
    "$EXISTING" 2>/dev/null; then
  EXISTING_CODE="$(python3 - "$EXISTING/nightly.json" <<'PY' 2>/dev/null || true
import json
import sys

try:
    print(json.load(open(sys.argv[1], encoding="utf-8")).get("versionCode", ""))
except Exception:
    pass
PY
)"
  if [ "$EXISTING_CODE" = "$VERSION_CODE" ] && [ -f "$EXISTING/$SIBLING" ]; then
    cp "$EXISTING/$SIBLING" "$STAGE/$SIBLING"
    echo "Carried over the sibling variant $SIBLING from the same run."
  fi
fi

# nightly.json lists exactly the APKs the branch now carries. The in-app updater
# reads `apk`, which is always the release build; `apkDebug` is informational.
python3 - "$STAGE" "$VERSION_NAME" "$VERSION_CODE" "$ISO" "$GITHUB_SHA" "$GITHUB_RUN_ID" <<'PY'
import json
import os
import sys

stage, name, code, iso, sha, run = sys.argv[1:7]
manifest = {
    "app": "Ansu",
    "package": "com.ansu.anime",
    "versionName": name,
    "versionCode": int(code),
    "builtAt": iso,
    "commit": sha,
    "runId": run,
}
if os.path.exists(os.path.join(stage, "Ansu-nightly.apk")):
    manifest["apk"] = "Ansu-nightly.apk"
if os.path.exists(os.path.join(stage, "Ansu-nightly-debug.apk")):
    manifest["apkDebug"] = "Ansu-nightly-debug.apk"

with open(os.path.join(stage, "nightly.json"), "w", encoding="utf-8", newline="\n") as handle:
    handle.write(json.dumps(manifest, indent=2) + "\n")
PY

cat > "$STAGE/README.md" <<'MD'
# Ansu — nightly APK

[![Download the release APK](https://img.shields.io/badge/Download-Ansu--nightly.apk-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://raw.githubusercontent.com/Ansu216/Anisu/apk-nightly/Ansu-nightly.apk)
[![Download the debug APK](https://img.shields.io/badge/Download-Ansu--nightly--debug.apk-FF6D00?style=for-the-badge&logo=android&logoColor=white)](https://raw.githubusercontent.com/Ansu216/Anisu/apk-nightly/Ansu-nightly-debug.apk)

This branch is **generated automatically** by the
[`Ansu Nightly APK`](../actions/workflows/apk-nightly.yml) workflow.
Every run replaces the branch with the newest build. The release and the debug
APK are published independently, as soon as their own build succeeds.

| File | Description |
|---|---|
| `Ansu-nightly.apk` | Signed **release** build (smaller, minified, R8). |
| `Ansu-nightly-debug.apk` | Signed **debug** build (easier to read stack traces). |
| `nightly.json` | Version / build metadata for this run. |
| `fix-report.json` | What the last `fix.zip` changed (commit, author, files). |
| `fix-report.md` | The same report, readable on this page. |

The `fix-report.*` files are written when a `fix.zip` was extracted into
the project and this build was made from the commit it produced. A build
that is not an extraction keeps the previous report, so it describes the
last fix that landed rather than disappearing on the next hourly build.

**Do not commit to this branch by hand — it is overwritten on every run.**

## Install

Download `Ansu-nightly.apk` on your phone and open it. Android will ask
you to allow installing unknown apps for your browser/file manager.

Both APKs are signed with the same key, so each nightly installs as a
normal upgrade over the previous one (no uninstall needed).
MD

ls -la "$STAGE"
