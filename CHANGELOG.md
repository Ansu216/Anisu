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

## [0.1.1] - 2026-10-11

### Added

- **The last episode you were watching is pre-loaded when the app opens.** A "Pre-load" switch in Settings → Player and streaming (on by default) makes Anisu resolve that show's source at launch and buffer about a minute from where you left off into a shared cache, so opening it starts already filled instead of waiting on the network.

- **Picture in picture, with a switch in Settings → Player and streaming.** With the switch on (the default), pressing Home or using the gesture while an episode is playing shrinks the video into a floating window that keeps playing; the controls and panels are hidden there, and the window has its own play/pause button.

- **Episodes picker in the landscape player, with two styles.** The Episodes button now slides in a side panel like Sources, Subtitles and Audio (the video shrinks to make room, the pill drops and the seek bar moves down, then everything returns on close).

- **Press and hold a Continue Watching card to open its actions.** The card squeezes under your finger, then floats from the row to the middle of the screen over a frosted blur of the live screen (the hero banner keeps moving behind it), with its title, episode line and a small menu: Go to details and Remove.

- **Search history, with the search bar opening into a full-screen panel.** Tapping the search bar now grows a dark panel out of the bar, with rounded corners that square off, until it reaches the edges of the screen; the magnifier turns into a back arrow, the grid and filter buttons fold away so the bar takes the whole row, and the history fades in.

- **Bottom navigation bar has a real frosted blur, with a switch in Appearance.** The old Blur slider only softened the bar's own flat tint, so it was invisible.

- **My Space cards show how far through a show you are.** Every card in My List now has "11/12" and a thin progress bar at the bottom (just "Ep 11" when the episode total is unknown).

- **The details page shows a season chip under the hero.** The chip appears only for a second or later season ("Season 2"), with the part added when the title names one ("Season 2 · Part 2", or just "Part 2" for a first season split in parts).

- **English mode on the details page now shows AniList's own artwork with the plain English title on it.** Without a TMDB key there is no source of English logos, and ani.zip's first logo was often Japanese.

- **English mode now shows an English title logo, or the plain English title, instead of a Japanese-lettered logo.** The details hero and the home carousel always used the first `clearlogo` ani.zip lists, which for many shows (Yuru Camp, for one) is the Japanese logo, whatever the Title language setting said.

- **Built-in 18+ filter: no hentai in Search, and no adult sources.** The filter is always on and has no switch.

- **Subtitle offset can go negative.** The Subtitles panel only moved subtitles later (0 to +10 s) because the player hands over a cue only when playback reaches it.

- **Details pages have a Trailers row between the synopsis and the episodes.** It lists the show's trailers and PVs (Jikan, keyed by the MyAnimeList id; AniList's own trailer is used when Jikan has none) as 16:9 thumbnails.

- **The screen now stays on while an episode is streaming.** The player never told Android to keep the display awake, so the phone's sleep timer could blank the screen mid-episode.

- **Details pages now open out of the tapped poster and close back into it.** Tapping a poster (home rows, hero banner, search grid/list, My Space, schedule, "More from this Show" and related rows) grows the details page from that poster's position to full screen with rounded window corners that square off, like an app opening from its icon; going back shrinks it into the same poster.

- **The bottom navigation bar now moves with a held drag.** While the selection pill is held, the whole bar leans up to 10dp toward the pill and swells slightly, then settles back on release.

- **The bottom bar's selection pill now slides between tabs, swells when held and can be dragged.** The pill used to jump to the tapped tab.

- **Subtitle colours and background, with a live preview in Settings > Player and streaming.** A new Subtitles box has a preview, text colour and background colour swatches, a background density slider, and size and height sliders.

- **Portrait player: Sources, Subtitles and Audio open as bottom sheets with the same design as the landscape panels.** The portrait player used plain Material sheets with a different look and extra controls the landscape panels do not have.

- **Sources, Audio and Subtitles open as a small tab on the right of the fullscreen player.** Tapping one in the pill now slides a panel in from the right (380 ms) instead of a bottom sheet.

- **Subtitles panel with separate boxes.** One box holds the on/off toggle, the stream's source and the language (tap to pick another track); offset, size and height each get their own box with a minus/plus stepper.

- **Sources panel with chips, quality/sub-dub tags and latency.** Chips are built from the extensions that answered ("All" plus one per source), each stream is a card with its resolution and Sub/Dub tag when the label has them, and a latency measured with a tiny ranged request when the panel opens.

- **Audio panel with language cards.** Every audio track is a card with its name and, when the player reports them, channels and bitrate; the playing one is outlined.

