# Anisu — nightly APK

[![Download the release APK](https://img.shields.io/badge/Download-Ansu--nightly.apk-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://raw.githubusercontent.com/Ansu216/Anisu/apk-nightly/Ansu-nightly.apk)
[![Download the debug APK](https://img.shields.io/badge/Download-Ansu--nightly--debug.apk-FF6D00?style=for-the-badge&logo=android&logoColor=white)](https://raw.githubusercontent.com/Ansu216/Anisu/apk-nightly/Ansu-nightly-debug.apk)

This branch is **generated automatically** by the
[`Anisu Nightly APK`](../actions/workflows/apk-nightly.yml) workflow.
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
