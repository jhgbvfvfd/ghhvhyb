package com.example.kpzi.engine.installer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.example.kpzi.data.local.InstalledModRepository
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.ModDependency
import com.example.kpzi.domain.model.ModManifest
import com.example.kpzi.engine.loader.AssetManager
import com.example.kpzi.engine.loader.CompatibilityEngine
import com.example.kpzi.engine.loader.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile as Zip4jFile
import net.lingala.zip4j.exception.ZipException
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

sealed interface ZipStageResult {
    data class Ready(val pending: PendingModInstall) : ZipStageResult
    data class PasswordRequired(
        val archiveFile: File,
        val sourceLabel: String,
        val isOwnedTempFile: Boolean,
        val errorMessage: String? = null,
    ) : ZipStageResult
}

internal class ZipPasswordRequiredException(message: String? = null) : RuntimeException(message)

data class PendingModInstall(
    val sourceLabel: String,
    val stagedDir: File,
    val manifest: ModManifest,
    val hasEntryDll: Boolean,
    val isUpdate: Boolean,
    val existingFolderName: String?,
    val existingVersion: String?,
    val missingDependencies: List<String>,
)

sealed interface ModInstallResult {
    data class Success(val mod: InstalledMod, val wasUpdate: Boolean) : ModInstallResult
    data class Failure(val reason: String, val rolledBack: Boolean = false) : ModInstallResult
}

/**
 * Clean-room Mod Installation Engine with ZIP Path Traversal protection,
 * ZIP Bomb limits, manifest.json validation, backup & rollback, and DLL warning inspection.
 */
