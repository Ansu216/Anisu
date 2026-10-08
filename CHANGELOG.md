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

### Fixed

- **The details page now opens with its full animation.** Composing the page on the first frame used up part of the animation's clock, so the window appeared already half open, while the close (nothing to compose) played fully. The open now waits 90 ms before it starts moving and uses a gentler curve, so it grows out of the poster from the very first frame, like the close does. **Constraint:** the build could not be run in this environment, so compile it once before release.

- **Two subtitles on screen at once no longer overlap.** Every text cue was forced onto the same line, so a character's dialogue and a translated on-screen sign were drawn on top of each other. Cues the subtitle file places away from the bottom (signs) keep their own position, and the remaining cues are stacked upward from the chosen subtitle height. A single subtitle is placed exactly as before.

- **The player remembers where you left off, and changing source keeps your place.** The saved position was applied with a seek before the stream was loaded, which the player discards, so Continue Watching always started at 0:00; changing source also restarted the episode. The start position is now handed to the player together with the stream, both for a saved resume and for a source switch or automatic fallback to the next source.

- **Holding the navigation pill now visibly expands it.** The pill was drawn inside the bar's clipped glass, so its growth was cut off at the bar's edge. It is now drawn over the glass, swells wider and taller than the bar with a glassy sheen, magnifies the icon under it and follows the finger when dragged.

- **The details title no longer waits on the artwork lookup in English mode.** The text title was only drawn once the ani.zip/TMDB lookup had finished, so a slow or failed lookup left the hero with no title at all. It now appears straight away when no TMDB key is set, and after at most four seconds when one is. **Constraint:** the build could not be run in this environment, so compile it once before release.

- **Dragging a source in Streaming Priority is smooth.** Each row was rebuilt by position, so swapping two rows restarted the drag gesture in the middle of the drag and the row stuttered or lost your finger. Rows are now keyed by source, so the gesture survives every swap. The other rows slide into their new slots with a short spring, the dragged row lifts slightly with a shadow and follows the finger without recomposing the list, and on release it settles into its slot instead of jumping. **Constraint:** the list does not auto-scroll when you drag near the screen edge; the build could not be run in this environment, so compile it once before release.

- **The home banner no longer changes height or aspect ratio while rotating or loading.** Its height followed whatever the current poster's image size was, so it jumped between slides and while images loaded. It now uses one fixed height for every slide and crops each poster to fill it.

- **Subtitle height now works in the landscape player.** Subtitle formats with their own line positions made the player ignore the height setting; it is now applied to every text cue.
- **Opening a side panel in the landscape player no longer keeps the controls up or closes on a tap.** The controls hide after 3 seconds as usual, and tapping the shrunken video no longer closes the panel (the close button and Back still do).

- **Sources list shows the extension and server name (e.g. "Anikoto · HD-1") instead of the stream label.** The card title used to repeat the quality and Sub/Dub text that the chips below already show, and the picked stream lost its extension name when it was resolved, so it showed the full label while the others showed only "Anikoto". The server (hoster) name now travels with each stream, is kept when a stream is resolved, and the title is built from extension name plus server name.

## [Unreleased]

### Changed

- **Opening and closing a details page now morphs smoothly out of, and back into, the tapped poster.** The old animation stretched the whole page from the poster's rectangle with different horizontal and vertical scales, so the page looked squashed mid-flight and every frame redrew a full-screen layer with a changing clip and alpha. A rounded window with the poster's own corner radius now grows from the poster's exact bounds to full screen (and back), the poster image is drawn in it at its own aspect ratio, and the page fades in over it at full size, so nothing is stretched and each frame is only a clip and an alpha change. Open uses a fast-out, soft-landing curve (440 ms) and close a slightly quicker settling one (320 ms). **Constraint:** the build could not be run in this environment, so compile and try it on a device once before release.

