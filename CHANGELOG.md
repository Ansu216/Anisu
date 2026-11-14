# Changelog

All notable changes to this project are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## How this file is used

- Generated from `CHANGELOG.dev.md` by `scripts/split_changelog.py`. Never edit this file by hand: a change made here is gone at the next run.
- One line per change: the entry's head, read off the dev file. The whole story stays in `CHANGELOG.dev.md`.

## [Unreleased]

### Added

- [APK] **Adult extensions say "18+" in their description line, with the version and language.**
- [APK] **The manual source picker shows each stream's size.**

## [1.1.1] - 2026-11-14

### Added

- [APK] **An NSFW checkbox in a three-dot menu on the Extensions screen.**
- [APK] **Commits now lists the repository's real history, not just the build's SHA.**

### Changed

- [APK] **The settings top bars fade out at their bottom edge instead of ending in a hard line.**
- [APK] **Updates is called Updates, sits in the app bar, and no longer shows the package name.**
- [APK] **The download counters are back under The project, and Developer options unlock from App version.**
- [APK] **About and Playback put their titles in the app bar too.**
- [APK] **The seasonal logo wears vector art instead of emoji: a Santa hat, a witch hat and bunny ears.**
- [APK] **The update subscreen now carries the app's mark, like vivi's updater.**
- [APK] **Notification settings all live in Updates, and Developer options have a category of their own with an off switch.**
- [APK] **Build logs and scratch files live in `.ignore/`.**

### Fixed

- [APK] **The search bar is a visible pill again.**

## [1.1.0] - 2026-11-13

### Added

- [APK] **Hidden seasonal themes: Christmas, Halloween and Easter.**
- [APK] **The update has a screen of its own, and its download is separate from its install.**
- [APK] **Live status while sources load and test.**
- [APK] **Subtitle translation.**
- [APK] **Blur when a poster opens and closes, and one switch for all the frosted glass.**

### Changed
- [APK] **Update Settings is vivi-music's screen, drawn in Anisu's black glass.**
- [APK] **The search bar blurs what scrolls behind it, the way Telegram's does.**
- [APK] **The nightly APK is always a nightly, whatever `version.txt` says.**
- [APK] **Smaller APK.**
- [APK] **Casting now looks like the sketch.**
- [APK] **Updates is a page of settings rows again, with the nightly channel as a plain switch.**
- [APK] **A docs-only push no longer starts a nightly build.**
- [APK] **About is the identity page from the preview: hero, developer, contributors, support, links and the download counters.**
- [APK] **Content cards are opaque, so the animated background no longer shows through them, and Updates shows one title instead of two.**
- [APK] **About wears the real GitHub and Telegram marks, and its Links card closes on Back.**
- [APK] **Updates is laid out like vivi-music's updater, with the build commit and changelog on the page.**
- [APK] **Content cards are dark again.**
- [APK] **Playback gets the same one-title page as the rest of the settings.**
- [APK] **Autorelease builds debug and release on separate branches and publishes only the release APK.**
- [APK] **Version code moved to 2026111304.**

### Removed
- [APK] **Preload Last Episode is gone.**
- [APK] **The Halloween background animation is gone.**
- [APK] **Four settings are gone.**

### Fixed
- [APK] **A nightly published a moment ago is offered at the next check.**
- [APK] **The CloudStream pin is one JitPack actually publishes.**
- [APK] **`NiceHttp` is declared explicitly, because the library only leaks it at runtime.**
- [APK] **The JDK instruction in `CLAUDE.md` §4.1 was wrong and is corrected.**
- [APK] **Navigation bar hides while scrolling and returns when you scroll back.**
- [APK] **Smoother animations overall.**
- [APK] **Cloudstream plugin errors name the missing piece.**
- [APK] **Smoother details open and close.**
- [APK] **Glass cards, menus and sheets are as heavy as the navigation bar.**
- [APK] **The Frosted blur switch is gone; the blur is always on.**
- [APK] **The pause screen now shows every time.**
- [APK] **Chromecast search is sturdier and now tells you why it finds nothing.**
- [APK] **The thin white line through the nav bar's selector pill is fixed for good when the bar is made rounded.**
- [APK] **The Cast button now appears in the player.**
- [APK] **The build works again.**

## [1.0.3] - 2026-10-29

### Added
- [APK] **"No image" picture for titles without a wide banner.**
- [APK] **Chromecast.**
- [APK] **Orion Store badge in the README.**

