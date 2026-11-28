package com.example.kpzi.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.example.kpzi.KpziContainer
import com.example.kpzi.data.local.toDomain
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.ui.i18n.AppLanguage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first

/**
 * WorkManager CoroutineWorker that monitors the download queue in the background
 * and triggers system notifications when queued downloads, mod installations, or
 * file export processes complete successfully.
 */
class DownloadQueueMonitorWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val container = KpziContainer.getInstance(applicationContext)
        val english = container.settingsPreferences.settingsFlow.first().appLanguage == AppLanguage.ENGLISH
        container.notificationHelper.isEnglish = english
        return when (inputData.getString(KEY_WORK_MODE) ?: MODE_MONITOR_QUEUE) {
            MODE_NOTIFY_MOD_INSTALLED -> {
                val modName = inputData.getString(KEY_MOD_NAME).orEmpty()
                val version = inputData.getString(KEY_MOD_VERSION).orEmpty()
                val uniqueId = inputData.getString(KEY_MOD_UNIQUE_ID).orEmpty()
                val wasUpdate = inputData.getBoolean(KEY_WAS_UPDATE, false)
                if (modName.isNotBlank()) {
                    container.notificationHelper.showModInstallCompletedAlert(
                        modName = modName,
                        version = version.ifBlank { "1.0.0" },
                        uniqueId = uniqueId,
                        wasUpdate = wasUpdate,
                        english = english,
                    )
                }
                Result.success()
            }

            MODE_NOTIFY_FILE_EXPORTED -> {
                val fileName = inputData.getString(KEY_EXPORT_FILE_NAME).orEmpty()
                val defaultLabel = if (english) "Export File" else "ส่งออกไฟล์"
                val label = inputData.getString(KEY_EXPORT_LABEL).orEmpty().ifBlank { defaultLabel }
                val sizeBytes = inputData.getLong(KEY_EXPORT_SIZE_BYTES, 0L)
                if (fileName.isNotBlank()) {
                    container.notificationHelper.showFileExportCompletedAlert(
                        fileName = fileName,
                        exportLabel = label,
                        sizeBytes = sizeBytes,
                        english = english,
                    )
                }
                Result.success()
            }

            else -> monitorDownloadQueue(container)
        }
    }

    private suspend fun monitorDownloadQueue(container: KpziContainer): Result {
        val trackedActiveIds = mutableSetOf<String>()
        var idlePolls = 0

        while (!isStopped) {
            val tasks = container.database.downloadRecordDao().observeAll().first().map { it.toDomain() }
            val activeIds = tasks.filter {
                it.status == DownloadStatus.QUEUED ||
                    it.status == DownloadStatus.DOWNLOADING ||
                    it.status == DownloadStatus.VERIFYING
            }.map { it.id }.toSet()

            trackedActiveIds.addAll(activeIds)

            for (task in tasks) {
                if (task.id in trackedActiveIds) {
                    when (task.status) {
                        DownloadStatus.COMPLETED -> {
                            container.notificationHelper.showDownloadCompletedAlert(task)
                            trackedActiveIds.remove(task.id)
                        }
                        DownloadStatus.FAILED -> {
                            container.notificationHelper.showDownloadFailedAlert(task)
                            trackedActiveIds.remove(task.id)
                        }
                        DownloadStatus.CANCELLED -> {
                            trackedActiveIds.remove(task.id)
                        }
                        else -> Unit
                    }
                }
            }

            if (activeIds.isEmpty()) {
                idlePolls++
                if (idlePolls >= 2) {
                    return Result.success(workDataOf(KEY_QUEUE_DRAINED to true))
                }
            } else {
                idlePolls = 0
            }

            delay(1_500L)
        }
        return Result.success()
    }

    companion object {
        private const val UNIQUE_QUEUE_WORK_NAME = "kpzi_download_queue_monitor"

        const val KEY_WORK_MODE = "work_mode"
        const val MODE_MONITOR_QUEUE = "monitor_queue"
        const val MODE_NOTIFY_MOD_INSTALLED = "notify_mod_installed"
        const val MODE_NOTIFY_FILE_EXPORTED = "notify_file_exported"

        const val KEY_MOD_NAME = "mod_name"
        const val KEY_MOD_VERSION = "mod_version"
        const val KEY_MOD_UNIQUE_ID = "mod_unique_id"
        const val KEY_WAS_UPDATE = "was_update"

        const val KEY_EXPORT_FILE_NAME = "export_file_name"
        const val KEY_EXPORT_LABEL = "export_label"
        const val KEY_EXPORT_SIZE_BYTES = "export_size_bytes"
        const val KEY_QUEUE_DRAINED = "queue_drained"

        fun enqueueQueueMonitor(context: Context) {
            runCatching {
                val req = OneTimeWorkRequestBuilder<DownloadQueueMonitorWorker>()
                    .setInputData(workDataOf(KEY_WORK_MODE to MODE_MONITOR_QUEUE))
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
                    UNIQUE_QUEUE_WORK_NAME,
                    ExistingWorkPolicy.KEEP,
                    req,
                )
            }
        }

        fun enqueueModInstalledNotification(
            context: Context,
            modName: String,
            version: String,
            uniqueId: String,
            wasUpdate: Boolean,
        ) {
            runCatching {
                val req = OneTimeWorkRequestBuilder<DownloadQueueMonitorWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_WORK_MODE to MODE_NOTIFY_MOD_INSTALLED,
                            KEY_MOD_NAME to modName,
                            KEY_MOD_VERSION to version,
                            KEY_MOD_UNIQUE_ID to uniqueId,
                            KEY_WAS_UPDATE to wasUpdate,
                        ),
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueue(req)
            }
        }

        fun enqueueFileExportedNotification(
            context: Context,
            fileName: String,
            exportLabel: String,
            sizeBytes: Long,
        ) {
            runCatching {
                val req = OneTimeWorkRequestBuilder<DownloadQueueMonitorWorker>()
                    .setInputData(
                        workDataOf(
                            KEY_WORK_MODE to MODE_NOTIFY_FILE_EXPORTED,
                            KEY_EXPORT_FILE_NAME to fileName,
                            KEY_EXPORT_LABEL to exportLabel,
                            KEY_EXPORT_SIZE_BYTES to sizeBytes,
                        ),
                    )
                    .build()
                WorkManager.getInstance(context.applicationContext).enqueue(req)
            }
        }
    }
}