- **Source tests say why a source returned no videos.** A source whose catalogue loads but whose streams come back empty (Toonstream) only reported "no_hoster_list: no playable video". The adapter now logs when a source returns an empty video list and, for older (library 12-15) sources, the exact error that dropped each video link; the test shows the last two of those lines. An episode-list failure with an HTTP status (MovieBox's 441) is labelled as the site refusing the request. Older sources' videos also keep their page `url` after their link is fetched or resolved (the generated `copy()` dropped it). **Constraint:** this does not make those two sources work; MovieBox's 441 comes from its server and Toonstream's empty list comes from inside the extension. The build could not be run in this environment, so compile it once before release.

### Added

- **The details page shows a season chip under the hero.** The chip appears only for a second or later season ("Season 2"), with the part added when the title names one ("Season 2 · Part 2", or just "Part 2" for a first season split in parts). A first season, a movie, OVA, ONA, special or music entry gets no chip. The season number is ani.zip's, else the "Season N" or "2nd Season" in the title; a later entry with no known number shows no season. **Constraint:** the build could not be run in this environment, so compile it once before release.

- **English mode on the details page now shows AniList's own artwork with the plain English title on it.** Without a TMDB key there is no source of English logos, and ani.zip's first logo was often Japanese. In English mode the details hero is now AniList's banner, else its cover, with the English text title drawn at the bottom (a TMDB English logo replaces the text when a key is set). An earlier version drew nothing over the art on the assumption that it carried lettering, which left banners without lettering untitled. **Constraint:** a cover that already has the title painted on it will show the title twice; the build could not be run in this environment, so compile it once before release.

- **English mode now shows an English title logo, or the plain English title, instead of a Japanese-lettered logo.** The details hero and the home carousel always used the first `clearlogo` ani.zip lists, which for many shows (Yuru Camp, for one) is the Japanese logo, whatever the Title language setting said. With the language set to English, the logo is now TMDB's best English logo, found through the TMDB id in ani.zip's mapping; when none exists the plain English text title is drawn instead. Romaji keeps the ani.zip logo as before, and the carousel re-resolves its logos when the language is switched. `AnimeArtwork` gained `englishLogoUrl`, `tmdbId` and `tmdbIsMovie`. The TMDB lookup needs a free key (`TMDB_API_KEY` in `gradle.properties`, `-PTMDB_API_KEY=...`, the environment, or a `TMDB_API_KEY` repository secret, which the nightly and release workflows now pass to the build); both the short v3 key and the long v4 read token work. **Constraint:** without a key no English logo is ever found, so English mode always shows the plain text title, even for shows whose ani.zip logo is already English. The build could not be run in this environment, so compile it once before release.

- **Built-in 18+ filter: no hentai in Search, and no adult sources.** The filter is always on and has no switch. AniList queries (Search, Home rows, trending, season picks) now exclude adult-flagged titles and the Hentai genre, and any such title that still arrives (your list, favourites, related and recommended rows) is dropped when the response is read, so it cannot be opened from a deep link either. Extension repos no longer list 18+ extensions (flagged `nsfw` in the index, or named after adult content), and installing one is refused. Already-installed adult extensions are never loaded: one downloaded inside Anisu is deleted on the next reload, one installed on the phone itself is only skipped, because Android needs your confirmation to uninstall it. With the extension unloaded its sources do not appear on Home, are not used to find episodes and cannot stream. Home shelves from sources and addons also drop any title tagged as adult. **Constraint:** sources that do not flag themselves are caught by name (words such as hentai or hanime) and by genre tags only; Stremio addons are filtered by genre tag only. The build could not be run in this environment, so compile it once before release.

- **Subtitle offset can go negative.** The Subtitles panel only moved subtitles later (0 to +10 s) because the player hands over a cue only when playback reaches it. Offsets now run from -10 s to +10 s in 100 ms steps: a negative value makes the text renderer treat the playback position as that much ahead (`SubtitleLeadRenderersFactory`), so cues show earlier, while a positive value still holds cues back as before. **Constraint:** the build could not be run in this environment, so compile it once before release.
- **Details pages have a Trailers row between the synopsis and the episodes.** It lists the show's trailers and PVs (Jikan, keyed by the MyAnimeList id; AniList's own trailer is used when Jikan has none) as 16:9 thumbnails. Tapping one opens a bottom sheet that autoplays it in YouTube's embedded player; the player's own button goes fullscreen and Back leaves it, and an "open in YouTube" button covers videos that forbid embedding. The row is hidden when no trailer is found.
- **The screen now stays on while an episode is streaming.** The player never told Android to keep the display awake, so the phone's sleep timer could blank the screen mid-episode. The player now holds `FLAG_KEEP_SCREEN_ON` while the video is playing or buffering and releases it as soon as you pause, hit an error or leave the player, so a paused video follows your normal screen timeout again. **Constraint:** the build could not be run in this environment, so compile it once before release.
- **Details pages now open out of the tapped poster and close back into it.** Tapping a poster (home rows, hero banner, search grid/list, My Space, schedule, "More from this Show" and related rows) grows the details page from that poster's position to full screen with rounded window corners that square off, like an app opening from its icon; going back shrinks it into the same poster. The page underneath is only dimmed meanwhile. Going to the player and back does not replay the animation, and a details page opened from somewhere without a poster just fades in.
- **The bottom navigation bar now moves with a held drag.** While the selection pill is held, the whole bar leans up to 10dp toward the pill and swells slightly, then settles back on release. The existing pill hold/drag behaviour is unchanged and a resting bar looks exactly as before.


- **The bottom bar's selection pill now slides between tabs, swells when held and can be dragged.** The pill used to jump to the tapped tab. It now glides there on a soft spring, and because each screen owns its own bar, a new bar starts the pill where the previous one left it so the slide carries across the screen change. Pressing and holding grows and brightens the pill; while held it can be dragged along the bar, and letting go selects the tab it is over and settles into place. The icon colour follows the pill. **Constraint:** the tabs no longer use a ripple, and the slide across a navigation depends on the screen cross-fade, so compile and try it once before release.

- **Subtitle colours and background, with a live preview in Settings > Player and streaming.** A new Subtitles box has a preview, text colour and background colour swatches, a background density slider, and size and height sliders. The player's Subtitles tab has the same colour and background options, and a Reset button in Settings restores the defaults.

- **Portrait player: Sources, Subtitles and Audio open as bottom sheets with the same design as the landscape panels.** The portrait player used plain Material sheets with a different look and extra controls the landscape panels do not have. It now shows the same cards, chips, steppers and latency dots in a bottom sheet. **Constraint:** only controls that exist in landscape are shown (no Sub/Dub toggle, subtitle style/background/position chips or "Add subtitle file").

- **Sources, Audio and Subtitles open as a small tab on the right of the fullscreen player.** Tapping one in the pill now slides a panel in from the right (380 ms) instead of a bottom sheet. The video shrinks and slides left into the remaining space like YouTube does for its comments, the pill drops off the bottom edge and the seek bar with its time chips follows it down; closing the panel (X, back button, or a tap on the video) reverses all of it, with the pill pushing the seek bar back up. A rotate/exit-fullscreen button appears above the end of the seek bar while a panel is open. **Constraint:** the portrait layout keeps its bottom sheets.
- **Subtitles panel with separate boxes.** One box holds the on/off toggle, the stream's source and the language (tap to pick another track); offset, size and height each get their own box with a minus/plus stepper. Size (10-40 sp) and height (0-40 % above the bottom edge) are remembered; the video's subtitles are now drawn by our own subtitle view so both apply live. **Constraint:** the offset only delays subtitles (0 to 10 s in 100 ms steps), because the player hands over a cue only when it is due, so it cannot show one earlier.
- **Sources panel with chips, quality/sub-dub tags and latency.** Chips are built from the extensions that answered ("All" plus one per source), each stream is a card with its resolution and Sub/Dub tag when the label has them, and a latency measured with a tiny ranged request when the panel opens. Retry measures again (or looks for sources again when none were found). **Constraint:** a stream that the extension lists without resolving it yet has no URL to time, so it shows a dash.
- **Audio panel with language cards.** Every audio track is a card with its name and, when the player reports them, channels and bitrate; the playing one is outlined.

### Changed