### Fixed
- [APK] **No more thin white line inside the nav bar's selector pill while it moves.**

## [1.0.2] - 2026-10-28

### Added
- [APK] **Cute bread background.**
- [APK] **Seek bar styles.**

### Fixed
- [APK] **The clock no longer runs past the end of the bar.**

## [1.0.1] - 2026-10-27

### Added
- [APK] **Hold the video for 2x speed.**
- [APK] **Skip times from the stream itself.**
- [APK] **Failed extension requests are written to the diagnostics log.**

### Removed
- [APK] The unused "Skip outro" jump to the next episode that belonged to the old last-two-minutes fallback.

### Fixed
- [APK] **The number inside the skip button is centred.**
- [APK] **AniSkip is asked again with the episode length.**

## [1.0.0] - 2026-10-24

### Added
- [APK] **A one-time welcome card on first launch.**
- [APK] **A "Movies" section on a movie's page, under Comments.**
- [APK] **Deleting a download now asks first.**
- [APK] **Downloads and stars counters in About.**
- [APK] **Nightly download counter, shown only on the nightly channel.**
- [APK] **The changelog and the licence are readable inside the app.**
- [APK] **Design credits**.

### Changed
- [APK] **About links to Telegram instead of Discord.**
- [APK] **`CLAUDE.md` records the AniList query split.**
- [APK] **Clearer disclaimer and credits.**

### Fixed
- [APK] **Search finds shows even when the title is misspelled.**
- [APK] **The app logo now looks the same with a custom Android colour palette.**

## [0.5.5] - 2026-10-22

### Added
- [APK] **Screens open without a connection.**
- [APK] **Logging can be turned off.**
- [APK] **Notifications show the app's real logo.**
- [APK] **Discord link in About.**
- [APK] **Old log entries delete themselves.**

### Changed
- [APK] **The details page keeps its extra data on a separate request.**

## [0.5.4] - 2026-10-22

### Added
- [APK] **Start-up animation.**
- [APK] **Faster downloads.**
- [APK] **Download queue controls.**
- [APK] **Retry with backoff.**
- [APK] **Download next episodes.**
- [APK] **Download quality.**
- [APK] **Wi-Fi only and pause on low battery**.
- [APK] **Faster Start**.
- [APK] **Subtitles scale with the video.**
- [APK] **Auto Source setting (Settings > Player and streaming > Preferred stream).**
- [APK] **Search by source, in the filter sheet.**
- [APK] **Download progress in the notification.**
- [APK] **Buffering screen when a source is chosen.**
- [APK] **"Play manually" in the Continue Watching hold menu.**
- [APK] **Source picker when you tap an episode.**
- [APK] **"Next Up" Continue Watching cards.**
- [APK] **Episode downloads, with every subtitle.**
- [APK] **My Space redesign.**
- [APK] **"New Anime" and "New Movies" banners on Home.**
- [APK] **Optional "Swipe To Seek" gesture.**
- [APK] **YouTube-style double-tap skip feedback.**

### Changed
- [APK] **Video playback and downloads use OkHttp**.
- [APK] **The pause screen no longer resumes the video when touched.**
- [APK] **The app opens sooner.**
- [APK] **Searching inside a source is several times faster.**
- [APK] **Animations no longer rebuild the screens they sit on.**
- [APK] **The AniList response cache stays a fixed size.**
- [APK] **"Your downloads" is now the heading beside the back button.**
- [APK] **Continue Watching chip is darker and squarer.**

### Removed
- [APK] **The source catalogue rows are gone from Home.**

