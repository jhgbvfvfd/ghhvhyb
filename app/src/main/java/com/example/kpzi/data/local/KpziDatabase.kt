package com.example.kpzi.data.local

import android.content.Context
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.domain.model.DownloadTask
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.ModDependency
import com.example.kpzi.domain.model.ModManifest
import com.example.kpzi.domain.model.NexusModSummary
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "mod_cache")
data class ModCacheEntity(
    @PrimaryKey val modId: Long,
    val name: String,
    val summary: String,
    val description: String,
    val author: String,
    val version: String,
    val categoryName: String,
    val pictureUrl: String?,
    val downloads: Long,
    val endorsements: Long,
    val updatedEpochSec: Long,
    val fileSizeBytes: Long?,
    val compatibility: String,
    val feedTag: String,
    val cachedAtMs: Long,
)

@Entity(tableName = "download_records")
data class DownloadRecordEntity(
    @PrimaryKey val id: String,
    val modId: Long,
    val fileId: Long,
    val modName: String,
    val fileName: String,
    val version: String,
    val pictureUrl: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val status: String,
    val localFilePath: String?,
    val sha256: String?,
    val isCorrupted: Boolean,
    val errorMessage: String?,
    val supportsResume: Boolean,
    val createdAtMs: Long,
)

/**
 * Local Room entity tracking installed Stardew Valley mods for fast local management
 * without repeated filesystem/manifest scans.
 */
@Entity(
    tableName = "installed_mods",
    indices = [
        Index(value = ["is_enabled"]),
        Index(value = ["name"]),
    ],
)
data class InstalledModEntity(
    @PrimaryKey
    @ColumnInfo(name = "unique_id")
    val uniqueId: String,
    @ColumnInfo(name = "version")
    val version: String,
    @ColumnInfo(name = "local_path")
    val localPath: String,
    @ColumnInfo(name = "is_enabled")
    val isEnabled: Boolean,
    @ColumnInfo(name = "folder_name")
    val folderName: String,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "author")
    val author: String,
    @ColumnInfo(name = "description")
    val description: String,
    @ColumnInfo(name = "minimum_api_version")
    val minimumApiVersion: String?,
    @ColumnInfo(name = "entry_dll")
    val entryDll: String?,
    @ColumnInfo(name = "content_pack_for_unique_id")
    val contentPackForUniqueId: String?,
    @ColumnInfo(name = "dependencies_json")
    val dependenciesJson: String,
    @ColumnInfo(name = "update_keys_json")
    val updateKeysJson: String,
    @ColumnInfo(name = "compatibility")
    val compatibility: String,
    @ColumnInfo(name = "issue_message")
    val issueMessage: String?,
    @ColumnInfo(name = "update_available_version")
    val updateAvailableVersion: String?,
    @ColumnInfo(name = "nexus_mod_id")
    val nexusModId: Long?,
    @ColumnInfo(name = "size_bytes")
    val sizeBytes: Long,
    @ColumnInfo(name = "installed_at_ms")
    val installedAtMs: Long,
    @ColumnInfo(name = "last_synced_at_ms")
    val lastSyncedAtMs: Long,
)

@Entity(
    tableName = "play_sessions",
    indices = [Index(value = ["ended_at_ms"])],
)
data class PlaySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "started_at_ms") val startedAtMs: Long,
    @ColumnInfo(name = "ended_at_ms") val endedAtMs: Long,
    @ColumnInfo(name = "duration_seconds") val durationSeconds: Long,
    @ColumnInfo(name = "active_mod_count") val activeModCount: Int,
    @ColumnInfo(name = "launch_mode") val launchMode: String,
)

@Entity(tableName = "favorite_rooms")
data class FavoriteRoomEntity(
    @PrimaryKey @ColumnInfo(name = "room_id") val roomId: String,
    @ColumnInfo(name = "room_name") val roomName: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "host_address") val hostAddress: String,
    @ColumnInfo(name = "port") val port: Int,
    @ColumnInfo(name = "room_code") val roomCode: String,
    @ColumnInfo(name = "saved_at_ms") val savedAtMs: Long,
)

@Entity(
    tableName = "coop_history",
    indices = [Index(value = ["played_at_ms"])],
)
data class CoopHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "room_id") val roomId: String,
    @ColumnInfo(name = "room_name") val roomName: String,
    @ColumnInfo(name = "owner_name") val ownerName: String,
    @ColumnInfo(name = "role") val role: String,
    @ColumnInfo(name = "game_version") val gameVersion: String,
    @ColumnInfo(name = "ping_ms") val pingMs: Int?,
    @ColumnInfo(name = "room_code") val roomCode: String,
    @ColumnInfo(name = "played_at_ms") val playedAtMs: Long,
)

@Dao
interface ModCacheDao {
    @Query("SELECT * FROM mod_cache WHERE feedTag = :feedTag ORDER BY cachedAtMs DESC, downloads DESC")
    fun observeByFeed(feedTag: String): Flow<List<ModCacheEntity>>

