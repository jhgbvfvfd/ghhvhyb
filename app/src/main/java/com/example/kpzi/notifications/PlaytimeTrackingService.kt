package com.example.kpzi.notifications

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.kpzi.KpziContainer
import com.example.kpzi.ui.i18n.AppLanguage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground Service keeping the persistent playtime notification bar updated
 * with months/days/hours/minutes while the user plays Stardew Valley.
 */
class PlaytimeTrackingService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var tickerJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val container = KpziContainer.getInstance(applicationContext)
        when (intent?.action) {
            ACTION_STOP -> {
                tickerJob?.cancel()
                serviceScope.launch(Dispatchers.IO) {
                    val stopped = container.settingsPreferences.stopPlaytimeSession()
                    if (stopped != null && stopped.durationSeconds >= 1L) {
                        container.playStatsRepository.recordCompletedSession(
                            startedAtMs = stopped.startedAtMs,
                            endedAtMs = stopped.endedAtMs,
                            durationSeconds = stopped.durationSeconds,
                            activeModCount = stopped.activeModCount,
                            launchMode = stopped.launchMode,
                        )
                    }
                    container.notificationHelper.cancelPlaytimeNotification()
                    stopForegroundCompat()
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            else -> {
                val explicitStart = intent?.getLongExtra(EXTRA_START_EPOCH_MS, 0L)?.takeIf { it > 0L }
                serviceScope.launch(Dispatchers.IO) {
                    val settings = container.settingsPreferences.settingsFlow.first()
                    val english = settings.appLanguage == AppLanguage.ENGLISH
                    container.notificationHelper.isEnglish = english
                    val sessionStart = settings.activePlaytimeStartMs
                        ?: container.settingsPreferences.startPlaytimeSession(explicitStart ?: System.currentTimeMillis())
                    val totalBefore = settings.totalPlaytimeSeconds

                    val initialNotification = container.notificationHelper.buildPlaytimeNotification(
                        accumulatedBeforeSessionSeconds = totalBefore,
                        sessionStartMs = sessionStart,
                        english = english,
                    )
                    runCatching {
                        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                        } else {
                            0
                        }
                        ServiceCompat.startForeground(
                            this@PlaytimeTrackingService,
                            KpziNotificationHelper.NOTIFICATION_ID_PLAYTIME,
                            initialNotification,
                            fgsType,
                        )
                    }.onFailure {
                        container.notificationHelper.notifyPlaytimeUpdate(totalBefore, sessionStart)
                    }

                    startTicker(container, sessionStart)
                }
                return START_STICKY
            }
        }
    }

    private fun startTicker(container: KpziContainer, sessionStartMs: Long) {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                delay(15_000L)
                val settings = container.settingsPreferences.settingsFlow.first()
                val english = settings.appLanguage == AppLanguage.ENGLISH
                container.notificationHelper.isEnglish = english
                val activeStart = settings.activePlaytimeStartMs ?: break
                container.notificationHelper.notifyPlaytimeUpdate(
                    accumulatedBeforeSessionSeconds = settings.totalPlaytimeSeconds,
                    sessionStartMs = activeStart.coerceAtMost(sessionStartMs),
                    english = english,
                )
            }
        }
    }

    private fun stopForegroundCompat() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_START = "com.example.kpzi.action.START_PLAYTIME"
        const val ACTION_STOP = "com.example.kpzi.action.STOP_PLAYTIME"
        const val EXTRA_START_EPOCH_MS = "extra_start_epoch_ms"

        fun start(context: Context, startEpochMs: Long = System.currentTimeMillis()) {
            val intent = Intent(context, PlaytimeTrackingService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_START_EPOCH_MS, startEpochMs)
            }
            runCatching {
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, PlaytimeTrackingService::class.java).apply {
                action = ACTION_STOP
            }
            runCatching {
                context.startService(intent)
            }
        }
    }
}
