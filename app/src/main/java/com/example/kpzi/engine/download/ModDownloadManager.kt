package com.example.kpzi.engine.download

import com.example.kpzi.data.local.DownloadRecordDao
import com.example.kpzi.data.local.toDomain
import com.example.kpzi.data.local.toEntity
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.domain.model.DownloadTask
import com.example.kpzi.engine.loader.AssetManager
import com.example.kpzi.engine.loader.LogManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

/**
 * Real OkHttp streaming Download Manager with persistent Room queue/history,
 * HTTP Range pause/resume, speed & ETA calculation, SHA-256 & ZIP integrity verification.
 */
class ModDownloadManager(
    private val httpClient: OkHttpClient,
    private val assetManager: AssetManager,
    private val downloadDao: DownloadRecordDao,
    private val logManager: LogManager,
    private val onBackgroundStateChanged: (Boolean) -> Unit = {},
    private val onDownloadOutcome: (DownloadTask, Boolean) -> Unit = { _, _ -> },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val activeJobs = ConcurrentHashMap<String, Job>()
    private val activeUrls = ConcurrentHashMap<String, String>()

    val downloadsFlow: Flow<List<DownloadTask>> = downloadDao.observeAll().map { list ->
        list.map { it.toDomain() }
    }

    fun enqueueDownload(
        modId: Long,
        fileId: Long,
        modName: String,
        fileName: String,
        version: String,
        pictureUrl: String?,
        downloadUrl: String,
        expectedBytes: Long = 0L,
    ) {
        val safeFileName = fileName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "mod_${modId}_${fileId}.zip" }
        val taskId = "${modId}_${fileId}_${System.currentTimeMillis()}"
        val destFile = File(assetManager.downloadsDir, safeFileName)
        activeUrls[taskId] = downloadUrl

        val initialTask = DownloadTask(
            id = taskId,
            modId = modId,
            fileId = fileId,
            modName = modName,
            fileName = safeFileName,
            version = version,
            pictureUrl = pictureUrl,
            totalBytes = expectedBytes,
            downloadedBytes = 0L,
            speedBytesPerSec = 0L,
            etaSeconds = null,
            status = DownloadStatus.QUEUED,
            localFilePath = destFile.absolutePath,
            sha256 = null,
        )

        onBackgroundStateChanged(true)
        scope.launch {
            downloadDao.upsert(initialTask.toEntity())
            startDownloadJob(initialTask, downloadUrl, resumeFromExisting = false)
        }
    }

    fun pauseDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            val current = downloadDao.getById(taskId)?.toDomain() ?: return@launch
            if (current.status == DownloadStatus.DOWNLOADING || current.status == DownloadStatus.QUEUED) {
                downloadDao.upsert(
                    current.copy(
                        status = DownloadStatus.PAUSED,
                        speedBytesPerSec = 0L,
                        etaSeconds = null,
                    ).toEntity(),
                )
            }
        }
    }

    fun resumeOrRetryDownload(taskId: String, refreshedUrl: String? = null) {
        scope.launch {
            val current = downloadDao.getById(taskId)?.toDomain() ?: return@launch
            val url = refreshedUrl ?: activeUrls[taskId]
            if (url.isNullOrBlank()) {
                downloadDao.upsert(
                    current.copy(
                        status = DownloadStatus.FAILED,
                        errorMessage = "ลิงก์ดาวน์โหลดหมดอายุ กรุณากดดาวน์โหลดใหม่จากหน้ามอด",
                    ).toEntity(),
                )
                return@launch
            }
            activeUrls[taskId] = url
            startDownloadJob(current, url, resumeFromExisting = current.supportsResume)
        }
    }

    fun cancelDownload(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch {
            val current = downloadDao.getById(taskId)?.toDomain() ?: return@launch
            current.localFilePath?.let { path ->
                runCatching { File("$path.part").delete() }
            }
            downloadDao.upsert(
                current.copy(
                    status = DownloadStatus.CANCELLED,
                    speedBytesPerSec = 0L,
                    etaSeconds = null,
                ).toEntity(),
            )
        }
    }

    fun removeRecord(taskId: String) {
        activeJobs[taskId]?.cancel()
        activeJobs.remove(taskId)
        scope.launch { downloadDao.deleteById(taskId) }
    }

    private fun startDownloadJob(task: DownloadTask, url: String, resumeFromExisting: Boolean) {
        activeJobs[task.id]?.cancel()
        onBackgroundStateChanged(true)
        val job = scope.launch {
            val finalPath = task.localFilePath ?: File(assetManager.downloadsDir, task.fileName).absolutePath
            val finalFile = File(finalPath)
            val partFile = File("$finalPath.part")

            var existingBytes = if (resumeFromExisting && partFile.exists()) partFile.length() else 0L
            if (!resumeFromExisting && partFile.exists()) partFile.delete()

            val reqBuilder = Request.Builder().url(url)
            if (existingBytes > 0L) {
                reqBuilder.header("Range", "bytes=$existingBytes-")
            }

            try {
                httpClient.newCall(reqBuilder.build()).execute().use { response ->
                    if (!response.isSuccessful && response.code != 206) {
                        throw IllegalStateException("HTTP ${response.code}: ดาวน์โหลดไฟล์ไม่สำเร็จ")
                    }

                    val supportsRange = response.code == 206 ||
                        response.header("Accept-Ranges").equals("bytes", ignoreCase = true)

                    if (existingBytes > 0L && response.code != 206) {
                        existingBytes = 0L
                        partFile.delete()
                    }

                    val body = response.body ?: throw IllegalStateException("Response body ว่างเปล่า")
                    val contentLength = body.contentLength().coerceAtLeast(0L)
                    val totalBytes = if (contentLength > 0L) existingBytes + contentLength else task.totalBytes

                    downloadDao.upsert(
                        task.copy(
                            status = DownloadStatus.DOWNLOADING,
                            totalBytes = totalBytes,
                            downloadedBytes = existingBytes,
                            supportsResume = supportsRange,
                            errorMessage = null,
                        ).toEntity(),
                    )

                    RandomAccessFile(partFile, "rw").use { raf ->
                        raf.seek(existingBytes)
                        val input = body.byteStream()
                        val buffer = ByteArray(16 * 1024)
                        var downloaded = existingBytes
                        var lastReportMs = System.currentTimeMillis()
                        var bytesSinceReport = 0L

                        while (true) {
                            val read = input.read(buffer)
                            if (read == -1) break
                            raf.write(buffer, 0, read)
                            downloaded += read
                            bytesSinceReport += read

                            val now = System.currentTimeMillis()
                            val elapsedMs = now - lastReportMs
                            if (elapsedMs >= 450L) {
                                val speed = (bytesSinceReport * 1000L) / elapsedMs.coerceAtLeast(1L)
                                val remaining = (totalBytes - downloaded).coerceAtLeast(0L)
                                val eta = if (speed > 0L && totalBytes > 0L) remaining / speed else null
                                downloadDao.upsert(
                                    task.copy(
                                        status = DownloadStatus.DOWNLOADING,
                                        totalBytes = totalBytes,
                                        downloadedBytes = downloaded,
                                        speedBytesPerSec = speed,
                                        etaSeconds = eta,
                                        supportsResume = supportsRange,
                                    ).toEntity(),
                                )
                                lastReportMs = now
                                bytesSinceReport = 0L
                            }
                        }

                        downloadDao.upsert(
                            task.copy(
                                status = DownloadStatus.VERIFYING,
                                totalBytes = if (totalBytes > 0L) totalBytes else downloaded,
                                downloadedBytes = downloaded,
                                speedBytesPerSec = 0L,
                                etaSeconds = 0L,
                                supportsResume = supportsRange,
                            ).toEntity(),
                        )
                    }

                    if (finalFile.exists()) finalFile.delete()
                    partFile.renameTo(finalFile)

                    val (sha256, isValidZip) = verifyArchiveIntegrity(finalFile)
                    if (!isValidZip) {
                        logManager.error("DownloadManager", "ตรวจพบไฟล์เสียหาย: ${finalFile.name}")
                        val failedTask = task.copy(
                            status = DownloadStatus.FAILED,
                            totalBytes = finalFile.length(),
                            downloadedBytes = finalFile.length(),
                            sha256 = sha256,
                            isCorrupted = true,
                            errorMessage = "ไฟล์ ZIP เสียหายหรือไม่สมบูรณ์ กรุณาดาวน์โหลดใหม่อีกครั้ง",
                        )
                        downloadDao.upsert(failedTask.toEntity())
                        onDownloadOutcome(failedTask, false)
                    } else {
                        logManager.info("DownloadManager", "ดาวน์โหลดและตรวจสอบไฟล์ ${finalFile.name} สำเร็จ")
                        val completedTask = task.copy(
                            status = DownloadStatus.COMPLETED,
                            totalBytes = finalFile.length(),
                            downloadedBytes = finalFile.length(),
                            speedBytesPerSec = 0L,
                            etaSeconds = 0L,
                            localFilePath = finalFile.absolutePath,
                            sha256 = sha256,
                            isCorrupted = false,
                            errorMessage = null,
                        )
                        downloadDao.upsert(completedTask.toEntity())
                        onDownloadOutcome(completedTask, true)
                    }
                }
            } catch (t: Throwable) {
                val latest = downloadDao.getById(task.id)?.toDomain()
                if (latest?.status == DownloadStatus.PAUSED || latest?.status == DownloadStatus.CANCELLED) {
                    return@launch
                }
                logManager.error("DownloadManager", "ดาวน์โหลด ${task.fileName} ล้มเหลว: ${t.localizedMessage}")
                val failedTask = task.copy(
                    status = DownloadStatus.FAILED,
                    speedBytesPerSec = 0L,
                    etaSeconds = null,
                    errorMessage = t.localizedMessage ?: "ดาวน์โหลดล้มเหลว",
                )
                downloadDao.upsert(failedTask.toEntity())
                onDownloadOutcome(failedTask, false)
            } finally {
                activeJobs.remove(task.id)
                if (activeJobs.isEmpty()) {
                    onBackgroundStateChanged(false)
                }
            }
        }
        activeJobs[task.id] = job
    }

    private fun verifyArchiveIntegrity(file: File): Pair<String, Boolean> {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(8192)
            while (true) {
                val r = input.read(buf)
                if (r <= 0) break
                digest.update(buf, 0, r)
            }
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        val zipOk = runCatching {
            ZipFile(file).use { zip -> zip.entries().hasMoreElements() }
        }.getOrElse {
            runCatching {
                val z4j = net.lingala.zip4j.ZipFile(file)
                z4j.isValidZipFile && z4j.fileHeaders.isNotEmpty()
            }.getOrDefault(false)
        }
        return hex to zipOk
    }
}