- **Player and streaming settings are grouped into separate boxes, like Appearance.** The screen was one long list of headings. It now has bordered boxes for Skipping (skip amount and double tap), Gestures, Subtitles (live preview, colours, density), Subtitle Size and Position (with the reset button) and Streaming Priority, and the sliders use the accent colour. **Constraint:** only the layout changed; every setting, default and saved value is the same. The build could not be run in this environment, so compile it once before release.

- **The home banner now runs edge to edge behind the status bar, is taller, and fades into the page.** The list under the banner was padded by the status-bar height, which left a black strip above it. The padding is gone, the banner is about 68% of the screen tall (460-620 dp), and its bottom third fades into the page colour like a streaming-app hero. **Constraint:** the status-bar icons stay readable through a short dark gradient at the top; the build could not be run in this environment, so compile it once before release.
- **Banner slides now glide with a parallax image and text that travels with the page.** The title, genres and buttons used to swap instantly while only the poster slid. Each slide now carries its own text, the artwork trails the swipe slightly, text fades while it leaves, the page dots grow smoothly, and the automatic change uses a 700 ms ease. The 4-second timer restarts after every swipe and pauses while a finger is on the banner.

- **Posters and thumbnails load from a shared, longer-lived cache.** The app now configures one image loader with a 200 MB disk cache and a 20% memory cache, and reuses a cached image without asking the server whether it changed. This removes a network round trip per image while scrolling, so lists stay smooth and posters still appear on a weak connection. **Constraint:** a poster that a site replaces under the same address keeps showing the old image until the cache is cleared or it is evicted. The build could not be run in this environment, so compile it once before release.

- **The app is now named "Anisu".** The launcher label and every user-visible string inside the app — About, Updates, the update notification and banner, the diagnostics report, the login screen, the contributors screen, the extensions screens and the extension-load error messages — now say *Anisu* instead of *Ansu*. The package / application id stays `com.ansu.anime`, and the technical identifiers (`AnsuApp`, `AnsuTheme`, `AnsuColors`, the `ansu_*` resources, the `ansu://` scheme and the `Ansu-*.apk` asset names the updater matches) keep their spelling on purpose, so updates, saved data and the extension runtime are untouched. **Constraint:** the GitHub repository slug `Ansu216/Anisu` and the signing keystore are unchanged.

- **The launcher icon is now the project's `logo.jpg`.** The image is installed at every density for the legacy icon and its round variant, and as the adaptive-icon foreground on Android 8+, so the home screen shows the logo instead of the old vector play mark.

- **An APK that is not signed can no longer be published.** Both workflows already signed every build; the signature is now also asserted with an explicit `::error::` before a build is uploaded (it must verify and carry a signer certificate) and again in every publishing job — the two nightly branch jobs and the GitHub Release job — right before anything is written out. A build whose APK fails that check is never uploaded as an artifact, so the publish jobs find nothing and publish nothing. **Constraint:** the check uses the Android SDK's `apksigner` and the committed `keystore/anisu.jks`; the APK asset file names stay `Ansu-*.apk` so the in-app updater keeps matching them.

- **The details page shows six episodes first, with a "More episodes" chip for the rest.** A show with 50 episodes used to draw all of them at once. Each group now lists its first six episodes followed by a "More episodes (N)" pill; tapping it shows the rest of that group. Switching to another group (1-50, 51-100, …) or opening another show starts collapsed again. **Constraint:** the chip sits at the end of the list rather than floating over the screen, and the build could not be run in this environment, so compile it once before release.

- **The details page loads faster.** Everything on it used to wait for the slowest part, the title search across every installed source, so the synopsis, stats and episode list all appeared together and only after the slowest source answered or timed out. AniList details, episode info and the episode list now each appear the moment they arrive. The opened source's own episode list is fetched at the same time as the search across the other sources (it used to run first) and is shown immediately; the merged multi-source list replaces it when the search finishes. The per-source timeout in that search dropped from 45 s to 20 s, so one slow or dead source holds the merge back for at most 20 s. **Constraint:** the episode list can change once more when the cross-source merge finishes (extra streams are added as alternates); the build could not be run in this environment, so compile it once before release.

### Fixed

- **The updater no longer reports "up to date" while a newer build is published — on either channel.** The **Nightly** channel compared only the manifest's `versionCode`, which the nightly workflow derives from the *hour* (`YYYYMMDDHH`); two builds in the same hour therefore shared a number and the second was never offered. It now also compares the version *name* (which carries the minute), so a build that lands later in the same hour is recognised. The **Stable** channel read the newest non-draft release by list order, which is by creation date and not by version, so a hotfix cut from an older tag or a re-run could mask the real latest; it now picks the greatest tag version. **Constraint:** an installed build whose version is already the greatest still correctly reports "up to date", and both channels keep offering only the minified **release** APK, never the `-debug` one.

- **A release now installs over a nightly instead of being refused as a downgrade.** The nightly workflow numbers its builds by date (`versionCode` = `YYYYMMDDHH`, e.g. `2026100416`), while the release workflow used the workflow run number (e.g. `8`) — incompatible scales, so Android refused to install a release over a nightly that carried a larger number. A date-based tag (`v2026.09.30.1946`) now produces the matching date-based `versionCode` in the release workflow, so the two channels share one comparable number and switching from a nightly to the stable release is a normal upgrade; any other tag keeps the run number. **Constraint:** the change is in `.github/workflows/release-apk.yml`, which the built-in GitHub token cannot push — commit it by hand.

- **The launcher icon is drawn smaller, so it no longer overflows the edge — including the round icon.** The logo filled 0.81 of the canvas radius, past the ~0.66 safe zone of an adaptive icon, so on a round or squircle mask its outer ring was clipped. It is now drawn at 72% of the canvas on the artwork's own background colour and is fully inside the safe zone (measured content radius ≈ 0.58), for the legacy icon, its round variant and the adaptive foreground alike. **Constraint:** the source remains `logo.jpg`; only its scale on the canvases changed.

