package com.kernel.anime.addon.model

import kotlinx.serialization.Serializable

/**
 * The response from an addon's `/manifest.json`. Both Stremio addons and
 * Nuvio Streams-style addons publish this same shape, which is what lets
 * Kernel treat them interchangeably: adding an addon is just pointing the
 * app at any URL that serves one of these.
 */
@Serializable
data class StremioManifest(
    val id: String,
    val name: String,
    val version: String = "0.0.0",
    val description: String? = null,
    val logo: String? = null,
    val types: List<String> = emptyList(),
    val resources: List<String> = emptyList(),
    val catalogs: List<StremioCatalogDef> = emptyList(),
    val idPrefixes: List<String>? = null,
)

@Serializable
data class StremioCatalogDef(
    val type: String,
    val id: String,
    val name: String? = null,
)

@Serializable
data class StremioCatalogResponse(
    val metas: List<StremioMeta> = emptyList(),
)

@Serializable
data class StremioMeta(
    val id: String,
    val type: String = "series",
    val name: String = "",
    val poster: String? = null,
    val background: String? = null,
    val description: String? = null,
    val releaseInfo: String? = null,
    val imdbRating: String? = null,
    val genres: List<String>? = null,
    val videos: List<StremioVideoEntry>? = null,
)

@Serializable
data class StremioVideoEntry(
    val id: String,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val thumbnail: String? = null,
)

@Serializable
data class StremioMetaResponse(val meta: StremioMeta? = null)

@Serializable
data class StremioStreamResponse(val streams: List<StremioStream> = emptyList())

@Serializable
data class StremioStream(
    val url: String? = null,
    val name: String? = null,
    val title: String? = null,
    val description: String? = null,
    val behaviorHints: StremioBehaviorHints? = null,
)

@Serializable
data class StremioBehaviorHints(
    val bingeGroup: String? = null,
    val notWebReady: Boolean? = null,
)

/** An addon that has been added, persisted, and can be queried. */
data class InstalledAddon(
    val id: String,
    val name: String,
    val baseUrl: String,
    val manifestUrl: String,
    val version: String,
    val logoUrl: String?,
    val types: List<String>,
    val catalogs: List<StremioCatalogDef>,
    val enabled: Boolean = true,
)
