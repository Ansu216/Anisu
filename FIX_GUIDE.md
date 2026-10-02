# Ansu: extension API v16 support + crash fixes

NOT COMPILED. This environment had no Android SDK / Kotlin compiler, so none of this has been built or run.
Build it (`./gradlew assembleDebug`) and fix any compile error first; send me the output if there is one.

## What changed
Extension API library 16 replaced "episode -> videos" with "episode -> hosters -> videos".
Both generations now live side by side in `eu.kanade.tachiyomi.animesource`, so lib 12-16 extensions load.
`AniyomiRuntime.MAX_LIB_VERSION` is now 16.

| File | Change |
|---|---|
| `animesource/model/Hoster.kt` (new) | lib 16 `Hoster` + `toHosterList()` |
| `animesource/model/Video.kt` | lib 16 data class `Video` (videoUrl, videoTitle, resolution, bitrate, preferred, timestamps, mpvArgs...) plus lib 12-15 constructors, `url`, `quality` |
| `animesource/AnimeSource.kt` | adds `getSeasonList`, `getHosterList`, `getVideoList(hoster)`; keeps `getVideoList(episode)` |
| `animesource/online/AnimeHttpSource.kt` | hoster/season requests+parsers, `resolveVideo`; lib 12-15 extensions fall back to the old flow automatically |
| `animesource/online/ParsedAnimeHttpSource.kt` | hoster/season selectors; formerly-abstract members are `open` so no extension hits `AbstractMethodError` |
| `animesource/LegacyApiException.kt` (new) | marker used to pick the right generation |
| `extension/aniyomi/AniyomiSourceAdapter.kt` | loads hosters in parallel (4 at a time, 25 s each); one failing hoster no longer kills the rest |
| `extension/aniyomi/AniyomiExtensionLoader.kt` | shows the real exception instead of `InvocationTargetException` |

## Why Test Source / Play crashed (likely cause)
Network calls went through RxJava 1, which rethrows `LinkageError`s (`NoSuchMethodError`, `AbstractMethodError`,
`NoClassDefFoundError`) that a mismatched extension raises. Nothing catches those, and `SourceTester` only caught
`Exception`. Now: requests use suspend `awaitSuccess()` (no Rx), `SourceTester` catches `Throwable`, and the adapter
converts such Errors into a normal exception shown in the UI. Full traces are logged under tags
`AniyomiLoader`, `AniyomiSource`, `SourceTester`, `PlayerViewModel`.

## Known limits (be aware)
- Signatures were rebuilt from the published lib 16 docs and other projects' notes, not from the stub AAR, so a
  rarely used member can still be missing. If an extension shows `NoSuchMethodError: ...` or `NoSuchFieldError: ...`,
  send me that text and I'll add exactly that member. (`ChapterType` entries are my best match.)
- Lib 17 is not supported (needs more members: memo, related anime, episode/season updates, HttpServer).
- Extensions' own settings screens are still not shown (unchanged).

## Update: "UnsupportedOperationException: Failed to load sources"
An extension stub threw a message-less UnsupportedOperationException while listing hosters.
- `AnimeHttpSource.getHosterList` now falls back to the lib 12-15 flow on any UnsupportedOperationException.
- `AniyomiSourceAdapter.getVideoList` retries via `getVideoList(episode)` and, if that fails too, reports
  "<source>: extension cannot list videos ... at Class.method" so the next error names the culprit.
NOT COMPILED.

## Update: playback crash, multi-source, movie buffering (NOT COMPILED)
- Crash `ClassNotFoundException: HlsMediaSource$Factory`: `media3-exoplayer-hls` (and dash) were never a dependency. Added + R8 keep rules.
- Only AniWave listed: title matching kept only the best source. Now every matching source's episode is attached as `SEpisode.alternates`; the player queries all in parallel and merges (one failing source is skipped).
- Player error now falls through to the next listed source.
- Movies: larger buffer (30-120 s), playback starts after ~1.5 s, buffering spinner.