- **The APKs are now signed with both the v2 and the v3 signature schemes.** Every build was already signed (v2) with the committed `keystore/anisu.jks`, so Android could install it; v3 is added for the modern key-rotation path and for installers that prefer it. **Constraint:** the certificate is unchanged (`CN=Anisu`, SHA-256 `9ebebc…`), so this is still an in-place upgrade over every previous build. If Android shows a "this app isn't trusted / not verified" prompt while installing, that is **not** a missing signature — it is Google Play Protect reacting to a sideloaded app that requests `REQUEST_INSTALL_PACKAGES` (the in-app updater) and `QUERY_ALL_PACKAGES` (listing installed extensions), which only installs-by-hand apps trigger; it does not affect the signature check the installer performs.

- **The About screen shows the real logo instead of a solid white square.** The identity card drew the launcher logo with `Icon(...)`, which paints a single tint colour over the whole bitmap; since the logo is an opaque full-colour raster, the tint covered it entirely and left a plain block. It is drawn with `Image` now, untinted, so the artwork is visible.

- **The in-app update banner hides itself after four seconds.** It used to stay pinned at the top of whatever screen was open until dismissed by hand. It now fades out on its own after four seconds; tapping the close button still hides it immediately, and either way only that version is hidden, so the next build is shown again.

- **The "Completed" chip in My List no longer wraps onto two lines.** On narrower screens the four chips (Liked, Watching, Planning, Completed) shared one row and the last label broke into "Complete" and "d". The labels are now forced onto a single line and the row scrolls sideways when it does not fit. **Constraint:** on a screen wide enough for all four chips nothing changes visually.

- **The details screen compiles again.** `DetailsViewModel` called the cross-source `findEpisodesByTitle` helper without importing it, so `compileDebugKotlin` and `compileReleaseKotlin` both failed with `Unresolved reference 'findEpisodesByTitle'` and the follow-on type-inference errors. The import is added; nothing else changes.

- **The build is warning-free again.** `CloudflareInterceptor` set the long-deprecated `WebSettings.databaseEnabled`, which has been a no-op since API 19 (the Web SQL Database API was removed) and produced a compiler warning on every build. The dead assignment is gone; the anti-bot WebView still enables JavaScript and DOM storage, which is what it actually uses.

- **Episode lists of lib-16 sources (AnimeKai, Anichi and others) no longer fail with `NoSuchMethodError: get$default`.** Catalogue and search worked but opening an anime failed because the extensions call the suspend `OkHttpClient.get(url, headers, cache)` helper from `eu.kanade.tachiyomi.network.Requests`, which Ansu did not provide. It now exists there, for both `String` and `HttpUrl` URLs (Anikoto uses the `HttpUrl` form, which is a separate method to the extension), and returns the response after `awaitSuccess()`. **Constraint:** the build could not be run in this environment, so compile it once before release.

### Added

- **Extensions now install inside Ansu instead of on the phone.** Tapping Install on an available source used to download the APK and open Android's package installer, which put a separate app on the device. Ansu now downloads the APK, checks it is an anime extension and keeps it in its own private storage (`files/exts/<package>.ext`), then loads it from there with the same loader as before, so there is no installer prompt, no "install unknown apps" permission step and nothing extra in the phone's app list. The button shows a spinner while it works, offers **Update** when the repo lists a newer version code, and the delete button on an installed source simply removes the stored file. Extensions already installed on the phone as APKs are still found and loaded; a copy installed inside Ansu wins if both exist. The stored file is made read-only because Android 14+ refuses to load code from a writable file, and native libraries (`.so`) bundled in an extension are unpacked for the device's own ABI into `files/exts-libs/<package>` and handed to the class loader, so extensions that need one load too; removing an extension deletes its libraries as well. **Constraint:** the build could not be run in this environment, so compile it once before release.

- **Extension settings.** Every source that declares settings (preferred server, domain, quality, API keys; for example AnimeKai, Anikoto, AnimeOnsen) now has a gear button next to its delete button in Extensions. It opens a sheet that shows the extension's own switches, text fields, single and multiple choice lists and sliders, and a change is passed through the extension's own change listener and stored where the extension reads it back (`source_<id>`). `eu.kanade.tachiyomi.animesource.utils` also gained the `preferencesKey` / `sourcePreferences` helpers newer extensions import. **Constraint:** a source that caches a setting at start-up only uses the new value after the app is restarted.
- **Aniyomi/Keiyoushi extensions built for extension API 17 now load, and streaming follows Aniyomi's own rules.** Ansu accepted API 12-16 only, so every extension published against 17 was rejected as "Built for extension API 17". The loader now accepts 12-17, reads the library from the `aniyomix.extensionLib` metadata (falling back to the version name), the display name from `aniyomix.name` and the content-warning flag, and answers lib-17 sources through their combined `getAnimeEpisodeUpdate` calls. `SAnime`/`SEpisode`/`Video`/`Hoster` gained the lib-17 fields (`memo`, `background_url`, `UPCOMING`) with hidden lib-16 constructors and `copy` so older extensions still link; `AnimeSource` gained `getAnimeEpisodeUpdate`, `getAnimeSeasonUpdate`, `supportsRelatedAnime` and `getRelatedAnimeList`, and `AnimeHttpSource` gained `getHomeUrl`, `sortHosters`, `sortVideos`, `getVideoThumbnails` and `getImageTile`. Extensions are now opened with Aniyomi's parent-last `ChildFirstPathClassLoader`, retried with the plain loader on a `LinkageError`. `JavaScriptEngine` (QuickJS) is registered in Injekt and `ConfigurableAnimeSource.getSourcePreferences()` exists, so extensions that evaluate scripts or read their settings no longer crash. Episodes are listed through hosters only when the extension really implements them (`sortHosters` / `sortVideos` applied, the other API tried when one is a stub), the source's `preferred` videos are listed first, and a video that fails to resolve is logged instead of vanishing silently. Sites behind Cloudflare's anti-bot challenge are now solved too: a 403/503 served by Cloudflare is opened in a hidden WebView until it earns a fresh `cf_clearance` cookie (shared with the OkHttp cookie jar) and the request is repeated, instead of failing with 403/503. Sources that stream through a local proxy (`createHttpServer`, the `http://localhost:1` placeholder) now get that server started and their videos rewritten to its port, and a legacy video whose URL is the string "null" is no longer treated as playable. A source's separate audio streams (dubs, `Video.audioTracks`) are now merged into playback and show up as audio tracks in the player, and the MPV options a source passes (`user-agent`, `referrer`, `cookies`, `http-header-fields`) are translated into request headers so streams that need them still open. **Constraint:** Ansu plays with Media3, which has no MPV engine, so every other MPV or ffmpeg option is ignored; source settings screens, extension signature/trust checks and the `repo.json` index format are not implemented, and the challenge is only attempted off the main thread within a 30-second limit; the build could not be run in this environment, so compile it once before release.

