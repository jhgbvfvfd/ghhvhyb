package com.example.kpzi.data.local

import com.example.kpzi.domain.model.InstalledMod
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Repository abstracting ModDao CRUD access for locally installed Stardew Valley mods.
 */
class InstalledModRepository(private val modDao: ModDao) {

    val allMods: Flow<List<InstalledMod>> = modDao.getAllInstalledMods().map { entities ->
        entities.map { it.toDomain() }
    }

    fun observeByStatus(isEnabled: Boolean): Flow<List<InstalledMod>> =
        modDao.observeModsByStatus(isEnabled).map { entities ->
            entities.map { it.toDomain() }
        }

    fun observeModCount(): Flow<Int> = modDao.observeModCount()

    suspend fun getAllInstalledModsOnce(): List<InstalledMod> =
        modDao.getAllInstalledModsOnce().map { it.toDomain() }

    suspend fun getByUniqueId(uniqueId: String): InstalledMod? =
        modDao.getByUniqueId(uniqueId)?.toDomain()

    suspend fun insertMod(mod: InstalledMod) {
        modDao.insertMod(mod.toEntity())
    }

    suspend fun updateMod(mod: InstalledMod) {
        modDao.updateMod(mod.toEntity())
    }

    suspend fun updateStatus(uniqueId: String, isEnabled: Boolean) {
        modDao.updateStatus(uniqueId = uniqueId, isEnabled = isEnabled)
    }

    suspend fun updateModStatusAndPath(uniqueId: String, isEnabled: Boolean, localPath: String) {
        modDao.updateStatusAndPath(
            uniqueId = uniqueId,
            isEnabled = isEnabled,
            localPath = localPath,
        )
    }

    suspend fun syncFromDisk(mods: List<InstalledMod>) {
        modDao.replaceAll(mods.map { it.toEntity() })
    }

    suspend fun deleteMod(mod: InstalledMod) {
        modDao.deleteMod(mod.toEntity())
    }

    suspend fun deleteByUniqueId(uniqueId: String) {
        modDao.deleteByUniqueId(uniqueId)
    }
}
