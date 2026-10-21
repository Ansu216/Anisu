package com.ansu.anime.data.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import java.io.File

/**
 * One on-disk media cache shared by the player and the [Prefetcher]. The prefetcher writes segments
 * here ahead of time and the player reads them back, so a resumed episode starts already filled
 * instead of waiting on the network. The player's own RAM window (its LoadControl) is separate.
 */
@OptIn(UnstableApi::class)
class MediaCache(context: Context, private val client: OkHttpClient? = null) {
    private val appContext = context.applicationContext

    val cache: SimpleCache = SimpleCache(
        File(appContext.cacheDir, CACHE_DIR),
        LeastRecentlyUsedCacheEvictor(MAX_BYTES),
        StandaloneDatabaseProvider(appContext),
    )

    /** A data-source factory that serves cached bytes and falls back to [upstream] (plus the network). */
    fun dataSourceFactory(
        upstream: DataSource.Factory = defaultUpstream(),
    ): DataSource.Factory =
        CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

    /** The shared OkHttp client when there is one (HTTP/2, pooled connections); the stock HTTP source otherwise. */
    private fun defaultUpstream(): DataSource.Factory =
        if (client != null) {
            OkHttpDataSource.Factory(client)
        } else {
            DefaultHttpDataSource.Factory()
                // The stock 8 s timeouts fail slow CDNs outright, which the player then reports as a broken source.
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
        }

    /** The same upstream with the request headers a source asks for (Referer, User-Agent ...). */
    fun upstreamWithHeaders(headers: Map<String, String>): DataSource.Factory =
        if (client != null) {
            OkHttpDataSource.Factory(client).setDefaultRequestProperties(headers)
        } else {
            DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setConnectTimeoutMs(15_000)
                .setReadTimeoutMs(15_000)
                .setDefaultRequestProperties(headers)
        }

    private companion object {
        const val CACHE_DIR = "media-cache"
        const val MAX_BYTES = 512L * 1024 * 1024
    }
}
