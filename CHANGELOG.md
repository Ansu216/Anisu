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

## [1.0.0] - 2026-10-24

### Added
- **A one-time welcome card on first launch.** After the start-up animation, a frosted card in the style of the error card greets the person with the logo on a slowly rotating Material shape, a short welcome message a "Join us on Telegram" button and a sign-off from the Anisu Team, while the app behind it is blurred. It closes with its button and is never shown again.
- **A "Movies" section on a movie's page, under Comments.** Movies used to show only the "Play movie" button. Now a "Movies" header sits under Comments with a row laid out like an episode, minus the episode badge: the thumbnail, the movie's name, its release date and a short description. A tap plays the movie, and holding it opens the same menu as an episode (download, or play with the source picker / on auto). The release date comes from AniList's full start date, so the details request now asks for the month and day too.
- **Deleting a download now asks first.** Tapping the bin on the Downloads screen opens a small confirmation card with Cancel and Delete, so a stray tap no longer removes an episode.
- **Downloads and stars counters in About.** A new "The project" group shows how many times Anisu's APKs have been downloaded — every asset of every published release, summed — and how many stars the repository has, both read live from the public GitHub API the updater already uses. Each row opens the page it counts. The numbers count up when they arrive, `…` marks the wait and `—` a lookup that failed, so GitHub being unreachable never reads as an empty project. **Constraint:** asked once per app run, both because the answer does not change while the app is open and to stay well inside GitHub's unauthenticated rate limit.
- **Nightly download counter, shown only on the nightly channel.** GitHub counts downloads only for release assets, and nightly APKs live on the `apk-nightly` branch, where the API exposes no counter at all — so the number did not exist. Anisu now counts its own: every nightly update the app downloads adds one to a free public counter, and About reads the total back into a "Nightly Downloads" row under Downloads. It is anonymous — no id, no device, no session, just one number — and best-effort in both directions: a counter that cannot be reached shows `—` instead of failing the screen, and a download that cannot be recorded still installs. The row exists only in a nightly APK, because which build you are running comes from the channel line of `version.txt`, baked into the APK when it is built: switching to the nightly channel inside a stable install changes what the app looks for, it does not make that install a nightly. **Constraint:** it counts the nightly downloads made through the app, which is the path the nightly channel exists for; an APK fetched by hand from the branch cannot be counted.
- **The changelog and the licence are readable inside the app.** Two new rows in About open the project's own `CHANGELOG.md` and `LICENSE`, read live from the repository exactly the way the Contributors screen reads its list, with the copy from the last launch served when there is no connection and a refresh button for a forced re-read. The changelog is drawn with the few Markdown rules the file uses (headings, bullets, bold) instead of pulling in a Markdown engine, a bullet written over several lines is joined back into one bullet rather than leaving its second half as a paragraph of its own, and the licence, which is wrapped at 70-odd columns in the file, is joined back into paragraphs so the screen does the wrapping instead of drawing one short line per block. **Constraint:** no bundled copy — the text is always the one the repository publishes, so a note edited on GitHub shows up without a new APK.
- **Design credits** on the License and attribution screen: Nuvio for the animations and navigation bar, CornCastle for the details and schedule screens. Both cards link to their source, and the README credits them too.

### Changed
- **About links to Telegram instead of Discord.** The row now opens https://t.me/anisu_anime_app, with Telegram's own logo on it instead of a generic chat-bubble icon.
- **`CLAUDE.md` records the AniList query split.** The details query is the heavy one (characters, staff, relations, recommendations) and stays on the details page only; episodes, the player and the source search keep their own lighter requests. The rule is written down so a later change does not move episode fields into the details query and put the heavy answer back on the path to streaming.
- **Clearer disclaimer and credits.** The README and the License screen now say plainly that Anisu is free and non-commercial, hosts no video and ships no sources, credit Aniyomi and Tachiyomi (Apache 2.0) and AniList, and the add-repository box tells people Anisu provides no repos.

### Fixed
- **Search finds shows even when the title is misspelled.** The typo tolerance only ranked titles the app had already shown, so a mistyped title ("narto", "shingeki no kyojn") that AniList could not match returned nothing. When the first page has no close title, the search now retries with two shortened forms of what was typed, ranks everything it finds by how close it is to what was typed, and shows the close ones first.
- **The app logo now looks the same with a custom Android colour palette.** The launcher icon declared a monochrome layer, so with themed icons switched on Android drew only a tinted letter A instead of the logo. The layer is removed from both launcher icons, so the real logo shows in every palette. **Constraint:** the icon is no longer tinted by the system theme.

## [0.5.5] - 2026-10-22

### Added
- **Screens open without a connection.** Every answer AniList gives is now also kept on the device (in `files/anilist-cache`, one small file per request, at most 300 answers, dropped after 7 days), not only in memory for as long as the app runs. Home, search, the details page and the lists therefore still open with the last content they had when the phone is offline or AniList cannot be reached at all, and a small "Showing saved data · no connection" bar says so until a request works again. The bar disappears by itself the moment AniList answers. **Constraint:** a saved answer is only used for a request the app would cache anyway (feeds, details, search, lists); a mutation is never replayed from the cache.
- **Logging can be turned off.** Settings › System has a "Keep logs" switch. It is on by default; switched off, nothing new is written to the device (a crash is still recorded so the next launch can say what ended the last one), and the choice survives a restart. The in-memory session log is cleared when it is switched off.
- **Notifications show the app's real logo.** Android draws a notification's header with the icon it has cached for the package, and with Material You theming that icon is recoloured into the wallpaper palette (a red badge with a dark A, while the app still shows the dark logo). Every notification — downloads, update and new episode — now sets the app logo itself as its large icon, so the shade shows the same mark as the app whatever the system icon theme does. The adaptive icon also declares a monochrome layer now, so a themed launcher uses the A itself instead of a shape generated from the artwork.
- **Discord link in About.** The Links group now has a Discord row (`discord.gg/Qhn6B76Z3`), next to the repository and the issue tracker.
- **Old log entries delete themselves.** At every launch, lines older than 7 days are removed from both diagnostics files, together with their stack traces, so the report never carries weeks of history.

### Changed
- **The details page keeps its extra data on a separate request.** The MyAnimeList id (trailers) and the show's other titles (source search) are fetched by their own small query, in parallel with the details, exactly as in 0.5.4. Folding them into the details answer saved one request, but it put the heavy answer on the path the player needs; the split is the rule now (see `CLAUDE.md`, the AniList query split), so the details query stays heavy and everything around it asks what it needs. The page still writes one log line with the elapsed time and how many AniList requests the session has sent, so the cost of opening it can be read from an exported report.

## [0.5.4] - 2026-10-22