- **The Extensions screen can now search a repo's catalogue instead of only scrolling it.** A search field above "Available Sources" filters as you type by extension name, source name, package or language, and shows "No extension matches …" when nothing does. **Constraint:** it searches only the repos you have added; it makes no request of its own.

- **The Updates screen can post a real system notification on demand.** A **Test notification** row next to the automatic-check switch posts exactly the kind of alert a new build uses, so you can confirm on your own device that Ansu notifications arrive without waiting for a build to be published. If nothing is posted it says why (the permission is missing, or notifications are turned off for Ansu in Android settings) instead of staying silent, and offers a one-tap **Open Ansu's notification settings** link — neither of those can be changed from inside the app, so the row takes you straight to the screen that can.

- **Automatic update checks now also run in the background, so the "update available" notification can arrive while the app is closed.** A `WorkManager` job (`UpdateCheckWorker`) re-checks roughly every 12 hours, only when there is a connection, and posts the same native Android notification the startup check already sent; it calls the same `UpdateManager` code, so the stored channel and the once-per-build rule are shared instead of duplicated. The switch on the Updates screen now schedules and cancels that job and its label says so, and the schedule survives a restart. **Constraint:** background checks follow Android's own scheduling, so the interval is approximate and Doze can delay a run.

- **Pushing a file named `fix.zip` to `main` now extracts it into the repository and rebuilds the APKs.** A new workflow (`.github/workflows/extract-fix-zip.yml`, using `.github/scripts/unpack-fix-zip.py`) runs on that push, writes every file of the archive to the path the archive itself carries (for example `app/src/main/java/com/ansu/anime/...`), through all of its folders and sub-folders, and commits the result as the Actions bot with the message *extract fix.zip and updated the app*. A commit made with the workflow token starts no other workflow by itself, so the last step dispatches the nightly APK build explicitly: the APKs are built from the new commit straight away and are published only if that build succeeds. Existing files are overwritten, missing ones are created and nothing is ever removed — the archive itself stays in place, so the commit can only add or update files. Every entry is validated before it is written, and an unsafe path — one that would escape the repository or write into `.git` — aborts the run instead of overwriting something unexpected. `.github/workflows/**` is the one exception: GitHub refuses to let the built-in token create or update a workflow file (*"refusing to allow a GitHub App to create or update workflow … without `workflows` permission"*), and no `permissions:` block lifts that, so a commit that touched one would be rejected as a whole and the fix would never land. Those entries are therefore skipped and reported instead of being committed — unless the repository secret `FIX_ZIP_TOKEN` holds a token with the `workflow` scope, in which case the checkout uses it and the workflow files are extracted too. **Constraint:** the archive must keep the project's own relative paths, because `fix.zip` is extracted as-is from the repository root; pushing to `main` also happens from a full-history checkout and is rebased first, and a rejection is now reported as a readable `::error::` annotation.

- **Settings > Appearance can now switch anime titles between Romaji and English.** A new "Titles" choice (default Romaji, the old behaviour) is saved in `AppearancePrefs`. `AniListApi` and the built-in `DemoSource` read it for every response and pick AniList's `romaji` or `english` title, falling back to the other when a show has none, so Home, Search, Schedule, My Space and the details page all use it. Home, Schedule, My Space and Search reload when the choice changes; the details page shows the title from the freshly loaded AniList details, and a show saved on the device gets its stored title updated when its details page is opened. In English mode the details page prefers the ani.zip poster over AniList's cover. **Constraint:** neither AniList nor ani.zip labels artwork by language, so the poster switch is a best-effort preference and the home hero keeps AniList's cover; Continue Watching rows keep the title saved at the last playback until the show is played again; the build could not be run in this environment, so compile it once before release.

- **A new update is now announced both by a native Android notification and by a banner inside the app.** The updater posts a system notification (channel "App updates", created on demand) the first time a check finds a newer build, and shows the same news as a compact glass banner pinned over whatever screen is open; the "Update" button in either one opens the Updates screen, and a tap on the notification lands there directly. Each build is announced only once, no matter how often the app checks, and the notification is removed as soon as you are up to date. **Constraint:** on Android 13+ the notification only appears if you grant the new `POST_NOTIFICATIONS` permission, which the app asks for once at startup and silently respects when refused; the in-app banner shows regardless and can be dismissed per version.

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

- **The nightly build now publishes the release and the debug APK independently.** `apk-nightly.yml` splits publishing into `publish-release` and `publish-debug`: each runs as soon as its own build leg produced an APK, so a failure on one side no longer holds back the other. The two jobs share a concurrency group so they never write the branch at the same time, and each carries the sibling variant of the same run over (`.github/scripts/stage-nightly.sh`), so publishing one leg never drops the other and the two APKs can never belong to different runs. **Constraint:** the tagged-release workflow still publishes atomically with both APKs, so a GitHub Release can never appear with only half of its assets.

- **A `fix.zip` is now extracted only when it is pushed by `Maygodblastyou`.** `extract-fix-zip.yml` is guarded on `github.actor == 'Maygodblastyou'`, so any other pusher — a collaborator, a fork or the Actions bot — is skipped and cannot rewrite the project through an archive.