class ModInstallationEngine(
    private val context: Context,
    private val assetManager: AssetManager,
    private val compatibilityEngine: CompatibilityEngine,
    private val logManager: LogManager,
    private val installedModRepository: InstalledModRepository,
) {
    private val _installedMods = MutableStateFlow<List<InstalledMod>>(emptyList())
    val installedMods: StateFlow<List<InstalledMod>> = _installedMods.asStateFlow()

    suspend fun rescanInstalledMods(smapiVersion: String?): List<InstalledMod> = withContext(Dispatchers.IO) {
        val root = assetManager.modsRootDir
        val folders = root.listFiles()?.filter { it.isDirectory }.orEmpty()
        val parsedList = mutableListOf<Pair<File, ModManifest>>()

        for (dir in folders) {
            val manifestFile = findManifestFile(dir) ?: continue
            val manifest = runCatching { parseManifest(manifestFile.readText(Charsets.UTF_8)) }.getOrNull()
                ?: continue
            parsedList.add(dir to manifest)
        }

        val allUniqueIds = parsedList.map { it.second.uniqueId }.toSet()

        val result = parsedList.map { (dir, manifest) ->
            val isEnabled = !dir.name.startsWith(".")
            val (compat, issue) = compatibilityEngine.evaluateInstalledMod(
                manifest = manifest,
                installedUniqueIds = allUniqueIds,
                smapiVersion = smapiVersion,
            )
            val nexusId = manifest.updateKeys
                .firstOrNull { it.startsWith("Nexus:", ignoreCase = true) }
                ?.substringAfter(":")
                ?.trim()
                ?.toLongOrNull()

            InstalledMod(
                uniqueId = manifest.uniqueId,
                folderName = dir.name.removePrefix("."),
                folderPath = dir.absolutePath,
                manifest = manifest,
                isEnabled = isEnabled,
                compatibility = compat,
                issueMessage = issue,
                nexusModId = nexusId,
                sizeBytes = dirSize(dir),
                installedAtMs = dir.lastModified(),
            )
        }.sortedWith(compareByDescending<InstalledMod> { it.isEnabled }.thenBy { it.manifest.name.lowercase() })

        _installedMods.value = result
        installedModRepository.syncFromDisk(result)
        result
    }

    suspend fun stageZipUri(uri: Uri, password: String? = null): Result<ZipStageResult> =
        withContext(Dispatchers.IO) {
            val tempZip = File(context.cacheDir, "stage_${System.currentTimeMillis()}.zip")
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(tempZip).use { output ->
                        copyBounded(input, output, MAX_ARCHIVE_BYTES)
                    }
                } ?: error("ไม่สามารถเปิดไฟล์ ZIP ที่เลือกได้")
            }
            if (copied.isFailure) {
                tempZip.delete()
                return@withContext Result.failure(copied.exceptionOrNull()!!)
            }

            val label = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "mod_archive.zip" } ?: "mod_archive.zip"
            stageArchivedZipFile(
                archiveFile = tempZip,
                sourceLabel = label,
                password = password,
                isOwnedTempFile = true,
            )
        }

    suspend fun stageLocalZipFile(file: File, password: String? = null): Result<ZipStageResult> =
        withContext(Dispatchers.IO) {
            if (!file.exists() || file.length() <= 0L) {
                return@withContext Result.failure(IllegalArgumentException("ไม่พบไฟล์ ZIP หรือไฟล์ว่างเปล่า"))
            }
            if (file.length() > MAX_ARCHIVE_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("ขนาดไฟล์ ZIP เกินขีดจำกัดความปลอดภัย (500 MB)"))
            }
            stageArchivedZipFile(
                archiveFile = file,
                sourceLabel = file.name,
                password = password,
                isOwnedTempFile = false,
            )
        }

    suspend fun stageArchivedZipFile(
        archiveFile: File,
        sourceLabel: String,
        password: String?,
        isOwnedTempFile: Boolean,
    ): Result<ZipStageResult> = withContext(Dispatchers.IO) {
        val result = runCatching {
            val z4j = Zip4jFile(archiveFile)
            if (!z4j.isValidZipFile) {
                error("ไฟล์ ZIP เสียหายหรือไม่ใช่รูปแบบไฟล์บีบอัด ZIP ที่รองรับ")
            }
            if (z4j.isEncrypted && password.isNullOrEmpty()) {
                return@runCatching ZipStageResult.PasswordRequired(
                    archiveFile = archiveFile,
                    sourceLabel = sourceLabel,
                    isOwnedTempFile = isOwnedTempFile,
                    errorMessage = null,
                )
            }

            val stageRoot = File(context.cacheDir, "stage_zip_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            try {
                extractZipSafely(archiveFile, stageRoot, password)
                val pending = buildPendingFromStaging(stageRoot, sourceLabel)
                ZipStageResult.Ready(pending)
            } catch (e: ZipPasswordRequiredException) {
                stageRoot.deleteRecursively()
                ZipStageResult.PasswordRequired(
                    archiveFile = archiveFile,
                    sourceLabel = sourceLabel,
                    isOwnedTempFile = isOwnedTempFile,
                    errorMessage = e.message ?: "รหัสผ่านไม่ถูกต้อง กรุณาลองใหม่อีกครั้ง",
                )
            } catch (t: Throwable) {
                stageRoot.deleteRecursively()
                throw t
            }
        }

        if (isOwnedTempFile) {
            val outcome = result.getOrNull()
            if (outcome !is ZipStageResult.PasswordRequired) {
                archiveFile.delete()
            }
        }
        result
    }

    fun discardPasswordPrompt(archiveFile: File, isOwnedTempFile: Boolean) {
        if (isOwnedTempFile) {
            runCatching { archiveFile.delete() }
        }
    }

    suspend fun stageFolderTreeUri(treeUri: Uri): Result<PendingModInstall> = withContext(Dispatchers.IO) {
        runCatching {
            val stageRoot = File(context.cacheDir, "stage_folder_${System.currentTimeMillis()}").apply {
                mkdirs()
            }
            try {
                val docId = DocumentsContract.getTreeDocumentId(treeUri)
                copyDocumentTreeRecursive(treeUri, docId, stageRoot, depth = 0, totalCounter = LongArray(1))
                buildPendingFromStaging(stageRoot, docId.substringAfterLast("/").ifBlank { "FolderMod" })
            } catch (t: Throwable) {
                stageRoot.deleteRecursively()
                throw t
            }
        }
    }

    private fun buildPendingFromStaging(stageRoot: File, sourceLabel: String): PendingModInstall {
        val manifestFile = findManifestFile(stageRoot)
            ?: error("ไม่พบไฟล์ manifest.json ในมอดนี้ (ไม่ใช่โครงสร้างมอด SMAPI มาตรฐาน)")
        val modContentDir = manifestFile.parentFile ?: stageRoot
        val manifest = parseManifest(manifestFile.readText(Charsets.UTF_8))

        val existing = _installedMods.value.firstOrNull {
            it.uniqueId.equals(manifest.uniqueId, ignoreCase = true)
        }
        val installedIds = _installedMods.value.map { it.uniqueId }.toSet()
        val missingDeps = manifest.dependencies
            .filter { it.isRequired && it.uniqueId.isNotBlank() }
            .map { it.uniqueId }
            .filter { dep -> installedIds.none { it.equals(dep, ignoreCase = true) } }

        val hasDll = !manifest.entryDll.isNullOrBlank() ||
            modContentDir.walkTopDown().any { it.isFile && it.extension.equals("dll", ignoreCase = true) }

        return PendingModInstall(
            sourceLabel = sourceLabel,
            stagedDir = modContentDir,
            manifest = manifest,
            hasEntryDll = hasDll,
            isUpdate = existing != null,
            existingFolderName = existing?.folderName,
            existingVersion = existing?.manifest?.version,
            missingDependencies = missingDeps,
        )
    }

    suspend fun commitPendingInstall(
        pending: PendingModInstall,
        smapiVersion: String?,
    ): ModInstallResult = withContext(Dispatchers.IO) {
        val safeFolderName = sanitizeFolderName(
            pending.existingFolderName ?: pending.manifest.name.ifBlank { pending.manifest.uniqueId },
        )
        val modsRoot = assetManager.modsRootDir.canonicalFile
        val targetDir = File(modsRoot, safeFolderName).canonicalFile
        if (!targetDir.path.startsWith(modsRoot.path + File.separator)) {
            return@withContext ModInstallResult.Failure("ปฏิเสธเส้นทางโฟลเดอร์ที่ไม่ปลอดภัย")
        }

        val disabledMirror = File(modsRoot, ".$safeFolderName")
        val backupZip = if (targetDir.exists()) {
            File(assetManager.modBackupsDir, "${safeFolderName}_backup_${System.currentTimeMillis()}.zip")
        } else if (disabledMirror.exists()) {
            File(assetManager.modBackupsDir, "${safeFolderName}_backup_${System.currentTimeMillis()}.zip")
        } else {
            null
        }

        val existingSourceDir = when {
            targetDir.exists() -> targetDir
            disabledMirror.exists() -> disabledMirror
            else -> null
        }

        try {
            if (existingSourceDir != null && backupZip != null) {
                zipDirectory(existingSourceDir, backupZip)
                logManager.info("ModInstaller", "สำรองมอดเดิมก่อนอัปเดต: ${backupZip.name}")
                existingSourceDir.deleteRecursively()
            }

            targetDir.mkdirs()
            pending.stagedDir.copyRecursively(targetDir, overwrite = true)

            logManager.info(
                "ModInstaller",
                "ติดตั้งมอด ${pending.manifest.name} v${pending.manifest.version} (${pending.manifest.uniqueId}) สำเร็จ",
            )
            cleanupStaging(pending.stagedDir)
            val refreshed = rescanInstalledMods(smapiVersion)
            val installed = refreshed.firstOrNull { it.uniqueId.equals(pending.manifest.uniqueId, ignoreCase = true) }
                ?: error("ไม่สามารถอ่านข้อมูลมอดหลังติดตั้ง")
            ModInstallResult.Success(installed, wasUpdate = pending.isUpdate)
        } catch (t: Throwable) {
            logManager.error("ModInstaller", "ติดตั้งมอดล้มเหลว: ${t.localizedMessage}")
            var rolledBack = false
            if (backupZip != null && backupZip.exists()) {
                runCatching {
                    targetDir.deleteRecursively()
                    targetDir.mkdirs()
                    extractZipSafely(backupZip, targetDir)
                    rolledBack = true
                    logManager.warn("ModInstaller", "ทำ Rollback คืนค่ามอดเวอร์ชันเดิมสำเร็จ")
                }
            }
            cleanupStaging(pending.stagedDir)
            ModInstallResult.Failure(t.localizedMessage ?: "ติดตั้งมอดล้มเหลว", rolledBack = rolledBack)
        }
    }

    fun discardPendingInstall(pending: PendingModInstall) {
        cleanupStaging(pending.stagedDir)
    }

    suspend fun toggleModEnabled(uniqueId: String, smapiVersion: String?): Boolean = withContext(Dispatchers.IO) {
        val mod = _installedMods.value.firstOrNull { it.uniqueId.equals(uniqueId, ignoreCase = true) }
            ?: return@withContext false
        val currentDir = File(mod.folderPath).canonicalFile
        val modsRoot = assetManager.modsRootDir.canonicalFile
        if (!currentDir.exists() || currentDir.parentFile?.canonicalFile != modsRoot) return@withContext false

        val newName = if (mod.isEnabled) ".${mod.folderName}" else mod.folderName
        val destDir = File(modsRoot, newName)
        if (destDir.exists()) return@withContext false
        val renamed = currentDir.renameTo(destDir)
        if (renamed) {
            installedModRepository.updateModStatusAndPath(
                uniqueId = mod.uniqueId,
                isEnabled = !mod.isEnabled,
                localPath = destDir.absolutePath,
            )
            logManager.info(
                "ModInstaller",
                if (mod.isEnabled) "ปิดใช้งานมอด ${mod.manifest.name}" else "เปิดใช้งานมอด ${mod.manifest.name}",
            )
            rescanInstalledMods(smapiVersion)
        }
        renamed
    }

    suspend fun uninstallMod(uniqueId: String, smapiVersion: String?): Boolean = withContext(Dispatchers.IO) {
        val mod = _installedMods.value.firstOrNull { it.uniqueId.equals(uniqueId, ignoreCase = true) }
            ?: return@withContext false
        val currentDir = File(mod.folderPath).canonicalFile
        val modsRoot = assetManager.modsRootDir.canonicalFile
        // Strict boundary check: never delete outside Mods directory
        if (!currentDir.exists() || currentDir.parentFile?.canonicalFile != modsRoot) {
            logManager.error("ModInstaller", "ปฏิเสธการลบไฟล์นอกโฟลเดอร์มอด: ${currentDir.path}")
            return@withContext false
        }
        val deleted = currentDir.deleteRecursively()
        if (deleted) {
            installedModRepository.deleteByUniqueId(mod.uniqueId)
            logManager.info("ModInstaller", "ถอนการติดตั้งมอด ${mod.manifest.name} (${mod.uniqueId}) แล้ว")
            rescanInstalledMods(smapiVersion)
        }
        deleted
    }

    private fun extractZipSafely(zipFile: File, destRoot: File, password: String? = null) {
        extractZipArchiveSafely(zipFile, destRoot, password)
    }

    private fun copyDocumentTreeRecursive(
        treeUri: Uri,
        parentDocId: String,
        destDir: File,
        depth: Int,
        totalCounter: LongArray,
    ) {
        if (depth > 10) error("โครงสร้างโฟลเดอร์ลึกเกินกำหนด")
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIdx = cursor.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            while (cursor.moveToNext()) {
                val childId = cursor.getString(idIdx)
                val name = cursor.getString(nameIdx) ?: continue
                if (name.contains("..") || name.contains("/") || name.contains("\\")) continue
                val mime = cursor.getString(mimeIdx)
                val childTarget = File(destDir, name).canonicalFile
                if (!childTarget.path.startsWith(destDir.canonicalPath + File.separator)) continue

                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    childTarget.mkdirs()
                    copyDocumentTreeRecursive(treeUri, childId, childTarget, depth + 1, totalCounter)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    context.contentResolver.openInputStream(fileUri)?.use { input ->
                        FileOutputStream(childTarget).use { output ->
                            val copied = copyBounded(input, output, MAX_SINGLE_ENTRY_BYTES)
                            totalCounter[0] += copied
                            if (totalCounter[0] > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                error("ขนาดโฟลเดอร์มอดเกินขีดจำกัดความปลอดภัย")
                            }
                        }
                    }
                }
            }
        }
    }

    private fun findManifestFile(root: File): File? {
        val direct = File(root, "manifest.json")
        if (direct.isFile) return direct
        return root.walkTopDown()
            .maxDepth(3)
            .firstOrNull { it.isFile && it.name.equals("manifest.json", ignoreCase = true) }
    }

    private fun parseManifest(rawText: String): ModManifest {
        val sanitized = stripJsonCommentsAndTrailingCommas(rawText)
        val json = JSONObject(sanitized)
        val uniqueId = json.optString("UniqueID").trim()
        require(uniqueId.isNotBlank()) { "ไฟล์ manifest.json ไม่มีฟิลด์ UniqueID" }

        val name = json.optString("Name").ifBlank { uniqueId }
        val author = json.optString("Author").ifBlank { "ไม่ระบุผู้พัฒนา" }
        val version = json.optString("Version").ifBlank { "1.0.0" }
        val description = json.optString("Description", "")
        val minApi = json.optString("MinimumApiVersion").takeIf { it.isNotBlank() && it != "null" }
        val entryDll = json.optString("EntryDll").takeIf { it.isNotBlank() && it != "null" }
        val contentPackFor = json.optJSONObject("ContentPackFor")?.optString("UniqueID")
            ?.takeIf { it.isNotBlank() && it != "null" }

        val depsArr = json.optJSONArray("Dependencies") ?: JSONArray()
        val dependencies = buildList {
            for (i in 0 until depsArr.length()) {
                val depObj = depsArr.optJSONObject(i) ?: continue
                val depId = depObj.optString("UniqueID").trim()
                if (depId.isNotBlank()) {
                    add(
                        ModDependency(
                            uniqueId = depId,
                            minimumVersion = depObj.optString("MinimumVersion").takeIf { it.isNotBlank() && it != "null" },
                            isRequired = depObj.optBoolean("IsRequired", true),
                        ),
                    )
                }
            }
        }

        val updateArr = json.optJSONArray("UpdateKeys") ?: JSONArray()
        val updateKeys = buildList {
            for (i in 0 until updateArr.length()) {
                val key = updateArr.optString(i).trim()
                if (key.isNotBlank()) add(key)
            }
        }

        return ModManifest(
            uniqueId = uniqueId,
            name = name,
            author = author,
            version = version,
            description = description,
            minimumApiVersion = minApi,
            entryDll = entryDll,
            contentPackForUniqueId = contentPackFor,
            dependencies = dependencies,
            updateKeys = updateKeys,
        )
    }

    private fun stripJsonCommentsAndTrailingCommas(input: String): String {
        val sb = StringBuilder()
        var i = 0
        var inString = false
        var escape = false
        val cleanBom = input.removePrefix("\uFEFF")
        while (i < cleanBom.length) {
            val c = cleanBom[i]
            if (inString) {
                sb.append(c)
                if (escape) {
                    escape = false
                } else if (c == '\\') {
                    escape = true
                } else if (c == '"') {
                    inString = false
                }
                i++
            } else {
                if (c == '"') {
                    inString = true
                    sb.append(c)
                    i++
                } else if (c == '/' && i + 1 < cleanBom.length && cleanBom[i + 1] == '/') {
                    while (i < cleanBom.length && cleanBom[i] != '\n') i++
                } else if (c == '/' && i + 1 < cleanBom.length && cleanBom[i + 1] == '*') {
                    i += 2
                    while (i + 1 < cleanBom.length && !(cleanBom[i] == '*' && cleanBom[i + 1] == '/')) i++
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
        }
        return sb.toString().replace(Regex(",\\s*([}\\]])"), "$1")
    }

    private fun zipDirectory(sourceDir: File, outZip: File) {
        ZipOutputStream(FileOutputStream(outZip)).use { zos ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val relPath = file.relativeTo(sourceDir).path.replace(File.separatorChar, '/')
                zos.putNextEntry(ZipEntry(relPath))
                FileInputStream(file).use { it.copyTo(zos) }
                zos.closeEntry()
            }
        }
    }

    private fun copyBounded(input: InputStream, output: FileOutputStream, maxBytes: Long): Long {
        val buf = ByteArray(8192)
        var total = 0L
        while (true) {
            val r = input.read(buf)
            if (r <= 0) break
            total += r
            if (total > maxBytes) error("ขนาดไฟล์เกินขีดจำกัดความปลอดภัย")
            output.write(buf, 0, r)
        }
        return total
    }

    private fun cleanupStaging(stagedDir: File) {
        var cur: File? = stagedDir
        while (cur != null && cur.parentFile != null && cur.parentFile != context.cacheDir) {
            cur = cur.parentFile
        }
        if (cur != null && cur.name.startsWith("stage_")) {
            cur.deleteRecursively()
        } else {
            stagedDir.deleteRecursively()
        }
    }

    private fun sanitizeFolderName(raw: String): String {
        val cleaned = raw.replace(Regex("[\\\\/:*?\"<>|.]"), "_").trim('_', ' ')
        return cleaned.ifBlank { "StardewMod" }.take(64)
    }

    private fun dirSize(dir: File): Long =
        dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }

    companion object {
        private const val MAX_ZIP_ENTRIES = 5000
        private const val MAX_SINGLE_ENTRY_BYTES = 250L * 1024L * 1024L
        private const val MAX_TOTAL_UNCOMPRESSED_BYTES = 800L * 1024L * 1024L
        private const val MAX_ARCHIVE_BYTES = 500L * 1024L * 1024L

        internal fun extractZipArchiveSafely(
            zipFile: File,
            destRoot: File,
            password: String? = null,
        ) {
            val canonicalRoot = destRoot.canonicalFile
            val z4j = Zip4jFile(zipFile)
            if (z4j.isEncrypted && password.isNullOrEmpty()) {
                throw ZipPasswordRequiredException("ไฟล์ ZIP มีรหัสผ่าน กรุณากรอกรหัสผ่าน")
            }
            if (!password.isNullOrEmpty()) {
                z4j.setPassword(password.toCharArray())
            }

            val headers = try {
                z4j.fileHeaders
            } catch (e: ZipException) {
                if (e.type == ZipException.Type.WRONG_PASSWORD) {
                    throw ZipPasswordRequiredException("รหัสผ่านไม่ถูกต้อง กรุณาลองใหม่อีกครั้ง")
                }
                throw e
            }

            if (headers.isEmpty()) {
                error("ไฟล์ ZIP เสียหายหรือไม่มีข้อมูลภายใน")
            }
            if (headers.size > MAX_ZIP_ENTRIES) {
                error("ตรวจพบจำนวนไฟล์ใน ZIP มากผิดปกติ (ป้องกัน ZIP Bomb)")
            }

            var totalUncompressedBytes = 0L
            for (header in headers) {
                val normalizedName = header.fileName.replace('\\', '/').trimStart('/')
                if (normalizedName.isBlank()) continue

                val outFile = File(canonicalRoot, normalizedName).canonicalFile
                if (!outFile.path.startsWith(canonicalRoot.path + File.separator) && outFile != canonicalRoot) {
                    error("ตรวจพบ Path Traversal ที่ไม่ปลอดภัยในไฟล์ ZIP: ${header.fileName}")
                }

                if (header.isDirectory) {
                    outFile.mkdirs()
                    continue
                }

                if (header.uncompressedSize > MAX_SINGLE_ENTRY_BYTES) {
                    error("ไฟล์แตกออกมามีขนาดใหญ่ผิดปกติ (ป้องกัน ZIP Bomb)")
                }

                outFile.parentFile?.mkdirs()
                try {
                    z4j.getInputStream(header).use { entryStream ->
                        val buffer = ByteArray(8192)
                        var entryBytes = 0L
                        FileOutputStream(outFile).use { fos ->
                            while (true) {
                                val read = entryStream.read(buffer)
                                if (read <= 0) break
                                entryBytes += read
                                totalUncompressedBytes += read
                                if (entryBytes > MAX_SINGLE_ENTRY_BYTES || totalUncompressedBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                    error("ไฟล์แตกออกมามีขนาดใหญ่ผิดปกติ (ป้องกัน ZIP Bomb)")
                                }
                                fos.write(buffer, 0, read)
                            }
                        }
                    }
                } catch (e: ZipException) {
                    val msg = e.message.orEmpty().lowercase()
                    if (e.type == ZipException.Type.WRONG_PASSWORD ||
                        e.type == ZipException.Type.CHECKSUM_MISMATCH ||
                        msg.contains("password") ||
                        msg.contains("mac") ||
                        msg.contains("crc")
                    ) {
                        throw ZipPasswordRequiredException("รหัสผ่านไม่ถูกต้อง กรุณาลองใหม่อีกครั้ง")
                    }
                    throw e
                } catch (t: Throwable) {
                    val msg = t.message.orEmpty().lowercase()
                    if (z4j.isEncrypted && (msg.contains("password") || msg.contains("crc") || msg.contains("mac") || msg.contains("wrong"))) {
                        throw ZipPasswordRequiredException("รหัสผ่านไม่ถูกต้อง กรุณาลองใหม่อีกครั้ง")
                    }
                    throw t
                }
            }
        }
    }
}
