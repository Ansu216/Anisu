# AGENTS.md

Instructions for AI coding agents working in this repository.

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
| `ui/appearance` | Appearance settings (nav-bar roundness) |
| `ui/about` | About: version, update channel + install, developer credit and links |
| `ui/search`, `ui/details`, `ui/player` | Search, details (cast/crew sheet) and playback |
| `ui/extensions`, `ui/addons`, `ui/settings`, `ui/auth` | Extensions, addons, settings, AniList login |
| `core/model/AnimeModels.kt` | `SAnime`, `SEpisode`, `MediaOrigin`, `Shelf` |
| `core/util/` | `Formatting`, `SelectionHolder` |
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

## 3. Build, run and verify

```bash
./gradlew assembleDebug      # debug APK
./gradlew assembleRelease    # minified release APK
./gradlew clean              # wipe build output
```

- **Both** build types are signed with `keystore/anisu.jks`, so the debug and
  release APKs install as upgrades over each other.
- Signing credentials can be overridden without editing the build file:
  `-PANSU_STORE_PASSWORD=`, `-PANSU_KEY_ALIAS=`, `-PANSU_KEY_PASSWORD=`.
- Version is overridable from the command line / CI:
  `-PversionCode=42 -PversionName=1.2.3`. Defaults are `1` / `0.1.0`.
- `local.properties` is machine-local and **gitignored** — never commit it.
- `gradle/wrapper/gradle-wrapper.jar` **must stay committed**. The `.gitignore`
  `gradle-*` rules are deliberately anchored to the repo root precisely so they
  do not swallow the wrapper jar. Never loosen them back to a bare `gradle-*`.

**Verification bar: after any code change, run the build and reach zero errors
and zero warnings.** Do not leave deprecation warnings behind. There are no unit
tests in this repository; the build plus an APK inspection is the check. A quick
sanity check of a built APK:

```bash
"$ANDROID_SDK_ROOT/build-tools/35.0.0/aapt" dump badging app/build/outputs/apk/debug/app-debug.apk
# must report package: name='com.ansu.anime', application-label:'Ansu'
```

`how-to-compile.md` is the beginner, click-by-click guide (English). If you
change how the project is built, signing included, update it.

## 4. Code conventions

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
  the first line of the file that uses them.

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
(`Ansu216/Anisu`, used in the workflow badge URLs) is also fixed and must not be
rewritten.

## 5. Golden rule: "if it works, don't touch it"

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

## 6. Git conventions — MANDATORY (do not violate)

- **English only.** Every commit message — title **and** body — must be written
  in English. No Italian, no mixed language.
- **No co-author footer, ever.** Never append `Co-Authored-By: …`,
  `Generated with …`, `🤖` or any line crediting an agent, client or model.
  Write a plain commit message: a short imperative subject and, when useful, a
  body explaining **why** the change was made. This applies unless the user
  explicitly asks for a footer in that specific message.
- Do not commit build output, `local.properties`, `.kotlin/`, `.gradle/` or
  local Gradle distributions. These are already in `.gitignore`.
- Do not push, force-push or rewrite published history unless the user asks.
  When a push is rejected because the remote moved, integrate (rebase onto
  `origin/main`) rather than discarding remote work.

## 7. CI and releases

| Workflow | Trigger | Output |
|---|---|---|
| `.github/workflows/apk-nightly.yml` | hourly `cron`, **every push to `main`**, manual | force-publishes to `apk-nightly`: `Ansu-nightly.apk`, `Ansu-nightly-debug.apk`, `nightly.json`, generated `README.md` |
| `.github/workflows/release-apk.yml` | tag `v*`, manual | GitHub Release with `Ansu-<version>.apk` and `-debug.apk` |

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

## 8. Ask before assuming

If a request is ambiguous, conflicts with the rules above, or would require a
large one-way change (rewriting history, changing the package name, swapping a
library, adding a module), ask first rather than guessing. A wrong guess is more
expensive than one question.

## 9. Legal and content compliance

Ansu aggregates metadata from the public AniList API and plays media through
third-party sources, extensions and Stremio addons. Never hardcode credentials,
API keys or tokens into the repository or into commit messages, and do not add
code whose only purpose is to bypass a paywall, DRM or an access control. The
AniList client id lives in a `buildConfigField` placeholder and is meant to be
filled in per developer.