### Fixed
- **Notifications show the app's current logo.** The small icon every notification uses (downloads, updates, new releases) was still the old ring-and-play mark drawn before the A, so the shade and the status bar showed a logo the app no longer has. It is now the A with its star, drawn full-bleed for the 24 dp silhouette Android keeps from it.
- **About and License and attribution have their title in the top bar.** Both screens drew an empty bar with only the back arrow, and put a large heading underneath it, so the header looked cut off at the top and left a coloured band under the status bar. The title now sits beside the back button like the other sub-screens ("About", "Licenses"), and the bar no longer paints over the page background.
- **New error card design.** Errors now show as a centred frosted-glass card: a caution-triangle badge, bold title, grey message and a wide "Try again" button, with a soft red wash. The "AniList is down" card sits in the middle of the screen with the whole app blurred and dimmed behind it (Android 12+; older versions dim only). Tap outside the card or the close button to dismiss it. Home row errors use a smaller version of the same card.
- **"AniList is down" card now appears during a real outage.** While AniList was down it answered with 401/403-style errors, which the app read as an expired session and used to reset the outage check, so only the per-row "Couldn't load this row" cards showed. Such ambiguous failures now trigger one token-free test request (shared across rows for 15 s): if that fails too, the outage card shows at once; if it works, it is a genuine session or request problem and the usual message is kept.
- **The "Buffering..." screen no longer stays up while the episode is playing.** The starting screen could be raised again after playback had already begun, and nothing cleared it, so the sound played behind a frozen buffering screen. It now clears as soon as the player is ready and playing, and re-selecting the source that is already playing no longer brings it back.
- **The blur is even across the whole screen.** The frosted areas (pop-up menus, bottom bar) only blurred where a poster or card was, so they looked like blotches. The blurred copy now includes the page colour underneath.
- **The autoplay switch lines up with the lock icon** in the player's top bar.
- **The background animation now shows on the Search and Details pages** as well.
- **The account banner extends to the top of the screen** instead of leaving a black strip under the status bar.

