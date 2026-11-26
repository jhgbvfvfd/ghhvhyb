package com.example.kpzi.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * Room DAO providing CRUD operations for managing locally installed Stardew Valley mods.
 */
@Dao
interface ModDao {

    // --- Create / Insert ---

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMod(mod: InstalledModEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMods(mods: List<InstalledModEntity>)

    // --- Read / Retrieve ---

    @Query("SELECT * FROM installed_mods ORDER BY is_enabled DESC, name COLLATE NOCASE ASC")
    fun getAllInstalledMods(): Flow<List<InstalledModEntity>>

    @Query("SELECT * FROM installed_mods ORDER BY is_enabled DESC, name COLLATE NOCASE ASC")
    suspend fun getAllInstalledModsOnce(): List<InstalledModEntity>

    @Query("SELECT * FROM installed_mods WHERE is_enabled = :isEnabled ORDER BY name COLLATE NOCASE ASC")
    fun observeModsByStatus(isEnabled: Boolean): Flow<List<InstalledModEntity>>

    @Query("SELECT * FROM installed_mods WHERE unique_id = :uniqueId LIMIT 1")
    suspend fun getByUniqueId(uniqueId: String): InstalledModEntity?

    @Query("SELECT COUNT(*) FROM installed_mods")
    fun observeModCount(): Flow<Int>

    // --- Update ---

    @Update
    suspend fun updateMod(mod: InstalledModEntity)

    @Query("UPDATE installed_mods SET is_enabled = :isEnabled, last_synced_at_ms = :syncedAtMs WHERE unique_id = :uniqueId")
    suspend fun updateStatus(
        uniqueId: String,
        isEnabled: Boolean,
        syncedAtMs: Long = System.currentTimeMillis(),
    )

    @Query("UPDATE installed_mods SET is_enabled = :isEnabled, local_path = :localPath, last_synced_at_ms = :syncedAtMs WHERE unique_id = :uniqueId")
    suspend fun updateStatusAndPath(
        uniqueId: String,
        isEnabled: Boolean,
        localPath: String,
        syncedAtMs: Long = System.currentTimeMillis(),
    )

    // --- Delete ---

    @Delete
    suspend fun deleteMod(mod: InstalledModEntity)

    @Query("DELETE FROM installed_mods WHERE unique_id = :uniqueId")
    suspend fun deleteByUniqueId(uniqueId: String)

    @Query("DELETE FROM installed_mods")
    suspend fun deleteAll()

    // --- Atomic Sync ---

    @Transaction
    suspend fun replaceAll(mods: List<InstalledModEntity>) {
        deleteAll()
        if (mods.isNotEmpty()) {
            insertMods(mods)
        }
    }
}
