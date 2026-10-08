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
    entities = [ContinueWatchingEntity::class, LocalListEntity::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun continueWatchingDao(): ContinueWatchingDao
    abstract fun localListDao(): LocalListDao

    companion object {
        /** v1 -> v2: adds the local library table without touching continue-watching. */
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

        /**
         * v3 -> v4: addons were removed from the app. Drops their table and rebuilds continue-watching without the two
         * addon columns (SQLite before 3.35 cannot drop a column), keeping every row.
         */
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP TABLE IF EXISTS `installed_addons`")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `continue_watching_new` (" +
                        "`anilistId` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, " +
                        "`posterUrl` TEXT, " +
                        "`bannerUrl` TEXT, " +
                        "`episodeId` TEXT NOT NULL, " +
                        "`episodeNumber` REAL NOT NULL, " +
                        "`episodeName` TEXT NOT NULL, " +
                        "`positionSeconds` INTEGER NOT NULL, " +
                        "`durationSeconds` INTEGER NOT NULL, " +
                        "`originExtensionSourceId` INTEGER, " +
                        "`lastWatchedAt` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`anilistId`))",
                )
                db.execSQL(
                    "INSERT INTO `continue_watching_new` (`anilistId`, `title`, `posterUrl`, `bannerUrl`, `episodeId`, " +
                        "`episodeNumber`, `episodeName`, `positionSeconds`, `durationSeconds`, `originExtensionSourceId`, `lastWatchedAt`) " +
                        "SELECT `anilistId`, `title`, `posterUrl`, `bannerUrl`, `episodeId`, `episodeNumber`, `episodeName`, " +
                        "`positionSeconds`, `durationSeconds`, `originExtensionSourceId`, `lastWatchedAt` FROM `continue_watching`",
                )
                db.execSQL("DROP TABLE `continue_watching`")
                db.execSQL("ALTER TABLE `continue_watching_new` RENAME TO `continue_watching`")
            }
        }

        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "ansu.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
