# CLAUDE.md

Instructions for **Claude Code** (and any other AI agent) working in this
repository. Claude Code loads this file automatically.

**Read the whole file before your first edit.** Section 4 is the exact build
procedure — following it is what keeps the build green. Section 5 lists the traps
that have broken this build before. Section 7 is mandatory: every change touches
`CHANGELOG.md`.

## Before you start

1. Run the build once, as it is, before changing anything:
   `./gradlew assembleDebug assembleRelease --console=plain`. It must be green on
   an untouched tree. If it is not, that is the problem to fix first — do not
   start a feature on top of a red build.
2. Fix the user's actual request. Do not refactor, rename or "tidy up" working
   code that the request does not need (§6).
3. When you are done: green build (§4), APK identity unchanged (§4.4), a
   `CHANGELOG.md` entry (§7), and no commit unless the user asked for one (§8).

## 1. Project overview

**Ansu** is an Android client for [AniList](https://anilist.co) with anime
catalogue browsing, an airing schedule and news feed, library tracking and
playback through pluggable sources (built-in demo source, Stremio addons,
extensions).

- **Package / application id**: `com.ansu.anime`
- **Display name**: `Ansu`
- **Language/build**: Kotlin 2.1.0, Java 17 bytecode target, JDK 21 toolchain,
  Gradle Kotlin DSL, Gradle 8.9 (wrapper committed), AGP 8.7.2.
- **UI**: Jetpack Compose + Material 3, Navigation Compose, Coil for images.
- **Data**: Room (KSP codegen), plain `SharedPreferences` for settings,
  `EncryptedSharedPreferences` for the AniList token.
- **Network**: OkHttp + Retrofit + `kotlinx.serialization`.
- **Playback**: Media3 / ExoPlayer.
- **SDK levels**: `minSdk 26` (Android 8.0), `targetSdk`/`compileSdk` 35.
- **Version**: see `CHANGELOG.md`. The build defaults to `versionCode 1` /
  `versionName 0.1.0`, overridden per build (see §4).

### Do not bump the toolchain

The exact versions above are the ones that build. **Do not upgrade Gradle, AGP,
Kotlin, KSP, the Compose BOM or any library on your own initiative**, and do not
run `./gradlew wrapper --gradle-version …`. An unrequested bump is the single
most reliable way to turn a green build red, and the versions are intertwined
(KSP must match Kotlin exactly: `2.1.0-1.0.29` for Kotlin `2.1.0`). A dependency
or toolchain upgrade is a separate, explicitly requested task — and it comes with
a `### Changed` entry in `CHANGELOG.md`.

Adding a *new* dependency is done in two places, never one:
`gradle/libs.versions.toml` (the version + the `[libraries]` alias) **and**
`app/build.gradle.kts` (`implementation(libs.…)`). Declaring it in only one of
them fails the build.

## 2. Architecture and module structure

`settings.gradle.kts` sets `rootProject.name = "Ansu"` and declares a single
module: `:app`. There are no other Gradle modules — do not introduce one unless
the user explicitly asks.

Source root: `app/src/main/java/com/ansu/anime/`

| Path | Role |
|---|---|
| `AnsuApp.kt` | `Application` subclass; builds the DI container |
| `MainActivity.kt` | Single activity, hosts the Compose content |
| `di/AppContainer.kt` | **Manual** dependency container (there is no Hilt/Koin) |
| `ui/navigation/AnsuNavGraph.kt` | Navigation graph, `Dest` routes, `bottomNavDestinations` |
| `ui/theme/Theme.kt` | `AnsuTheme` + the `AnsuColors` palette and type/shape scales |
| `ui/components/Common.kt` | Shared composables (hero carousel, shelves, cards, chips, `PersonCard`) |
| `ui/components/GlassSurface.kt` | `FrostedGlassCard` / `BottomScrim` glass surfaces |
| `ui/components/AppBottomBar.kt` | Frosted bottom navigation bar (`AppBottomBar`, `NavBarSurface`) |
| `ui/home` | Home screen + view model (continue watching, trending, season picks, shelves) |
| `ui/schedule` | Schedule **and** News tab, its view model and UI models |
| `ui/myspace` | "My Space" profile tab (Liked / Watching / Completed lists) |
| `ui/appearance` | Appearance settings (nav-bar roundness, frostiness) |
| `ui/about` | About: version, update channel + install, developer credit and links |
| `ui/search`, `ui/details`, `ui/player` | Search, details (cast/crew sheet) and playback |
| `ui/extensions`, `ui/addons`, `ui/settings`, `ui/auth` | Extensions, addons, settings, AniList login |
| `core/model/AnimeModels.kt` | `SAnime`, `SEpisode`, `MediaOrigin`, `Shelf` |
| `core/util/` | `Formatting`, `MediaLabels`, `BioText` (cleans AniList bios), `SelectionHolder` |
| `core/net/` | `ApiException` / `ApiErrorKind` and `ApiErrorHandler`: every API failure is reported here (log + snackbar in `MainActivity`) |
| `data/db/AppDatabase.kt` | Room database |
| `data/repository/` | Catalogue and continue-watching repositories |
| `data/news/NewsRepository.kt` | News feed backing the Schedule screen's News tab |
| `data/prefs/` | `SharedPreferences`-backed settings: `AppearancePrefs`, `UpdatePrefs` |
| `data/update/` | Self-updater: `UpdateChecker` (GitHub Releases + `apk-nightly`), `UpdateInstaller`, `UpdateManager` |
| `extension/` | Extension manager, repo and the `AnimeCatalogueSource` API |
| `addon/` | Stremio addon client and models |
| `anilist/` | AniList GraphQL API (`AniListApi`), models, repository, OAuth manager |

Dependency wiring flows `AnsuApp` → `AppContainer` → screens/view models. Pass
dependencies explicitly; keep it that way.

`build/` directories are generated and gitignored — never edit or commit them.

## 3. Quick reference

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # minified release APK
./gradlew clean              # wipe build output
```

Section 4 is the full procedure, including what to do when it fails.

## 4. Build playbook — follow this exactly

This is the procedure that produces a green build. Do not improvise around it.

### 4.1 Environment

- **JDK 21.** The Gradle toolchain requires it. `java -version` must report 21
  (Temurin works). On a machine with several JDKs, point Gradle at it with
  `org.gradle.java.home` in the **gitignored** `local.properties` — never commit
  a machine path.
- **Android SDK** with `platforms;android-35` and `build-tools;35.0.0`.
  `local.properties` must contain `sdk.dir=<path>` (or export
  `ANDROID_SDK_ROOT`). `local.properties` is machine-local and **gitignored** —
  never commit it, and never edit it in a commit.
- **Network on the first run.** Gradle has to download the distribution and the
  dependencies. After that, `--offline` is fine for a **debug** build.

### 4.2 The commands

```bash
# The bar: zero errors AND zero warnings, both variants.
./gradlew assembleDebug assembleRelease --console=plain
```

- Anything that prints `w:` is a failure for this repository, exactly like an
  `e:`. Do not leave deprecation warnings behind.
- Use `--console=plain` so the error list is not overwritten by a progress bar,
  and grep it if it is long: `./gradlew … 2>&1 | grep '^e: '`.
- **Do not pass `--offline` to a release build.** The release variant runs lint,
  whose `lint-gradle` artifact is resolved lazily; with `--offline` it fails with
  `No cached version of com.android.tools.lint:lint-gradle:… available for
  offline mode`. That is a missing download, not a code error — rerun with the
  network. If `assembleDebug` is green and `assembleRelease` fails on that
  message, this is the cause.
- Both variants are signed by the same committed key, so nothing extra has to be
  configured to build a release.
- There are **no unit tests** in this repository. `./gradlew test` and
  `./gradlew connectedAndroidTest` have nothing to run; the build plus an APK
  inspection (§4.4) is the verification.

Optional extras, in increasing cost: `./gradlew lintDebug` (static analysis) and
`./gradlew dependencies` (dependency graph) — neither is part of the check.

### 4.3 When the build fails

1. **Read only the first `e:` line, then the ones that share its file.** Kotlin
   reports cascading nonsense after the first real error: one missing import can
   produce dozens of `Unresolved reference` and even
   `@Composable invocations can only happen from the context of a @Composable
   function` errors in *other* files. Fix the first cause and rebuild before
   reading further.
2. `Redeclaration` / `Conflicting overloads` / `Overload resolution ambiguity`
   means **two files declare the same top-level symbol**. Search the identifier
   across `app/src/main/java` before adding anything new, and delete or rename the
   duplicate. This exact failure is what broke the build before — see §5.
3. `Unresolved reference: item` inside a `LazyColumn`/`LazyRow` — do not import
   it, it is a member of `LazyListScope`. See §5.
4. `Unresolved reference` for a Compose API you just used — check §5 for the
   opt-in requirement and the icon variants before anything else.
5. `No cached version … available for offline mode` — rerun without `--offline`
   (§4.2).
6. `Unsupported class file major version` / `Unsupported Java` — the JDK is not
   21 (§4.1). Do not "fix" it by changing the Kotlin or Java target.
7. Still stuck? Run `./gradlew assembleDebug --stacktrace` and read the
   **first** `Caused by:`.

**Never make a red build green by** deleting a file you do not understand,
commenting out the failing code, weakening a rule in this document, changing the
toolchain, or disabling a lint/compiler check. Fix the cause.

### 4.4 Verifying a built APK

`.apk` path: `app/build/outputs/apk/debug/app-debug.apk` and
`app/build/outputs/apk/release/app-release.apk`.

```bash
"$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/debug/app-debug.apk
# must report: package: name='com.ansu.anime'   application-label:'Ansu'   launchable-activity: com.ansu.anime.MainActivity

"$ANDROID_SDK_ROOT/build-tools/35.0.0/apksigner" verify --print-certs app/build/outputs/apk/release/app-release.apk
# must report: Verified using v1/v2 scheme ... and the Ansu certificate
```

A wrong `package:`/`application-label:`, or a missing `launchable-activity`,
means the manifest and the package name have drifted — that is an instant
`ClassNotFoundException` on launch.

### 4.5 Checklist before you say "done"

- [ ] `./gradlew assembleDebug assembleRelease` → **0 errors, 0 warnings**.
- [ ] `aapt dump badging` still reports `com.ansu.anime` / `Ansu`.
- [ ] No new `com.kernel.anime` / `Anisu` / `StreamHub` name anywhere (§5).
- [ ] No duplicate top-level symbols were introduced (§4.3.2).
- [ ] `CHANGELOG.md` `## [Unreleased]` updated (§7).
- [ ] Docs updated if the change alters how the project is built, signed or
      released (`README.md`, `how-to-compile.md`, `CLAUDE.md`, `AGENTS.md`).
- [ ] `git status` contains no `local.properties`, `build/`, `.gradle/` or
      `.kotlin/`.

## 5. Code conventions

- Match the surrounding style: same naming, same import ordering, same
  composable structure. Prefer the smallest change that solves the request.
- Screens are plain composables that receive `AppContainer` and a
  `NavController`; state lives in a `*ViewModel` exposing a single UI state
  (e.g. `DetailsUiState`) through a `StateFlow`.
- **Do not add a DI framework**, a second module, or a new networking/DB stack.
  Use what is already wired.
- Material 3 APIs that are still experimental (`TopAppBar`, `ModalBottomSheet`,
  `HorizontalDivider`) are opted into **per file**, with
  `@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)` as
  the **first line** of the file, before `package`. A new file that uses one of
  those APIs needs that line or it will not compile. Do not add a
  `-opt-in=` compiler flag in Gradle instead.

### Known traps — these have broken the build before

- **Never leave two files declaring the same top-level symbol.** A duplicated
  `Application`, nav graph, `Dest`, composable or model does not just shadow:
  it makes the whole file set fail with `Redeclaration` / `Conflicting
  overloads` / `Overload resolution ambiguity`, and the follow-on errors
  (`@Composable invocations can only happen from the context of a @Composable
  function`) are pure noise. Search before adding a new screen or model.
- **`item { }` inside a `LazyColumn`/`LazyRow` is a member of `LazyListScope`**,
  not a top-level function. `import androidx.compose.foundation.lazy.item` does
  not exist and fails with "Unresolved reference". Only `items(...)` is
  imported (`androidx.compose.foundation.lazy.items`).
- Never nest a vertical `LazyColumn`/`LazyVerticalGrid` inside another
  vertically scrolling container — it either crashes at runtime or needs a
  magic height. Build non-scrolling grids with `chunked(n)` + `Row`/`Column`.
- Use the auto-mirrored icons: `Icons.AutoMirrored.Filled.ArrowBack`,
  `Icons.AutoMirrored.Filled.ViewList`, … The `Icons.Filled.*` variants are
  deprecated and produce warnings.
- `Modifier.padding(horizontal = …, top = …, bottom = …)` is not a valid
  overload. Combine only `horizontal`/`vertical`, or use `start`/`end`/`top`/
  `bottom` explicitly.
- `EncryptedSharedPreferences` can throw on some ROMs and after a backup
  restore. Anything constructed in `Application.onCreate` **must not** be able
  to crash the app — keep the `runCatching { … }.getOrElse { plain prefs }`
  fallback in `AniListAuthManager`.
- Creating `EncryptedSharedPreferences` twice, or a second `companion object`,
  fails to compile.
- The app draws edge to edge (`enableEdgeToEdge()`) and a Material 3 `Scaffold`
  lays its `bottomBar`/`topBar` slots flush against the window. A **custom**
  (non-Material) bar must apply its own insets —
  `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` — or it ends up
  underneath the system navigation bar, exactly as `AppBottomBar` did.

### Branding — never regress this

The app is **Ansu** and its package is `com.ansu.anime`. There must be **no**
`com.kernel.anime` and no leftover `Anisu`/`StreamHub` naming anywhere, in any
file name, path or source line. Keep the class names `AnsuApp`, `AnsuTheme`,
`AnsuNavGraph`, `AnsuColors`, the colour resources `ansu_*`, the URI scheme
`ansu://` and the app label `Ansu`.

The only intentional legacy names are the **signing identity**: the keystore
file `keystore/anisu.jks` and its key alias `anisu`, plus the prefs file name in
`AniListAuthManager`. Do not rename them — that would change the signing
certificate or silently log every user out. The GitHub repository slug
(`Ansu216/Anisu`, used in the workflow badge URLs and by the in-app updater) is
also fixed and must not be rewritten.

## 6. Golden rule: "if it works, don't touch it"

Do not refactor, rewrite or "clean up" modules, files or functions that are
already working, unless (a) it is strictly necessary for the requested change,
or (b) the user explicitly asks for it. State the risk before editing something
that could break existing behaviour, and ask when in doubt.

#### Do-not-touch areas (verified working — leave alone)

- **The signing setup and the committed keystore.** `keystore/anisu.jks` is
  committed on purpose so local and CI builds produce identically signed APKs.
- **The launch path.** `AnsuApp` → `MainActivity` → `AnsuNavGraph` is verified
  against the built APK; a mismatch between the manifest class names and the
  package is an instant `ClassNotFoundException` on launch.
- **The `apk-nightly` branch.** It is generated by CI. Never commit to it by
  hand and never "fix" it manually.
- **The workflow job graphs.** `plan` → `build` (parallel matrix) →
  `publish`/`release` exists so a partial build can never be published. Do not
  merge the matrix legs back into one job, and do not remove the
  `fail-fast: false` / `always()` guards.

## 7. Versioning and the changelog — MANDATORY

**Every change that alters behaviour, the UI, the build, the CI or the docs is
added to `CHANGELOG.md` in the same commit.** A change with no changelog entry is
an incomplete change.

### 7.1 The file

`CHANGELOG.md` follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/)
and this project follows [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

- New work goes under `## [Unreleased]`, in the right category, in the right
  order:
  `### Added`, `### Changed`, `### Deprecated`, `### Removed`, `### Fixed`,
  `### Security`.
- If a category does not exist under `## [Unreleased]`, create it — do not
  invent a new one.
- At release time the entries are moved under a new heading,
  `## [<version>] - <YYYY-MM-DD>` (inserted directly under `## [Unreleased]`),
  and `## [Unreleased]` is left empty for the next cycle. **`<version>` is the
  tag without its `v`**: tag `v1.2.3` → `## [1.2.3]`.
- **Never rewrite a published section.** A mistake is corrected in a new release.
- Comparing the file's two ends is the fastest way to see what a release
  contains: `Unreleased` (empty in a clean tree) and the newest `<version>`.

### 7.2 Which number to bump (SemVer)

Given `MAJOR.MINOR.PATCH` (`1.4.2`), and an optional pre-release suffix
(`1.4.2-rc.1`):

| Bump | When |
|---|---|
| **MAJOR** | A change users cannot ignore or that breaks compatibility: the package/application id changes, an existing extension's contract breaks, saved data stops loading, a feature is removed. |
| **MINOR** | A new, backwards-compatible capability: a new screen, a new source type, a new setting, a new channel. **Anything a user would notice as "more app".** |
| **PATCH** | Backwards-compatible fixes only: bug fixes, wording, performance, CI and docs corrections, dependency patches. |

- Before `1.0.0` (`0.x.y`) the same rules are used, but the project is allowed to
  break compatibility in a MINOR bump — still say so in `### Changed`.
- A **pre-release** (`-alpha`, `-beta`, `-rc.1`, …) is only for a version that is
  not ready to be the default download. `release-apk.yml` marks it as a
  pre-release on GitHub automatically, purely from the suffix in the tag.
- Build metadata (`+build.5`) is ignored for ordering and is not used here.
- The tag is `v` + the version: `v1.4.2`. **The tag and the changelog heading must
  match**, or the release body comes out empty.

### 7.3 How to write an entry

Write for the person installing the APK, not for the git log. One bullet per
change, in this shape:

```markdown
- **Bold, one-line statement of the change.** What was wrong or missing, and why
  the change fixes it — the mechanism, not a restatement of the code. A
  `**Constraint:**` sentence records a limit that was deliberately kept.
```

- **Say what and why**, and include the detail someone would ask for next: which
  file/flow is involved, what the old behaviour was, what the new behaviour is.
- No commit hashes, no file-path-only bullets, no "various fixes".
- Group related changes into one entry rather than listing one bullet per file.
- English only, like the commit messages (§8).

### 7.4 Where the changelog ends up

`release-apk.yml` extracts the `## [<version>]` section from `CHANGELOG.md` on the
tagged commit, plus the commits since the previous tag, and uses both as the
GitHub Release body. If the section is missing the workflow does **not** fail —
it emits a warning annotation and publishes a placeholder body — but that release
is worse for every user, so add the section **before** pushing the tag.

## 8. Git conventions — MANDATORY (do not violate)

- **English only.** Every commit message — title **and** body — must be written
  in English. No Italian, no mixed language.
- **No co-author footer, ever.** Never append `Co-Authored-By: …`,
  `Generated with …`, `🤖` or any line crediting an agent, client or model.
  Write a plain commit message: a short imperative subject and, when useful, a
  body explaining **why** the change was made. This applies unless the user
  explicitly asks for a footer in that specific message.
- Do not commit build output, `local.properties`, `.kotlin/`, `.gradle/` or
  local Gradle distributions. These are already in `.gitignore`.
- Do not commit unless the user asks for it, and do not push, force-push or
  rewrite published history unless the user asks. When a push is rejected
  because the remote moved, integrate (rebase onto `origin/main`) rather than
  discarding remote work.

## 9. CI and releases

| Workflow | Trigger | Output |
|---|---|---|
| `.github/workflows/apk-nightly.yml` | hourly `cron`, **every push to `main`**, manual | force-publishes to `apk-nightly`: `Ansu-nightly.apk`, `Ansu-nightly-debug.apk`, `nightly.json`, generated `README.md` |
| `.github/workflows/release-apk.yml` | tag `v*`, manual | GitHub Release with `Ansu-<version>.apk` and `-debug.apk`, notes extracted from `CHANGELOG.md` |
| `.github/workflows/extract-fix-zip.yml` | push of `fix.zip` to `main`, manual | extracts the archive into the working tree (its own relative paths), commits it to `main` as `extract fix.zip and updated the app`, then dispatches the nightly APK build |

The two APK workflows are built the same way, and **both compile their APKs in
parallel**:

```
plan  ──▶  build (matrix: release, debug — one runner each, fail-fast: false)
                     │
                     ▼
          publish / release  (needs both legs; the only job that writes anything)
```

- The `build` job is a **matrix**: `release` and `debug` run simultaneously on
  separate runners. Never merge them back into a single job that calls
  `assembleDebug assembleRelease` — that is exactly the serialization this
  structure removes.
- `fail-fast: false` keeps the other leg running when one fails, so a single run
  shows the real state of *both* variants.
- The last job is guarded with
  `if: ${{ always() && … build.result == 'success' }}` so it publishes only when
  every leg succeeded, and it re-checks that the APKs it expected are actually
  present. A partial or half-finished build is therefore never published.
- The nightly publishes one consistent set: both APKs of a run share the stamp
  computed by the `plan` job, and the branch is replaced as a whole.
- The release is a **pre-release** automatically when the tag carries a SemVer
  pre-release suffix (`v1.2.3-rc.1`), and a stable release otherwise. The manual
  run can override that with the `channel` input.

`extract-fix-zip.yml` extracts `fix.zip`, commits the result to `main` and then
starts the nightly APK build. Two details are load-bearing:

- The extraction **only ever writes**. It updates existing files, creates missing
  ones and never deletes anything — `fix.zip` is left in place too — so the commit
  it makes can contain additions and modifications only.
- The pushed commit **does not trigger other workflows by itself**: a push made
  with the built-in `GITHUB_TOKEN` starts nothing. `workflow_dispatch` is the one
  exception, so the final step calls `gh workflow run apk-nightly.yml`, which is
  what makes the APK build run "by itself" after the commit. That needs
  `actions: write` next to `contents: write` in the workflow's `permissions`.

  Storing a personal access token and pushing with it works too and would trigger
the push-based workflows directly, but it needs a repository secret; the dispatch
needs none. Every entry of the archive is validated before it is written, and one
that would escape the repository or write into `.git` aborts the run.

Notes for anyone editing these:

- The runners already ship the Android SDK at `$ANDROID_SDK_ROOT`. Do **not**
  add `android-actions/setup-android` — it shells out to `sdkmanager tools`, a
  package that no longer exists, and fails every run.
- The workflow checks out the repository, so the Gradle wrapper jar must be
  committed or `./gradlew` cannot start.
- Both workflows build with `-PversionCode` / `-PversionName`, which only work
  because `app/build.gradle.kts` reads those properties. Keep that wiring.
- The nightly README is generated inside the workflow heredoc, so its two
  download badges must be edited there — not on the branch, which is
  overwritten on every run.
- The nightly workflow also writes `nightly.json`, including the published APK's
  file name under `apk`. The in-app updater reads that file, so keep its fields
  in sync with `NightlyManifest` in `data/update/UpdateModels.kt` — that is what
  lets the APK be renamed without breaking updates.
- Pushing to `apk-nightly` requires `permissions: contents: write` and the
  repository setting *Actions → General → Workflow permissions → Read and write*.
- The release job checks out with `fetch-depth: 0`: it needs the previous tags
  (`git describe`) and `CHANGELOG.md` at the tagged commit.

## 10. Ask before assuming

If a request is ambiguous, conflicts with the rules above, or would require a
large one-way change (rewriting history, changing the package name, swapping a
library, bumping the toolchain, adding a module), ask first rather than guessing.
A wrong guess is more expensive than one question.

## 11. Legal and content compliance

Ansu aggregates metadata from the public AniList API and plays media through
third-party sources, extensions and Stremio addons. Never hardcode credentials,
API keys or tokens into the repository or into commit messages, and do not add
code whose only purpose is to bypass a paywall, DRM or an access control. The
AniList client id lives in a `buildConfigField` placeholder and is meant to be
filled in per developer.
