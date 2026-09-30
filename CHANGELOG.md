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

### Added

- **Settings → System → Export logs produces a complete diagnostic report.** Ansu now keeps a local black box (`Diagnostics`) that records every crash (with its stack trace), playback event, tap, navigation move, update check and network failure, buffered for the session and appended to a rotating file so it survives a crash and a restart. The new **Settings → System** screen shows the log size and the last captured crash, exports the whole thing as one shareable text file (through the app's existing `FileProvider` and the system share sheet) together with device, build and memory details, and can clear the stored log. **Constraint:** the report is generated on-device only and is never uploaded; the user has to share it explicitly, and the log is capped at 512 KB per file with one rotated backup.

- **A new Contributors screen credits the people behind Ansu and is read live from the repository.** `contributorsansu.json` at the root of the project (`Ansu216/Anisu`) lists each person; the screen fetches it when it opens, with the copy bundled in `assets/` used only as an offline fallback, so a name can be added or fixed without shipping a new APK. Lead developer Ansuman Sahu (`@Ansu216`) is listed first, followed by `@PiBOH` (Pietro Bonaldo), who compiled and tested every release on his own PC. Tapping a card opens the person's GitHub profile.

- **About and the updater are rebuilt as two focused screens.** About now leads with the app identity and installed version, then quick actions into Updates and Contributors, the lead-developer card and the repository links. The new **Updates** screen is a proper store page: the installed build, a channel selector with an explanation, the automatic-check switch, a status card (checking / up to date / failed) and, when there is one, an update card with the release notes, a download progress bar and a one-tap *Download & install*. Both stay inside the app's dark glass theme.

- **Appearance has a new Blur control for the floating bar.** It applies a real platform blur to the bar's glass layer on Android 12+ and is ignored on Android 8–11, which have no such effect, so the bar simply keeps its frosted look there.

- **Anime titles now show in their own poster lettering on the home hero and the details banner.** Until now the title was plain white text. The new `ArtworkRepository` asks the ani.zip service (already used for episode thumbnails) for the show's title logo by AniList id and caches the answer for the session, so the carousel and the details page never ask twice. `TitleLogo` draws the logo, and falls back to the old text title when a show has no logo, the image fails to load, or the lookup fails. **Constraint:** the logo comes from ani.zip's artwork list, so a show it has no logo for keeps the text title; no logo is drawn while its lookup is still running, to avoid the text flashing before the lettering appears.

- **API errors are now handled in one place and shown to the user.** Before, every failed AniList request was swallowed (`runCatching { ... }.getOrDefault(emptyList())`), so a dead connection looked exactly like "nothing here". Now `AniListApi` throws a typed `ApiException` (offline, timeout, rate limited, server error, session expired, not found, unreadable response), `AniListRepository` passes it to the new `ApiErrorHandler`, and a snackbar at the top of the screen says what happened (e.g. "You're offline. Check your connection and try again."). The handler also logs every failure, and the same kind of error is not shown again for 10 seconds, so a screen that fires four requests does not stack four snackbars. Timeouts, dropped connections, HTTP 5xx and a short HTTP 429 wait are retried up to 3 times before giving up. **Constraint:** the favourite toggle is never retried after a timeout or server error (repeating it could flip the heart back); optional data such as episode thumbnails, watch-progress uploads and your list status fail quietly with no snackbar; HTTP 404 from ani.zip is treated as "no data", not an error.

- **Settings > Appearance > Navigation bar has a Frostiness slider for the floating bar's background.** It goes from 0% (clear glass, the page shows through) to 100% (heavily frosted, almost solid) and defaults to 50%, which is exactly the look the bar had before. The value is saved (`AppearancePrefs.navBarFrostiness`) and drives the tint opacity of the real bar through `LocalNavBarFrostiness`, and the same value feeds the Live preview, so both change while you drag. The preview now sits on colourful bands instead of a flat dark panel, because a translucent bar over a single solid colour looks identical at every setting. "Reset to default" now resets frostiness as well as roundness. **Constraint:** frostiness changes the background's opacity only; it does not blur the content behind the bar.

- **Search has a filter button with a full filter sheet, and can browse with no search text.** The button next to the search bar opens a sheet with Sort by, Format (TV, TV Short, Movie, Special, OVA, ONA, Music), Status (Airing, Finished, Upcoming, Hiatus, Cancelled), Season and year, Genres, and Tags split into collapsible categories (Demographic, Themes, Setting, Cast & characters, Sports & games), plus a "Show 18+ titles" switch. Changes apply when you tap "Show results", and a badge on the button counts the active filters; each one also shows as a removable chip under the search bar. Filters run on AniList (`AniListApi.searchMediaPage`), so picking only filters lists matching titles by popularity, and results now load 20 at a time as you scroll. **Constraint:** installed extensions cannot apply these filters, so they only add results to a plain text search with no filters on.

- **Search results can be switched between a grid and a list with the button beside the search bar.** The grid shows three posters per row with the title over the bottom of each poster. The list shows a poster on the left and, on the right, the name, episode count and year, up to three genre tags and the format. The button's icon shows the view you would switch to.

- **Every poster card now carries a small frosted-glass age-rating chip in its top-left corner.** The chip (`AgeRatingChip`) is on all `AnimeCard` posters (home rows, Search) and on both Search layouts. AniList publishes no age rating, so the text is an estimate in the MyAnimeList style from its adult flag and genres: Rx (adult flag or Hentai), R+ (Ecchi), R-17+ (Horror, Thriller, Psychological), PG-13 (Action, Drama, Mystery, Romance, Supernatural, Sci-Fi, Mecha), PG for any other title, or NR when a title has no genre data. **Constraint:** no IMDb-rating chip was added, and the hero carousel and continue-watching cards have no chip.

- **My Space has a Sync button, and the details-page bookmark now manages your AniList lists when you are signed in.** A "Sync" pill sits on the right of the "My List" header and appears only while connected to AniList. Tapping it uploads what is saved on this device: hearts you have not already given on AniList, and Watching/Planning/Completed entries (with watched-episode progress) for shows that have no list entry there yet, then refreshes the lists. It only fills gaps, so anything already on AniList is left alone and nothing is un-hearted or overwritten, and requests are spaced out to stay under AniList's rate limit. The bookmark button on the details page is no longer hidden when signed in: it shows the show's real AniList status (`mediaListEntry`) and lets you move it between Watching, Planning and Completed or remove it (`SaveMediaListEntry` / `DeleteMediaListEntry`), reverting the button if AniList rejects the change. **Constraint:** sync is one-way (device to AniList) and keeps the local copy; an entry that exists on both sides keeps AniList's status.

- **Favourites and the Watching, Planning and Completed lists are now saved on the device when you are not signed in to AniList.** Before, the heart on the details page and the home hero did nothing while signed out, and My Space just asked you to connect. Now the heart stores the show locally, and a new bookmark button next to it on the details page lets you put a show in Watching, Planning or Completed (or remove it). Playing an episode moves a show to Watching, records watched episodes, and moves it to Completed after its last known episode. My Space shows these local lists live, with a new Planning tab. The data lives in a new Room table (`local_list_entries`, database version 1 to 2 via a real migration, so continue-watching and addons are kept). **Constraint:** when signed in, lists come from AniList; only shows with an AniList id can be saved locally.

- **Long shows on the details page are split into groups of 50 episodes with selector chips.** When a show has more than 50 episodes, a row of pill chips (1-50, 51-100, 101-150, …) appears under the "Episodes" heading; the selected chip is white with dark text, the others are dark, and tapping one shows only that group. The last chip is labelled with the real final episode (e.g. 201-230). Shows with 50 or fewer episodes look as before, with no chips. **Constraint:** the Play button still starts from the first episode of the whole list, not of the selected group.

- **The home screen has nine catalogue rows that never run out.** Trending Now, Currently Airing, Trending Movies, All-Time Popular Anime, All-Time Popular Movies, Upcoming Anime, Upcoming Movies, Top Rated Anime and Top Rated Movies are all AniList queries (`AniListFeed`, `AniListApi.getMediaPage`). Each row loads ten titles at a time and fetches ten more when you scroll close to its end, showing a spinner at the tail (or a retry button if a page failed) until AniList reports no more pages. Rows other than Trending Now fetch their first page only when they scroll into view, which keeps a cold start to a few requests and stays under AniList's rate limit. Adult titles are excluded from these rows. **Constraint:** the "Top Picks For You" grid, the hero carousel and the extension/addon shelves are unchanged; the hero now reads from the first page of Trending Now.

### Changed

- **The floating bar's "Frostiness" control is now "Frostiness (Opacity)" and can reach a fully opaque bar.** The slider used to stop at a 95% tint; it now maps 0–100% to 5%–100% background opacity, so the bar can be made completely solid while its icons stay crisp. "Reset to default" also clears the new blur value.

- **Screen transitions are about 50% faster.** The navigation graph now uses explicit short fades (175 ms in, 150 ms out) instead of the platform default, which makes moving between tabs and screens noticeably snappier.

- **Updates are downloaded through a resumable `.part` file.** The updater streams the APK into `ansu-update-<version>.apk.part` and only renames it to `.apk` — and hands it to the system installer — once it is complete, resuming an interrupted download with an HTTP `Range` request when the server supports it. Stale `.part`/`.apk` files from older versions are cleaned up. **Constraint:** the APK still comes only from the official `Ansu216/Anisu` repository (`releases/latest` and the `apk-nightly` branch), so Android's signature check guarantees an update can only ever be Ansu itself.

- **The home hero now uses a sharper poster when one is available.** The hero stretched AniList's cover (its largest size is only about 460 px wide) across the whole screen width, so it looked soft. `ArtworkRepository` now also reads the portrait poster from ani.zip (`AnimeArtwork.posterUrl`, cached with the logo, no extra request) and `HeroCarousel` shows it in place of the AniList cover. **Constraint:** if ani.zip has no poster for a show, or the image fails to load, that page falls back to the AniList cover exactly as before.

- **The details page banner now fades into the page like the home hero.** The frame is 4:3 instead of 16:9 and the bottom gradient is a longer, eased fade that ends in the page colour, so there is no hard edge under the banner. The same soft top shade as on the home hero keeps the status-bar icons readable. **Constraint:** the picture choice order from the previous entry is unchanged; the taller frame crops the sides of wide art a little more.

- **The details page banner is now horizontal (16:9) instead of a cropped vertical poster.** The hero was a fixed 320dp box, and when a show had no banner it stretched the portrait poster into it, cutting off the sides. The frame is now 16:9 and picks its picture in this order: ani.zip 16:9 backdrop, the show's own banner, AniList's banner, ani.zip's wide strip, and only then the poster.

- **The synopsis on the details page is now laid out as short paragraphs instead of one block.** New `parseSynopsis` reads the AniList description (which uses `<br>`, HTML and Markdown), keeps each line as its own paragraph, splits paragraphs over about 380 characters at sentence ends, shows "- item" lines as a bulleted list under a bold lead-in line (e.g. the special-episode list), and shows "(Source: ...)" as a small credit line. Collapsed it shows the first paragraph in 4 lines; "Read More" shows everything with gaps between the parts. Before, the tags were stripped without a line break, so every paragraph ran together.

- **The home hero banner is brighter.** The artwork gets about +12% brightness, the bottom gradient is lighter in its middle (`BottomScrim` gained `midAlpha` and `midStop`, with defaults equal to the old look), and the top status-bar shade is softer. **Constraint:** the gradient still ends in the page colour, so the buttons at the bottom stay readable.

- **Unreleased episodes no longer leave big blank rows on the details page.** An episode counts as unreleased when its air date is in the future, or when it has no date and follows an unreleased one. It is now a single slim line (the E5 badge plus "Airs 2026-10-15", and its title if one is known) with no empty thumbnail box, and it cannot be tapped to play. Unreleased episodes with no announced date at all collapse into one line such as "E5-E12 · Not yet scheduled". The Play button starts from the first released episode. **Constraint:** a show whose episodes simply have no metadata and no future date is left fully playable.

- **The character and staff sheet is cleaned up and laid out in paragraphs.** AniList bios contain Markdown that was printed literally (`__Height:__`, `~!...!~` spoiler marks, `[Luffy](https://anilist.co/character/40)` links). These are now removed (a link keeps its text), bold "Label: value" lines become a tidy label/value card at the top (Height, Affiliation, Position, Devil Fruit, Bounty, ...), and the rest is split into separate paragraphs: blank lines start a new paragraph, and any paragraph over about 400 characters is broken at sentence ends. Spoiler text is still shown, just without the marks. Staff use the same layout.

- **Titles in My Space lists show year, episodes and format instead of "Details".** Each row now reads like "2021 · 13 EP · TV"; a part AniList does not know (episode count of a running show, for instance) is left out. To make the format available for shows saved on the device, the local library table gets a `format` column (database version 2 to 3 via a real migration, so nothing saved is lost); shows saved earlier fill in their format and year the next time you open their details page. **Constraint:** until then, an older saved show shows just the year and episode count.

- **The search bar is redesigned as a pill-shaped glass field.** It has a search icon, a "Search anime..." hint and a clear button while there is text, and pressing the keyboard's search key dismisses the keyboard. With nothing typed the screen shows a hint instead of an empty grid, and a failed request now says so and offers Retry instead of reading "No results found.". **Constraint:** text of one character still searches nothing on its own; two or more characters, or any filter, starts a search.

- **Search hides 18+ titles by default.** The old search asked AniList for adult titles too; it now excludes them unless "Show 18+ titles" is switched on in the filter sheet, matching the home rows, which already excluded them.

- **Episode rows on the details page now show an "E1" badge and the air date instead of "Episode 1 - " in the title.** The title line is just the episode's name (or "Untitled" when no name is known); underneath it sit a small rounded badge with the episode number (E1, E2, …) and the air date (e.g. 1999-10-20). The date comes from the ani.zip metadata already used for titles and thumbnails, so it is simply left out when that service has none. **Constraint:** the thumbnail and synopsis layout is unchanged.

- **The Schedule tab date bar is now a single pill showing each day's date.** The old row of seven separate weekday chips is replaced by one rounded pill of the next 7 days starting today, each showing the weekday over its date number (e.g. TUE / 29), with the selected day highlighted; the list below follows the chosen date. The airing query now follows AniList's pagination, since a week can hold more than the 50 episodes a single page returns. **Constraint:** paging stops after 12 pages (600 episodes) to stay within AniList's rate limit.

- **The home hero banner now starts at the very top of the screen and shows the full poster.** The frosted "Ansu" header with its search button is gone (Search already has its own tab in the navigation bar), so the hero extends up behind the status bar instead of sitting under a header. The hero frame is now exactly poster-shaped (2:3) and uses the portrait poster as its image, so the artwork fills it edge to edge with nothing cropped; before, a wide banner was cropped into a tall frame and lost most of the picture. The banner image is only used for titles that have no poster, and a soft dark gradient at the top keeps the status-bar icons readable. **Constraint:** the title, genres, View Details/Like buttons and page dots are unchanged.
- **The bottom navigation bar is now a compact, icon-only floating pill.** It used to span the screen width with a label under every icon; it now wraps four 70dp x 40dp slots with 24dp icons and no labels (content descriptions are kept for accessibility), and sits closer to the bottom edge. The current tab is marked with a translucent glass pill instead of only a tint change. **Constraint:** the Appearance roundness slider still drives both the bar and the highlight shape, and the Settings live preview uses the same composable.

### Fixed

- **The details page no longer crashes when it is scrolled.** The episode list and the character, staff, related and "More from this Show" rows key every item by its id, and AniList (and some sources) can return the same node twice, which made a `LazyColumn`/`LazyRow` throw `Key … was already used` the moment the repeated row scrolled into view. The lists are now de-duplicated (`distinctBy`) and keyed by id **plus position**, and the view model drops repeated or id-less episodes, so the page scrolls no matter what the API answers. **Constraint:** the same fix covers episodes from an extension or addon, whose ids the app does not control.

- **The home screen can no longer crash when two shelves share a title.** Shelves are keyed by title, and the same addon serving both series and movies (or an extension and an addon with the same name) produced two identical keys; the key now includes the shelf's position.

- **The app can no longer be taken down at startup by the AniList token store.** `EncryptedSharedPreferences.create` can throw on some ROMs and after a backup restore; the `runCatching` fallback to plain prefs was missing again and is restored, so `Application.onCreate` can never crash there.

- **Unsafe `!!` casts on AniList responses are gone.** The viewer, list progress/status and schedule entries are now read defensively, so a schema change or a partial payload degrades the extra data instead of throwing.

- **The details page lists every episode of a show instead of stopping at 12.** The built-in demo source returned a hard-coded 12 episodes for every title. It now asks AniList for the real count: the total for finished shows, or the number already aired for shows still running (so a show like One Piece lists all aired episodes). **Constraint:** if AniList cannot report a count, the list falls back to 12.

- **Search now works with no extension or addon installed.** `CatalogRepository.search` only asked installed sources, and the built-in demo source ran its blocking HTTP call on the main thread, which threw and was swallowed, so every search came back empty. Search now queries AniList directly and adds installed-source results on top (duplicates of an AniList hit are dropped), sources are called on `Dispatchers.IO`, and the demo source's calls are moved off the main thread. The search box also cancels the previous request on each keystroke, so old results can no longer overwrite newer ones, and clears the list when the query drops below two characters.
- **Episode rows on the details page now show real titles, thumbnails and synopses.** Every row used to show the show's own synopsis and a blank thumbnail because sources only supply episode numbers. Episode details are now looked up by AniList id (ani.zip for title, synopsis and image, AniList `streamingEpisodes` to fill gaps) and merged into the list by episode number; a row with no data simply shows only its number. **Constraint:** the list of episodes still comes from the source (the built-in demo source now sizes it from AniList's real episode count).

- **The black strip around the floating navigation bar is gone.** Each screen's `Scaffold` padded its content by the bar height, leaving an empty black band behind the transparent slot. Screens now pad only the top and let content scroll behind the bar, adding the bar height as bottom content padding so the last item and the Schedule sub-tab toggle still clear it.

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