### Fixed
- [APK] **Notifications show the app's current logo.**
- [APK] **About and License and attribution have their title in the top bar.**
- [APK] **New error card design.**
- [APK] **"AniList is down" card now appears during a real outage.**
- [APK] **The "Buffering..." screen no longer stays up while the episode is playing.**
- [APK] **The blur is even across the whole screen.**
- [APK] **The autoplay switch lines up with the lock icon**.
- [APK] **The background animation now shows on the Search and Details pages**.
- [APK] **The account banner extends to the top of the screen**.
- [APK] **Posters at the sides and corners no longer shift when the details page opens from them.**
- [APK] **Automatic source mode no longer gives up on the first source.**
- [APK] **A downloaded (or streamed) episode no longer stops with a playback error when a subtitle track cannot load.**
- [APK] **The Extensions screen no longer crashes when you scroll through many installed sources.**
- [APK] **The source list now fills in much faster.**
- [APK] **Dubbed episodes now download with their subtitles.**
- [APK] **Downloads now save every subtitle the source offers.**
- [APK] **Less buffering during playback.**
- [APK] **A stream that never loads no longer leaves the buffering screen up forever.**
- [APK] **Downloads screen now moves live.**
- [APK] **The nav pill stays on My Space (or any tab) when Settings or Your downloads opens.**
- [APK] **A disabled source can be switched back on.**
- [APK] **The video no longer keeps playing after picture-in-picture is closed or the app is left while loading.**
- [APK] **Tapping left then right quickly no longer skips the video.**
- [APK] **"360p" in Settings > Playback no longer breaks into "3 / 6 / 0p".**
- [APK] **Preferred Language "Dub" now starts a dub.**
- [APK] **Bottom sheets (characters, trailers, comments, menus) no longer show the page sharp behind them, and no thin

## [0.5.2] - 2026-10-20

### Added
- [APK] **Sakura accent colour**.
- [APK] **Three new background animations:**

## [0.5.1] - 2026-10-20

### Added
- [APK] **Background animation**.
- [APK] **Autoplay switch in the player.**

### Changed
- [APK] Autoplay is controlled only from the player, not from Settings.

### Removed
- [APK] Haptic feedback: the vibration on taps, switches and holds, its Settings switch and the VIBRATE permission.
- [APK] YouTube from the License and attribution screen.

## [0.4.5] - 2026-10-18

### Added
- [APK] **Your own font (Settings > Appearance > Font).**
- [APK] **Ambient mode in the portrait player.**
- [APK] **Frosted glass menus and sheets.**
- [APK] **"Any" for Preferred Quality (Settings > Playback).**
- [APK] **"Your Next Watch" cards open their details with black bars.**
- [APK] **Preferred stream: quality and language, in Settings > Playback.**
- [APK] **"Your Next Watch" banner on Home.**
- [APK] **Pause screen in the player, in the style of Nuvio.**
- [APK] **Release and new-episode notifications, with an inbox.**
- [APK] **Soft haptic feedback, with an on/off switch.**
- [APK] **11 more accent colours.**
- [APK] **Each source remembers its last test.**
- [APK] **License and attribution screen**.
- [APK] **Comments on the details page, through your AniList account.**
- [APK] **A "Clear anime details cache" button on the Backup screen.**
- [APK] **Trailers now play inside the app, from YouTube's own stream, instead of YouTube's embedded player.**
- [APK] **A fix archive pushed with the wrong commit title now says so instead of passing in silence.**
- [APK] **Search suggests titles as you type, and shows the top results in your chosen layout.**
- [APK] **Anisu now backs itself up: one `.anisu` archive a day, the last three kept.**
- [APK] **Updates can empty the APKs it already downloaded, and open the changelog.**
- [APK] **The notification row in Updates says whether Android would really deliver an alert, and opens the switch.**
- [APK] **Backup has its own row in Settings, right beside System, and a screen of its own.**

### Changed
- [APK] **Resuming a show now tries the source it last played from.**
- [APK] **The app version is now 0.4.2 (version code 2026101703).**
- [APK] **The app version is now 0.4.1 (version code 2026101702).**
- [APK] **Every settings screen now has the same shape: heading outside, a box, a line between rows, a description under each title.**
- [APK] **TMDB logos, posters and backdrops load much faster.**
- [APK] **Extensions screen redesigned.**
- [APK] **Home hero banner loops endlessly.**
- [APK] **Search list mode: the whole card opens the page out of its poster.**
- [APK] **Player subtitles: one joined box.**
- [APK] **Extensions: choose which repo "Available sources" shows.**
- [APK] **Extensions: "Available sources" folds.**
- [APK] **Extensions "Test sources".**
- [APK] **Movies on the details page.**
- [APK] **Subtitle backgrounds are softer, with a "Rounded subtitle background" switch.**
- [APK] **The Trailers row lists every trailer.**
- [APK] **Subtitles have rounded corners.**
- [APK] **Subtitles are on by default, and your choice is remembered for the session.**
- [APK] **Your AniList lists are now kept cached while the app is open.**
- [APK] **Switching tabs is now a soft cross-fade instead of a slide.**
- [APK] **The details page now closes into the home banner as the banner itself.**
- [APK] **Nightly builds are named `0.3.0-nightly`, without the timestamp.**
- [APK] **Updates leads with the answer and keeps every setting one tap away.**
- [APK] **Backup is one compact card instead of three, and the long explanation folds away.**
- [APK] **A fix archive can be any format, and a push applies only the archives it brought in.**
- [APK] **The version rule now lives in one place, with every reader of the file listed.**

