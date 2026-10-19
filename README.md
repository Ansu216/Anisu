<div align="center">

<img src="logo.jpg" alt="Anisu logo" width="120" height="120" />

# Anisu

**An Android anime client built around AniList: browse, track, stream and download, with sources you choose.**

![Platform](https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-2.1.0-7F52FF?logo=kotlin&logoColor=white)
![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?logo=jetpackcompose&logoColor=white)
![Version](https://img.shields.io/badge/version-0.4.8-blue)

</div>

---

## What is Anisu?

Anisu is a native Android app that puts your AniList account, the airing schedule, news and playback in one place. It ships with **no video sources of its own**: you add extension repositories, and Anisu finds each episode in every source you have enabled, so you can play whichever stream works best for you.

- Package id: `com.ansu.anime`
- Minimum Android: 8.0 (API 26), target API 35
- Source code: <https://github.com/Ansu216/Anisu>

---

## Features

### Discover and track
- **Home** with a looping hero banner, Continue Watching, trending and seasonal shelves, "Your Next Watch" recommendations based on what you watch, and banners for newly released titles.
- **Search** with filters, grid or list view (your choice is remembered), romaji-name matching, and an optional per-source search.
- **Schedule and News** tabs: the airing calendar and an Anime News Network feed.
- **My Space**: sign in with AniList and see Favourites, Watching, Planning and Dropped lists with progress bars, plus Sync.
- **Details page** with episodes, trailers (sub, dub and others), characters, comments and discussions, English titles and clear-logo artwork. Unreleased titles show a "Notify me" button instead of playable episodes, and movies show their duration with a "Play movie" button.
- **Notifications** for new episodes of titles you are watching and for titles that have just released, collected in an inbox at the top of Home.

### Watch
- **Pluggable sources.** Anisu loads Aniyomi-compatible extension APKs (extension library versions 12 to 17) from the repositories you add. The same episode is matched across every enabled source.
- **Source picker.** Tap an episode to choose from every stream found, grouped by extension. Tap to play, hold to download.
- **Smart stream choice.** Set a preferred quality (Any, 1080p, 720p, 480p, 360p) and language (Sub or Dub); Anisu starts from those, remembers the source that worked for each show, and keeps a stream you picked by hand for that show's next episodes. A drag-to-order **streaming priority** list decides which sources win ties.
- **Player** built on Media3 / ExoPlayer: side panels for Sources, Audio, Subtitles and Episodes (landscape and portrait), subtitle offset, size, position and a rounded-bar background, double-tap and swipe gestures, brightness and volume gestures, AniSkip intro/outro buttons, a Nuvio-style pause overlay and picture-in-picture.
- **Downloads.** Save episodes for offline viewing, along with every subtitle the source offers (including subtitles carried inside the stream, and a dub borrows the sub stream's subtitle files when it lists none). Progress shows live in the Your downloads screen and in the notification.

### Make it yours
- Appearance options: nav-bar frosted blur and roundness, custom fonts (including a separate subtitle font), and optional haptic feedback with adjustable strength.
- A built-in adult-content filter that keeps 18+ sources and titles out of search and blocks them from streaming.
- Backup and restore (manual and automatic), a self-updater (stable releases and nightly builds), and an in-app diagnostics log.

> Extension support is currently the Aniyomi format only. Other formats (for example Cloudstream plugins) are planned, not built.

---

## Install

Download the newest APK from the project's **Releases** page, or a nightly build from the `apk-nightly` branch, and install it on your phone. Android may ask you to allow installing from your browser or file manager the first time. After that, Anisu can update itself from **Settings > About / Updates**.

### Add sources

Anisu does not include any streaming sources. To watch anything:

1. Open **Extensions**.
2. Add an extension repository (its `index.min.json` address).
3. Install the extensions you want from **Available**, and use **Test** on an installed one to check that it can find episodes and streams.

You are responsible for the repositories you add and for making sure your use of them is lawful where you live.

---

## Build from source

You need **JDK 21**, the Android SDK (`platforms;android-35`, `build-tools;35.0.0`) and a network connection on the first run.

```bash
git clone https://github.com/Ansu216/Anisu.git
cd Anisu
echo "sdk.dir=/path/to/Android/Sdk" > local.properties

./gradlew assembleDebug                         # debug APK
./gradlew assembleDebug assembleRelease --console=plain   # the full check, both variants
```

The APK lands in `app/build/outputs/apk/`. New to Android builds? [`how-to-compile.md`](how-to-compile.md) walks through it step by step.

Please do not upgrade Gradle, AGP, Kotlin, KSP or the Compose BOM unless that is the task; the versions are tied together. [`CLAUDE.md`](CLAUDE.md) holds the exact build procedure and the traps that have broken it before.

### Version and release

[`version.txt`](version.txt) is the single source of truth: line 1 is the version, line 2 the version code (only ever increases), line 3 the channel (`nightly`, `beta` or `stable`). Release notes come from [`CHANGELOG.md`](CHANGELOG.md), so every change adds an entry under `## [Unreleased]`.

---

## Tech stack

| Area | Used |
|---|---|
| Language and build | Kotlin 2.1.0, Java 17 target, JDK 21 toolchain, Gradle 8.9, AGP 8.7.2, KSP |
| UI | Jetpack Compose, Material 3, Navigation Compose, Coil |
| Playback | Media3 / ExoPlayer (HLS, DASH), Media3 download manager |
| Data | Room, SharedPreferences and DataStore, EncryptedSharedPreferences for the AniList token, WorkManager |
| Network | OkHttp, DNS over HTTPS, kotlinx.serialization |
| Extensions | Aniyomi extension API re-implementation, Jsoup, RxJava, Injekt, QuickJS, NanoHTTPD |

There is no dependency-injection framework: `AnsuApp` builds a manual container (`di/AppContainer.kt`) and passes dependencies explicitly.

### Project layout

Everything lives in one Gradle module, `:app`, under `app/src/main/java/com/ansu/anime/`:

| Path | What it holds |
|---|---|
| `ui/` | Screens and view models: home, search, details, player, player settings, sources, downloads, extensions, My Space, schedule, settings, about |
| `extension/` | Extension manager and repositories, source matching and testing, the Aniyomi loader and adapter |
| `eu/kanade/tachiyomi/` | Intentionally not renamed: the Aniyomi extension API that extension APKs are compiled against |
| `anilist/` | AniList GraphQL API, models, repository and OAuth |
| `data/` | Downloads, backup, notifications, preferences, repositories, updater, Room database |
| `core/` | Shared models, networking and error handling, diagnostics, haptics, utilities |

More detail on the architecture, conventions and pitfalls is in [`CLAUDE.md`](CLAUDE.md). Design notes and older implementation guides are in [`docs/`](docs/).

---

## Data sources and credits

Anisu is built on the work of others. The in-app **License and attribution** screen lists them all; the main ones are:

- [AniList](https://anilist.co) for accounts, lists, titles, schedules, comments and discussions
- [MyAnimeList](https://myanimelist.net) through [Jikan](https://jikan.moe) for English titles, trailers and ids
- [TMDB](https://www.themoviedb.org) for posters and artwork (this product uses the TMDB API but is not endorsed or certified by TMDB)
- [ani.zip](https://ani.zip) for episode titles, thumbnails and logos, and [Kitsu](https://kitsu.app) for id mappings
- [AniSkip](https://api.aniskip.com) for intro and outro timestamps
- [Anime News Network](https://www.animenewsnetwork.com) for news headlines
- YouTube for trailer playback
- [Aniyomi](https://github.com/aniyomiorg/aniyomi) for the extension format Anisu is compatible with

Contributors are listed in the app (Settings > Contributors) and read from [`contributorsansu.json`](contributorsansu.json); open a pull request to add yourself.

---

## Contributing

Bug reports and pull requests are welcome.

1. Run the build once before you change anything; it must be green on a clean tree.
2. Fix what the issue asks and avoid unrelated refactors.
3. Add an entry to `CHANGELOG.md` under `## [Unreleased]`.
4. Make sure `./gradlew assembleDebug assembleRelease --console=plain` finishes with no errors and no warnings.

When reporting a playback or download problem, attach the app's diagnostics log if you can: it records which source answered and why a stream failed.

---

## Disclaimer

Anisu is a client application. It does not host, store or distribute any video content, and it is not affiliated with AniList, MyAnimeList, TMDB, Aniyomi or any extension or source. Content comes from third-party extensions that you choose to install.