    @Query("SELECT * FROM mod_cache ORDER BY downloads DESC")
    fun observeAll(): Flow<List<ModCacheEntity>>

    @Query("SELECT * FROM mod_cache WHERE modId = :modId LIMIT 1")
    suspend fun getById(modId: Long): ModCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<ModCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: ModCacheEntity)

    @Query("DELETE FROM mod_cache WHERE cachedAtMs < :olderThanMs")
    suspend fun pruneOlderThan(olderThanMs: Long)
}

@Dao
interface DownloadRecordDao {
    @Query("SELECT * FROM download_records ORDER BY createdAtMs DESC")
    fun observeAll(): Flow<List<DownloadRecordEntity>>

    @Query("SELECT * FROM download_records WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: DownloadRecordEntity)

    @Query("DELETE FROM download_records WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface PlaySessionDao {
    @Query("SELECT * FROM play_sessions ORDER BY ended_at_ms DESC LIMIT 20")
    fun observeRecent(): Flow<List<PlaySessionEntity>>

    @Insert
    suspend fun insert(session: PlaySessionEntity)

    @Query("DELETE FROM play_sessions")
    suspend fun clearAll()
}

@Dao
interface MultiplayerDao {
    @Query("SELECT * FROM favorite_rooms ORDER BY saved_at_ms DESC")
    fun observeFavorites(): Flow<List<FavoriteRoomEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(item: FavoriteRoomEntity)

    @Query("DELETE FROM favorite_rooms WHERE room_id = :roomId")
    suspend fun deleteFavorite(roomId: String)

    @Query("SELECT * FROM coop_history ORDER BY played_at_ms DESC LIMIT 30")
    fun observeHistory(): Flow<List<CoopHistoryEntity>>

    @Insert
    suspend fun insertHistory(entry: CoopHistoryEntity)

    @Query("DELETE FROM coop_history")
    suspend fun clearHistory()
}

@Database(
    entities = [
        ModCacheEntity::class,
        DownloadRecordEntity::class,
        InstalledModEntity::class,
        PlaySessionEntity::class,
        FavoriteRoomEntity::class,
        CoopHistoryEntity::class,
    ],
    version = 4,
    exportSchema = false,
)
abstract class KpziDatabase : RoomDatabase() {
    abstract fun modCacheDao(): ModCacheDao
    abstract fun downloadRecordDao(): DownloadRecordDao
    abstract fun modDao(): ModDao
    abstract fun playSessionDao(): PlaySessionDao
    abstract fun multiplayerDao(): MultiplayerDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `installed_mods` (
                        `unique_id` TEXT NOT NULL,
                        `version` TEXT NOT NULL,
                        `local_path` TEXT NOT NULL,
                        `is_enabled` INTEGER NOT NULL,
                        `folder_name` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `author` TEXT NOT NULL,
                        `description` TEXT NOT NULL,
                        `minimum_api_version` TEXT,
                        `entry_dll` TEXT,
                        `content_pack_for_unique_id` TEXT,
                        `dependencies_json` TEXT NOT NULL,
                        `update_keys_json` TEXT NOT NULL,
                        `compatibility` TEXT NOT NULL,
                        `issue_message` TEXT,
                        `update_available_version` TEXT,
                        `nexus_mod_id` INTEGER,
                        `size_bytes` INTEGER NOT NULL,
                        `installed_at_ms` INTEGER NOT NULL,
                        `last_synced_at_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`unique_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installed_mods_is_enabled` ON `installed_mods` (`is_enabled`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_installed_mods_name` ON `installed_mods` (`name`)")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `play_sessions` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `started_at_ms` INTEGER NOT NULL,
                        `ended_at_ms` INTEGER NOT NULL,
                        `duration_seconds` INTEGER NOT NULL,
                        `active_mod_count` INTEGER NOT NULL,
                        `launch_mode` TEXT NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_play_sessions_ended_at_ms` ON `play_sessions` (`ended_at_ms`)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `favorite_rooms` (
                        `room_id` TEXT NOT NULL,
                        `room_name` TEXT NOT NULL,
                        `owner_name` TEXT NOT NULL,
                        `host_address` TEXT NOT NULL,
                        `port` INTEGER NOT NULL,
                        `room_code` TEXT NOT NULL,
                        `saved_at_ms` INTEGER NOT NULL,
                        PRIMARY KEY(`room_id`)
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `coop_history` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `room_id` TEXT NOT NULL,
                        `room_name` TEXT NOT NULL,
                        `owner_name` TEXT NOT NULL,
                        `role` TEXT NOT NULL,
                        `game_version` TEXT NOT NULL,
                        `ping_ms` INTEGER,
                        `room_code` TEXT NOT NULL,
                        `played_at_ms` INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_coop_history_played_at_ms` ON `coop_history` (`played_at_ms`)")
            }
        }

        @Volatile
        private var instance: KpziDatabase? = null

        fun getInstance(context: Context): KpziDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    KpziDatabase::class.java,
                    "kpzi_mod_manager.db",
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    .build()
                    .also { instance = it }
            }
        }
    }
}

