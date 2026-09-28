# Ansu

An Android client for [AniList](https://anilist.co): browse the catalogue, follow
the airing schedule and anime news, keep your AniList lists in sync, and play
episodes through **pluggable sources** — a built-in demo source, Keiyoushi-style
extension APKs and Stremio/Nuvio-protocol HTTP addons.

- **App name:** Ansu
- **Package / application id:** `com.ansu.anime`
- **Minimum Android:** 8.0 (API 26) · **Target:** Android 15 (API 35)

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
- **AniList** (`anilist/`) — OAuth2 implicit-grant login via Custom Tabs, a
  GraphQL client hitting `graphql.anilist.co` directly, watching/planning list
  sync, trending and current-season queries, the weekly airing schedule,
  favourite toggling, and progress pushed back once an episode is ~90% watched.
- **Home** (`ui/home/`) — frosted top bar, a swipeable hero carousel built from
  what's trending, the continue-watching row, a *Trending Now* row, a *Top Picks
  For You* grid for the current season, then one shelf per installed extension,
  addon and (when signed in) AniList list.
- **Schedule & News** (`ui/schedule/`) — one screen, two tabs: AniList's real
  airing schedule grouped by weekday with day chips, and a news feed (a fixture
  seam today, swappable for a real HTTP feed without touching the UI).
- **My Space** (`ui/myspace/`) — profile header plus your Liked, Watching and
  Completed lists.
- **Continue watching** (`data/db`, `data/repository/ContinueWatchingRepository`)
  — Room-backed row on the home screen with a progress bar drawn onto each
  thumbnail.
- **Details page** (`ui/details/`) — deliberately CornCastle-shaped rather than
  a thin "poster + play" screen: banner with centered title, a big Play
  button plus an AniList favourite heart, a score/episodes/year/format stat
  row, genre chips, an expandable synopsis, a vertical episode list, then
  **Characters** and **Staff** grids (tap either for a bottom sheet with
  their bio/role/voice actor), and a "More Like This" row. Episode data comes
  from whichever extension/addon supplied the show; everything else
  (characters, staff, stats, related titles, the favourite toggle) comes from
  AniList's public GraphQL API via `anilistId`, since extensions/addons don't
  carry that metadata. A title with no `anilistId` (most addon-only content)
  still gets episodes and playback, just without the cast/crew sections.
- **Player** (`ui/player/`) — a Media3 `ExoPlayer` with fully custom overlay
  controls (no default Android controller) and a source-picker sheet for
  switching between addon/extension results.
- **Appearance** (`ui/appearance/`, `data/prefs/`) — nav-bar roundness stored in
  DataStore, applied live through a `CompositionLocal`.
- **DI** — a single hand-written `AppContainer` (`di/AppContainer.kt`) instead
  of Hilt/Dagger, to keep the first build simple.

The visual language is a dark, glassy Material 3 theme: a near-black background,
one white accent, and translucent "frosted" cards (`ui/components/GlassSurface.kt`).

## Build

```bash
./gradlew assembleDebug      # debug APK  -> app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease    # release APK -> app/build/outputs/apk/release/app-release.apk
```

Both variants are signed with the committed `keystore/anisu.jks`, so the debug
and release builds install as upgrades over each other. `how-to-compile.md` has
a beginner, click-by-click version of these instructions.

## Setup

1. Open the project root in Android Studio (Ladybug or newer) and let it sync.
2. Register a client at <https://anilist.co/settings/developer> with redirect
   URI `ansu://anilist-auth`, then put the client ID in
   `app/build.gradle.kts` (`ANILIST_CLIENT_ID`), replacing the placeholder.
3. Build and run. The home screen will show AniList trending data via the
   built-in demo source immediately; no extension or addon is required to see
   the UI working end to end (including playback, against a public test
   stream).
4. To add real content: install a Keiyoushi-format extension APK on the
   device (Settings → Extensions → Rescan), or add a Stremio/Nuvio addon
   manifest URL (Settings → Addons).

## Known rough edges

- Matching an AniList list entry back to a specific installed extension is
  simplified to "use whatever the first source is" (`CatalogRepository`). A real
  app would want fuzzy title matching across sources, which is a reasonable next
  feature rather than something this scaffold gets fully right.
- The player's seek bar calls `seekTo` on every drag tick rather than only on
  release — functional, just chattier than ideal.
- The News tab reads from `NewsRepository`, which returns fixture data: AniList
  has no news endpoint. Swap the implementation for a real feed (RSS/JSON, or a
  backend) when you have one — the screen needs no changes.
- No app icon PNGs were generated (only a simple vector adaptive icon), and
  no custom font files are bundled — the type scale uses the system font
  with deliberate weights/sizes instead. Both are easy to swap in.

## Where things live

```
app/src/main/java/com/ansu/anime/
├── core/model/        SAnime, SEpisode, Video, MediaOrigin, Shelf
├── core/util/         Formatting, SelectionHolder
├── extension/          AnimeCatalogueSource contract + APK loader + DemoSource
├── addon/              Stremio/Nuvio protocol client + AddonManager
├── anilist/            OAuth, GraphQL client, models, repository
├── data/db/            Room entities/DAOs (continue watching, addons)
├── data/news/          NewsRepository (News tab seam)
├── data/prefs/         AppearancePrefs (DataStore)
├── data/repository/    CatalogRepository (home feed), ContinueWatchingRepository
├── di/AppContainer.kt  Manual dependency graph
└── ui/                 Compose screens: home, search, schedule, myspace,
                        appearance, details, player, extensions, addons,
                        settings, auth + shared components and theme
```

## Continuous integration

- `apk-nightly.yml` rebuilds and force-publishes the `apk-nightly` branch
  (`Ansu-nightly.apk`, `Ansu-nightly-debug.apk`, `nightly.json`) every hour and
  on every push to `main`.
- `release-apk.yml` builds a GitHub Release with `Ansu-<version>.apk` and
  `Ansu-<version>-debug.apk` when you push a `v*` tag.
