# Changelog (developer)

All technical changes to this project, written for whoever works on the code next.
`CHANGELOG.md` says what changed for the person installing the APK; this file says
how it was done. The method is the one `PiBOH/vivi-music-de` uses: both files are
updated in the same change, with the same headings and the same version sections.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## How this file is used

- Every change adds an entry to `## [Unreleased]` here **and** in `CHANGELOG.md`.
- Categories are the Keep a Changelog ones, in order: `### Added`, `### Changed`,
  `### Deprecated`, `### Removed`, `### Fixed`- **Nav bar scroll-hide lives in `AnsuNavGraph`** (a `NestedScrollConnection` on the root Box + a `graphicsLayer` translationY on the bar's `AnimatedVisibility`); `AppBottomBar`, `ExtensionsBottomBar` and the Schedule pill are back to their original code. The `exactTint` parameter is removed; the tint floor moved to a new `HeavyGlassCard` wrapper (`GlassSurface.kt`) that every non-bar card now calls, while `FrostedGlassCard` is again the plain original.
- **Baseline profile** `app/src/main/baseline-prof.txt` (wildcard rules for `com.ansu.anime`, `androidx.compose`, `coil`), compiled into the release APK by AGP; no `baselineprofile` Gradle plugin or generated profile (none can be recorded without a device). `frostedBackdrop`: `blurLayer.renderEffect` assigned in `remember(blurLayer, radiusPx)` instead of in every draw.
- **`describePluginError` (extension/cloudstream/PluginErrors.kt)** replaces the raw `simpleName: message` in `CloudStreamSourceAdapter.io` and `CloudStreamExtensionLoader.load`: unwraps the cause chain, extracts the class from "Failed resolution of: L...;" for `NoClassDefFoundError`/`ClassNotFoundException`. Extensions Test failure line `maxLines` 2 -> 5. No host-app shims (`AcraApplication`, `CommonActivity`, `DataStore`) were added: their exact signatures are not known without the missing class names.
- **Poster blur cost.** `PosterTransition.blurRadiusPx(amount, density)` replaces the per-frame continuous radius: full strength at progress 0.5, quantised to 5 steps, so `BlurEffect` is equal frame to frame and the layer is not invalidated (previously a new radius every frame re-rendered the whole page's RenderEffect). `BLUR_RADIUS_DP` 22dp -> 18dp. `BackgroundAnimation` stops publishing its frame clock while `PosterTransition.blurBehindEntryId != null`, since each tick invalidated the blurred page.
- **`FrostedGlassCard` has a floor tint, `GLASS_MIN_TINT = 0.62f`**, so no card is lighter than the nav bar (`navBarTintAlpha(0.5)` ~ 0.53, which sits on a blurred backdrop). `glassPopupFill` / `GlassPopupFill` 0.66 -> 0.80, `WindowBlurBehind` default 24dp -> 34dp (menus 20dp -> 28dp), poster chips' black fill 0.32 -> 0.55, `SearchFilterSheet` tile 0.35 -> 0.6. Real `frostedBackdrop` blur is not usable for in-content cards: the backdrop source records the content the card is part of, so the card would draw itself into itself; it stays on the bars, scrims and pop-ups.
- **Removed `FrostedBlur` and the `frosted_blur` pref.** The object, `AppearancePrefs.frostedBlur` / `setFrostedBlur`, the Appearance > Glass group and its Reset line are deleted. `posterBlurBehind`, the poster blur effect in `PosterTransition.kt`, `glassPopupFill`, `WindowBlurBehind`, `frostedBackdrop` and the sheen/edge in `FrostedGlassCard` no longer check any global flag. `PosterTransition.BLUR_RADIUS_DP` 16dp -> 22dp, the same as `frostedBackdrop`'s default. `frostedBackdrop` is still skipped when its own `enabled` argument is false (the nav bar's Backdrop blur switch) or below Android 12.
, `### Security`.
- At release time both files get the same `## [<version>] - <YYYY-MM-DD>` heading,
  inserted directly under `## [Unreleased]`, and `## [Unreleased]` is emptied.
- Sections released before this file existed live in `CHANGELOG.md` only; from the
  first release after its introduction the two files stay in step.
- Never rewrite a published section here either — correct it in a new release.

## [Unreleased]

### Changed
- **`apk-nightly.yml` no longer reads `version.txt` line 3.** The `plan` job hardcodes `CHANNEL=nightly` and names the build `${VERSION}-nightly` without looking at the channel, and the matrix build step now passes `-Pchannel=nightly` next to `-PversionName` / `-PversionCode`, so `BuildConfig.RELEASE_CHANNEL` is `nightly` on every nightly whatever the file says — which is what gates the nightly-only rows in `AboutScreen.kt` (`onNightly`) and `UpdatesScreen.kt`. The validation now only requires lines 1-2 (Gradle itself still errors when line 3 is missing). `release-apk.yml` keeps reading the channel.
- **Pre-load removed end to end.** Deleted `data/player/Prefetcher.kt`, its `AppContainer.prefetcher` wiring and the `container.prefetcher.prefetchOnLaunch()` call in `MainActivity.onCreate`; `PlayerPrefs.prefetchEnabled` / `setPrefetchEnabled` / `KEY_PREFETCH` (`prefetch_enabled`) and the `Preload` group ("Preload Last Episode" toggle) in `ui/playersettings/PlayerStreamingScreen.kt` are gone, and the Settings subtitle in `SettingsScreen.kt` no longer lists preload. `MediaCache` stays: `PlayerViewModel` still opens its streams through `mediaCache.dataSourceFactory()` / `upstreamWithHeaders()`, so an episode that was played once still reads from disk. Nothing else called the prefetcher's source resolution (`ContinueWatchingRepository.lastSourceFor` is used by the player's own source pick, not by the removed warm-up).
- **The changelog is now two files, `CHANGELOG.md` and `CHANGELOG.dev.md`**, the method `PiBOH/vivi-music-de` uses: the first is written for the person installing the APK, this one for developers. `CLAUDE.md` §7 now specifies both, and §11's `fix.zip` carries both.

