# Kernel

A hybrid anime app: Nuvio's dark, addon-driven home screen and player, CornCastle's
AniList-synced continue-watching row, built extension-based like Keiyoushi/Aniyomi
sources, **plus** support for Stremio/Nuvio-protocol HTTP addons.

## Architecture at a glance

- **Extensions** (`extension/`) — separate installable APKs, discovered via
  `PackageManager` and loaded with a `PathClassLoader` + reflection into the
  `AnimeCatalogueSource` interface. This is the same mechanism Keiyoushi/Aniyomi
  extensions use: an extension APK declares an empty `<receiver>` with an
  intent-filter for `com.kernel.anime.extension.ANIME_SOURCE`, and a `<meta-data>`
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
   It will offer to generate the Gradle wrapper jar automatically — accept
   that, since it isn't checked into this download.
2. Register a client at <https://anilist.co/settings/developer> with redirect
   URI `kernel://anilist-auth`, then put the client ID in
   `app/build.gradle.kts` (`ANILIST_CLIENT_ID`), replacing the placeholder.
3. Build and run. The home screen will show AniList trending data via the
   built-in demo source immediately; no extension or addon is required to see
   the UI working end to end (including playback, against a public test
   stream).
4. To add real content: install a Keiyoushi-format extension APK on the
   device (Settings → Extensions → Rescan), or add a Stremio/Nuvio addon
   manifest URL (Settings → Addons).

## Honest limitations

This was written in a sandboxed environment with no network access, so **it
has not been compiled or run**. It's a complete, coherent source tree
following real, documented protocols (Keiyoushi extension loading, the
Stremio addon spec, AniList's public GraphQL API), but treat the first build
in Android Studio as exactly that — a first build. Likely rough edges:

- Dependency versions (AGP/Kotlin/Compose BOM/Room/Media3) were current as of
  writing but Android Studio may prompt to bump one or two on first sync —
  that's expected and safe to accept.
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
app/src/main/java/com/kernel/anime/
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