### Fixed
- [APK] **Poster open and close animation no longer stutters now and then.**
- [APK] **The "Add extension" / "Update sources" card no longer flashes twice when it closes.**
- [APK] **Switching tabs while one is still loading no longer leaves the old tab drawn on the new one.**
- [APK] **Nav bar: the pill now comes to a pressed tab immediately.**
- [APK] **Kohi-den repo adds work again (it moved off GitHub).**
- [APK] **Test results keep every title's failure.**
- [APK] **Extensions built for library 17 (e.g. Kohi-den's Anikoto) listed no episodes.**
- [APK] **Adding a repo from `raw.githubusercontent.com/<user>/<repo>/main/index.min.json` gave HTTP 404.**
- [APK] **Adding a repo gave "Unexpected JSON token ... �".**
- [APK] The Extensions "Test a source" episode-list step waits 45 s instead of 20 s, so large lists (One Piece) are not
- [APK] **The home banner's heart now shows a liked title as liked.**
- [APK] **"Start discussion" no longer fails with "The server rejected that request".**
- [APK] **The navigation bar's frosted blur works on My Space.**
- [APK] **Switching tabs while My Space is loading no longer leaves it drawn over another tab.**
- [APK] **Trailers that buffered and then went blank now play.**
- [APK] **Upcoming shows and movies show an English title.**
- [APK] **The News tab now shows real, current news.**
- [APK] **Switching Home, My Space and back quickly no longer leaves My Space drawn over Home with a spinner.**
- [APK] **The home banner's heart now shows whether you have liked the title.**
- [APK] **My Space cards for shows still airing now have a progress bar.**
- [APK] **My Space cards open with the poster animation wherever you tap them.**
- [APK] **Playing a title from Continue Watching now loads its episodes.**
- [APK] **Dragging a source to the top or bottom of the Streaming Priority list now scrolls the page.**
- [APK] **A manual release with no tag takes its version from `version.txt` instead of inventing a date.**
- [APK] **A tab change no longer shows the screen twice, and a fast switch no longer leaves the previous tab stuck.**
- [APK] **The nightly channel can be offered a new build again.**
- [APK] **New builds install again as a normal upgrade over an older one.**
- [APK] **Settings uses one consistent padding grid.**
- [APK] **About no longer looks like it carries two versions.**
- [APK] **Switching tabs quickly no longer leaves two screens drawn over each other.**
- [APK] **A pushed fix archive is applied again — the extraction had silently stopped running.**

## [0.1.1] - 2026-10-11

### Added
- [APK] **The last episode you were watching is pre-loaded when the app opens.**
- [APK] **Picture in picture, with a switch in Settings → Player and streaming.**
- [APK] **Episodes picker in the landscape player, with two styles.**
- [APK] **Press and hold a Continue Watching card to open its actions.**
- [APK] **Search history, with the search bar opening into a full-screen panel.**
- [APK] **Bottom navigation bar has a real frosted blur, with a switch in Appearance.**
- [APK] **My Space cards show how far through a show you are.**
- [APK] **The details page shows a season chip under the hero.**
- [APK] **English mode on the details page now shows AniList's own artwork with the plain English title on it.**
- [APK] **English mode now shows an English title logo, or the plain English title, instead of a Japanese-lettered logo.**
- [APK] **Built-in 18+ filter: no hentai in Search, and no adult sources.**
- [APK] **Subtitle offset can go negative.**
- [APK] **Details pages have a Trailers row between the synopsis and the episodes.**
- [APK] **The screen now stays on while an episode is streaming.**
- [APK] **Details pages now open out of the tapped poster and close back into it.**
- [APK] **The bottom navigation bar now moves with a held drag.**
- [APK] **The bottom bar's selection pill now slides between tabs, swells when held and can be dragged.**
- [APK] **Subtitle colours and background, with a live preview in Settings > Player and streaming.**
- [APK] **Portrait player: Sources, Subtitles and Audio open as bottom sheets with the same design as the landscape panels.**
- [APK] **Sources, Audio and Subtitles open as a small tab on the right of the fullscreen player.**
- [APK] **Subtitles panel with separate boxes.**
- [APK] **Sources panel with chips, quality/sub-dub tags and latency.**
- [APK] **Audio panel with language cards.**
- [APK] **Extensions now install inside Ansu instead of on the phone.**
- [APK] **Extension settings.**
- [APK] **Aniyomi/Keiyoushi extensions built for extension API 17 now load, and streaming follows Aniyomi's own rules.**
- [APK] **The Extensions screen can now search a repo's catalogue instead of only scrolling it.**
- [APK] **The Updates screen can post a real system notification on demand.**
- [APK] **Automatic update checks now also run in the background, so the "update available" notification can arrive while the app is closed.**
- [APK] **Pushing a file named `fix.zip` to `main` now extracts it into the repository and rebuilds the APKs.**
- [APK] **Settings > Appearance can now switch anime titles between Romaji and English.**
- [APK] **A new update is now announced both by a native Android notification and by a banner inside the app.**
- [APK] **Settings → System → Export logs produces a complete diagnostic report.**
- [APK] **A new Contributors screen credits the people behind Ansu and is read live from the repository.**
- [APK] **About and the updater are rebuilt as two focused screens.**
- [APK] **Appearance has a new Blur control for the floating bar.**
- [APK] **Anime titles now show in their own poster lettering on the home hero and the details banner.**
- [APK] **API errors are now handled in one place and shown to the user.**
- [APK] **Settings > Appearance > Navigation bar has a Frostiness slider for the floating bar's background.**
- [APK] **Search has a filter button with a full filter sheet, and can browse with no search text.**
- [APK] **Search results can be switched between a grid and a list with the button beside the search bar.**
- [APK] **Every poster card now carries a small frosted-glass age-rating chip in its top-left corner.**
- [APK] **My Space has a Sync button, and the details-page bookmark now manages your AniList lists when you are signed in.**
- [APK] **Favourites and the Watching, Planning and Completed lists are now saved on the device when you are not signed in to AniList.**
- [APK] **Long shows on the details page are split into groups of 50 episodes with selector chips.**
- [APK] **The home screen has nine catalogue rows that never run out.**
- [APK] **The diagnostics report can be saved to the device, not only shared.**
- [APK] **Full settings and library backup and restore as a single `.anisu` file.**
- [APK] **Switching between the four bottom-bar tabs now slides the screens.**
- [APK] **A nightly build now ships its changelog, and the version comes from `version.txt`.**

### Changed
- [APK] **System, Updates and About are rebuilt from scratch with a leading header and grouped cards.**
- [APK] **The bottom bar's selection pill no longer stutters when you change tab.**
- [APK] **Playback now holds a big window in RAM, the way Netflix does.**
- [APK] **The bottom navigation bar now stays still while the tabs slide.**
- [APK] **The four tabs now slide with the movement instead of against it.**
- [APK] **The version, the version code and the channel all come from `version.txt` now.**
- [APK] **The Home carousel now uses TMDB's portrait picture first, with AniList's cover as the fallback.**
- [APK] **The details banner now uses TMDB's backdrop first, with AniList as the fallback.**
- [APK] **A show's episode list now comes from AniList, not from searching your sources.**
- [APK] **AniList answers are now reused for a few minutes, and a show's small facts come in one request.**
- [APK] **Episodes load faster: each source remembers which page matched, and the search starts without waiting on AniList.**
- [APK] **The player's play/pause button turns into the loading spinner.**
- [APK] **The nav bar's selection pill stretches and squeezes like a drop of glass when it moves.**
- [APK] **Closing a details page now shrinks the whole page into the poster, the way opening grows out of it.**
- [APK] **Opening and closing a details page now morphs smoothly out of, and back into, the tapped poster.**
- [APK] **Source tests say why a source returned no videos.**
- [APK] **Player and streaming settings are grouped into separate boxes, like Appearance.**
- [APK] **The home banner now runs edge to edge behind the status bar, is taller, and fades into the page.**
- [APK] **Banner slides now glide with a parallax image and text that travels with the page.**
- [APK] **Posters and thumbnails load from a shared, longer-lived cache.**
- [APK] **The app is now named "Anisu".**
- [APK] **The launcher icon is now the project's `logo.jpg`.**
- [APK] **An APK that is not signed can no longer be published.**
- [APK] **The details page shows six episodes first, with a "More episodes" chip for the rest.**
- [APK] **The details page loads faster.**
- [APK] **The nightly build now publishes the release and the debug APK independently.**
- [APK] **A `fix.zip` is now extracted only when it is pushed by `Maygodblastyou`.**
- [APK] **`CLAUDE.md` now specifies exactly what the end-of-chat `fix.zip` must contain.**
- [APK] **`contributorsansu.json` credits `@PrussianBlueStar`.**
- [APK] **`CHANGELOG.md` is reorganized into the Keep a Changelog order.**
- [APK] **The Updates screen now says what a build contains, not only which commit it is — on both channels.**
- [APK] **Contributors is now reachable only from About.**
- [APK] **The details banner is now chosen per season and per movie, not per show.**
- [APK] **The home hero now uses AniList's own portrait poster, and title logos are cleaned up and drawn larger.**
- [APK] **Settings is reorganized into labelled sub-categories instead of one flat list.**
- [APK] **The floating bar's "Frostiness" control is now "Frostiness (Opacity)" and can reach a fully opaque bar.**
- [APK] **Screen transitions are about 50% faster.**
- [APK] **Updates are downloaded through a resumable `.part` file.**
- [APK] **The home hero now uses a sharper poster when one is available.**
- [APK] **The details page banner now fades into the page like the home hero.**
- [APK] **The details page banner is now horizontal (16:9) instead of a cropped vertical poster.**
- [APK] **The synopsis on the details page is now laid out as short paragraphs instead of one block.**
- [APK] **The home hero banner is brighter.**
- [APK] **Unreleased episodes no longer leave big blank rows on the details page.**
- [APK] **The character and staff sheet is cleaned up and laid out in paragraphs.**
- [APK] **Titles in My Space lists show year, episodes and format instead of "Details".**
- [APK] **The search bar is redesigned as a pill-shaped glass field.**
- [APK] **Search hides 18+ titles by default.**
- [APK] **Episode rows on the details page now show an "E1" badge and the air date instead of "Episode 1 - " in the title.**
- [APK] **The Schedule tab date bar is now a single pill showing each day's date.**
- [APK] **The home hero banner now starts at the very top of the screen and shows the full poster.**
- [APK] **The bottom navigation bar is now a compact, icon-only floating pill.**
- [APK] **The Extensions screen is one clean, sectioned page.**
- [APK] **The Updates screen is split into clear cards.**

### Removed
- [APK] **Addons are gone from the app.**
- [APK] **The "Show 18+ titles" switch in the Search filters is gone.**

### Fixed
- [APK] **The bottom bar's frosted blur now shows from the first frame after launch.**
- [APK] **The Schedule / News switch now matches the bottom bar.**
- [APK] **The details page now opens with its full animation.**
- [APK] **Two subtitles on screen at once no longer overlap.**
- [APK] **The player remembers where you left off, and changing source keeps your place.**
- [APK] **Holding the navigation pill now visibly expands it.**
- [APK] **The details title no longer waits on the artwork lookup in English mode.**
- [APK] **Dragging a source in Streaming Priority is smooth.**
- [APK] **The home banner no longer changes height or aspect ratio while rotating or loading.**
- [APK] **Subtitle height now works in the landscape player.**
- [APK] **Opening a side panel in the landscape player no longer keeps the controls up or closes on a tap.**
- [APK] **Sources list shows the extension and server name (e.g. "Anikoto · HD-1") instead of the stream label.**
- [APK] **The video no longer keeps playing after you leave the app.**
- [APK] **The nightly APK is built and published again.**
- [APK] **Installing a new APK no longer fails with "invalid package".**
- [APK] **The English title logo, the TMDB banner and the TMDB poster now load.**
- [APK] **The details title is never missing.**
- [APK] **Backing out of a result's details no longer empties the Search tab.**
- [APK] **The updater no longer reports "up to date" while a newer build is published — on either channel.**
- [APK] **A release now installs over a nightly instead of being refused as a downgrade.**
- [APK] **The launcher icon is drawn smaller, so it no longer overflows the edge — including the round icon.**
- [APK] **The APKs are now signed with both the v2 and the v3 signature schemes.**
- [APK] **The About screen shows the real logo instead of a solid white square.**
- [APK] **The in-app update banner is slimmer, hides itself after four seconds, and shows its countdown as a thin outline.**
- [APK] **The "Completed" chip in My List no longer wraps onto two lines.**
- [APK] **The details screen compiles again.**
- [APK] **The build is warning-free again.**
- [APK] **Episode lists of lib-16 sources (AnimeKai, Anichi and others) no longer fail with `NoSuchMethodError: get$default`.**
- [APK] **Library 16 sources that passed the test but showed no video in the player now stream the way Aniyomi streams them.**
- [APK] **Search no longer lists the same show twice.**
- [APK] **Streams that don't name their container no longer fail with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (AniWaves/Vidplay).**
- [APK] **Extensions built against OkHttp 5 (AnimeKai, Anikoto) no longer crash with `NoSuchMethodError ... CacheControl$Builder.maxAge`.**
- [APK] **The build no longer stops on two compile errors in the player and continue-watching code.**
- [APK] **The player no longer fails to compile.**
- [APK] **The build compiles again after Media3 dropped two MIME constants.**
- [APK] **The nightly updater can no longer be pointed at the debug APK.**
- [APK] **AllAnime, Anikoto and other extensions no longer fail with `NoClassDefFoundError: kotlinx/serialization/json/okio/OkioStreamsKt` or `NoSuchMethodError: CacheControl$Builder.maxAge-...`, and a failing extension no longer closes the app when you play an episode.**
- [APK] **Extensions no longer fail with "InjektionException: No registered instance or factory for type ...".**
- [APK] **Extensions no longer fail with `LinkageError: ... overrides final method`.**
- [APK] **The delete button on an installed extension now works.**
- [APK] **The OkHttp response body extension functions now compile.**
- [APK] **The build no longer fails resolving Injekt.**
- [APK] **An older build is no longer told it is up to date.**
- [APK] **The Stable update channel works again, and it can no longer hand you the debug APK.**
- [APK] **The "update available" notification is now posted reliably, and it looks like a notification.**
- [APK] **Home hero title logos are now consistent: no poster in place of a title, and no tiny logos.**
- [APK] **The details page no longer crashes when it is scrolled.**
- [APK] **The home screen can no longer crash when two shelves share a title.**
- [APK] **The app can no longer be taken down at startup by the AniList token store.**
- [APK] **Unsafe `!!` casts on AniList responses are gone.**
- [APK] **The details page lists every episode of a show instead of stopping at 12.**
- [APK] **Search now works with no extension or addon installed.**
- [APK] **Episode rows on the details page now show real titles, thumbnails and synopses.**
- [APK] **The black strip around the floating navigation bar is gone.**
- [APK] **The build compiles again.**
- [APK] **Opening the same title a second time no longer crashes the app.**
- [APK] **The build is green again.**
- [APK] **The bottom bar's selection pill no longer replays its move.**
- [APK] **Closing a details page no longer leaves a half-transparent picture floating.**
- [APK] **The Extensions screen no longer nests two scrolling areas.**

## [0.1.0] - 2026-09-28

### Added
- [APK] **The whole catalogue and library experience, built on AniList's public GraphQL
- [APK] **An extension system, so content is not baked into the app.**
- [APK] **A Stremio-protocol addon client.**
- [APK] **A home screen that mixes every source.**
- [APK] **A CornCastle-shaped details page rather than a thin poster + play.**
- [APK] **Playback with fully custom controls.**
- [APK] **A Schedule screen that also carries the news feed.**
- [APK] **Search, My Space and Appearance.**
- [APK] **Continue watching, persisted in Room.**
- [APK] **A built-in updater inside About.**
- [APK] **A dark, glassy Material 3 look.**
- [APK] **Signing that works with zero setup.**
- [APK] **Continuous integration with parallel builds.**
- [APK] **Documentation for humans and agents.**

### Changed
- [APK] **The project was rebranded to Ansu.**
- [APK] **`minSdk` raised from 24 to 26 (Android 8.0).**
- [APK] **One implementation per screen.**
- [APK] **Version metadata is injectable again.**

### Fixed
- [APK] **The build compiles again.**
- [APK] **The build is warning-free too.**
- [APK] **The floating bottom navigation bar no longer sits under the system navigation
- [APK] **The updater can no longer be broken by renaming the published APK.**
- [APK] **Stale branding in the resources.**
- [APK] **The nightly workflow actually verifies what it publishes.**

### Security
- [APK] **No credentials are stored in the repository.**
- [APK] **The AniList token is encrypted at rest, with a working fallback.**
- [APK] **An update can only ever be the same app.**
