# Player side panels (Sources / Audio / Subtitles)

Landscape (fullscreen) player only. The portrait layout still uses its bottom sheets.

## Files

| File | What it does |
|---|---|
| `ui/player/PlayerSidePanels.kt` | `PlayerPanel` enum, the panel frame (slide + close button), `SubtitlesPanel`, `SourcesPanel`, `AudioPanel` and their building blocks |
| `ui/player/PlayerScreen.kt` | Owns `panel` + one `panelProgress` animation (380 ms, FastOutSlowIn). Shrinks and slides the video (`graphicsLayer` scale + translate of one layer), narrows the controls area (`freeWidth`), draws the panel |
| `ui/player/PlayerControls.kt` | `PlayerControlsOverlay(panelProgress = ...)`: the pill drops away (`dropAway`) so the seek bar and chips move down; the rotate button grows in above the seek bar (`growVertically`) |
| `ui/player/PlayerViewModel.kt` | `cues` flow (delayed by the subtitle offset), `setSubtitleOffset`, `measurePings`, `retrySources`, `PlayableSource.extensionName`, `TrackOption.detail` |
| `data/prefs/PlayerPrefs.kt` | `subtitleSize` and `subtitleHeight` |

## How the animation fits together

One `Animatable<Float>` (`panelProgress`, 0 = closed, 1 = open) drives everything. It is only read inside
layout / graphics-layer blocks, so a frame re-runs layout and draw, never composition.

- Video + subtitle layer: scale `1 -> (freeWidth / shownVideoWidth)` and translate `-panelWidth * p / 2`.
- Controls area: width `full - panelWidth * p`, so title, transport buttons and seek bar re-centre.
- Pill: height `H * (1 - p)` while sliding down and fading; the bottom-anchored column pushes the seek bar down.
- Panel: `translationX = (1 - p) * width`.

Tune the speed with `PANEL_ANIMATION_MS` in `PlayerScreen.kt`, the width with `PANEL_WIDTH_FRACTION` in `PlayerSidePanels.kt`.

## Known limits

- Subtitle offset can only delay (the player never hands over a cue early).
- Latency needs a resolved URL; unresolved streams show a dash.
- The build was not run where this was written (no Android SDK): compile once (`./gradlew assembleDebug assembleRelease`) before release.