- **Posters at the sides and corners no longer shift when the details page opens from them.** The opening animation started from the poster's visible rectangle (boundsInRoot), which is cut off where a poster is partly outside the screen or its list, so those posters gave a smaller, offset starting point. It now starts from the poster's full position and size. The same rectangle also sizes the poster picture drawn inside the window: for edge and corner posters it differed from the size the poster was loaded at, so the picture missed the image cache and was decoded again just as the animation started, which made it lag. The poster picture inside the window is also no longer dropped once the page is open and composed again on the first frame of the close, which delayed the start of the close. **Constraint:** the starting rectangle may extend past the screen edge, exactly as the poster does.
- **Automatic source mode no longer gives up on the first source.** When the stream that started failed or never got ready, the player is meant to move on to the next listed source, but it looked the playing stream up in the Sources list by its resolved copy while the list held the unresolved entry, so it found nothing and stopped with an error (the log showed 'did not get ready in 25s' and then nothing). The lookup now follows the entry that was selected, and a failed server's other qualities are skipped so the next server is reached sooner. A source that arrived after the failure was also never tried, because the 'an earlier stream failed' flag was read after being cleared; it is now read first. **Constraint:** a stream picked by hand is still reported, not swapped.
- **A downloaded (or streamed) episode no longer stops with a playback error when a subtitle track cannot load.** Media3 threw an IllegalStateException from inside a subtitle track's loader (ERROR_CODE_FAILED_RUNTIME_CHECK), which took the whole video down; it happened on episodes resumed from a saved position, online and offline. The player now starts the same stream again from where it was with one subtitle track (English if there is one), and with none if that fails too, and the log names the tracks involved. **Constraint:** the cause inside Media3 is not fixed; only its effect is contained.
- **The Extensions screen no longer crashes when you scroll through many installed sources.** The crash was `Key "src:..." was already used`. Each row in the Installed list was keyed by its source id, and two installed packages can expose a source with the same id (the same name and language from two different repos; a package can even list one twice). Compose only checks keys when a row is drawn, so nothing happened until the list was scrolled far enough to show both rows, and then the app closed. Every row key is now made unique (the package name is part of it, and any remaining repeat gets a "#2", "#3" suffix), so duplicates simply show as two rows. **Constraint:** two sources that share an id still share their enabled/disabled switch and their saved health result, because both are stored by source id; only the crash is fixed, not the clash itself.
- **The source list now fills in much faster.** It used to run the title search across every installed source (up to 8 s each) and only then ask any source for streams, so nothing appeared until the slowest source had answered, and one stuck extension kept the spinner up for 60 s. The episode's own source is now asked at once, the search for the same episode in the other sources runs alongside it, and each source starts listing streams the moment the search finds it. An extension that has not answered after 25 s (it was 60 s) is dropped; what it already sent is kept. Streams found for an episode are also remembered for 3 minutes, so going back to the list opens it instantly; the refresh button always asks again. **Constraint:** the 3-minute limit is deliberate, because stream links expire.
- **Dubbed episodes now download with their subtitles.** Two causes. A dub that lists no subtitle files of its own now borrows the same extension's sub streams' files (same server first, up to three tried). And when the player could not read an HLS stream and the playlist was read directly, only one picture quality was taken, which dropped subtitles carried inside the stream (they stream fine but were never saved); a playlist with embedded subtitles is now kept whole. The log also says how many embedded subtitle tracks a stream carries. **Constraint:** borrowed subtitles are timed to the sub, so a dub with a different intro can be a few seconds off (the Subtitles panel's offset fixes that); keeping a whole playlist makes that download larger.
- **Downloads now save every subtitle the source offers.** Subtitles were fetched one after another under a single 20 s limit, with the stream's Referer sent to the subtitle host (which many refuse), and a timeout threw away all of them. They are now fetched four at a time, each retried without the stream's headers if refused, and the ones that finished are kept when the 30 s limit hits.
- **Less buffering during playback.** After a stall the player resumed on only 3 s of video, so a weak connection stalled again within seconds; it now waits for 6 s (and starts on 2 s). The in-memory window was five minutes ahead and five behind with the memory cap overridden, which could squeeze low-memory phones into stutter; it is now two minutes ahead and one behind, with the cap respected. Streams also get 15 s connect and read timeouts (the stock 8 s failed slow servers outright) and follow redirects between http and https.
- **A stream that never loads no longer leaves the buffering screen up forever.** Only errors used to move playback on, so a link that hung without failing buffered endlessly. If a stream is not ready after 25 s the next listed source is tried; for a stream picked by hand the player says that source is not responding so another can be picked.
- **Downloads screen now moves live.** Media3 updates each download's progress inside the same object, so the list saw "no change" and stayed frozen (0% · 2 MB) while the notification kept counting. Progress is now copied out as plain values every half second, so the percent, size and bar on Your downloads follow the notification.
- **The nav pill stays on My Space (or any tab) when Settings or Your downloads opens.** While the bar fades out the route is no longer a tab, and the pill fell back to Home. It now keeps the last tab it was on.
- **A disabled source can be switched back on.** The long-press menu on an installed source kept the row it saw first, so it kept offering "Disable". The press handler now follows the row's current state, and Enable reads the live disabled list.
- **The video no longer keeps playing after picture-in-picture is closed or the app is left while loading.** Closing the floating window left the app stopped but the video running, because the player only paused on a stop that happened outside picture-in-picture. It now also pauses when picture-in-picture ends while the app is not on screen. A stream that finished loading after the person had already left the app used to start by itself in the background; it now waits, and starts when the app comes back.

### Changed
- **Video playback and downloads use OkHttp** (HTTP/2, pooled connections) instead of Media3's own HTTP source, so many small segment requests no longer each open a new connection.
- **The pause screen no longer resumes the video when touched.** A touch only takes it away and shows the controls, and every touch on the player restarts its timer.
- **The app opens sooner.** Scanning the installed extensions reads every package on the phone and loads their code; it ran on the main thread inside `Application.onCreate`, so the first frame waited for it. It now runs in the background while the start-up animation plays, the scheduled update check, backup and notification jobs are queued the same way instead of on the main thread, and the pre-load of the last-watched episode waits four seconds so it no longer competes with Home's first requests and with the animation. Anything that needs a source (details, source picker, pre-load) waits for the scan instead of finding an empty list.
- **Searching inside a source is several times faster.** Matching a source's page to AniList used one title search per result, and every request waits 350 ms for the one before it, so a page took around seven seconds before anything showed. The titles are now matched five at a time in one combined AniList request (with the old one-by-one search kept as a fallback if the combined query is refused), so a page needs three or four requests instead of twenty.
- **Animations no longer rebuild the screens they sit on.** The animated background used to redraw the whole app 60 times a second; its shapes drift a few pixels a second, so it now updates 30 times a second with no visible difference and half the work. The profile banner's colour drift (`ShiftingGradient`) and the player's skip chevrons read their animated value while composing, which recomposed those views on every frame for as long as the animation ran; both read it while drawing instead, so a frame now costs a repaint rather than a recomposition.
- **The AniList response cache stays a fixed size.** It kept one lock per request ever made and a search creates a new one, so a long session grew it without bound; the locks are now 32 fixed stripes chosen by key, and an expired answer is dropped when it is looked up instead of sitting in the cache until the next eviction.

- **"Your downloads" is now the heading beside the back button.** It was a large title under the top bar; it now sits in the bar itself, with the description right below.
- **Continue Watching chip is darker and squarer.** The chip in the top-right of a card (time left, "Next Up", air countdown) now uses the same dark glass as the hold menu and softer corners like the player's subtitle bars, instead of a light, pill-shaped chip.

### Added
- **Start-up animation.** The logo's A fades in alone on black, slides left as the letters of the name appear one after another in bold white, then fades into the app. It plays once per launch and is skipped when the system's animations are off.
- **Faster downloads.** Several episodes download at the same time (Settings > Playback > Downloads, 1 to 3, two by default) and each one fetches several parts of itself in parallel.
- **Download queue controls.** Pause all and Resume all on the Downloads screen, move a waiting download up, down or to the front, and a Retry button on failed ones.
- **Retry with backoff.** A failed download tries again after 5, 15 and 45 seconds. An expired or refused link (HTTP 401, 403, 404, 410) asks the source for a fresh one; a busy server or a network error tries the same link again. A failure that is about to be retried raises no notification.
- **Download next episodes.** A button above the episode list on the details page queues the next 1, 3, 5, 10 or all episodes from the one you are on.
- **Download quality.** Settings > Playback > Downloads picks Ask, Same as streaming, or a fixed 1080p, 720p, 480p or 360p. With Ask, a dialog offers the quality for each download.
- **Wi-Fi only and pause on low battery** for downloads (Settings > Playback > Downloads). Low battery means 15% or under while not charging; downloads carry on by themselves afterwards.
- **Faster Start** (Settings > Playback > Preferred stream). When a stream is picked automatically, the best few sources are opened at once and whichever answers first plays. A server that failed in the last few minutes is tried last.
- **Subtitles scale with the video.** Subtitle text follows the height of the picture on screen, smaller in the portrait player and larger on a big screen; the Text Size setting is the size on a phone held sideways. Scale With Video in Settings > Playback turns it off.

- **Auto Source setting (Settings > Player and streaming > Preferred stream).** When on, tapping an episode on the details page skips the source list and starts playing straight away; the player chooses the stream from the preferred quality, preferred language, remembered source and source priority, exactly as it already does for Continue Watching. The Sources panel in the player still lets you switch stream, and "Play manually" in the Continue Watching menu still opens the list. It is off by default.
- **Search by source, in the filter sheet.** The filter sheet has a "Search in source" section with "All" (the default, the normal search) and one chip per installed, switched-on source. Picking a source searches only that source (with no text it lists what the source has on its popular page), and each result is matched to its AniList title so it opens the usual details page, with that source asked first for streams. It is off until chosen, so the normal search makes no extra requests. The choice is remembered across restarts and shows as a "Source: name" chip under the search bar, whose cross switches it off. A title the source has that cannot be matched to AniList is left out, and the sheet's AniList filters are not applied while a source is chosen. A source that is switched off or removed falls back to "All" until it is back.
- **Download progress in the notification.** The running download notification now names the episode ("Show · E5"), shows a real progress bar with the percent and the size saved, says "Waiting for a connection…" when the network is missing and counts the episodes still queued. While the app is still finding the stream, a "Preparing download…" notification stands in, so there is feedback from the first tap. When an episode finishes, or fails, a separate notification says so and opens the app. **Constraint:** a notification only shows once the notification permission is granted.
- **Buffering screen when a source is chosen.** From the moment a stream is picked until its first picture is out, the player shows the show's artwork dimmed, its title logo (or name) in the middle, "Buffering…" under it and a back button, instead of a black screen with a spinner. It also shows while the sources are still being looked up, covers the small video box in the portrait layout, and fades away when the video starts. **Constraint:** a stream chosen in the player's own Sources panel does not bring it back, since the video is already on screen.
- **"Play manually" in the Continue Watching hold menu.** Holding a Continue Watching card now offers Go to details, Play manually and Remove. Play manually opens the source picker for the episode that card would resume (the next episode when the card says Next Up), so you choose the stream yourself instead of the app picking it; hold a stream there to download it.
- **Source picker when you tap an episode.** Tapping an episode (or Play / Play movie) on a show's page now opens a
  source screen instead of starting the stream blindly: the episode's picture and name on top, chips for All and each
  extension that answered (plus a refresh button), then one card per stream with the extension and server, the
  quality and Sub/Dub chips and the number of subtitle and audio tracks. Streams appear as each extension answers, so
  a slow extension never holds the list back. Tap a card to play it; hold a card for a menu with **Download**, which
  downloads exactly that stream (and its subtitles) rather than the one the app would have picked. The stream you tap
  is remembered for the show's next episodes, like a pick in the player's Sources panel, and the player reuses the
  list, so it does not ask every extension a second time. **Constraint:** only the details page opens the picker;
  Continue Watching and Downloads still start playing directly.
- **"Next Up" Continue Watching cards.** The card keeps its layout (title and episode under the picture) and gains a glass
  chip in the picture's top-right corner. While an episode is still going the chip shows the time left (for example
  "12m left") and the progress bar stays. Once an episode has 30 seconds or less to go, the card switches to **Next Up**:
  the chip reads "Next Up", the picture is the next episode's thumbnail (the show's horizontal banner when there is none
  or it fails to load), the line under the title names the next episode, and a tap starts it from the beginning. When the
  next episode has not aired yet, the chip shows "Airs in N days", and for the last 24 hours a live HH:MM:SS countdown;
  a tap opens the show's page. The air time comes from AniList's next-airing data (cached for five minutes), the episode
  thumbnail from the existing episode-details lookup. **Constraint:** movies show only the time left, and a card keeps
  its old look until the lookup answers, so it never flickers or shows a wrong episode when offline.
- **Episode downloads, with every subtitle.** Each episode row on a show's page has a download icon (an arrow, a
  progress ring while it downloads, a tick when saved). The stream is picked the way the player picks it (Preferred
  quality and language, source priority). An HLS or DASH stream keeps one video quality plus every audio and subtitle
  track it carries, and every external subtitle file the source lists is saved next to it. Downloads keep running in
  the background with a notification. **Your downloads** (My Space) lists them with progress and size, plays them
  without a connection (with all saved subtitles in the player's Subtitles list) and deletes them.
- **My Space redesign.** The page now follows the sketch: your AniList banner behind the picture and name (a slowly
  colour-changing gradient when the account has no banner), a Settings box, a Your downloads box, then "My [AniList
  logo] List" with Sync. The chips read Favourites, Watching, Planning, Dropped, then the rest. Each card shows the
  title, the release date, a progress bar with the episode count above it and the percentage under it.