fun ModCacheEntity.toSummary(): NexusModSummary {
    val compat = runCatching { CompatibilityLevel.valueOf(compatibility) }
        .getOrDefault(CompatibilityLevel.UNVERIFIED)
    return NexusModSummary(
        modId = modId,
        name = name,
        summary = summary,
        author = author,
        version = version,
        categoryName = categoryName,
        pictureUrl = pictureUrl,
        downloads = downloads,
        endorsements = endorsements,
        updatedEpochSec = updatedEpochSec,
        fileSizeBytes = fileSizeBytes,
        compatibility = compat,
        feedTag = feedTag,
    )
}

fun NexusModSummary.toCacheEntity(description: String = summary): ModCacheEntity = ModCacheEntity(
    modId = modId,
    name = name,
    summary = summary,
    description = description,
    author = author,
    version = version,
    categoryName = categoryName,
    pictureUrl = pictureUrl,
    downloads = downloads,
    endorsements = endorsements,
    updatedEpochSec = updatedEpochSec,
    fileSizeBytes = fileSizeBytes,
    compatibility = compatibility.name,
    feedTag = feedTag,
    cachedAtMs = System.currentTimeMillis(),
)

fun DownloadRecordEntity.toDomain(): DownloadTask = DownloadTask(
    id = id,
    modId = modId,
    fileId = fileId,
    modName = modName,
    fileName = fileName,
    version = version,
    pictureUrl = pictureUrl,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    speedBytesPerSec = speedBytesPerSec,
    etaSeconds = etaSeconds,
    status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.FAILED),
    localFilePath = localFilePath,
    sha256 = sha256,
    isCorrupted = isCorrupted,
    errorMessage = errorMessage,
    supportsResume = supportsResume,
    createdAtMs = createdAtMs,
)

fun DownloadTask.toEntity(): DownloadRecordEntity = DownloadRecordEntity(
    id = id,
    modId = modId,
    fileId = fileId,
    modName = modName,
    fileName = fileName,
    version = version,
    pictureUrl = pictureUrl,
    totalBytes = totalBytes,
    downloadedBytes = downloadedBytes,
    speedBytesPerSec = speedBytesPerSec,
    etaSeconds = etaSeconds,
    status = status.name,
    localFilePath = localFilePath,
    sha256 = sha256,
    isCorrupted = isCorrupted,
    errorMessage = errorMessage,
    supportsResume = supportsResume,
    createdAtMs = createdAtMs,
)

private val roomMoshi: Moshi by lazy {
    Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
}

private val dependencyListAdapter by lazy {
    roomMoshi.adapter<List<ModDependency>>(
        Types.newParameterizedType(List::class.java, ModDependency::class.java),
    )
}

private val stringListAdapter by lazy {
    roomMoshi.adapter<List<String>>(
        Types.newParameterizedType(List::class.java, String::class.java),
    )
}

fun InstalledModEntity.toDomain(): InstalledMod {
    val deps = runCatching { dependencyListAdapter.fromJson(dependenciesJson) }.getOrNull().orEmpty()
    val keys = runCatching { stringListAdapter.fromJson(updateKeysJson) }.getOrNull().orEmpty()
    val compat = runCatching { CompatibilityLevel.valueOf(compatibility) }
        .getOrDefault(CompatibilityLevel.UNVERIFIED)

    return InstalledMod(
        uniqueId = uniqueId,
        folderName = folderName,
        folderPath = localPath,
        manifest = ModManifest(
            uniqueId = uniqueId,
            name = name,
            author = author,
            version = version,
            description = description,
            minimumApiVersion = minimumApiVersion,
            entryDll = entryDll,
            contentPackForUniqueId = contentPackForUniqueId,
            dependencies = deps,
            updateKeys = keys,
        ),
        isEnabled = isEnabled,
        compatibility = compat,
        issueMessage = issueMessage,
        updateAvailableVersion = updateAvailableVersion,
        nexusModId = nexusModId,
        sizeBytes = sizeBytes,
        installedAtMs = installedAtMs,
    )
}

fun InstalledMod.toEntity(): InstalledModEntity {
    return InstalledModEntity(
        uniqueId = uniqueId,
        version = manifest.version,
        localPath = folderPath,
        isEnabled = isEnabled,
        folderName = folderName,
        name = manifest.name,
        author = manifest.author,
        description = manifest.description,
        minimumApiVersion = manifest.minimumApiVersion,
        entryDll = manifest.entryDll,
        contentPackForUniqueId = manifest.contentPackForUniqueId,
        dependenciesJson = dependencyListAdapter.toJson(manifest.dependencies),
        updateKeysJson = stringListAdapter.toJson(manifest.updateKeys),
        compatibility = compatibility.name,
        issueMessage = issueMessage,
        updateAvailableVersion = updateAvailableVersion,
        nexusModId = nexusModId,
        sizeBytes = sizeBytes,
        installedAtMs = installedAtMs,
        lastSyncedAtMs = System.currentTimeMillis(),
    )
}
