package com.ansu.anime.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

// ---------------------------------------------------------------------------
// Continue watching — the CornCastle-style row on the home screen. One row
// per show; re-upserting the same anilistId just moves it back to the top
// and updates progress.
// ---------------------------------------------------------------------------

@Entity(tableName = "continue_watching")
data class ContinueWatchingEntity(
    @PrimaryKey val anilistId: Int,
    val title: String,
    val posterUrl: String?,
    val bannerUrl: String?,
    val episodeId: String,
    val episodeNumber: Float,
    val episodeName: String,
    val positionSeconds: Long,
    val durationSeconds: Long,
    val originExtensionSourceId: Long?,
    val originAddonId: String?,
    val originAddonBaseUrl: String?,
    val lastWatchedAt: Long,
)

@Dao
interface ContinueWatchingDao {
    @Query("SELECT * FROM continue_watching ORDER BY lastWatchedAt DESC")
    fun observeAll(): Flow<List<ContinueWatchingEntity>>

    @Upsert
    suspend fun upsert(entity: ContinueWatchingEntity)

    @Query("DELETE FROM continue_watching WHERE anilistId = :anilistId")
    suspend fun remove(anilistId: Int)

    @Query("SELECT * FROM continue_watching WHERE anilistId = :anilistId LIMIT 1")
    suspend fun get(anilistId: Int): ContinueWatchingEntity?
}

// ---------------------------------------------------------------------------
// Installed addons — Stremio/Nuvio-protocol addons the user has added by
// manifest URL.
// ---------------------------------------------------------------------------

@Entity(tableName = "installed_addons")
data class InstalledAddonEntity(
    @PrimaryKey val id: String,
    val name: String,
    val baseUrl: String,
    val manifestUrl: String,
    val version: String,
    val logoUrl: String?,
    val typesCsv: String,
    val catalogsJson: String,
    val enabled: Boolean,
)

@Dao
interface InstalledAddonDao {
    @Query("SELECT * FROM installed_addons ORDER BY name ASC")
    fun observeAll(): Flow<List<InstalledAddonEntity>>

    @Query("SELECT * FROM installed_addons WHERE enabled = 1 ORDER BY name ASC")
    suspend fun getEnabled(): List<InstalledAddonEntity>

    @Upsert
    suspend fun upsert(entity: InstalledAddonEntity)

    @Query("DELETE FROM installed_addons WHERE id = :id")
    suspend fun remove(id: String)

    @Query("UPDATE installed_addons SET enabled = :enabled WHERE id = :id")
    suspend fun setEnabled(id: String, enabled: Boolean)
}

@Database(
    entities = [ContinueWatchingEntity::class, InstalledAddonEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun installedAddonDao(): InstalledAddonDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ansu.db")
                .fallbackToDestructiveMigration()
                .build()
    }
}