- **"New Anime" and "New Movies" banners on Home.** Two horizontal banners in the "Your Next Watch" style, one right above
  Upcoming Anime and one above Upcoming Movies. They show titles that premiered in the last 30 days, at most seven,
  with what came out today first, then yesterday, and so on (the most popular first within a day). Each card says
  "Released today", "Released yesterday" or "Released N days ago". A banner is hidden when nothing came out.
- **Optional "Swipe To Seek" gesture.** Swiping right across the video goes ahead and swiping left goes back (a swipe
  across the whole width is about two minutes), with a live "+23s" label and the seek applied on release. It is a
  toggle in Settings > Player and streaming, off by default, and is ignored while the controls are locked.
- **YouTube-style double-tap skip feedback.** Double tapping the left or right side now shows a soft half-disc on that
  side with three running chevrons and "N seconds". Further single taps on the same side within 0.7 s keep skipping and
  the seconds add up (10, 20, 30...), as on YouTube. Tapping the other side ends the run.

### Removed

- **The source catalogue rows are gone from Home.** The rows built from the installed sources (and the hero's fall-back
  to the first of them) no longer appear, and Home no longer fetches them.

### Fixed

- **Tapping left then right quickly no longer skips the video.** The player paired any two quick taps as a double tap,
  whatever side they landed on. A double tap now needs both taps on the same side; a tap on the other side just starts a
  new first tap.
- **"360p" in Settings > Playback no longer breaks into "3 / 6 / 0p".** The five quality chips shared one row and the
  last was squeezed into a sliver. They now sit in a wrapping row with labels that never break, so 360p drops to a
  new line when it does not fit.
- **Preferred Language "Dub" now starts a dub.** Playback began on the best-ranked source the moment it answered,
  and a dub usually answers later, so the sub started and the dub was never considered (the same happened when
  resuming from the remembered source). The first pick now waits for a stream in the chosen language: it starts as
  soon as one appears, when every source has answered, or after 15 seconds, so a show without a dub still plays.
  A dub is also recognised from the server or extension name and the audio list, not only the stream label.
  **Constraint:** a stream picked by hand for the show still wins over the language preference.

- **Bottom sheets (characters, trailers, comments, menus) no longer show the page sharp behind them, and no thin
  white box lines appear.** The window blur was requested while the sheet was still composing, before its window
  existed, so it silently never applied; it is now requested once the window is attached. On phones that switch
  window blur off (battery saver, low-end devices) the popups now use a nearly solid glass fill instead of a 66%
  see-through one, so text behind never overlaps. The sheet's hairline edge was a full `border`, which also stroked
  the straight sides and bottom of the sheet; it is now drawn along the top edge and corners only.
  **Constraint:** real blur still needs Android 12+ and the phone's window blur enabled.

## [0.5.2] - 2026-10-20

### Added
- **Sakura accent colour**, a soft cherry-blossom pink. Text on light accents is now dark so it stays readable.
- **Three new background animations:** Cherry blossom (drifting petals), Snowfall, and Halloween (pumpkins, bats and ghosts). The animation picker scrolls to fit them.

## [0.5.1] - 2026-10-20

### Added
- **Background animation** (Appearance > Background > Background Animation): None, Circles, Triangles or Material shapes drift slowly behind the screens. They are drawn behind everything, so posters, cards and sheets stay fully opaque and the shapes only show in the empty space. Off by default; the loop pauses when the app is in the background.
- **Autoplay switch in the player.** A YouTube-style pill in the player's top bar (landscape and portrait); when on, the next episode starts when one ends. Hide Next And Previous in Settings > Player and streaming removes those two buttons from the controls, and the play and seek buttons spread out to fill the gap.

### Changed
- Autoplay is controlled only from the player, not from Settings.

### Removed
- Haptic feedback: the vibration on taps, switches and holds, its Settings switch and the VIBRATE permission.
- YouTube from the License and attribution screen.

## [0.4.5] - 2026-10-18

### Added

- **Your own font (Settings > Appearance > Font).** Tap "Add your font" and pick a .ttf or .otf file: all text in the
  app switches to it, and the box turns into a card with the font's name and a bin to remove it again. Below it,
  **Use Different Font For Subtitle** reveals a second box for a font used by subtitles only; with it off, subtitles
  follow the app font. **Constraint:** the font is copied into the app's own storage, so deleting the original file
  does not affect it; a file that is not a font is refused with a message. A font with a single weight shows
  headings in the platform's synthetic bold.
- **Ambient mode in the portrait player.** The area under the video glows in the colours of what is playing, like
  YouTube's ambient mode, and the colours drift as the picture changes. It is read from the video a few times a
  second (once while paused) and can be switched off under Settings > Playback > Portrait player > Ambient Mode.
  **Constraint:** portrait only; if the phone refuses to hand the frames over (a protected stream) the glow stays off.
- **Frosted glass menus and sheets.** The dropdown menu, every bottom sheet (episodes, sources, subtitles, audio,
  player settings, search filters, comments, trailers, characters, extension settings) and the extension setting
  dialogs now use the same translucent glass as the Continue Watching "Remove" card, with the screen behind blurred
  on Android 12+ phones that allow blurring between windows. **Constraint:** on older phones, or with that
  blur turned off in the system, they keep the translucent glass without the blur. The landscape side panels are unchanged.
- **"Any" for Preferred Quality (Settings > Playback).** Asking for a particular resolution can mean waiting for the
  source that has it. With **Any**, playback starts on the first stream in your chosen language (Sub or Dub) that
  answers, with no wait for a better-ranked source or a higher resolution. A stream picked by hand for a show still
  wins. **Constraint:** the language preference is kept; only the resolution is given up.
- **"Your Next Watch" cards open their details with black bars.** Tapping a card now grows a black bar up out of its
  top edge and another down out of its bottom edge; each also grows across the card, so the two meet in the middle and
  the whole screen is black, and then the details page shows up out of the black. Going back plays it in reverse:
  the page goes, the bars start from the full screen and pull in to the card's edges. **Constraint:** only these cards
  use the bars; posters, list rows and the hero banner keep their window-grows-out-of-the-poster animation.
- **Preferred stream: quality and language, in Settings > Playback.** A new "Preferred stream" group has **Preferred
  Quality** (1080p, 720p, 480p, 360p) and **Preferred Language** (Sub or Dub). When a show starts playing, the app now
  picks its stream by them instead of just taking the first one: the language first (the other one only when the show
  has no match), then the closest resolution (the best stream at or below your choice, a higher one only when nothing
  at or below exists, and a stream that names no resolution last). Streams that still tie fall back to the source this
  show last played from and then to your source priority. **Constraint:** only the streams that have arrived when
  playback starts are compared, so a slow source that answers later is not considered, and the choice is read from the
  stream's label, so a source that labels neither quality nor Sub/Dub is treated as an unknown-quality sub. A stream
  chosen by hand in the Sources panel sticks: for the next 3 days that show's episodes start on the same stream (same
  extension, server, language and, when the exact one is not listed, the closest resolution), ahead of the preferred
  quality and language; picking another stream by hand replaces it.