- **Extensions now install inside Ansu instead of on the phone.** Tapping Install on an available source used to download the APK and open Android's package installer, which put a separate app on the device.

- **Extension settings.** Every source that declares settings (preferred server, domain, quality, API keys; for example AnimeKai, Anikoto, AnimeOnsen) now has a gear button next to its delete button in Extensions.

- **Aniyomi/Keiyoushi extensions built for extension API 17 now load, and streaming follows Aniyomi's own rules.** Ansu accepted API 12-16 only, so every extension published against 17 was rejected as "Built for extension API 17".

- **The Extensions screen can now search a repo's catalogue instead of only scrolling it.** A search field above "Available Sources" filters as you type by extension name, source name, package or language, and shows "No extension matches …" when nothing does.

- **The Updates screen can post a real system notification on demand.** A **Test notification** row next to the automatic-check switch posts exactly the kind of alert a new build uses, so you can confirm on your own device that Ansu notifications arrive without waiting for a build to be published.

- **Automatic update checks now also run in the background, so the "update available" notification can arrive while the app is closed.** A `WorkManager` job (`UpdateCheckWorker`) re-checks roughly every 12 hours, only when there is a connection, and posts the same native Android notification the startup check already sent; it calls the same `UpdateManager` code, so the stored channel and the once-per-build rule are shared instead of duplicated.

- **Pushing a file named `fix.zip` to `main` now extracts it into the repository and rebuilds the APKs.** A new workflow (`.github/workflows/extract-fix-zip.yml`, using `.github/scripts/unpack-fix-zip.py`) runs on that push, writes every file of the archive to the path the archive itself carries (for example `app/src/main/java/com/ansu/anime/...`), through all of its folders and sub-folders, and commits the result as the Actions bot with the message *extract fix.zip and updated the app*.

- **Settings > Appearance can now switch anime titles between Romaji and English.** A new "Titles" choice (default Romaji, the old behaviour) is saved in `AppearancePrefs`.

- **A new update is now announced both by a native Android notification and by a banner inside the app.** The updater posts a system notification (channel "App updates", created on demand) the first time a check finds a newer build, and shows the same news as a compact glass banner pinned over whatever screen is open; the "Update" button in either one opens the Updates screen, and a tap on the notification lands there directly.

- **Settings → System → Export logs produces a complete diagnostic report.** Ansu now keeps a local black box (`Diagnostics`) that records every crash (with its stack trace), playback event, tap, navigation move, update check and network failure, buffered for the session and appended to a rotating file so it survives a crash and a restart.

- **A new Contributors screen credits the people behind Ansu and is read live from the repository.** `contributorsansu.json` at the root of the project (`Ansu216/Anisu`) lists each person; the screen fetches it when it opens, with the copy bundled in `assets/` used only as an offline fallback, so a name can be added or fixed without shipping a new APK.

- **About and the updater are rebuilt as two focused screens.** About now leads with the app identity and installed version, then quick actions into Updates and Contributors, the lead-developer card and the repository links.

- **Appearance has a new Blur control for the floating bar.** It applies a real platform blur to the bar's glass layer on Android 12+ and is ignored on Android 8–11, which have no such effect, so the bar simply keeps its frosted look there.

- **Anime titles now show in their own poster lettering on the home hero and the details banner.** Until now the title was plain white text.

