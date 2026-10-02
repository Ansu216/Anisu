
## CRITICAL: Movies from extension sources
Movies from extension sources are often stored differently (not as episodes in `getEpisodeList()`).
- If a movie-format title has 0 episodes, the app now creates a synthetic "1f" episode so playback can query it.
- If your movie still fails: the extension itself may not support movies, or its `getVideoList()` returns no streams. Check the extension's source code or try a different one.