### Removed
- **The Pre-load feature** (see `### Changed`): `Prefetcher`, its launch call, its pref and its settings group. The `prefetch_enabled` value stays in `SharedPreferences` on an upgraded install and is simply never read again.

### Fixed
- **The CloudStream pin is one JitPack actually publishes.** `gradle/libs.versions.toml` asked for `com.github.recloudstream.cloudstream:library:v4.5.4`, which never existed: JitPack's `maven-metadata.xml` lists `4.5.4` and `4.5.2`-`4.8.0`, but `4.5.4`, `v4.5.4`, `4.6.0` and `v4.6.0` all answer 404, so `:app:checkDebugAarMetadata` / `:app:checkReleaseAarMetadata` failed before a single line was compiled. The pin is now `4.5.3`, whose POM and `library-jvm-4.5.3.module` resolve. **Constraint:** the newer artifacts that do exist are not usable here — `v4.7.0` needs compileSdk 36, `v4.8.0` and `master-SNAPSHOT` need 37, and AGP 8.7.2 tops out at 35, so moving up means a toolchain bump, not a version bump.
- **`NiceHttp` is declared explicitly, because the library only leaks it at runtime.** CloudStream's `library-jvm` declares its real dependencies (`NiceHttp 0.4.13`, `jackson-module-kotlin 2.13.1`, `kotlinx-coroutines-core 1.10.1`, `fuzzywuzzy 1.4.0`, `rhino 1.8.0`, `NewPipeExtractor v0.24.6`, `tmdb-java 2.11.0`) as `implementation`, so they reach the runtime classpath but not the compile one. `CloudStreamRuntime.install` sets `app.baseClient` on nicehttp's `Requests`, and `compileDebugKotlin` failed with *Cannot access class 'com.lagradost.nicehttp.Requests'*. `libs.versions.toml` now has `niceHttp = "0.4.13"` plus `libs.nicehttp` (`com.github.Blatzar:NiceHttp`), declared in `app/build.gradle.kts` next to the CloudStream library.
- **The JDK instruction in `CLAUDE.md` §4.1 was wrong and is corrected.** Gradle does not read `org.gradle.java.home` from `local.properties` (only AGP reads that file, for `sdk.dir`). With the property sitting there, Gradle launched on the machine's JDK 25 and died inside the Kotlin DSL before configuring: `java.lang.IllegalArgumentException: 25.0.5` from `JavaVersion.parse`. The documented fix is now `JAVA_HOME` pointing at JDK 21, or `org.gradle.java.home` in a gitignored `gradle.properties`.