- **API errors are now handled in one place and shown to the user.** Before, every failed AniList request was swallowed (`runCatching { ...

- **Settings > Appearance > Navigation bar has a Frostiness slider for the floating bar's background.** It goes from 0% (clear glass, the page shows through) to 100% (heavily frosted, almost solid) and defaults to 50%, which is exactly the look the bar had before.

- **Search has a filter button with a full filter sheet, and can browse with no search text.** The button next to the search bar opens a sheet with Sort by, Format (TV, TV Short, Movie, Special, OVA, ONA, Music), Status (Airing, Finished, Upcoming, Hiatus, Cancelled), Season and year, Genres, and Tags split into collapsible categories (Demographic, Themes, Setting, Cast & characters, Sports & games), plus a "Show 18+ titles" switch.

- **Search results can be switched between a grid and a list with the button beside the search bar.** The grid shows three posters per row with the title over the bottom of each poster.

- **Every poster card now carries a small frosted-glass age-rating chip in its top-left corner.** The chip (`AgeRatingChip`) is on all `AnimeCard` posters (home rows, Search) and on both Search layouts.

- **My Space has a Sync button, and the details-page bookmark now manages your AniList lists when you are signed in.** A "Sync" pill sits on the right of the "My List" header and appears only while connected to AniList.

- **Favourites and the Watching, Planning and Completed lists are now saved on the device when you are not signed in to AniList.** Before, the heart on the details page and the home hero did nothing while signed out, and My Space just asked you to connect.

- **Long shows on the details page are split into groups of 50 episodes with selector chips.** When a show has more than 50 episodes, a row of pill chips (1-50, 51-100, 101-150, …) appears under the "Episodes" heading; the selected chip is white with dark text, the others are dark, and tapping one shows only that group.

- **The home screen has nine catalogue rows that never run out.** Trending Now, Currently Airing, Trending Movies, All-Time Popular Anime, All-Time Popular Movies, Upcoming Anime, Upcoming Movies, Top Rated Anime and Top Rated Movies are all AniList queries (`AniListFeed`, `AniListApi.getMediaPage`).

- **The diagnostics report can be saved to the device, not only shared.** Settings → System has a new **Save logs to device** button that opens the system file picker and writes the report wherever you choose (Downloads, Drive, …), next to the existing Share button.

- **Full settings and library backup and restore as a single `.anisu` file.** Settings → System can write everything Anisu keeps on the device — appearance, player and streaming settings, search history, update settings, the added extension repos, the switched-off sources, and the Room database with your local library and Continue Watching — into one `.anisu` archive, and restore it on the same or a new install.

- **Switching between the four bottom-bar tabs now slides the screens.** Home, Search, Schedule and My Space move in from the side they sit on instead of a plain fade, so the change of tab has a direction.

- **A nightly build now ships its changelog, and the version comes from `version.txt`.** `version.txt` in the repository root is the single source of truth for the version; a nightly build is `<base>-nightly-<stamp>` (for example `0.1.0-nightly-2026.10.11.0930`).

### Changed

- **The bottom bar's selection pill no longer stutters when you change tab.** The bar read the pill's spring values while it composed itself, so the whole bar rebuilt on every animation frame; those values are now read only while drawing, which keeps the move smooth.

- **Playback now holds a big window in RAM, the way Netflix does.** Up to about five minutes ahead and five behind are kept in memory, bounded by a memory cap, so seeking back and resuming are instant instead of re-loading from the source.

- **The bottom navigation bar now stays still while the tabs slide.** The four tab screens each drew their own copy of the bar inside the screen, so a tab change carried the bar along with the page.

- **The four tabs now slide with the movement instead of against it.** Going right (Home → Search → Schedule → My Space) the new screen comes in from the left and the old one leaves to the right, and going left everything mirrors, so the page follows the direction of the swipe or of the dragged pill.

- **The version, the version code and the channel all come from `version.txt` now.** The file carries three value lines in the same shape as vivi-music-de — line 1 version, line 2 version code, line 3 channel, with `#` lines ignored — and `app/build.gradle.kts`, `apk-nightly.yml` and `release-apk.yml` all read it as their single source: the channel decides how a build is named (`nightly` gives `<version>-nightly-<YYYY.MM.DD.HHMM>`, anything else the plain `<version>`), and the version code is a plain build number shared by both channels and bumped by one per release, instead of being derived from the clock, the date or the workflow run number.

- **The Home carousel now uses TMDB's portrait picture first, with AniList's cover as the fallback.** TMDB has no portrait backdrops (its backdrops are all 16:9, which the tall pager would crop to a thin slice), so the carousel uses TMDB's portrait poster instead, a textless one when TMDB has it and otherwise the English poster, taken from the same TMDB lookup as the logo and backdrop.

- **The details banner now uses TMDB's backdrop first, with AniList as the fallback.** The hero used ani.zip's backdrop, then AniList's banner.

- **A show's episode list now comes from AniList, not from searching your sources.** The details page used to search every installed source and show whatever they returned, which was the slow part and made the list depend on each source's naming.

- **AniList answers are now reused for a few minutes, and a show's small facts come in one request.** Every visit used to send the same queries again: opening a details page sent four separate requests for the same show (details, MAL id, alternate titles, episode titles), and My Space sent seven (favourites plus one per list status).

- **Episodes load faster: each source remembers which page matched, and the search starts without waiting on AniList.** Every details page searched every installed source with up to five title variants each, waited for the slowest source (up to 12 seconds), and only started after an alternate-titles request had come back.

- **The player's play/pause button turns into the loading spinner.** Buffering and source loading used to show a stock circular spinner on top of the controls.

- **The nav bar's selection pill stretches and squeezes like a drop of glass when it moves.** The pill used to slide as one rigid block.

- **Closing a details page now shrinks the whole page into the poster, the way opening grows out of it.** The close only cropped the page with a shrinking frame, so the content stayed full size and was cut off at the sides.

- **Opening and closing a details page now morphs smoothly out of, and back into, the tapped poster.** The old animation stretched the whole page from the poster's rectangle with different horizontal and vertical scales, so the page looked squashed mid-flight and every frame redrew a full-screen layer with a changing clip and alpha.

- **Source tests say why a source returned no videos.** A source whose catalogue loads but whose streams come back empty (Toonstream) only reported "no_hoster_list: no playable video".

- **Player and streaming settings are grouped into separate boxes, like Appearance.** The screen was one long list of headings.

- **The home banner now runs edge to edge behind the status bar, is taller, and fades into the page.** The list under the banner was padded by the status-bar height, which left a black strip above it.

- **Banner slides now glide with a parallax image and text that travels with the page.** The title, genres and buttons used to swap instantly while only the poster slid.

- **Posters and thumbnails load from a shared, longer-lived cache.** The app now configures one image loader with a 200 MB disk cache and a 20% memory cache, and reuses a cached image without asking the server whether it changed.

- **The app is now named "Anisu".** The launcher label and every user-visible string inside the app — About, Updates, the update notification and banner, the diagnostics report, the login screen, the contributors screen, the extensions screens and the extension-load error messages — now say *Anisu* instead of *Ansu*.

- **The launcher icon is now the project's `logo.jpg`.** The image is installed at every density for the legacy icon and its round variant, and as the adaptive-icon foreground on Android 8+, so the home screen shows the logo instead of the old vector play mark.

- **An APK that is not signed can no longer be published.** Both workflows already signed every build; the signature is now also asserted with an explicit `::error::` before a build is uploaded (it must verify and carry a signer certificate) and again in every publishing job — the two nightly branch jobs and the GitHub Release job — right before anything is written out.

- **The details page shows six episodes first, with a "More episodes" chip for the rest.** A show with 50 episodes used to draw all of them at once.

- **The details page loads faster.** Everything on it used to wait for the slowest part, the title search across every installed source, so the synopsis, stats and episode list all appeared together and only after the slowest source answered or timed out.

- **The nightly build now publishes the release and the debug APK independently.** `apk-nightly.yml` splits publishing into `publish-release` and `publish-debug`: each runs as soon as its own build leg produced an APK, so a failure on one side no longer holds back the other.

- **A `fix.zip` is now extracted only when it is pushed by `Maygodblastyou`.** `extract-fix-zip.yml` is guarded on `github.actor == 'Maygodblastyou'`, so any other pusher — a collaborator, a fork or the Actions bot — is skipped and cannot rewrite the project through an archive.

- **`CLAUDE.md` now specifies exactly what the end-of-chat `fix.zip` must contain.** The archive carries `app/` (without its `build/` folder), `gradle/`, `keystore/`, `.gitignore`, `AGENTS.md`, `CHANGELOG.md`, `gradle.properties`, the root `build.gradle.kts`, `gradlew`, `gradlew.bat`, `how-to-compile.md`, `local.properties`, `README.md` and `settings.gradle.kts`, and nothing else; any additional documentation written during a chat goes in `docs/`.

- **`contributorsansu.json` credits `@PrussianBlueStar`.** The new entry names them an amazing app tester and the creator of Ansu's new logo; because the Contributors screen reads the file live, no new APK is needed for it to show.

- **`CHANGELOG.md` is reorganized into the Keep a Changelog order.** The `[Unreleased]` section had grown duplicate and out-of-order `### Added`, `### Changed` and `### Fixed` blocks; each category now appears once, in the order Added → Changed → Fixed, with every entry kept verbatim.

- **The Updates screen now says what a build contains, not only which commit it is — on both channels.** The Nightly channel reads the commit a build was made from out of `nightly.json` and showed nothing but its short SHA; the Stable channel's release notes carried the same bare SHA on their "built automatically from commit …" line.

- **Contributors is now reachable only from About.** The duplicate row under Settings → Info is gone, so the screen the About page already links to is the single way in.

- **The details banner is now chosen per season and per movie, not per show.** ani.zip's backdrop describes the whole series, so every season of a show (and its movies) opened with the same banner.

- **The home hero now uses AniList's own portrait poster, and title logos are cleaned up and drawn larger.** The hero no longer swaps in the ani.zip poster; it shows AniList's `extraLarge` cover (the banner stays only as a fallback).

- **Settings is reorganized into labelled sub-categories instead of one flat list.** The rows now sit in frosted-glass group cards under clear section labels — *Account* (the AniList profile and Connect/Log out), *Personalization* (Appearance), *Content & sources* (Extensions, Addons), *App (Updates, System)* and *Info* (Contributors, About) — so a setting is found by the kind of thing it changes rather than by scanning every row.

- **The floating bar's "Frostiness" control is now "Frostiness (Opacity)" and can reach a fully opaque bar.** The slider used to stop at a 95% tint; it now maps 0–100% to 5%–100% background opacity, so the bar can be made completely solid while its icons stay crisp.

- **Screen transitions are about 50% faster.** The navigation graph now uses explicit short fades (175 ms in, 150 ms out) instead of the platform default, which makes moving between tabs and screens noticeably snappier.

- **Updates are downloaded through a resumable `.part` file.** The updater streams the APK into `ansu-update-<version>.apk.part` and only renames it to `.apk` — and hands it to the system installer — once it is complete, resuming an interrupted download with an HTTP `Range` request when the server supports it.

- **The home hero now uses a sharper poster when one is available.** The hero stretched AniList's cover (its largest size is only about 460 px wide) across the whole screen width, so it looked soft.

- **The details page banner now fades into the page like the home hero.** The frame is 4:3 instead of 16:9 and the bottom gradient is a longer, eased fade that ends in the page colour, so there is no hard edge under the banner.

- **The details page banner is now horizontal (16:9) instead of a cropped vertical poster.** The hero was a fixed 320dp box, and when a show had no banner it stretched the portrait poster into it, cutting off the sides.

- **The synopsis on the details page is now laid out as short paragraphs instead of one block.** New `parseSynopsis` reads the AniList description (which uses `<br>`, HTML and Markdown), keeps each line as its own paragraph, splits paragraphs over about 380 characters at sentence ends, shows "- item" lines as a bulleted list under a bold lead-in line (e.g.

- **The home hero banner is brighter.** The artwork gets about +12% brightness, the bottom gradient is lighter in its middle (`BottomScrim` gained `midAlpha` and `midStop`, with defaults equal to the old look), and the top status-bar shade is softer.

- **Unreleased episodes no longer leave big blank rows on the details page.** An episode counts as unreleased when its air date is in the future, or when it has no date and follows an unreleased one.

- **The character and staff sheet is cleaned up and laid out in paragraphs.** AniList bios contain Markdown that was printed literally (`__Height:__`, `~!...!~` spoiler marks, `[Luffy](https://anilist.co/character/40)` links).

- **Titles in My Space lists show year, episodes and format instead of "Details".** Each row now reads like "2021 · 13 EP · TV"; a part AniList does not know (episode count of a running show, for instance) is left out.

- **The search bar is redesigned as a pill-shaped glass field.** It has a search icon, a "Search anime..." hint and a clear button while there is text, and pressing the keyboard's search key dismisses the keyboard.

- **Search hides 18+ titles by default.** The old search asked AniList for adult titles too; it now excludes them unless "Show 18+ titles" is switched on in the filter sheet, matching the home rows, which already excluded them.

- **Episode rows on the details page now show an "E1" badge and the air date instead of "Episode 1 - " in the title.** The title line is just the episode's name (or "Untitled" when no name is known); underneath it sit a small rounded badge with the episode number (E1, E2, …) and the air date (e.g.

- **The Schedule tab date bar is now a single pill showing each day's date.** The old row of seven separate weekday chips is replaced by one rounded pill of the next 7 days starting today, each showing the weekday over its date number (e.g.

- **The home hero banner now starts at the very top of the screen and shows the full poster.** The frosted "Ansu" header with its search button is gone (Search already has its own tab in the navigation bar), so the hero extends up behind the status bar instead of sitting under a header.

- **The bottom navigation bar is now a compact, icon-only floating pill.** It used to span the screen width with a label under every icon; it now wraps four 70dp x 40dp slots with 24dp icons and no labels (content descriptions are kept for accessibility), and sits closer to the bottom edge.

- **The Extensions screen is one clean, sectioned page.** Repositories, Available sources, Installed sources and Test source each sit in their own bordered card with a heading, the repository manager moved to a top-bar button, and the two lists no longer scroll inside the page.

- **The Updates screen is split into clear cards.** Installed build, Channel, Automatic checks and Status are now separate frosted cards instead of one long column, with the same channel switch, automatic-check toggle, test notification and download/install flow as before.

### Removed

- **Addons are gone from the app.** The Stremio/Nuvio-protocol addon client, its Settings → Addons screen and route, its entry on the Extensions screen and in the source tester, the addon shelves on Home and the addon branches of the details and player view models were deleted, along with `MediaOrigin.Addon`, the `addon/` package and the Retrofit dependency that only the addon client used.

- **The "Show 18+ titles" switch in the Search filters is gone.** Adult titles are now always hidden by the built-in filter, so the switch and its "18+ shown" chip no longer exist.

### Fixed

- **The bottom bar's frosted blur now shows from the first frame after launch.** The bar drew its blur from layout positions that were not observed, so if the bar was drawn before the screen behind it had been laid out (always the case at startup) it stayed unblurred until something else, like a scroll, redrew it.

- **The Schedule / News switch now matches the bottom bar.** It was a separate flat glass pill; it now uses the nav bar's roundness, frostiness and live backdrop blur from Appearance, has the same glass indicator that slides between the two options (stretching as it travels, with the accent glow) and swells while held.

- **The details page now opens with its full animation.** Composing the page on the first frame used up part of the animation's clock, so the window appeared already half open, while the close (nothing to compose) played fully.

- **Two subtitles on screen at once no longer overlap.** Every text cue was forced onto the same line, so a character's dialogue and a translated on-screen sign were drawn on top of each other.

- **The player remembers where you left off, and changing source keeps your place.** The saved position was applied with a seek before the stream was loaded, which the player discards, so Continue Watching always started at 0:00; changing source also restarted the episode.

- **Holding the navigation pill now visibly expands it.** The pill was drawn inside the bar's clipped glass, so its growth was cut off at the bar's edge.

- **The details title no longer waits on the artwork lookup in English mode.** The text title was only drawn once the ani.zip/TMDB lookup had finished, so a slow or failed lookup left the hero with no title at all.

- **Dragging a source in Streaming Priority is smooth.** Each row was rebuilt by position, so swapping two rows restarted the drag gesture in the middle of the drag and the row stuttered or lost your finger.

- **The home banner no longer changes height or aspect ratio while rotating or loading.** Its height followed whatever the current poster's image size was, so it jumped between slides and while images loaded.

- **Subtitle height now works in the landscape player.** Subtitle formats with their own line positions made the player ignore the height setting; it is now applied to every text cue.

- **Opening a side panel in the landscape player no longer keeps the controls up or closes on a tap.** The controls hide after 3 seconds as usual, and tapping the shrunken video no longer closes the panel (the close button and Back still do).

- **Sources list shows the extension and server name (e.g. "Anikoto · HD-1") instead of the stream label.** The card title used to repeat the quality and Sub/Dub text that the chips below already show, and the picked stream lost its extension name when it was resolved, so it showed the full label while the others showed only "Anikoto".

- **The video no longer keeps playing after you leave the app.** Pressing Home, switching apps or locking the screen left the stream playing in the background.

- **The nightly APK is built and published again.** The `plan` job of `apk-nightly.yml` read `version.txt` without checking the repository out first, so every run since the version file landed died on `version.txt: No such file or directory` before a single APK was built.

- **Installing a new APK no longer fails with "invalid package".** Two causes: the version code could go *down* (a locally built APK carried `1` and a tagged release carried the workflow run number, both far below the date-coded `2026101113` the nightly workflow published, and Android refuses to install over a higher code), and a download that was cut off mid-transfer was still renamed to `.apk` and handed to the installer.

- **The English title logo, the TMDB banner and the TMDB poster now load.** ani.zip reports the TMDB id as a *string* (`"37854"`), and the lookup read it as a number, so it was null for every show and TMDB was never asked at all: no English title logo, no textless backdrop and no TMDB poster ever appeared.

- **The details title is never missing.** The plain title was drawn only once the artwork lookup had finished, so a title logo that was slow, stalled or rejected left the hero with no title at all.

- **Backing out of a result's details no longer empties the Search tab.** The query, filters, results, paging and scroll position were plain screen state, so opening a result threw them away and the tab came back as the trending list.

- **The updater no longer reports "up to date" while a newer build is published — on either channel.** The **Nightly** channel compared only the manifest's `versionCode`, which the nightly workflow derives from the *hour* (`YYYYMMDDHH`); two builds in the same hour therefore shared a number and the second was never offered.

- **A release now installs over a nightly instead of being refused as a downgrade.** The nightly workflow numbers its builds by date (`versionCode` = `YYYYMMDDHH`, e.g.

- **The launcher icon is drawn smaller, so it no longer overflows the edge — including the round icon.** The logo filled 0.81 of the canvas radius, past the ~0.66 safe zone of an adaptive icon, so on a round or squircle mask its outer ring was clipped.

- **The APKs are now signed with both the v2 and the v3 signature schemes.** Every build was already signed (v2) with the committed `keystore/anisu.jks`, so Android could install it; v3 is added for the modern key-rotation path and for installers that prefer it.

- **The About screen shows the real logo instead of a solid white square.** The identity card drew the launcher logo with `Icon(...)`, which paints a single tint colour over the whole bitmap; since the logo is an opaque full-colour raster, the tint covered it entirely and left a plain block.

- **The in-app update banner is slimmer, hides itself after four seconds, and shows its countdown as a thin outline.** It used to stay pinned at the top of whatever screen was open until dismissed by hand.

- **The "Completed" chip in My List no longer wraps onto two lines.** On narrower screens the four chips (Liked, Watching, Planning, Completed) shared one row and the last label broke into "Complete" and "d".

- **The details screen compiles again.** `DetailsViewModel` called the cross-source `findEpisodesByTitle` helper without importing it, so `compileDebugKotlin` and `compileReleaseKotlin` both failed with `Unresolved reference 'findEpisodesByTitle'` and the follow-on type-inference errors.

- **The build is warning-free again.** `CloudflareInterceptor` set the long-deprecated `WebSettings.databaseEnabled`, which has been a no-op since API 19 (the Web SQL Database API was removed) and produced a compiler warning on every build.

- **Episode lists of lib-16 sources (AnimeKai, Anichi and others) no longer fail with `NoSuchMethodError: get$default`.** Catalogue and search worked but opening an anime failed because the extensions call the suspend `OkHttpClient.get(url, headers, cache)` helper from `eu.kanade.tachiyomi.network.Requests`, which Ansu did not provide.

- **Library 16 sources that passed the test but showed no video in the player now stream the way Aniyomi streams them.** The Test button only asked a source for its popular list, so a green result said nothing about hosters or videos; and the player's stream path differed from Aniyomi's in three ways that each made a library 16 source come back empty.

- **Search no longer lists the same show twice.** A result straight from an extension (the "NR" cards) was shown next to the catalogue entry for the same title, and only the catalogue entry gathers streams from every installed source.

- **Streams that don't name their container no longer fail with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (AniWaves/Vidplay).** The player guessed the container from the URL and forced MP4 on any address containing "cdn", "stream", "video" or "media", so an HLS stream on such a URL was rejected.

- **Extensions built against OkHttp 5 (AnimeKai, Anikoto) no longer crash with `NoSuchMethodError ... CacheControl$Builder.maxAge`.** Ansu shipped OkHttp 4.12.0 while Aniyomi's extensions are compiled against 5.x, which has the `maxAge(Duration)` call they use.

- **The build no longer stops on two compile errors in the player and continue-watching code.** `ContinueWatchingRepository` took an `ExtensionManager` it never used, so `AppContainer` passed its arguments one place off, and `PlayerViewModel` called `findEpisodesByTitle` without importing it.

- **The player no longer fails to compile.** `PlayerViewModel.loadSources` declared `val anime` twice and called `ContinueWatchingRepository.cacheAnime`, a function that does not exist; the duplicate declaration and the call are removed, and continue-watching still saves progress through `updateProgress` as before.

- **The build compiles again after Media3 dropped two MIME constants.** `PlayerViewModel.detectMimeType` called `MimeTypes.VIDEO_QUICKTIME` and `MimeTypes.VIDEO_3GPP`, which do not exist in the Media3 version the app ships, so `compileDebugKotlin` failed with two `Unresolved reference` errors and both CI legs failed.

- **The nightly updater can no longer be pointed at the debug APK.** `UpdateChecker` refuses any `nightly.json` name that mentions `debug` and falls back to the published release name (`Ansu-nightly.apk`), so the Nightly channel always offers the minified release build, exactly as the Stable channel already does.

- **AllAnime, Anikoto and other extensions no longer fail with `NoClassDefFoundError: kotlinx/serialization/json/okio/OkioStreamsKt` or `NoSuchMethodError: CacheControl$Builder.maxAge-...`, and a failing extension no longer closes the app when you play an episode.** Extensions use the okio flavour of kotlinx.serialization (`decodeFromStream`) and OkHttp 5's `maxAge(Duration)`, and bundle neither.

- **Extensions no longer fail with "InjektionException: No registered instance or factory for type ...".** Extensions look up `Application` and `NetworkHelper` through Injekt, which builds its lookup key from the generic signature of an anonymous `TypeReference`.

- **Extensions no longer fail with `LinkageError: ... overrides final method`.** The release build's keep rules for `eu.kanade.tachiyomi.**` (and the other libraries extensions use) were `-keep,allowoptimization`, which lets R8 mark methods `final` when nothing inside Ansu overrides them.

- **The delete button on an installed extension now works.** `ExtensionManager.uninstall` sends `ACTION_DELETE`, which Android ignores for apps that do not declare `REQUEST_DELETE_PACKAGES`; the permission is now in the manifest, so the system uninstall dialog appears.

- **The OkHttp response body extension functions now compile.** `OkHttpExtensions.kt` and `JsoupExtensions.kt` called `.body.string()` on nullable `okhttp3.ResponseBody?` without asserting non-null, so Kotlin rejected them.

- **The build no longer fails resolving Injekt.** `com.github.inorichi.injekt:injekt-core:65b0440` does not exist.

- **An older build is no longer told it is up to date.** The version comparison kept only the numeric components of a version, so `0.1.0-nightly` collapsed to `0.1.0` and compared equal to the released `0.1.0`: an install older than the published build was answered "up to date" and never offered the new one.

- **The Stable update channel works again, and it can no longer hand you the debug APK.** `releases/latest` deliberately ignores pre-releases and every build published so far is one, so the GitHub API answered 404 and the Stable channel reported "no releases published yet" while the builds were sitting right there; `UpdateChecker` now falls back to the full release list and takes its newest non-draft entry.

- **The "update available" notification is now posted reliably, and it looks like a notification.** `UpdateNotifier.notifyUpdate` reports whether the system really accepted the post, and `UpdateManager` only records a build as announced when it did; before, a check that finished before you granted the Android 13 `POST_NOTIFICATIONS` permission was marked as announced anyway, so that build was never announced again — and since the permission is asked for at the same moment as the startup check, that was the normal case on a fresh install.

- **Home hero title logos are now consistent: no poster in place of a title, and no tiny logos.** Some hero titles showed a small poster thumbnail where the lettering should be, and others rendered very small.

- **The details page no longer crashes when it is scrolled.** The episode list and the character, staff, related and "More from this Show" rows key every item by its id, and AniList (and some sources) can return the same node twice, which made a `LazyColumn`/`LazyRow` throw `Key … was already used` the moment the repeated row scrolled into view.

- **The home screen can no longer crash when two shelves share a title.** Shelves are keyed by title, and the same addon serving both series and movies (or an extension and an addon with the same name) produced two identical keys; the key now includes the shelf's position.

- **The app can no longer be taken down at startup by the AniList token store.** `EncryptedSharedPreferences.create` can throw on some ROMs and after a backup restore; the `runCatching` fallback to plain prefs was missing again and is restored, so `Application.onCreate` can never crash there.

- **Unsafe `!!` casts on AniList responses are gone.** The viewer, list progress/status and schedule entries are now read defensively, so a schema change or a partial payload degrades the extra data instead of throwing.

- **The details page lists every episode of a show instead of stopping at 12.** The built-in demo source returned a hard-coded 12 episodes for every title.

- **Search now works with no extension or addon installed.** `CatalogRepository.search` only asked installed sources, and the built-in demo source ran its blocking HTTP call on the main thread, which threw and was swallowed, so every search came back empty.

- **Episode rows on the details page now show real titles, thumbnails and synopses.** Every row used to show the show's own synopsis and a blank thumbnail because sources only supply episode numbers.

- **The black strip around the floating navigation bar is gone.** Each screen's `Scaffold` padded its content by the bar height, leaving an empty black band behind the transparent slot.

- **The build compiles again.** The Appearance screen's colour preview called a `Modifier.paddingTop` that does not exist, which failed both the debug and release compile; it now uses `Modifier.padding(top = 8.dp)`.

- **Opening the same title a second time no longer crashes the app.** The details screen's `rawEpisodes` and `episodeMeta` were declared below the `init` block, so the episode list coming from the cache was published before those fields existed (a null `map` crash), and they were then reset to empty once the constructor finished.

- **The build is green again.** `DetailsViewModel` passed the per-source progress callback as a trailing lambda after naming the last parameter, so Kotlin bound it to the wrong argument and failed both variants with *Too many arguments* and the follow-on *Suspension functions can only be called within coroutine body*.

- **The bottom bar's selection pill no longer replays its move.** Tapping a tab animated the pill in the gesture handler and then again when the destination's own bar took over from the remembered position, so the pill slid twice.

- **Closing a details page no longer leaves a half-transparent picture floating.** During the close the shrinking page faded out before the tapped poster had faded back in, so part of the animation showed neither clearly.

- **The Extensions screen no longer nests two scrolling areas.** The Available and Installed lists scrolled inside the page that already scrolls, which fought the finger and could stall the list.

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