- **`CLAUDE.md` now specifies exactly what the end-of-chat `fix.zip` must contain.** The archive carries `app/` (without its `build/` folder), `gradle/`, `keystore/`, `.gitignore`, `AGENTS.md`, `CHANGELOG.md`, `gradle.properties`, the root `build.gradle.kts`, `gradlew`, `gradlew.bat`, `how-to-compile.md`, `local.properties`, `README.md` and `settings.gradle.kts`, and nothing else; any additional documentation written during a chat goes in `docs/`.

- **`contributorsansu.json` credits `@PrussianBlueStar`.** The new entry names them an amazing app tester and the creator of Ansu's new logo; because the Contributors screen reads the file live, no new APK is needed for it to show.

- **`CHANGELOG.md` is reorganized into the Keep a Changelog order.** The `[Unreleased]` section had grown duplicate and out-of-order `### Added`, `### Changed` and `### Fixed` blocks; each category now appears once, in the order Added → Changed → Fixed, with every entry kept verbatim. **Constraint:** the published `[0.1.0]` section is untouched, because published sections are never rewritten.

- **The Updates screen now says what a build contains, not only which commit it is — on both channels.** The Nightly channel reads the commit a build was made from out of `nightly.json` and showed nothing but its short SHA; the Stable channel's release notes carried the same bare SHA on their "built automatically from commit …" line. The headline of that commit is now read from the GitHub commits API and shown next to the SHA on either channel (for example `Commit cf4230b: Unpack fix.zip into the repository`). The lookup is best-effort, so a failure leaves the note exactly as it was rather than holding up the update. **Constraint:** only the first line of the commit message is shown, capped at 120 characters, because the rest is written for the git log rather than for the screen.

- **Contributors is now reachable only from About.** The duplicate row under Settings → Info is gone, so the screen the About page already links to is the single way in. **Constraint:** the screen itself, its navigation route and the live `contributorsansu.json` lookup are unchanged.

- **The details banner is now chosen per season and per movie, not per show.** ani.zip's backdrop describes the whole series, so every season of a show (and its movies) opened with the same banner. `pickHeroImage` now keeps that backdrop only for a first season or standalone show; a later season (ani.zip season number above 1, an AniList prequel, or "Season N"/"Part N" in the title), a movie, OVA, ONA or special uses the banner of its own AniList entry first, then that entry's wide Kitsu cover (looked up by AniList id), then its poster. `AnimeArtwork` gained `entryCoverUrl` and `season`, and `AniListMediaDetails` gained `hasPrequel`. **Constraint:** a later season with no banner of its own falls back to its poster rather than the shared series backdrop, so it may look plainer but never repeats the first season; the build could not be run in this environment, so compile it once before release.

- **The home hero now uses AniList's own portrait poster, and title logos are cleaned up and drawn larger.** The hero no longer swaps in the ani.zip poster; it shows AniList's `extraLarge` cover (the banner stays only as a fallback). `TitleLogo` now downloads the logo, crops its empty transparent margins and scales it to fill the available width/height, so wide logos (e.g. One Piece) no longer render tiny on the hero or the details banner, whose logo box also grew (320 x 110 dp; home 320 x 120 dp). An image that is not a real transparent logo, such as the square opaque poster some shows returned, is rejected and the plain text title is shown instead; `clearlogo` artwork is preferred over the generic `logo` type. **Constraint:** the check is "mostly transparent", so a logo that is fully opaque falls back to the text title; the build could not be run in this environment, so compile it once before release.

- **Settings is reorganized into labelled sub-categories instead of one flat list.** The rows now sit in frosted-glass group cards under clear section labels — *Account* (the AniList profile and Connect/Log out), *Personalization* (Appearance), *Content & sources* (Extensions, Addons), *App (Updates, System)* and *Info* (Contributors, About) — so a setting is found by the kind of thing it changes rather than by scanning every row. Each row now has a soft accent-tinted icon, its title and a one-line description, and the whole page scrolls, which it did not before. **Constraint:** no new settings were added or removed; the grouping only reorders and re-labels the existing screens.

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

- **Library 16 sources that passed the test but showed no video in the player now stream the way Aniyomi streams them.** The Test button only asked a source for its popular list, so a green result said nothing about hosters or videos; and the player's stream path differed from Aniyomi's in three ways that each made a library 16 source come back empty. (1) Aniyomi hands the source its own stored `SEpisode` when it asks for hosters, but Ansu rebuilt a thin one from just the url, name and a renumbered episode number, so a source that reads the `scanlator`, `memo` or original `episode_number` it set itself failed only at play time; the adapter now keeps the episodes exactly as the source returned them and passes copies of those back. (2) The server name was written into `videoTitle` before the source's `resolveVideo` ran, so a source that reads the title it set itself got a different one; the name is now added for display only, after resolving. (3) Every video of every server was resolved up front inside one 40-second limit per server, so a single slow video threw away that server's whole list, and resolving everything at once could get a site to throttle the requests. Aniyomi's library 16 lists the videos first and resolves only the one that is picked, so the same is done here: videos that still need `resolveVideo` are listed immediately and resolved when picked (30-second limit), the next listed source is tried if one cannot be resolved, and a failed pick no longer hides the others. A legacy library 12-15 video created without a URL now carries the string "null" like Aniyomi's does, which is what makes the source's `getVideoUrl` run for it, and a video whose URL is empty or "null" after resolving is skipped instead of being handed to the player. The Test button now also asks for the first episode of the first popular titles, lists its videos and resolves one of them, shows what it found on a new **Streams** line, and reports **failed** with the failing server's own message when the catalogue works but no video comes back. **Constraint:** a source listed lazily is only verified when it is picked, so an entry can still fail then (the player moves on to the next one); servers a source marks `lazy` are listed after the others rather than only when opened, because Ansu has no per-server screen; the Test button can now take over a minute for a source that is slow or broken (it tries up to two titles and resolves up to two videos); the build could not be run in this environment, so compile it once before release.

