package com.ansu.anime.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.StremioAddonApi
import com.ansu.anime.anilist.AniListApi
import com.ansu.anime.anilist.AniListAuthManager
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.data.db.AppDatabase
import com.ansu.anime.data.prefs.AppearancePrefs
import com.ansu.anime.data.repository.CatalogRepository
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.data.update.UpdateManager
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.ExtensionRepo
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit

/**
 * A single, plainly-constructed object graph. Every dependency is created
 * once here and handed out via simple property access — no annotation
 * processor, no reflection-based DI framework, which keeps this project easy
 * to build the first time you open it.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val json = Json { ignoreUnknownKeys = true }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC })
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl("https://example-placeholder.invalid/") // unused: every call supplies a full @Url
        .client(okHttpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    val stremioAddonApi: StremioAddonApi = retrofit.create(StremioAddonApi::class.java)

    val database: AppDatabase = AppDatabase.build(appContext)

    val extensionManager: ExtensionManager = ExtensionManager(appContext)
    val extensionRepo: ExtensionRepo = ExtensionRepo(appContext, okHttpClient)
    val addonManager: AddonManager = AddonManager(stremioAddonApi, database.installedAddonDao())

    val aniListAuthManager: AniListAuthManager = AniListAuthManager(appContext)
    val aniListApi: AniListApi = AniListApi(okHttpClient, aniListAuthManager)
    val aniListRepository: AniListRepository = AniListRepository(aniListApi, aniListAuthManager)

    val continueWatchingRepository: ContinueWatchingRepository = ContinueWatchingRepository(
        database.continueWatchingDao(),
        aniListRepository,
    )

    val catalogRepository: CatalogRepository = CatalogRepository(extensionManager, addonManager, aniListRepository)

    val selectionHolder: SelectionHolder = SelectionHolder()

    val appearancePrefs: AppearancePrefs = AppearancePrefs(appContext)

    val updateManager: UpdateManager = UpdateManager(appContext, okHttpClient)

    init {
        // The demo source ships built into the app so there's content on
        // first launch; reloadAll() then adds any real installed extensions
        // alongside it.
        extensionManager.registerBuiltIn(com.ansu.anime.extension.DemoSource())
        extensionManager.reloadAll()
    }
}
