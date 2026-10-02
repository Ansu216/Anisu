#!/usr/bin/env bash
#
# Commit a directory staged by `stage-nightly.sh` as the `apk-nightly` branch and
# force-push it. Because every run replaces the branch as a whole, the staged
# directory must already contain every file the branch should carry — which is
# what `stage-nightly.sh` guarantees by merging the sibling variant of the same
# run before this script runs.
#
# Usage: push-nightly.sh <staged-directory>
#
# Reads GITHUB_REPOSITORY and GITHUB_TOKEN from the environment.

set -euo pipefail

STAGE="${1:?usage: push-nightly.sh <staged-directory>}"
: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"
: "${ISO:?ISO is required}"

cd "$STAGE"

git init -q
git checkout -q -b apk-nightly
git config user.name  "github-actions[bot]"
git config user.email "41898282+github-actions[bot]@users.noreply.github.com"
git add -A
git commit -q -m "nightly APK ${ISO}"
git remote add origin "https://x-access-token:${GITHUB_TOKEN}@github.com/${GITHUB_REPOSITORY}.git"
git push --force origin apk-nightly
echo "Published to branch apk-nightly"
