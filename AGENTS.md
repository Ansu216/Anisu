# AGENTS.md

Instructions for AI coding agents working in this repository.

## 1. Project overview

**Anisu** is an Android client for [AniList](https://anilist.co) with anime
catalogue browsing, library tracking and playback through pluggable sources
(built-in demo source, Stremio addons, extensions).

- **Package / application id**: `com.ansu.anime`
- **Display name**: `Anisu`
- **Language/build**: Kotlin 2.1.0, Java 17 bytecode target, JDK 21 toolchain,
  Gradle Kotlin DSL, Gradle 8.14.5 (wrapper committed).
- **UI**: Jetpack Compose + Material 3, Navigation Compose, Coil for images.
- **Data**: Room (KSP codegen), DataStore Preferences, `EncryptedSharedPreferences`.
- **Network**: OkHttp + Retrofit + `kotlinx.serialization`.
- **Playback**: Media3 / ExoPlayer.
- **SDK levels**: `minSdk 24` (Android 7.0), `targetSdk`/`compileSdk` 35.

## 2. Architecture and module structure

`settings.gradle.kts` sets `rootProject.name = "Anisu"` and declares a single
module: `:app`. There are no other Gradle modules — do not introduce one unless
the user explicitly asks.

Source root: `app/src/main/java/com/ansu/anime/`

| Path | Role |
|---|---|
| `AnisuApp.kt` | `Application` subclass; builds the DI container |
| `MainActivity.kt` | Single activity, hosts the Compose content |
| `di/AppContainer.kt` | **Manual** dependency container (there is no Hilt/Koin) |
| `ui/navigation/AnisuNavGraph.kt` | Navigation graph and `Dest` routes |
| `ui/theme/Theme.kt` | `AnisuTheme` + the `Anisu*` color palette |
| `ui/components/Common.kt` | Shared composables (bars, shelves, cards, chips) |
| `ui/home`, `ui/search`, `ui/library`, `ui/details`, `ui/player` | Feature screens |
| `ui/details/CharacterStaffSheet.kt` | Cast/crew bottom sheet for the details page |
| `ui/extensions`, `ui/addons`, `ui/settings`, `ui/auth` | Extension, addon, settings and AniList login screens |
| `core/model/AnimeModels.kt` | `SAnime`, `SEpisode`, `MediaOrigin` |
| `data/db/AppDatabase.kt` | Room database |
| `data/repository/` | Catalogue and continue-watching repositories |
| `extension/` | Extension manager, repo and the `AnimeCatalogueSource` API |
| `addon/` | Stremio addon client and models |
| `anilist/` | AniList GraphQL API, models, repository, OAuth manager |

Dependency wiring flows `AnisuApp` → `AppContainer` → screens/view models. Pass
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
  `-PANISU_STORE_PASSWORD=`, `-PANISU_KEY_ALIAS=`, `-PANISU_KEY_PASSWORD=`.
- Version is overridable from the command line / CI:
  `-PversionCode=42 -PversionName=1.2.3`. Defaults are `1` / `0.1.0`.
- `local.properties` is machine-local and **gitignored** — never commit it.
- `gradle/wrapper/gradle-wrapper.jar` **must stay committed**. The `.gitignore`
  `gradle-*` rules are deliberately anchored to the repo root precisely so they
  do not swallow the wrapper jar. Never loosen them back to a bare `gradle-*`.

**Verification bar: after any code change, run the build and reach zero errors
and zero warnings.** Do not leave deprecation warnings behind. There are no unit
tests in this repository; the build plus an APK inspection is the check.

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
  `HorizontalDivider`) are opted into **once** in `app/build.gradle.kts` via
  `-opt-in=androidx.compose.material3.ExperimentalMaterial3Api`. Do not annotate
  individual composables.

### Known traps — these have broken the build before

- **`item { }` inside a `LazyColumn`/`LazyRow` is a member of `LazyListScope`**,
  not a top-level function. `import androidx.compose.foundation.lazy.item` does
  not exist and fails with "Unresolved reference". Only `items(...)` is
  imported (`androidx.compose.foundation.lazy.items`).
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

The app was renamed from "Kernel" to "Anisu" and its package from
`com.kernel.anime` to `com.ansu.anime`. There must be **no** `com.kernel.anime`
reference anywhere, in any file name, path or source line. Keep the class names
`AnisuApp`, `AnisuTheme`, `AnisuNavGraph`, the colour names `anisu_*`, the URI
scheme `anisu://` and the app label `Anisu`.

## 5. Golden rule: "if it works, don't touch it"

Do not refactor, rewrite or "clean up" modules, files or functions that are
already working, unless (a) it is strictly necessary for the requested change,
or (b) the user explicitly asks for it. State the risk before editing something
that could break existing behaviour, and ask when in doubt.

#### Do-not-touch areas (verified working — leave alone)

- **The signing setup and the committed keystore.** `keystore/anisu.jks` is
  committed on purpose so local and CI builds produce identically signed APKs.
- **The launch path.** `AnisuApp` → `MainActivity` → `AnisuNavGraph` is verified
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
| `.github/workflows/apk-nightly.yml` | hourly `cron`, **every push to `main`**, manual | force-publishes to `apk-nightly`: `Anisu-nightly.apk`, `Anisu-nightly-debug.apk`, `nightly.json`, generated `README.md` |
| `.github/workflows/release-apk.yml` | tag `v*`, manual | GitHub Release with `Anisu-<version>.apk` and `-debug.apk` |

Notes for anyone editing these:

- The runners already ship the Android SDK at `$ANDROID_SDK_ROOT`. Do **not**
  add `android-actions/setup-android` — it shells out to `sdkmanager tools`, a
  package that no longer exists, and fails every run.
- The workflow checks out the repository, so the Gradle wrapper jar must be
  committed or `./gradlew` cannot start.
- The nightly README is generated inside the workflow heredoc, so its two
  download badges must be edited there — not on the branch, which is
  overwritten on every run.
- Pushing to `apk-nightly` requires `permissions: contents: write` and the
  repository setting *Actions → General → Workflow permissions → Read and write*.

## 8. Ask before assuming

If a request is ambiguous, conflicts with the rules above, or would require a
large one-way change (rewriting history, changing the package name, swapping a
library, adding a module), ask first rather than guessing. A wrong guess is more
expensive than one question.

## 9. Legal and content compliance

Anisu aggregates metadata from the public AniList API and plays media through
third-party sources, extensions and Stremio addons. Never hardcode credentials,
API keys or tokens into the repository or into commit messages, and do not add
code whose only purpose is to bypass a paywall, DRM or an access control. The
AniList client id lives in a `buildConfigField` placeholder and is meant to be
filled in per developer.
