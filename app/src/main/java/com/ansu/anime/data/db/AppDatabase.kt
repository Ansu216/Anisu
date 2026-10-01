package com.ansu.anime.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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

// ---------------------------------------------------------------------------
// Local library — favourites and watch lists kept on the device, used when the
// user is not signed in to AniList. One row per show: [isFavourite] is the
// heart, [status] is the list it sits in (AniList's CURRENT / PLANNING /
// COMPLETED strings, or null for "not in a list").
// ---------------------------------------------------------------------------

@Entity(tableName = "local_list_entries")
data class LocalListEntity(
    @PrimaryKey val anilistId: Int,
    val title: String,
    val posterUrl: String?,
    val bannerUrl: String?,
    val description: String?,
    val genresCsv: String,
    val year: Int?,
    val averageScore: Int?,
    val episodes: Int?,
    val isFavourite: Boolean,
    val status: String?,
    val progress: Int,
    val updatedAt: Long,
    /** AniList's raw format enum (TV, MOVIE, ...); null until the details page has loaded it. */
    val format: String? = null,
)

@Dao
interface LocalListDao {
    @Query("SELECT * FROM local_list_entries ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LocalListEntity>>

    @Query("SELECT * FROM local_list_entries")
    suspend fun getAll(): List<LocalListEntity>

    @Query("SELECT * FROM local_list_entries WHERE anilistId = :anilistId LIMIT 1")
    fun observe(anilistId: Int): Flow<LocalListEntity?>

    @Query("SELECT * FROM local_list_entries WHERE anilistId = :anilistId LIMIT 1")
    suspend fun get(anilistId: Int): LocalListEntity?

    @Upsert
    suspend fun upsert(entity: LocalListEntity)

    @Query("UPDATE local_list_entries SET format = :format WHERE anilistId = :anilistId")
    suspend fun setFormat(anilistId: Int, format: String)

    @Query("UPDATE local_list_entries SET title = :title WHERE anilistId = :anilistId")
    suspend fun setTitle(anilistId: Int, title: String)

    @Query("UPDATE local_list_entries SET year = :year WHERE anilistId = :anilistId AND year IS NULL")
    suspend fun fillYear(anilistId: Int, year: Int)

    @Query("DELETE FROM local_list_entries WHERE anilistId = :anilistId")
    suspend fun remove(anilistId: Int)
}

@Database(
    entities = [ContinueWatchingEntity::class, InstalledAddonEntity::class, LocalListEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun installedAddonDao(): InstalledAddonDao
    abstract fun localListDao(): LocalListDao

    companion object {
        /** v1 -> v2: adds the local library table without touching continue-watching or addons. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `local_list_entries` (" +
                        "`anilistId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`posterUrl` TEXT, " +
                        "`bannerUrl` TEXT, " +
                        "`description` TEXT, " +
                        "`genresCsv` TEXT NOT NULL, " +
                        "`year` INTEGER, " +
                        "`averageScore` INTEGER, " +
                        "`episodes` INTEGER, " +
                        "`isFavourite` INTEGER NOT NULL, " +
                        "`status` TEXT, " +
                        "`progress` INTEGER NOT NULL, " +
                        "`updatedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`anilistId`))",
                )
            }
        }

        /** v2 -> v3: adds the show's format to saved entries so My Space can display it. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `local_list_entries` ADD COLUMN `format` TEXT")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ansu.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .fallbackToDestructiveMigration()
                .build()
    }
}
