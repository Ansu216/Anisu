# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## How this file is used

- Every change updates the `## [Unreleased]` section **in the same commit** as
  the code. See `CLAUDE.md` §7 for the full rules.
- At release time the entries move under a new `## [<version>] - <YYYY-MM-DD>`
  heading, where `<version>` is the pushed tag **without** its `v` prefix
  (tag `v1.2.3` → heading `## [1.2.3]`).
- `release-apk.yml` extracts that section verbatim and uses it as the GitHub
  Release body, so the section is written for users, not for the git log.
- Entries lead with a bold, one-line statement of the change, then explain what
  was wrong or missing and why the change fixes it. A `**Constraint:**` sentence
  records the limit that was deliberately kept, when there is one.
- Published sections are never rewritten. A mistake is corrected in a new
  release.

## [Unreleased]

## [0.1.0] - 2026-09-28

First versioned release. Ansu is an Android client for AniList that browses the
catalogue, follows the airing schedule, tracks a library and plays episodes
through pluggable sources.

### Added

- **The whole catalogue and library experience, built on AniList's public GraphQL
  API.** `AniListApi` talks to `graphql.anilist.co` directly (no SDK, no wrapper
  library) with OkHttp + Retrofit + `kotlinx.serialization`, and `AniListRepository`
  exposes it to the UI through `Flow`s. Signing in is an OAuth2 implicit-grant
  flow opened in a Custom Tab, with the redirect (`ansu://anilist-auth`) handled
  back in `MainActivity`; the token lives in `EncryptedSharedPreferences`. The
  favourite heart on the details page writes straight back to AniList, and
  watching progress is pushed when an episode passes ~90%. **Constraint:** the
  GraphQL schema is used read-mostly — list sync writes only the fields the app
  itself displays, so a schema change on AniList's side degrades the extra data
  and never the playback path.
- **An extension system, so content is not baked into the app.** An extension is
  a separate APK that declares an empty `<receiver>` with an intent-filter for
  `com.ansu.anime.extension.ANIME_SOURCE` and a `<meta-data>` entry naming its
  source classes; `ExtensionManager` discovers them through `PackageManager` and
  loads them with a `PathClassLoader` + reflection into the `AnimeCatalogueSource`
  interface. This is the same mechanism Keiyoushi/Aniyomi extensions use, so
  existing extension authoring knowledge transfers. `DemoSource` ships inside the
  app (AniList metadata + a public Apple HLS test stream) so the app has content
  on first launch and there is a working example to copy. **Constraint:** a broken
  extension can only break its own shelf — loading is per-extension and wrapped,
  so one bad APK cannot take the home screen down.
- **A Stremio-protocol addon client.** Any URL serving a `manifest.json` /
  `catalog` / `meta` / `stream` endpoint can be added from Settings → Addons.
  Nuvio Streams addons speak the same protocol, so one client (`AddonManager`,
  `StremioAddonApi`) serves both.
