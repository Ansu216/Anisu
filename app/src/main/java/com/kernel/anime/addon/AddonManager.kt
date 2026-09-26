package com.kernel.anime.addon

import com.kernel.anime.addon.model.InstalledAddon
import com.kernel.anime.addon.model.StremioCatalogDef
import com.kernel.anime.addon.model.StremioMeta
import com.kernel.anime.addon.model.StremioStream
import com.kernel.anime.data.db.InstalledAddonDao
import com.kernel.anime.data.db.InstalledAddonEntity
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

/**
 * Owns the set of addons the user has added by manifest URL, and fans requests
 * for catalogs/streams out to whichever addons can answer them. Works equally
 * for a Stremio community addon or a Nuvio Streams instance — both speak the
 * same manifest/catalog/meta/stream protocol.
 */
class AddonManager(
    private val api: StremioAddonApi,
    private val dao: InstalledAddonDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    val installedAddons: Flow<List<InstalledAddon>> = dao.observeAll().map { list -> list.map { it.toModel() } }

    suspend fun addAddon(manifestUrl: String): Result<InstalledAddon> = runCatching {
        val manifest = api.getManifest(manifestUrl)
        val baseUrl = manifestUrl.removeSuffix("/manifest.json")
        val entity = InstalledAddonEntity(
            id = manifest.id,
            name = manifest.name,
            baseUrl = baseUrl,
            manifestUrl = manifestUrl,
            version = manifest.version,
            logoUrl = manifest.logo,
            typesCsv = manifest.types.joinToString(","),
            catalogsJson = json.encodeToString(
                kotlinx.serialization.builtins.ListSerializer(StremioCatalogDef.serializer()),
                manifest.catalogs,
            ),
            enabled = true,
        )
        dao.upsert(entity)
        entity.toModel()
    }

    suspend fun removeAddon(id: String) = dao.remove(id)

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun getCatalog(addon: InstalledAddon, catalog: StremioCatalogDef, page: Int = 0): List<StremioMeta> {
        val skip = page * 20
        val url = "${addon.baseUrl}/catalog/${catalog.type}/${catalog.id}/skip=$skip.json"
        return runCatching { api.getCatalog(url).metas }.getOrDefault(emptyList())
    }

    /** Queries every enabled addon's catalogs of [type] in parallel and merges the results into shelves. */
    suspend fun getShelvesForType(type: String): Map<InstalledAddon, List<StremioMeta>> = coroutineScope {
        val addons = dao.getEnabled().map { it.toModel() }.filter { type in it.types }
        addons.associateWith { addon ->
            async {
                addon.catalogs.filter { it.type == type }.firstOrNull()?.let { catalog ->
                    getCatalog(addon, catalog)
                } ?: emptyList()
            }
        }.mapValues { it.value.await() }
    }

    suspend fun getMeta(addon: InstalledAddon, type: String, stremioId: String): StremioMeta? {
        val url = "${addon.baseUrl}/meta/$type/$stremioId.json"
        return runCatching { api.getMeta(url).meta }.getOrNull()
    }

    suspend fun getStreams(addon: InstalledAddon, type: String, stremioId: String): List<StremioStream> {
        val url = "${addon.baseUrl}/stream/$type/$stremioId.json"
        return runCatching { api.getStreams(url).streams }.getOrDefault(emptyList())
    }

    /** Aggregates stream results from every enabled addon that supports [type], keyed by addon name. */
    suspend fun getStreamsFromAllAddons(type: String, stremioId: String): Map<String, List<StremioStream>> = coroutineScope {
        val addons = dao.getEnabled().map { it.toModel() }.filter { type in it.types }
        addons.map { addon -> addon to async { getStreams(addon, type, stremioId) } }
            .associate { (addon, deferred) -> addon.name to deferred.await() }
            .filterValues { it.isNotEmpty() }
    }

    private fun InstalledAddonEntity.toModel(): InstalledAddon {
        val catalogs = runCatching {
            json.decodeFromString(
                kotlinx.serialization.builtins.ListSerializer(StremioCatalogDef.serializer()),
                catalogsJson,
            )
        }.getOrDefault(emptyList())
        return InstalledAddon(
            id = id,
            name = name,
            baseUrl = baseUrl,
            manifestUrl = manifestUrl,
            version = version,
            logoUrl = logoUrl,
            types = typesCsv.split(",").filter { it.isNotBlank() },
            catalogs = catalogs,
            enabled = enabled,
        )
    }
}
