package com.ansu.anime.addon

import com.ansu.anime.addon.model.StremioCatalogResponse
import com.ansu.anime.addon.model.StremioManifest
import com.ansu.anime.addon.model.StremioMetaResponse
import com.ansu.anime.addon.model.StremioStreamResponse
import retrofit2.http.GET
import retrofit2.http.Url

/**
 * Every method takes a full absolute URL because each addon has its own base
 * URL: Retrofit here is just a thin, typed HTTP client, not bound to one
 * server. This matches the resource paths any Stremio-protocol addon serves:
 * `/manifest.json`, `/catalog/{type}/{id}.json`, `/meta/{type}/{id}.json`,
 * `/stream/{type}/{id}.json`.
 */
interface StremioAddonApi {
    @GET
    suspend fun getManifest(@Url manifestUrl: String): StremioManifest

    @GET
    suspend fun getCatalog(@Url catalogUrl: String): StremioCatalogResponse

    @GET
    suspend fun getMeta(@Url metaUrl: String): StremioMetaResponse

    @GET
    suspend fun getStreams(@Url streamUrl: String): StremioStreamResponse
}