- **A home screen that mixes every source.** Frosted top bar, a swipeable hero
  carousel built from what is trending, the continue-watching row, a *Trending
  Now* row, a *Top Picks For You* grid for the current season, then one shelf per
  installed extension, per addon and (when signed in) per AniList list. The
  trending and current-season queries (`getTrending`, `getTopThisSeason`, using
  AniList's `season` / `seasonYear` arguments) were added to `AniListApi` and
  `AniListRepository` for this screen.
- **A CornCastle-shaped details page rather than a thin poster + play.** Banner
  with a centred title, a large Play button next to the AniList favourite heart,
  a score/episodes/year/format stat row, genre chips, an expandable synopsis, a
  vertical episode list, **Characters** and **Staff** grids that open a bottom
  sheet with the person's bio, role and voice actor, and a *More Like This* row.
  Episodes come from whichever extension or addon supplied the show; the cast,
  crew, stats, related titles and the favourite toggle come from AniList through
  `anilistId`. **Constraint:** a title with no `anilistId` (most addon-only
  content) still gets episodes and playback, just without the cast/crew sections —
  the page degrades instead of failing.
- **Playback with fully custom controls.** `ui/player/` is a Media3 `ExoPlayer`
  with its own overlay controls (the default Android controller is never used) and
  a source-picker sheet for switching between the results an addon or extension
  returned for the same episode.
- **A Schedule screen that also carries the news feed.** One screen, two tabs:
  AniList's real airing schedule grouped by weekday with day chips for jumping
  between days, and a news feed behind `NewsRepository`.
- **Search, My Space and Appearance.** Search over the AniList catalogue; a *My
  Space* profile tab with the Liked / Watching / Completed lists; and an
  Appearance screen storing the bottom bar's roundness in `SharedPreferences`,
  applied live through a `CompositionLocal` (`LocalNavBarRoundness`) so every
  screen's bar follows the slider without a restart. Settings also renders the
  identical, non-navigating bar as a live preview.
- **Continue watching, persisted in Room.** `ContinueWatchingRepository` +
  `AppDatabase` feed the home row, and the saved position is drawn as a progress
  bar onto each thumbnail so the row reads at a glance.
- **A built-in updater inside About.** `data/update/` checks two real channels —
  tagged GitHub **Releases** (`releases/latest`) and the hourly **`apk-nightly`**
  branch through its machine-readable `nightly.json` — compares versions
  numerically, and offers the release notes plus one-tap *Download & install*
  with a progress bar. The APK is downloaded into `cacheDir` and handed to
  Android's installer through the same `FileProvider` + package-installer intent
  the extension installs already used, so no new permission was needed
  (`REQUEST_INSTALL_PACKAGES` was already declared for extensions). Automatic
  checking on launch is on by default and can be turned off.
- **A dark, glassy Material 3 look.** A near-black background, one white accent
  and translucent surfaces (`AnsuColors`, `GlassSurface`/`FrostedGlassCard`,
  `BottomScrim`), a frosted bottom navigation bar and a deliberate type/shape
  scale using the system font — no custom font files are bundled.
- **Signing that works with zero setup.** A keystore is committed at
  `keystore/anisu.jks` and **both** build types use it, so the debug and release
  APKs, the nightly release/debug pair and the tagged releases all install as
  upgrades over each other instead of demanding an uninstall. Credentials can be
  overridden from the command line or CI without editing the build file
  (`-PANSU_STORE_PASSWORD`, `-PANSU_KEY_ALIAS`, `-PANSU_KEY_PASSWORD`).
- **Continuous integration with parallel builds.** `apk-nightly.yml` rebuilds and
  force-publishes `Ansu-nightly.apk`, `Ansu-nightly-debug.apk`, `nightly.json`
  and a generated `README.md` to the `apk-nightly` branch every hour and on every
  push to `main`; `release-apk.yml` attaches `Ansu-<version>.apk` and
  `Ansu-<version>-debug.apk` to a GitHub Release on a `v*` tag. In both, the two
  variants are built by **parallel matrix legs** on separate runners and a single
  final job collects them, so the published set is always one consistent build
  and a half-finished run can never publish a partial one.
- **Documentation for humans and agents.** `README.md` (architecture tour and
  setup), `how-to-compile.md` (click-by-click build guide, signing included),
  `keystore/README.md` (what the committed keystore is and how to rotate it) and
  `CLAUDE.md` — the complete rulebook for AI agents: the exact build procedure,
  the traps that have broken this build, the branding rules and the mandatory
  changelog/SemVer rules. `AGENTS.md` is deliberately reduced to a pointer at
  `CLAUDE.md`, so a tool that looks for the older convention still lands on the
  rules instead of on an out-of-date copy.

### Changed

- **The project was rebranded to Ansu.** The application id is `com.ansu.anime`,
  the launcher label is `Ansu`, the deep-link scheme is `ansu://` and the colour
  resources are `ansu_*`. Every earlier `kernel` / `Anisu` / `StreamHub` name was
  removed from class names, paths, resources and docs. **Constraint:** the signing
  identity was deliberately *not* renamed — the keystore file stays
  `keystore/anisu.jks` with the key alias `anisu` and the certificate stays
  `CN=Anisu`, because renaming them would change the signing certificate and
  every existing installation would stop accepting upgrades. The GitHub repository
  slug (`Ansu216/Anisu`) is fixed for the same reason: it is what the updater and
  the nightly badges point at.
- **`minSdk` raised from 24 to 26 (Android 8.0).** The updater, the extension
  installer and the encrypted-preferences fallback all assume APIs that are
  guarded below 26 for no benefit, and 26 is the oldest level with a sane
  `FileProvider` + package-installer combination to build the update flow on.
  `targetSdk`/`compileSdk` remain 35.
- **One implementation per screen.** A UI overhaul had been landed half-applied,
  leaving two parallel screen sets in the tree (`Anisu*` and the older
  `*StreamHub` files duplicating the `Ansu*` ones). The orphan designs that were
  worth keeping — the glass top bar + hero carousel + Top Picks home layout, and
  the day-chip + news-card schedule layout — were folded into the wired
  `ui/home` and `ui/schedule` screens and backed by the real repositories and view
  models, and the superseded files were deleted. The dead `LibraryScreen` (which
  referenced a `Dest.LIBRARY` route that no longer existed, already replaced by
  *My Space*) went with them.
- **Version metadata is injectable again.** `app/build.gradle.kts` reads
  `versionCode` / `versionName` from Gradle properties, which is what lets both
  workflows stamp the nightly (`2026.09.28.1500` / `2026092815`) and the release
  (the tag's version) without patching the build file. Defaults stay `1` /
  `0.1.0`.

### Fixed

- **The build compiles again.** The half-applied overhaul produced a wall of
  `Redeclaration`, `Conflicting overloads` and `@Composable invocations can only
  happen from the context of a @Composable function` errors (the follow-on errors
  were pure noise caused by the duplicate symbols), and it had silently dropped
  the `signingConfigs` block and the version-property wiring from
  `app/build.gradle.kts`. Removing the duplicates and restoring the build file
  brought the project back to zero errors.
- **The build is warning-free too.** Seven `Icons.Filled.ArrowBack` /
  `Icons.Filled.ViewList` uses were deprecated in favour of the auto-mirrored
  variants; they now use `Icons.AutoMirrored.Filled.*`, so a clean build reports
  **0 errors and 0 warnings** for both variants. This is the bar every later
  change has to keep.
- **The floating bottom navigation bar no longer sits under the system navigation
  bar.** The app draws edge to edge (`enableEdgeToEdge()`), and a Material 3
  `Scaffold` places its `bottomBar` slot flush against the window instead of
  padding it. Because the bar is a custom glass card rather than a Material
  `NavigationBar`, nothing applied the navigation-bar inset, so on devices with a
  gesture pill or three-button bar the card was overlapped and its labels were
  partly unreachable. `AppBottomBar` now applies
  `Modifier.windowInsetsPadding(WindowInsets.navigationBars)` on the outside of
  the card, keeping its own 8dp gap and floating it clear of the system bar.
  **Constraint:** the inset is applied to the navigable bar only, not to
  `NavBarSurface`, so the copy Settings renders as a *preview* inside a scrolling
  column is not pushed around by insets it does not need.
- **The updater can no longer be broken by renaming the published APK.**
  `nightly.json` now carries the published file name in `apk` (and the debug one
  in `apkDebug`), and `UpdateChecker` reads that field instead of assuming
  `Ansu-nightly.apk`. The branch still served `Anisu-nightly.apk` from a run
  predating the rebrand, which would have made the nightly channel report "no
  update" for every build; an older manifest without the field still falls back
  to the default name.
- **Stale branding in the resources.** `colors.xml` / `themes.xml` still declared
  `kernel_*` colours and a `Kernel` theme name; they are now `ansu_*` with a
  matching theme.
- **The nightly workflow actually verifies what it publishes.** Both APKs are
  checked with `apksigner verify --print-certs` before the branch is pushed, and
  the publish job fails loudly instead of pushing a partial set when an expected
  APK is missing.

### Security

- **No credentials are stored in the repository.** GitHub Actions needs none at
  all — the updater and the badges read the public repository, and the committed
  keystore is what signs every build — so nothing is read from secrets except the
  automatic `GITHUB_TOKEN`. The AniList client id is a `buildConfigField`
  placeholder meant to be filled in per developer, and no token ever reaches a log
  or a commit message.
- **The AniList token is encrypted at rest, with a working fallback.**
  `AniListAuthManager` keeps it in `EncryptedSharedPreferences`, but constructing
  that can throw on some ROMs and after a backup restore; the
  `runCatching { … }.getOrElse { plain prefs }` fallback is deliberate, because
  anything built in `Application.onCreate` must never be able to crash the app.
- **An update can only ever be the same app.** Installing a downloaded APK relies
  on Android's own signature check, which refuses an APK not signed with the key
  that signed the installed app — so a hijacked download URL cannot silently
  replace Ansu with something else.
