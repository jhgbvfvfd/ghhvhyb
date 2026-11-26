package com.example.kpzi.engine.installer

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.example.kpzi.domain.model.BackupCategory
import com.example.kpzi.domain.model.SaveBackupInfo
import com.example.kpzi.engine.loader.AssetManager
import com.example.kpzi.engine.loader.LogManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Manages full mod/log ZIP backups, SAF Stardew Valley save folder backups,
 * user-initiated ZIP download/export via SAF CreateDocument, FileProvider sharing,
 * and safe ZIP restoration with Path Traversal & ZIP Bomb protection.
 */
class SaveBackupManager(
    private val context: Context,
    private val assetManager: AssetManager,
    private val logManager: LogManager,
) {
    private val _backups = MutableStateFlow<List<SaveBackupInfo>>(emptyList())
    val backups: StateFlow<List<SaveBackupInfo>> = _backups.asStateFlow()

    suspend fun refreshBackups(): List<SaveBackupInfo> = withContext(Dispatchers.IO) {
        val list = assetManager.saveBackupsDir
            .listFiles { f -> f.isFile && f.extension.equals("zip", ignoreCase = true) }
            .orEmpty()
            .sortedByDescending { it.lastModified() }
            .map {
                SaveBackupInfo(
                    fileName = it.name,
                    filePath = it.absolutePath,
                    sizeBytes = it.length(),
                    createdAtMs = it.lastModified(),
                )
            }
        _backups.value = list
        list
    }

    /**
     * Packages all installed mods (Mods/), runtime logs (Logs/), and metadata
     * into a downloadable ZIP archive in saveBackupsDir.
     */
    suspend fun createModsAndLogsBackupZip(): Result<SaveBackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outZip = File(assetManager.saveBackupsDir, "KpziModsBackup_$stamp.zip")
            var fileCount = 0

            val modFiles = assetManager.modsRootDir.walkTopDown().filter { it.isFile }.toList()
            val logFiles = assetManager.logsDir.walkTopDown().filter { it.isFile }.toList()
            val modFolderCount = assetManager.modsRootDir.listFiles()?.count { it.isDirectory } ?: 0

            if (modFiles.isEmpty() && logFiles.isEmpty()) {
                error("ยังไม่มีมอดหรือบันทึกข้อมูลสำหรับสร้างไฟล์สำรอง")
            }

            ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                modFiles.forEach { file ->
                    val rel = file.relativeTo(assetManager.modsRootDir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry("Mods/$rel"))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                    fileCount++
                }
                logFiles.forEach { file ->
                    val rel = file.relativeTo(assetManager.logsDir).path.replace(File.separatorChar, '/')
                    zos.putNextEntry(ZipEntry("Logs/$rel"))
                    FileInputStream(file).use { it.copyTo(zos) }
                    zos.closeEntry()
                    fileCount++
                }

                val metaJson = JSONObject().apply {
                    put("app", "Kpzi Mod Manager")
                    put("version", "1.0.0")
                    put("createdAtMs", System.currentTimeMillis())
                    put("modFolderCount", modFolderCount)
                    put("totalFiles", fileCount)
                }.toString(2)
                zos.putNextEntry(ZipEntry("backup_metadata.json"))
                zos.write(metaJson.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            logManager.info(
                "SaveBackup",
                "สร้างไฟล์ ZIP สำรองมอดและบันทึกสำเร็จ (${outZip.name}, $modFolderCount มอด, $fileCount ไฟล์)",
            )
            refreshBackups()
            SaveBackupInfo(
                fileName = outZip.name,
                filePath = outZip.absolutePath,
                sizeBytes = outZip.length(),
                createdAtMs = outZip.lastModified(),
                category = BackupCategory.MODS_AND_CONFIG,
            )
        }
    }

    /**
     * Copies a generated backup ZIP to a user-selected SAF destination URI
     * (e.g. phone Downloads folder via CreateDocument).
     */
    suspend fun exportBackupToUri(backup: SaveBackupInfo, destUri: Uri): Result<Long> =
        withContext(Dispatchers.IO) {
            runCatching {
                val srcFile = File(backup.filePath)
                require(srcFile.exists() && srcFile.isFile) { "ไม่พบไฟล์สำรอง ${backup.fileName}" }
                var copiedBytes = 0L
                context.contentResolver.openOutputStream(destUri)?.use { output ->
                    FileInputStream(srcFile).use { input ->
                        copiedBytes = input.copyTo(output)
                    }
                    output.flush()
                } ?: error("ไม่สามารถเขียนไฟล์ไปยังปลายทางที่เลือกได้")
                logManager.info("SaveBackup", "ส่งออกไฟล์สำรอง ${backup.fileName} ลงเครื่องสำเร็จ")
                copiedBytes
            }
        }

    fun getShareableUri(backup: SaveBackupInfo): Uri {
        val file = File(backup.filePath)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    suspend fun createBackupFromTreeUri(treeUri: Uri): Result<SaveBackupInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val outZip = File(assetManager.saveBackupsDir, "StardewSave_$stamp.zip")
            val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
            var fileCount = 0

            ZipOutputStream(FileOutputStream(outZip)).use { zos ->
                fileCount = zipTreeRecursive(treeUri, rootDocId, "", zos, depth = 0)
            }
            if (fileCount == 0) {
                outZip.delete()
                error("ไม่พบไฟล์เซฟในโฟลเดอร์ที่เลือก")
            }

            logManager.info("SaveBackup", "สำรองไฟล์เซฟเกมสำเร็จ (${outZip.name}, $fileCount ไฟล์)")
            refreshBackups()
            SaveBackupInfo(
                fileName = outZip.name,
                filePath = outZip.absolutePath,
                sizeBytes = outZip.length(),
                createdAtMs = outZip.lastModified(),
                category = BackupCategory.SAVE_FILES,
            )
        }
    }

    suspend fun restoreModsFromBackupZip(backup: SaveBackupInfo): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val zipFile = File(backup.filePath)
            require(zipFile.exists()) { "ไม่พบไฟล์สำรอง ${backup.fileName}" }
            extractModsFromBackupZipFile(zipFile)
        }
    }

    suspend fun importAndRestoreModsBackupUri(sourceUri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val tempZip = File(context.cacheDir, "restore_backup_${System.currentTimeMillis()}.zip")
            try {
                context.contentResolver.openInputStream(sourceUri)?.use { input ->
                    FileOutputStream(tempZip).use { output ->
                        val buf = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val r = input.read(buf)
                            if (r <= 0) break
                            total += r
                            if (total > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                error("ไฟล์สำรองมีขนาดใหญ่เกินกำหนด")
                            }
                            output.write(buf, 0, r)
                        }
                    }
                } ?: error("ไม่สามารถเปิดไฟล์ ZIP ที่เลือกได้")
                extractModsFromBackupZipFile(tempZip)
            } finally {
                tempZip.delete()
            }
        }
    }

    private fun extractModsFromBackupZipFile(zipFile: File): Int {
        val modsRoot = assetManager.modsRootDir.canonicalFile
        var entryCount = 0
        var totalBytes = 0L
        var restoredFileCount = 0

        ZipInputStream(BufferedInputStream(FileInputStream(zipFile))).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                entryCount++
                if (entryCount > MAX_ZIP_ENTRIES) {
                    error("จำนวนไฟล์ใน ZIP เกินขีดจำกัดความปลอดภัย")
                }

                val normalizedName = entry.name.replace('\\', '/').trimStart('/')
                if (!entry.isDirectory && normalizedName.startsWith("Mods/")) {
                    val relModPath = normalizedName.removePrefix("Mods/")
                    if (relModPath.isNotBlank() && !relModPath.contains("..")) {
                        val outFile = File(modsRoot, relModPath).canonicalFile
                        if (outFile.path.startsWith(modsRoot.path + File.separator)) {
                            outFile.parentFile?.mkdirs()
                            val buf = ByteArray(8192)
                            FileOutputStream(outFile).use { fos ->
                                while (true) {
                                    val r = zis.read(buf)
                                    if (r <= 0) break
                                    totalBytes += r
                                    if (totalBytes > MAX_TOTAL_UNCOMPRESSED_BYTES) {
                                        error("ขนาดไฟล์ที่แตกออกเกินขีดจำกัดความปลอดภัย")
                                    }
                                    fos.write(buf, 0, r)
                                }
                            }
                            restoredFileCount++
                        }
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }

        if (restoredFileCount == 0) {
            error("ไม่พบโฟลเดอร์ Mods/ ในไฟล์ ZIP สำรองนี้")
        }
        logManager.info("SaveBackup", "กู้คืนไฟล์มอดจาก ZIP สำรองสำเร็จ ($restoredFileCount ไฟล์)")
        return restoredFileCount
    }

    suspend fun restoreBackupToTreeUri(backup: SaveBackupInfo, targetTreeUri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val zipFile = File(backup.filePath)
                require(zipFile.exists()) { "ไม่พบไฟล์สำรอง ${backup.fileName}" }
                val rootDocId = DocumentsContract.getTreeDocumentId(targetTreeUri)
                var restoredCount = 0

                ZipInputStream(BufferedInputStream(zipFile.inputStream())).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        val safeName = entry.name.replace('\\', '/')
                        if (!entry.isDirectory && !safeName.contains("..")) {
                            val parts = safeName.split("/").filter { it.isNotBlank() }
                            if (parts.isNotEmpty()) {
                                val parentDoc = ensureSubdirectories(targetTreeUri, rootDocId, parts.dropLast(1))
                                val fileName = parts.last()
                                val docUri = DocumentsContract.createDocument(
                                    context.contentResolver,
                                    DocumentsContract.buildDocumentUriUsingTree(targetTreeUri, parentDoc),
                                    "application/octet-stream",
                                    fileName,
                                )
                                if (docUri != null) {
                                    context.contentResolver.openOutputStream(docUri)?.use { os ->
                                        zis.copyTo(os)
                                    }
                                    restoredCount++
                                }
                            }
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
                logManager.info("SaveBackup", "กู้คืนไฟล์เซฟจาก ${backup.fileName} สำเร็จ ($restoredCount ไฟล์)")
                restoredCount
            }
        }

    suspend fun deleteBackup(backup: SaveBackupInfo) = withContext(Dispatchers.IO) {
        runCatching { File(backup.filePath).delete() }
        refreshBackups()
    }

    private fun zipTreeRecursive(
        treeUri: Uri,
        docId: String,
        relPrefix: String,
        zos: ZipOutputStream,
        depth: Int,
    ): Int {
        if (depth > 6) return 0
        var count = 0
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
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
                val entryPath = if (relPrefix.isEmpty()) name else "$relPrefix/$name"
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    count += zipTreeRecursive(treeUri, childId, entryPath, zos, depth + 1)
                } else {
                    val fileUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                    context.contentResolver.openInputStream(fileUri)?.use { input ->
                        zos.putNextEntry(ZipEntry(entryPath))
                        input.copyTo(zos)
                        zos.closeEntry()
                        count++
                    }
                }
            }
        }
        return count
    }

    private fun ensureSubdirectories(treeUri: Uri, rootDocId: String, segments: List<String>): String {
        var currentDocId = rootDocId
        for (segment in segments) {
            val existing = findChildDir(treeUri, currentDocId, segment)
            if (existing != null) {
                currentDocId = existing
            } else {
                val createdUri = DocumentsContract.createDocument(
                    context.contentResolver,
                    DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId),
                    DocumentsContract.Document.MIME_TYPE_DIR,
                    segment,
                )
                currentDocId = createdUri?.let { DocumentsContract.getDocumentId(it) } ?: currentDocId
            }
        }
        return currentDocId
    }

    private fun findChildDir(treeUri: Uri, parentDocId: String, name: String): String? {
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
                if (cursor.getString(nameIdx) == name &&
                    cursor.getString(mimeIdx) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return cursor.getString(idIdx)
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_ZIP_ENTRIES = 10_000
        const val MAX_TOTAL_UNCOMPRESSED_BYTES = 1024L * 1024L * 1024L
    }
}