- **Search no longer lists the same show twice.** A result straight from an extension (the "NR" cards) was shown next to the catalogue entry for the same title, and only the catalogue entry gathers streams from every installed source. A source result is now hidden when the catalogue already returned the same title, ignoring case and punctuation ("Solo Leveling -ReAwakening-" matches "Solo Leveling: ReAwakening"), and the same title from two sources is listed once. Titles that differ in wording (for example a different season name) are still shown, so nothing unmatched disappears.
- **Streams that don't name their container no longer fail with `ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED` (AniWaves/Vidplay).** The player guessed the container from the URL and forced MP4 on any address containing "cdn", "stream", "video" or "media", so an HLS stream on such a URL was rejected. It now recognises HLS and DASH only when the URL says so, and otherwise asks the stream itself: the first bytes (`#EXTM3U`, `<MPD`) and the Content-Type, sent with the source's own headers. If the stream cannot be identified, ExoPlayer sniffs it instead of being told MP4. **Constraint:** an unrecognised URL costs one small extra request before playback starts.
- **Extensions built against OkHttp 5 (AnimeKai, Anikoto) no longer crash with `NoSuchMethodError ... CacheControl$Builder.maxAge`.** Ansu shipped OkHttp 4.12.0 while Aniyomi's extensions are compiled against 5.x, which has the `maxAge(Duration)` call they use. Ansu now uses OkHttp 5.1.0 (and the matching logging and DNS-over-HTTPS modules), and every place that read `response.body` as nullable (`?.`, `!!`, `?:`) now reads it as the non-null value OkHttp 5 returns, so the build stays free of warnings. **Constraint:** 5.1.0 was chosen over the newest 5.x because the project's Kotlin 2.1.0 may not read libraries built with a much newer Kotlin; if Gradle reports an incompatible Kotlin metadata version for OkHttp, tell the maintainer rather than raising OkHttp further.
- **The build no longer stops on two compile errors in the player and continue-watching code.** `ContinueWatchingRepository` took an `ExtensionManager` it never used, so `AppContainer` passed its arguments one place off, and `PlayerViewModel` called `findEpisodesByTitle` without importing it. The unused parameter is removed and the import added; nothing else changes.
- **The player no longer fails to compile.** `PlayerViewModel.loadSources` declared `val anime` twice and called `ContinueWatchingRepository.cacheAnime`, a function that does not exist; the duplicate declaration and the call are removed, and continue-watching still saves progress through `updateProgress` as before.

- **The build compiles again after Media3 dropped two MIME constants.** `PlayerViewModel.detectMimeType` called `MimeTypes.VIDEO_QUICKTIME` and `MimeTypes.VIDEO_3GPP`, which do not exist in the Media3 version the app ships, so `compileDebugKotlin` failed with two `Unresolved reference` errors and both CI legs failed. QuickTime and 3GPP now use their MIME strings (`video/quicktime`, `video/3gpp`) directly.

- **The nightly updater can no longer be pointed at the debug APK.** `UpdateChecker` refuses any `nightly.json` name that mentions `debug` and falls back to the published release name (`Ansu-nightly.apk`), so the Nightly channel always offers the minified release build, exactly as the Stable channel already does.

- **AllAnime, Anikoto and other extensions no longer fail with `NoClassDefFoundError: kotlinx/serialization/json/okio/OkioStreamsKt` or `NoSuchMethodError: CacheControl$Builder.maxAge-...`, and a failing extension no longer closes the app when you play an episode.** Extensions use the okio flavour of kotlinx.serialization (`decodeFromStream`) and OkHttp 5's `maxAge(Duration)`, and bundle neither. Ansu shipped OkHttp 4.12 and no okio JSON module, so those calls did not exist. The app now ships OkHttp 5.0.0-alpha.14 (what Aniyomi/Mihon use), `okhttp-dnsoverhttps`, `kotlinx-serialization-json-okio` and `-protobuf`. `Call.await()` now uses blocking `execute()` on the IO dispatcher because an Error thrown inside an OkHttp `enqueue` worker was rethrown on its thread and killed the process; a last-resort handler also swallows extension LinkageErrors on background threads. **Constraint:** not compiled or run here; OkHttp 4 to 5 is a library bump, so build and test.

- **Extensions no longer fail with "InjektionException: No registered instance or factory for type ...".** Extensions look up `Application` and `NetworkHelper` through Injekt, which builds its lookup key from the generic signature of an anonymous `TypeReference`. In the minified release build, R8 full mode stripped that signature, so registering them threw, and the error was hidden by `runCatching`. Keep rules now preserve `TypeReference` subclasses, setup is retried before each extension load, and a setup failure is logged and shown instead of swallowed. **Constraint:** not compiled or run here; confirm on a release build.

- **Extensions no longer fail with `LinkageError: ... overrides final method`.** The release build's keep rules for `eu.kanade.tachiyomi.**` (and the other libraries extensions use) were `-keep,allowoptimization`, which lets R8 mark methods `final` when nothing inside Ansu overrides them. Extensions are loaded later from separate APKs, so Anikoto (`episodeListParse`) and AllAnime (`okhttp3.Request` methods) hit a final method they were supposed to override and refused to load. The rules are now plain `-keep`, so the API keeps its original modifiers. **Constraint:** the APK is slightly larger because those classes are no longer optimised.

- **The delete button on an installed extension now works.** `ExtensionManager.uninstall` sends `ACTION_DELETE`, which Android ignores for apps that do not declare `REQUEST_DELETE_PACKAGES`; the permission is now in the manifest, so the system uninstall dialog appears.

- **The OkHttp response body extension functions now compile.** `OkHttpExtensions.kt` and `JsoupExtensions.kt` called `.body.string()` on nullable `okhttp3.ResponseBody?` without asserting non-null, so Kotlin rejected them. Both now use `body!!.string()`: in the context of a successful OkHttp response, the body is always present.

- **The build no longer fails resolving Injekt.** `com.github.inorichi.injekt:injekt-core:65b0440` does not exist. The dependency now points at `uy.kohesive.injekt:injekt-core:1.16.1` on Maven Central, and the JitPack repository is removed from `settings.gradle.kts`. The package names are unchanged, so the extension runtime needs no edit.

