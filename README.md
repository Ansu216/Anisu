# Anisu

**Package:** `com.ansu.anime` · **Min Android:** 7.0 (API 24) · **Target:** Android 15 (API 35)

A hybrid anime app: Nuvio's dark, addon-driven home screen and player, CornCastle's
AniList-synced continue-watching row, built extension-based like Keiyoushi/Aniyomi
sources, **plus** support for Stremio/Nuvio-protocol HTTP addons.

APKs are signed (`keystore/anisu.jks`) and published automatically:

- **Nightly:** every hour to the [`apk-nightly`](https://github.com/Ansu216/Anisu/tree/apk-nightly) branch
- **Release:** on a `v*` tag, attached to a GitHub Release

See [`how-to-compile.md`](how-to-compile.md) for the full build guide.

## Architecture at a glance

- **Extensions** (`extension/`) — separate installable APKs, discovered via
  `PackageManager` and loaded with a `PathClassLoader` + reflection into the
  `AnimeCatalogueSource` interface. This is the same mechanism Keiyoushi/Aniyomi
  extensions use: an extension APK declares an empty `<receiver>` with an
  intent-filter for `com.ansu.anime.extension.ANIME_SOURCE`, and a `<meta-data>`
  entry naming its source class(es). `DemoSource` is a real, working example
  bundled straight into the app (pulls AniList metadata, points at a public
  Apple HLS test stream) so there's content on first launch and something to
  copy when writing a real extension.
- **Addons** (`addon/`) — any URL serving a Stremio-protocol `manifest.json` /
  `catalog` / `meta` / `stream` endpoint. Nuvio Streams addons speak the exact
  same protocol, so one client (`AddonManager`, `StremioAddonApi`) handles both.
  Add one from Settings → Addons by pasting its manifest URL.
- **AniList** (`anilist/`) — OAuth2 implicit-grant login via Custom Tabs,
  GraphQL client hitting `graphql.anilist.co` directly, watching/planning list
  sync, and progress pushed back once an episode is ~90% watched.
- **Continue watching** (`data/db`, `data/repository/ContinueWatchingRepository`)
  — Room-backed, CornCastle-style row on the home screen with a progress bar
  drawn onto each thumbnail.
- **UI** — Jetpack Compose throughout. `ui/home` is the Nuvio-style hero +
  shelves layout; `ui/player` is a Media3 `ExoPlayer` with fully custom
  overlay controls (no default Android controller) and a source-picker sheet
  for switching between addon/extension results.
- **DI** — a single hand-written `AppContainer` (`di/AppContainer.kt`) instead
  of Hilt/Dagger, to keep the first build simple.

## Setup

1. Open the project root in Android Studio (Ladybug or newer) and let it sync.
   The Gradle wrapper (`gradlew` + `gradle/wrapper/gradle-wrapper.jar`) **is**
   checked in, so no wrapper generation step is needed.
2. Register a client at <https://anilist.co/settings/developer> with redirect
   URI `anisu://anilist-auth`, then put the client ID in
   `app/src/main/java/com/ansu/anime/anilist/AniListAuthManager.kt`
   (`ANILIST_CLIENT_ID`), replacing the placeholder.
3. Build and run — `./gradlew assembleDebug` or **Build → Build APK(s)**.
   The home screen will show AniList trending data via the built-in demo
   source immediately; no extension or addon is required to see the UI working
   end to end (including playback, against a public test stream).
4. To add real content: install a Keiyoushi-format extension APK on the
   device (Settings → Extensions → Rescan), or add a Stremio/Nuvio addon
   manifest URL (Settings → Addons).

## Build status

This tree now compiles and is verified: `./gradlew assembleDebug assembleRelease`
produces signed APKs with **zero errors and zero warnings**, and the APKs install
and launch (the previous instant-launch crash — the manifest pointing at
`com.ansu.anime.AnisuApp`, a class that does not exist — is fixed).

Known rough edges that remain:

- Dependency versions (AGP/Kotlin/Compose BOM/Room/Media3) were current as of
  writing but Android Studio may prompt to bump one or two on first sync —
  that's expected and safe to accept. If you bump Kotlin, bump KSP to the
  matching `<kotlinVersion>-<kspVersion>` release too.
- Matching an AniList list entry back to a specific installed extension is
  simplified to "use whatever the first source is" (`CatalogRepository`).
  A real app would want fuzzy title matching across sources, which is a
  reasonable next feature rather than something this scaffold gets fully
  right.
- The player's seek bar calls `seekTo` on every drag tick rather than only on
  release — functional, just chattier than ideal.
- No app icon PNGs were generated (only a simple vector adaptive icon), and
  no custom font files are bundled — the type scale uses the system font
  with deliberate weights/sizes instead. Both are easy to swap in.

## Where things live

```
app/src/main/java/com/ansu/anime/
├── core/model/        SAnime, SEpisode, Video, MediaOrigin
├── extension/          AnimeCatalogueSource contract + APK loader + DemoSource
├── addon/              Stremio/Nuvio protocol client + AddonManager
├── anilist/            OAuth, GraphQL client, repository
├── data/db/            Room entities/DAOs (continue watching, addons)
├── data/repository/    CatalogRepository (home feed), ContinueWatchingRepository
├── di/AppContainer.kt  Manual dependency graph
└── ui/                 Compose screens: home, details, player, search,
                        library, extensions, addons, settings, auth
```
