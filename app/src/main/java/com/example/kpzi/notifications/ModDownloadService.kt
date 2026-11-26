package com.example.kpzi.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.kpzi.KpziContainer
import com.example.kpzi.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Foreground dataSync Service keeping background mod downloads alive when the user
 * exits the app, updating progress and shutting down once the queue drains.
 */
class ModDownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var observerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = KpziContainer.getInstance(applicationContext)
        val initial = container.notificationHelper.buildDownloadProgressNotification(
            activeCount = 1,
            primaryTask = null,
        )
        runCatching {
            val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            }
            ServiceCompat.startForeground(
                this,
                KpziNotificationHelper.NOTIFICATION_ID_DOWNLOAD_PROGRESS,
                initial,
                fgsType,
            )
        }

        if (observerJob == null) {
            observerJob = serviceScope.launch {
                container.modDownloadManager.downloadsFlow.collectLatest { tasks ->
                    val active = tasks.filter {
                        it.status == DownloadStatus.DOWNLOADING ||
                            it.status == DownloadStatus.QUEUED ||
                            it.status == DownloadStatus.VERIFYING
                    }
                    if (active.isEmpty()) {
                        ServiceCompat.stopForeground(
                            this@ModDownloadService,
                            ServiceCompat.STOP_FOREGROUND_REMOVE,
                        )
                        stopSelf()
                    } else if (container.notificationHelper.canPostNotifications()) {
                        val notification = container.notificationHelper.buildDownloadProgressNotification(
                            activeCount = active.size,
                            primaryTask = active.firstOrNull { it.status == DownloadStatus.DOWNLOADING }
                                ?: active.first(),
                        )
                        runCatching {
                            NotificationManagerCompat.from(this@ModDownloadService).notify(
                                KpziNotificationHelper.NOTIFICATION_ID_DOWNLOAD_PROGRESS,
                                notification,
                            )
                        }
                    }
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        observerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ModDownloadService::class.java),
                )
            }
        }
    }
}