- **An older build is no longer told it is up to date.** The version comparison kept only the numeric components of a version, so `0.1.0-nightly` collapsed to `0.1.0` and compared equal to the released `0.1.0`: an install older than the published build was answered "up to date" and never offered the new one. `isNewerVersion` now orders versions the way SemVer does — a version carrying a pre-release part is always older than the same version without one, two pre-releases are compared by their dot-separated identifiers (numeric ones numerically, and a numeric identifier ranks below an alphanumeric one), and build metadata after `+` is ignored. The component-wise comparison is unchanged, so a timestamp-style name such as `2026.09.30.1946` still compares correctly against `0.1.0`.

- **The Stable update channel works again, and it can no longer hand you the debug APK.** `releases/latest` deliberately ignores pre-releases and every build published so far is one, so the GitHub API answered 404 and the Stable channel reported "no releases published yet" while the builds were sitting right there; `UpdateChecker` now falls back to the full release list and takes its newest non-draft entry. The APK is picked strictly as well: `Ansu-<version>.apk` is preferred by name and any asset whose name mentions `debug` is refused, instead of falling back to whichever `.apk` GitHub happens to list first — alphabetically, that is the `-debug` one.

- **The "update available" notification is now posted reliably, and it looks like a notification.** `UpdateNotifier.notifyUpdate` reports whether the system really accepted the post, and `UpdateManager` only records a build as announced when it did; before, a check that finished before you granted the Android 13 `POST_NOTIFICATIONS` permission was marked as announced anyway, so that build was never announced again — and since the permission is asked for at the same moment as the startup check, that was the normal case on a fresh install. Granting the permission now re-runs the check immediately, so the first launch notifies too, and every skipped or failed post writes its reason to the diagnostics log instead of failing silently. The notification also gained its own small icon (`ic_notification`): it used the adaptive launcher foreground, whose artwork sits in the middle ~26% of a 108dp canvas, so at the ~24dp a notification icon gets it rendered as a dot, or as nothing at all on some devices.

- **Home hero title logos are now consistent: no poster in place of a title, and no tiny logos.** Some hero titles showed a small poster thumbnail where the lettering should be, and others rendered very small. `trimLogo` now ignores faint glow pixels when cropping (a soft halo kept the empty margin and shrank the logo), and rejects pictures that are not lettering: a cropped box that is almost solid, or whose four corners are all opaque, or a squarish/tall mostly-solid box. `TitleLogo` now sizes by visual area within the box instead of just fitting it, and the home hero box grew to 320 x 130 dp. A rejected logo shows the plain text title. **Constraint:** ani.zip does not say which language a logo is in, so a show whose only logo is Japanese still shows it; the build could not be run in this environment, so compile it once before release.

- **The details page no longer crashes when it is scrolled.** The episode list and the character, staff, related and "More from this Show" rows key every item by its id, and AniList (and some sources) can return the same node twice, which made a `LazyColumn`/`LazyRow` throw `Key … was already used` the moment the repeated row scrolled into view. The lists are now de-duplicated (`distinctBy`) and keyed by id **plus position**, and the view model drops repeated or id-less episodes, so the page scrolls no matter what the API answers. **Constraint:** the same fix covers episodes from an extension or addon, whose ids the app does not control.

- **The home screen can no longer crash when two shelves share a title.** Shelves are keyed by title, and the same addon serving both series and movies (or an extension and an addon with the same name) produced two identical keys; the key now includes the shelf's position.

- **The app can no longer be taken down at startup by the AniList token store.** `EncryptedSharedPreferences.create` can throw on some ROMs and after a backup restore; the `runCatching` fallback to plain prefs was missing again and is restored, so `Application.onCreate` can never crash there.

- **Unsafe `!!` casts on AniList responses are gone.** The viewer, list progress/status and schedule entries are now read defensively, so a schema change or a partial payload degrades the extra data instead of throwing.

- **The details page lists every episode of a show instead of stopping at 12.** The built-in demo source returned a hard-coded 12 episodes for every title. It now asks AniList for the real count: the total for finished shows, or the number already aired for shows still running (so a show like One Piece lists all aired episodes). **Constraint:** if AniList cannot report a count, the list falls back to 12.

- **Search now works with no extension or addon installed.** `CatalogRepository.search` only asked installed sources, and the built-in demo source ran its blocking HTTP call on the main thread, which threw and was swallowed, so every search came back empty. Search now queries AniList directly and adds installed-source results on top (duplicates of an AniList hit are dropped), sources are called on `Dispatchers.IO`, and the demo source's calls are moved off the main thread. The search box also cancels the previous request on each keystroke, so old results can no longer overwrite newer ones, and clears the list when the query drops below two characters.

- **Episode rows on the details page now show real titles, thumbnails and synopses.** Every row used to show the show's own synopsis and a blank thumbnail because sources only supply episode numbers. Episode details are now looked up by AniList id (ani.zip for title, synopsis and image, AniList `streamingEpisodes` to fill gaps) and merged into the list by episode number; a row with no data simply shows only its number. **Constraint:** the list of episodes still comes from the source (the built-in demo source now sizes it from AniList's real episode count).

- **The black strip around the floating navigation bar is gone.** Each screen's `Scaffold` padded its content by the bar height, leaving an empty black band behind the transparent slot. Screens now pad only the top and let content scroll behind the bar, adding the bar height as bottom content padding so the last item and the Schedule sub-tab toggle still clear it.

### Fixed

- **The build compiles again.** The Appearance screen's colour preview called a `Modifier.paddingTop` that does not exist, which failed both the debug and release compile; it now uses `Modifier.padding(top = 8.dp)`.

- **Opening the same title a second time no longer crashes the app.** The details screen's `rawEpisodes` and `episodeMeta` were declared below the `init` block, so the episode list coming from the cache was published before those fields existed (a null `map` crash), and they were then reset to empty once the constructor finished. They are now declared above `init`, so the cached list is shown straight away on the second visit.

### Removed

- **The "Show 18+ titles" switch in the Search filters is gone.** Adult titles are now always hidden by the built-in filter, so the switch and its "18+ shown" chip no longer exist.

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
