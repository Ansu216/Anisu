package com.ansu.anime.di

import android.content.Context
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.StremioAddonApi
import com.ansu.anime.anilist.AniListApi
import com.ansu.anime.anilist.AniListAuthManager
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.net.ApiErrorHandler
import com.ansu.anime.data.contributors.ContributorsRepository
import com.ansu.anime.data.db.AppDatabase
import com.ansu.anime.data.prefs.AppearancePrefs
import com.ansu.anime.data.repository.CatalogRepository
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.data.repository.ArtworkRepository
import com.ansu.anime.data.repository.EpisodeMetadataRepository
import com.ansu.anime.data.repository.LibrarySyncRepository
import com.ansu.anime.data.repository.LocalListRepository
import com.ansu.anime.data.update.UpdateManager
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.ExtensionRepo
import com.ansu.anime.extension.SourceTester
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

    /** Ansu's black box: every crash, navigation, tap, playback and network event. */
    val diagnostics: Diagnostics = Diagnostics(appContext)

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
    val sourceTester: SourceTester = SourceTester(okHttpClient, addonManager)

    val apiErrorHandler: ApiErrorHandler = ApiErrorHandler(diagnostics)

    val aniListAuthManager: AniListAuthManager = AniListAuthManager(appContext)
    val appearancePrefs: AppearancePrefs = AppearancePrefs(appContext)

    val aniListApi: AniListApi = AniListApi(okHttpClient, aniListAuthManager) { appearancePrefs.titleLanguage.value }
    val aniListRepository: AniListRepository = AniListRepository(aniListApi, aniListAuthManager, apiErrorHandler)

    val aniSkipRepository: com.ansu.anime.data.repository.AniSkipRepository =
        com.ansu.anime.data.repository.AniSkipRepository(okHttpClient)

    val localListRepository: LocalListRepository = LocalListRepository(database.localListDao())

    val librarySyncRepository: LibrarySyncRepository = LibrarySyncRepository(localListRepository, aniListRepository)

    val continueWatchingRepository: ContinueWatchingRepository = ContinueWatchingRepository(
        database.continueWatchingDao(),
        aniListRepository,
        localListRepository,
    )

    val catalogRepository: CatalogRepository = CatalogRepository(extensionManager, addonManager, aniListRepository)

    val episodeMetadataRepository: EpisodeMetadataRepository = EpisodeMetadataRepository(okHttpClient, aniListRepository, apiErrorHandler)

    val artworkRepository: ArtworkRepository = ArtworkRepository(okHttpClient, apiErrorHandler)

    val selectionHolder: SelectionHolder = SelectionHolder()

    val contributorsRepository: ContributorsRepository = ContributorsRepository(appContext, okHttpClient, apiErrorHandler)

    val updateManager: UpdateManager = UpdateManager(appContext, okHttpClient, diagnostics)

    init {
        // Aniyomi/Keiyoushi extensions need the Injekt container filled in before they are instantiated.
        // Do not swallow a failure silently: without this, every extension fails with "No registered instance".
        try {
            com.ansu.anime.extension.aniyomi.AniyomiRuntime.install(appContext, okHttpClient)
        } catch (t: Throwable) {
            android.util.Log.e("AniyomiRuntime", "Injekt setup failed", t)
        }
        runCatching { extensionManager.reloadAll() }
    }
}