- **"Your Next Watch" banner on Home.** Under Continue Watching there is now a horizontal banner of wide cards that
  loops endlessly and slides on by itself every few seconds (the centred card is full size, its neighbours peek in
  smaller and dimmer, and the text lags behind the picture as it slides). Each card shows the title on top and, at the
  bottom, "Because you watched ..." with the show of yours it was suggested by. The suggestions are AniList's own
  recommendations for what is in Continue Watching plus your Watching and Completed lists (AniList's when signed in,
  the device's otherwise), one from each show in turn, never a show you already watch, never an adult title. It stays
  hidden until there is something to base it on. **Constraint:** the banner is rebuilt when the set of shows changes,
  not while an episode plays.
- **Pause screen in the player, in the style of Nuvio.** Two and a half seconds after you pause, the controls leave and
  the frozen video dims under "You're watching", the show's logo (its plain title when there is none), the episode as
  "S2E5", the episode name and its synopsis, bottom left; the text fades and rises into place. A tap on it resumes.
  Movies show no episode line, and an episode a source only calls "Episode 5" gets no name line. Portrait has a smaller
  version over the video. Settings > Playback has a **Pause Screen** switch (on by default).
- **Release and new-episode notifications, with an inbox.** A small bell at the top right of the home banner grows,
  the way the search bar opens, into a list of every notification received so far (newest first, with poster, message
  and time; a dot on the bell while something is unread). A tap on a row opens that show; the cross or Back folds it
  and marks everything read, and "Clear" empties it. The inbox is filled even when the system cannot show the
  notification. Notifications themselves: an unreleased show you asked about is announced the moment it releases, and
  everything on your Watching list (AniList or on-device) or followed gets one per new episode. Upcoming shows'
  details page says **Notify me** (then "Notifying you") where it used to say "No episodes yet". A background check
  runs about every three hours with a network; the first time a show is seen it only records where it is, so nothing
  fires for episodes that were already out. A tap on the system notification opens that show's details page.
  Settings > Notifications has a master switch and a Watching-list switch; the notifications use their own
  "New episodes & releases" channel. **Constraint:** AniList only knows a show's next episode, so a notification can
  arrive a few hours after the episode aired, and Android 13+ needs the notification permission the app already asks
  for at launch.
- **Soft haptic feedback, with an on/off switch.** The phone now gives a short, gentle vibration when you tap a poster or
  card, flip a switch, change tab (the nav-bar pill also ticks as you drag it across tabs), pick a chip or detail tab,
  press play/pause or seek in the player, use the player's side panels, and when a press-and-hold opens the Continue
  Watching or Extensions menu. The pulses are deliberately light - the soft "low tick" primitive on phones that have it,
  a very short low-amplitude pulse elsewhere - and the old sharp system long-press buzz was replaced with them.
  Settings > Feedback > "Haptic feedback" turns all of it off. **Constraint:** the system's own "touch feedback" setting
  is respected too, so nothing vibrates if the phone has that off; the auto-rotating home banner never vibrates on its own.

- **11 more accent colours.** Red, Crimson, Coral, Pink, Purple, Indigo, Blue, Cyan, Teal, Brown and Slate join the
  palette in Appearance (the row already scrolls sideways). **Constraint:** all are mid-tones so the white text on
  accent buttons stays readable; saved choices keep their keys.

- **Each source remembers its last test.** The Extensions list now shows "Streams OK" or "Streaming failed" with how long
  ago it was tested, so you can see which sources work without testing again. **Constraint:** informational only;
  sources are not hidden or reordered automatically.

### Fixed

- **Poster open and close animation no longer stutters now and then.** The details page used to receive its data
  (artwork, AniList details, episode list; usually straight from the cache) in the middle of the animation, and each
  arrival rebuilt the hero and the whole episode list in a frame that also had to move the window, so some opens were
  smooth and some were not, depending on how fast the cache answered. The page now holds that data back until the
  motion has finished and applies it in one go. Two smaller causes are fixed as well: the window's outline built a
  new native path on every frame (it is reused now), and the home banners kept sliding on by themselves while a
  details page was opening over them (they now wait until the home screen is in front again).
- **The "Add extension" / "Update sources" card no longer flashes twice when it closes.** On the frame where the close
  started, the frosted scrim and the card were removed for a moment (the screen was checking whether the close
  animation was running, and it had not started yet) and then came back at full strength to animate out, which showed
  as two flashes. They are now kept on screen by the animation's own progress, so the card slides away and the blur
  fades out in one motion. The card is also present from the first frame of opening.

- **Switching tabs while one is still loading no longer leaves the old tab drawn on the new one.** All four tab
  screens recorded their content into one shared layer for the nav bar's blur, and each also drew that layer back as
  its own content. During a tab change two screens are alive at once, so the screen that was not redrawing showed the
  other screen's picture (a stuck spinner) until a touch or scroll redrew it. Each screen now records into its own
  layer, and the bar blurs whichever screen appeared last; a screen fading out can no longer take that from the one
  fading in.

- **Nav bar: the pill now comes to a pressed tab immediately.** Holding a tab that was not selected left the pill
  where it was until the finger moved, then it jumped under the finger. A press now sends the pill to that tab with
  the usual stretchy spring, and dragging only moves the pill's target, so there is no jump. Releasing settles it on the
  tab under the finger.

- **Kohi-den repo adds work again (it moved off GitHub).** `raw.githubusercontent.com/Kohi-den/extensions/...` now returns
  HTTP 404 for every branch; the index is published on `kohiden.xyz`. Any Kohi-den link is now also tried there first.
  The error also lists every address that was tried, instead of only the last fallback branch.
  **Constraint:** other repos are untouched; only Kohi-den links get the mirror.
- **Test results keep every title's failure.** A streaming test tries two titles and used to show only the second
  one's reason, which hid why the first failed.

### Changed

- **Resuming a show now tries the source it last played from.** While a stream plays, the app remembers which extension, server and quality it came from, and keeps that for 3 days after it last played. Resuming from Continue Watching (or opening the next episode of that show) starts with the same source as soon as it answers, waits up to 8 seconds for it if it is slow, and only then falls back to your source ranking. Pre-load warms that same source. Removing a show from Continue Watching forgets its source, and after 3 days without playing the memory expires on its own. **Constraint:** if the remembered source does not have the episode or fails, the normal ranking takes over, and the exact server is only matched when it is listed for that episode.

- **The app version is now 0.4.2 (version code 2026101703).** `version.txt` is the single source for both. The code is one above 2026101702 (0.4.1), so the update installs over it. **Constraint:** the code is a date-style number that must stay above this one from now on.

- **The app version is now 0.4.1 (version code 2026101702).** `version.txt` is the single source for both. The code is higher than the previous 2026101503 and than 2026101701, so the update installs over either build. **Constraint:** the code is a date-style number that must stay above this one from now on.

- **Every settings screen now has the same shape: heading outside, a box, a line between rows, a description under each title.** Player & Streaming is rebuilt from your sketch as Player gestures (skip amount, double tap to skip, one "swipe to adjust volume & brightness" switch), Picture in picture, Pre-load, Subtitle and Streaming priority. The subtitle live preview stays, as the first row of the Subtitle box. Appearance is split the same way (Title, Color palette, Navigation bar, Reset) with its sliders, presets and blur switch as rows, System's log buttons are rows with descriptions, and the headings on Settings, About, Updates and Backup are now plain sentence-case text instead of small capitals. Shared pieces `GlassContentRow` and `GlassSliderRow` were added, and `GlassToggleRow` no longer needs an icon. **Constraint:** the two gesture switches are now one, so brightness and volume swipes turn on and off together.

- **TMDB logos, posters and backdrops load much faster.** Answers (ani.zip mapping plus the TMDB images) are now kept on the phone for 14 days, so a show seen once opens with its art instantly, even after a restart. Two screens asking for the same show share one request instead of making two, the slow Kitsu cover can no longer hold the rest up (it gets 4 seconds), every artwork request has a 10 second limit, and Home starts the lookups for the whole carousel as soon as it is known, a few at a time. A failed TMDB call is never stored, so a bad moment does not stick. **Constraint:** the stored answers are dropped when a TMDB key is added or removed, and "clear cache" wipes them too.

- **Extensions screen redesigned.** A floating, frosted Available / Installed bar (the Home nav bar's twin: same glass, spring pill and hold-to-drag) sits at the bottom with a circle beside it. The circle opens an "Add extension" card that rises from under the screen over a frosted blur (the plus turns into a close cross); it lists your repos with an on/off switch and a delete button each. On Installed, when an extension has a newer build, the circle becomes a download arrow that opens an "Update sources" card with Update per source, Update all and Not now. Installed rows have a Test button, and holding one floats it over a live blur like Continue Watching, with Configuration, Disable / Enable and Delete. The old stacked page (repository box, long lists, test dropdown) is gone. **Constraint:** deleting a source removes its whole extension when that extension bundles several sources.

- **Home hero banner loops endlessly.** Swiping past the last slide now continues to the first (and backwards from the
  first to the last) instead of stopping at the ends; the dots and the auto-rotation follow the loop.

- **Search list mode: the whole card opens the page out of its poster.** Like My Space cards, a tap anywhere on a
  list-mode result (title, tags, empty space) now starts the poster-expands-into-details animation; before only a
  tap on the poster itself did, and any other tap opened the page with a plain fade.

- **Player subtitles: one joined box.** A multi-line subtitle's rounded bars now join into a single stepped shape
  (corners between neighbouring lines are squared off, only the outer corners stay rounded). With the rounded
  background off, the plain rectangle hugs the text instead of stretching across the whole video width.

- **Extensions: choose which repo "Available sources" shows.** With more than one repo added, a chip row above the
  search box offers **All** (the default, repos merged) and one chip per repo (e.g. "yuzono/anime-repo (120)");
  picking a repo lists only its extensions, and "Update all" then covers just that list.

- **Extensions: "Available sources" folds.** The catalogue starts collapsed (tap its header to open; typing in the
  search box opens it). A new **Update all (N)** button, visible even while folded, downloads and installs every
  installed extension that has a newer build in a repo, one after another, with a progress line and any failures shown.

- **Extensions "Test sources".** Every source in the picker now shows its language and extension version
  ("Anikoto · EN · v16.11"), so two sources with the same name can be told apart. Several sources can be ticked and
  tested in one run, and a "Test all" button runs every installed source one after another with a progress line, a
  "N of M working" summary and one result card per source.

- **Movies on the details page.** A movie (AniList format MOVIE) shows its running time (e.g. "1h 52m", from AniList)
  in the stats row instead of an episode count, the main button reads "Play movie" / "Continue movie · 12:30" /
  "Rewatch movie" instead of "Play Ep. 1", and the Episodes section is removed entirely.

### Fixed

- **Extensions built for library 17 (e.g. Kohi-den's Anikoto) listed no episodes.** Their listing entries can be
  "Seasons" containers whose episodes live in `getSeasonList`; Anisu dropped `fetch_type` / `memo` / `season_number`
  when it handed an entry back to the source and never asked for seasons. The adapter now keeps the source's own
  entry, reads the type from the details call, and gathers the episodes of every season (also as a fallback when a
  plain episode list comes back empty).
- **Adding a repo from `raw.githubusercontent.com/<user>/<repo>/main/index.min.json` gave HTTP 404.** Extension repos
  publish the index on the `repo` branch, so a `main`/`master` link now also tries `refs/heads/repo` and `repo`, and a
  bare `github.com/<user>/<repo>` link resolves to that branch.
- **Adding a repo gave "Unexpected JSON token ... �".** The index came back as gzip bytes; the repo reader now unpacks
  gzip before parsing (and drops a UTF-8 BOM).
- The Extensions "Test a source" episode-list step waits 45 s instead of 20 s, so large lists (One Piece) are not
  reported as timed out.

### Added

- **License and attribution screen** (Settings > Info, above About). A card for every service Anisu uses (AniList,
  MyAnimeList / Jikan, TMDB, ani.zip, Kitsu, AniSkip, YouTube, Anime News Network, Aniyomi and the open-source libraries)
  with its logo, what Anisu uses it for, the credit line it asks for (including TMDB's required notice) and a link.
  Logos are the services' own icons fetched when the screen opens, over a coloured letter badge that shows offline.

- **Comments on the details page, through your AniList account.** A new "Comments" card opens a sheet with the show's
  AniList discussions (its forum threads). Pick one to read its comments with the replies nested under them, and load
  more as you scroll. Signed in with AniList you can write a comment, reply to one, like or un-like it, and start a
  discussion when the show has none; your comment shows at once where it belongs. Signed out you can read everything
  and are told that writing needs the AniList sign-in. Locked threads are read-only. **Constraint:** comments are AniList's
  own, so a show without an AniList id has no Comments card, and the text is shown as plain words (images, videos and
  spoilers appear as "[image]", "[video]" and "[spoiler: ...]").

- **A "Clear anime details cache" button on the Backup screen.** It forgets everything the details page keeps:
  the saved episode lists (memory and disk), episode titles, thumbnails and synopses, logos and backdrops, trailer
  lists and the AniList answers, so the next details page fetches them fresh. That is the fix for a title stuck on
  an old or wrong episode list. **Constraint:** nothing of the user's is touched: lists, watch progress, settings and
  backups stay as they are, and no pictures are removed.

- **Trailers now play inside the app, from YouTube's own stream, instead of YouTube's embedded player.** Many
  trailers refuse the embedded player and showed "video unavailable". The app now asks YouTube for the video's stream
  the way its own apps do and plays it with the app's player: playback controls, buffering indicator, a fullscreen
  button that turns the video landscape, and a pause when the app leaves the screen. It prefers an adaptive HLS
  stream, then a single mp4 with sound, then a picture file and a sound file joined (up to 1080p). If YouTube gives
  no stream, or the stream fails to play, the sheet falls back to the embedded player as before, and the button in
  the sheet still opens the video in YouTube. **Constraint:** YouTube changes which app identities it accepts from time
  to time; `YouTubeStreamResolver` keeps them in one list, and a trailer that goes back to the embedded player is the
  sign that list needs updating.

- **A fix archive pushed with the wrong commit title now says so instead of passing in silence.**
  The extraction only runs for a commit title starting with `Maygodblastyou`, `fix` or `n`, and a
  title that missed all three produced a run that was simply skipped — indistinguishable, in the
  Actions list, from an archive that had been applied. Such a push now reports the archive it did
  not apply as a warning annotation and in the run summary, naming the file and the title it read,
  and a push that changed no archive at all no longer starts the workflow.

- **Search suggests titles as you type, and shows the top results in your chosen layout.** AniList only finds
  titles that match what is typed exactly, so a small typo or a half-typed word turned up nothing useful. While the
  search panel is open it now lists your earlier searches that match, then up to five titles close to what you typed
  (a missing or swapped letter still counts), then the top results: nine as a grid or five as a list, following the
  grid/list switch. **Constraint:** suggestions are ranked from the titles this session has already shown, so a title
  AniList never returns for a badly misspelled query cannot be suggested.

- **Anisu now backs itself up: one `.anisu` archive a day, the last three kept.** Nobody should have to remember
  to tap **Create backup**, so a daily job writes a copy into app-private storage and drops the oldest one as a
  new one lands. Settings → Backup shows the switch, when the last copy was written, a **Back up now** action,
  and a **Restore** beside every kept copy. The job runs while the app is closed and needs no network, no
  permission and no account.

- **Updates can empty the APKs it already downloaded, and open the changelog.** "Clear downloaded updates"
  counts the update APKs sitting in the cache and deletes them on a tap, and "Changelog" opens the project's
  `CHANGELOG.md` in the browser.

- **The notification row in Updates says whether Android would really deliver an alert, and opens the switch.**
  It reports On/Off and goes straight to Anisu's own notification settings, which used to be reachable only
  after a test notification had failed.

- **Backup has its own row in Settings, right beside System, and a screen of its own.** Saving and
  restoring your settings, watch progress and local library into an `.anisu` file used to be buried at
  the bottom of Settings → System, under the diagnostics card, so it was easy to miss. It is now a
  **Backup** row in the App group that opens a dedicated `Backup` screen; System keeps only the
  diagnostics tooling. The two actions, the file picker and the "Anisu restarts itself to apply the
  restore" behaviour are unchanged — only where you find them moved.

### Changed

- **Subtitle backgrounds are softer, with a "Rounded subtitle background" switch.** The bars behind each line were
  nearly pill-shaped; they now have gently rounded corners, a little more side padding, and the bars of a two-line cue
  touch. A new switch (Settings > Player and streaming > Subtitles, and the player's subtitle panel) turns it off, and
  the subtitles then sit in one plain rectangle box.

- **The Trailers row lists every trailer.** AniList's own, Jikan's promos and a YouTube search for the show's trailers, PVs,
  teasers and sub / dub versions (short videos that name the show) are merged without duplicates.

- **Subtitles have rounded corners.** The background behind the subtitle text was a square block, and the player's
  subtitle view cannot draw anything else. Each line of a cue now gets its own rounded bar, as wide as the line, in the
  background colour and opacity chosen in the Subtitles panel (a fully transparent background still gives the
  outline-only look). **Constraint:** the subtitle file's own colours, fonts and sizes are still ignored, as before; picture
  subtitles (bitmap formats) keep their own look.

- **Subtitles are on by default, and your choice is remembered for the session.** A stream with subtitles used to
  start with them off, so they had to be switched on for every episode and every source. Now subtitles turn on by
  themselves when the stream has them (the English track if there is one, otherwise the first), and once you
  pick a track or turn them off, that choice is used for every following episode and source until the app is closed.
  **Constraint:** the choice is kept for the run of the app only; a fresh start begins with subtitles on again.

- **Your AniList lists are now kept cached while the app is open.** My Space and the home banner's hearts
  used to ask AniList when you first looked, and again once the two-minute cache had run out. The six lists
  (Watching, Planning, Completed, Paused, Dropped, Repeating) and your favourites are now fetched as soon as the
  app opens and fetched again every 90 seconds, a little inside the cache's lifetime, so they are always ready.
  It runs only while the app is on screen and only while signed in. **Constraint:** the lists are read one after
  the other, so the refresh stays far below AniList's request limit.

- **Switching tabs is now a soft cross-fade instead of a slide.** Home, Search, Schedule and My Space used to
  travel sideways as one sheet; the page you leave now fades out while the next one fades in, in place, like
  the reference recording. The bar still waits `TAB_TRANSITION_MILLIS` before it accepts the next queued tab.

- **The details page now closes into the home banner as the banner itself.** Opened from the hero banner, the
  page used to shrink to the banner's size showing a bare picture, and only when the transition ended did the
  gradient and title pop in. The banner's own slide (artwork, fade to the page colour, logo or title, genres,
  buttons) is now drawn under the shrinking window, so it lands looking exactly like the banner and the
  hand-over is invisible; the open starts from the same picture. The banner's logo is remembered once it has
  been prepared, so the copy under the window shows the lettering at once instead of the plain title first.
  **Constraint:** posters and list cards keep the picture-only morph; only the banner uses its own cover.

- **Nightly builds are named `0.3.0-nightly`, without the timestamp.** The `-2026.10.11.1846` suffix made every
  hourly build look like a different app and put a clock reading where a version belongs. What identifies a build
  is now the commit it was made from, which CI records and the updater compares.

- **Updates leads with the answer and keeps every setting one tap away.** The screen was six groups of rows —
  install, check, version, channel, automatic checks, storage — so "is there something to install?" and the
  button that installs it were things to hunt for, and taking a new build meant scrolling past settings nobody
  opened the screen for. It opens with one status card now: the installed build, what the updater is doing and
  the single button that acts on it (download and install when there is a build, check when there is not, its
  own progress while the file comes down), with the build's notes inside that card and the version code in its
  small print. Everything that is a setting sits behind one "Options" row that opens in place, and the channel
  is a two-option switch on a row instead of a card. About and the rows themselves still use the shared pieces
  in `ui/components/GlassSettings.kt` on the app's 16dp grid. **Constraint:** the behaviour is untouched — same
  GitHub check, same resumable `.part` download, same signature-verified install, same channel preference.

- **Backup is one compact card instead of three, and the long explanation folds away.** The switch, the last
  run, a manual copy and the kept copies sat in three separate groups with a paragraph of prose above them, so
  the screen opened on a wall of text and what a person comes for — that backups happen, and when the last one
  landed — was the part that had scrolled. It is one card now, with the two file-picker actions side by side at
  the bottom and what an archive holds (and that the AniList sign-in is deliberately left out) behind a
  "What's inside" row that opens in place. The new `GlassDisclosureRow` is shared, so any settings screen can
  fold a long explanation the same way. **Constraint:** restoring still closes the database first and
  relaunches Anisu, exactly as before.

- **A fix archive can be any format, and a push applies only the archives it brought in.** The extractor read a
  zip at the repository root and nothing else, so a `.7z`, a `.rar` or a `.tar.gz` was ignored; and it applied
  whatever `fix.zip` happened to be lying there, so an archive written before a version bump would have put an
  older `version.txt` back over a newer one. Anything named `fix.*` at the root is read now (7z and rar through a
  7-Zip binary the workflow installs when the runner has none), and a push extracts only the archive that push
  changed. A manual run still applies every archive present, because that is what "re-apply my archive" means.

- **The version rule now lives in one place, with every reader of the file listed.** How the
  version and the version code travel through the project — which reader takes which line, and that
  a `-P` value on the command line is a build input rather than a second source — was spread across
  the build script, the two workflows and the notes in `version.txt` itself. It is one section now,
  and it states the rule plainly: an update edits `version.txt` and `CHANGELOG.md`, and no other file
  repeats the number.

### Fixed

- **The home banner's heart now shows a liked title as liked.** Favourites were read before the AniList profile had
  loaded (so the first read was empty) and only the first 50 were fetched. They now wait for the profile and load every page.

- **"Start discussion" no longer fails with "The server rejected that request".** List values in the request were sent as
  a quoted string instead of a JSON array, and AniList also needs a forum category. Both are fixed.

- **The navigation bar's frosted blur works on My Space.** The screen's recorded backdrop had no background, so there was
  nothing solid to blur.

- **Switching tabs while My Space is loading no longer leaves it drawn over another tab.** The bar now waits for the
  navigation host to report that the previous tab move has finished instead of waiting a guessed time, and My Space shows
  its spinner from the first frame.

- **Trailers that buffered and then went blank now play.** Every stream YouTube offers is collected (HLS first); one that
  errors, does not start within 12 s or freezes for 10 s moves on to the next, and the embedded player is the last resort.

- **Upcoming shows and movies show an English title.** When AniList has no English title yet, a Latin-lettered synonym is
  used before the romaji title. **Constraint:** if AniList lists no English name at all, the romaji title stays.

- **The News tab now shows real, current news.** It listed five fixed sample articles dated September 2026 and never
  changed. It now reads Anime News Network's feed (MyAnimeList's news when ANN cannot be reached), newest first, with
  plain-text summaries, categories and dates, and tapping an article opens it in the browser. The news is fetched when
  the tab is opened and again whenever the copy in memory is over ten minutes old; if a fetch fails the last good copy
  stays on screen, and an empty tab now says it could not load instead of "check back soon".

- **Switching Home, My Space and back quickly no longer leaves My Space drawn over Home with a spinner.** Tapping My
  Space while it was still loading and then Home again could leave My Space's loading screen on top of Home until
  the screen was touched or scrolled. The bar kept the next tab move waiting for a fixed time after the last one, but a
  screen that is slow to appear (My Space while it loads) starts its fade late, so the next move could begin over a fade
  that was still running, and a move started over a running one leaves the earlier screen composed on top of the
  new one. The tab screens now tell the bar when their fade really starts and ends, and the next move waits for that
  (with time limits, so a move that never animates cannot block the bar).

- **The home banner's heart now shows whether you have liked the title.** It was a per-session toggle that
  started empty, so a title already in your favourites (One Piece) showed an empty heart, and the heart reset every
  time you left Home. The banner now reads your real favourites (AniList's when signed in, the device's otherwise),
  fills the heart for liked titles and keeps it in step when you tap it; a heart change that AniList refuses is put back.

- **My Space cards for shows still airing now have a progress bar.** A show with no announced episode total
  (One Piece) showed only "Ep 1174" and no bar. The bar is now measured against the episodes aired so far, which the
  list queries fetch, and a started title with no usable count at all still gets a dimmed full bar so the card keeps its
  shape. **Constraint:** the label stays "Ep N" when the total is not announced, since the aired count is an estimate.

- **My Space cards open with the poster animation wherever you tap them.** Only a tap on the small poster set the
  animation's starting point; a tap on the title or the rest of the card opened the page with a plain fade. A touch
  anywhere on the card now starts the same open and close as the posters, growing out of the card's poster.

- **Playing a title from Continue Watching now loads its episodes.** The episode list handed to the player came
  only from the details page, which that path skips, so the player had no episode picker and no previous/next
  after a fresh start. The player now builds the list itself when it has none: AniList's episode count (the same
  rule as the details page, so unaired episodes are left out), with episode titles and thumbnails, and the
  sources' saved list when AniList cannot say how many episodes there are.

- **Dragging a source to the top or bottom of the Streaming Priority list now scrolls the page.** Holding a row
  near either edge of the screen scrolls the page under it, faster the closer to the edge, and the row keeps
  swapping places as it passes the others, so a long list can be reordered in one drag.

- **A manual release with no tag takes its version from `version.txt` instead of inventing a date.**
  Leaving the tag empty used to tag the release `v2026.10.12.1846`, which no `## [...]` heading in
  `CHANGELOG.md` matches, so the release came out with a placeholder body and a version nobody ever
  chose. The tag is now `v` plus line 1 of `version.txt` — the single source the nightlies and the
  build itself read — and a file missing that line fails the run with an explicit message.
  **Constraint:** a tag typed in or pushed still decides the release name, and the version code stays
  on line 2 for both channels.

- **A tab change no longer shows the screen twice, and a fast switch no longer leaves the previous tab stuck.**
  The move slid each screen a sixth of the width *and* faded both of them at once, so two full screens were
  drawn on top of each other for the whole transition — which is what "everything shows up twice" was. It is a
  plain full-width slide now, so the animation is still there, and the bar runs one tab move at a time: a tab
  tapped during a slide is applied the moment that slide ends instead of overlapping it.

- **The nightly channel can be offered a new build again.** Every nightly now carries the same version name and
  the same version code by design, so a check that compared either of them answered "up to date" for every build
  after the first one. A check compares the commit instead, and the "announced once" rule uses it too, so each
  nightly is announced once rather than only the very first one ever.

- **New builds install again as a normal upgrade over an older one.** The version code had been changed to the
  small number `126`, which is *below* the code already on people's phones, so Android refused every install
  and reported only "the package appears to be invalid". Line 2 of `version.txt` is `2026101201` now — above
  every code already published — and counts up from there. **Constraint:** the counter is shared by the nightly
  and the stable channel, so consecutive nightlies carry the same code until a release moves it, and it must
  never go down: a lower one is exactly what the installer refuses.

- **Settings uses one consistent padding grid.** The group labels started 10dp from the edge while the rows
  under them started at 16dp, the icon-to-text gap was 14dp here and 12dp on Appearance, Player and streaming
  and Updates, the rows sat at 14dp while two groups were 4dp apart, and a wrapped description had no gap under
  its title. Everything is on the app's grid now — 16dp, a 12dp icon gap and the same 2dp under a title — so
  moving between the settings screens no longer shifts the layout.

- **About no longer looks like it carries two versions.** The header ended with two pills side by side —
  `Version 0.2.0-nightly-2026.10.11.1900` and `2026101201 · Nightly` — which read as two different builds. The
  name and the code are different things, so there is one row naming the installed build and a labelled second
  one carrying the version code; the rebuilt Updates screen follows the same rule, its header naming the build
  alone.

- **Switching tabs quickly no longer leaves two screens drawn over each other.** The tab bar waited exactly as
  long as the slide lasted before it allowed the next tab. A frame of slack was missing, so a fast second tap began
  the next slide while the old screen was still on its way out, and the two showed at once. The bar now holds the
  next tab for a short settle time after each slide.

- **A pushed fix archive is applied again — the extraction had silently stopped running.** The workflow started
  only for an account named `Maygodblastyou`, and no account with that name can push to `main` any more: every run
  of these days, a `Maygodblastyou`-titled push and a manual dispatch alike, finished as `skipped` in a second,
  having done nothing. The gate is now the commit's title (`Maygodblastyou`, `fix` or `n`) or a manual run, which
  always starts; no login is checked, since only someone who can already push to `main` reaches this job.

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

- **System, Updates and About are rebuilt from scratch with a leading header and grouped cards.** Each now opens with a header that leads with the identity or the installed build over a soft accent glow, then puts the diagnostics, the updater and the links in their own black-glass cards, so the page reads top to bottom instead of as one long list.

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
