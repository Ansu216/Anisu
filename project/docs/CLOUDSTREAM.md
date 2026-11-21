# CloudStream extension support

Anisu loads CloudStream plugins (`.cs3`) next to Aniyomi/Keiyoushi APKs.

* Add a repo on the Extensions screen with any of: a `repo.json` URL, a `plugins.json` URL, a
  `cloudstreamrepo://...` link or a cutt.ly short code. Plugins marked down, NSFW, or without an
  Anime/AnimeMovie/OVA type are not listed (the built-in 18+ filter applies as for Aniyomi).
* Plugins are downloaded into private storage (`files/exts/cs3.<internalName>.ext`) and loaded with a
  `PathClassLoader` whose parent is Anisu, which carries the CloudStream library.
* Code: `extension/cloudstream/` (runtime, loader, `CloudStreamSourceAdapter`), shim
  `com/lagradost/cloudstream3/plugins/Plugin.kt`, repo parsing in `ExtensionRepo.kt`.

## Before the first build
`gradle/libs.versions.toml` -> `cloudstream` is pinned to `4.5.3` (compileSdk 35, matches this project). `4.5.4` is listed in JitPack's
`maven-metadata.xml` but was never built there (both `4.5.4` and `v4.5.4` answer 404), so `4.5.3` is the last usable release of
that line; 4.6+/4.7+ need compileSdk 36+ and a newer AGP, and `master-SNAPSHOT` needs 37. Tag from
recloudstream/cloudstream that builds, then fix any signature differences in `CloudStreamSourceAdapter`
(`getMainPage`/`MainPageRequest`, `search`, `loadLinks`, `ExtractorLink`) for that version.

One dependency is deliberately declared twice: the library builds on `com.github.Blatzar:NiceHttp` as an
`implementation` dependency, so it reaches the runtime classpath but not the compile one. `CloudStreamRuntime`
installs Anisu's OkHttp client into nicehttp's global `app.baseClient`, so the app declares the same NiceHttp
version itself (`libs.nicehttp`) — drop it and `compileDebugKotlin` fails with
"Cannot access class 'com.lagradost.nicehttp.Requests'".

## Not supported
Plugin settings screens, plugin code that reaches into CloudStream's app module (`AcraApplication`, `CommonActivity`,
UI classes), torrents/magnets, dub-vs-sub choice (subbed episodes are used first), search filters and search paging.

## Repo names
The Add extension card shows each repo's name above its link. A CloudStream `repo.json` supplies its own `name`;
other repos get `user/repo` (GitHub/Forgejo links) or the host, worked out in `repoDisplayName()` (ExtensionRepo.kt).
Names are stored in the `extension_repos` preferences (`repo_names`) and refreshed whenever a repo is re-read.
