package com.example.kpzi.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.kpzi.domain.model.DownloadTask
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class KpziNotificationHelper(private val context: Context) {

    init {
        ensureChannels()
    }

    fun canPostNotifications(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun ensureChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val playtimeChannel = NotificationChannel(
            CHANNEL_PLAYTIME,
            "เวลาเล่นเกม Stardew Valley",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "แสดงเวลาที่เล่นเกมเป็น เดือน / วัน / ชั่วโมง / นาที บนแถบแจ้งเตือนขณะกำลังเล่น"
            setShowBadge(false)
        }

        val downloadProgressChannel = NotificationChannel(
            CHANNEL_DOWNLOAD_PROGRESS,
            "สถานะกำลังดาวน์โหลดมอด",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "แสดงความคืบหน้าขณะดาวน์โหลดมอดในพื้นหลัง"
            setShowBadge(false)
        }

        val downloadAlertChannel = NotificationChannel(
            CHANNEL_DOWNLOAD_ALERT,
            "แจ้งเตือนดาวน์โหลดเสร็จสิ้น",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "เด้งแจ้งเตือนพร้อมเสียงทันทีเมื่อดาวน์โหลดมอดเสร็จสิ้นแม้ออกจากแอปแล้ว"
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        manager.createNotificationChannels(
            listOf(playtimeChannel, downloadProgressChannel, downloadAlertChannel),
        )
    }

    fun buildPlaytimeNotification(
        accumulatedBeforeSessionSeconds: Long,
        sessionStartMs: Long,
        nowMs: Long = System.currentTimeMillis(),
    ): Notification {
        ensureChannels()
        val sessionSeconds = ((nowMs - sessionStartMs).coerceAtLeast(0L)) / 1000L
        val totalSeconds = accumulatedBeforeSessionSeconds + sessionSeconds
        val totalBreakdown = PlaytimeFormatter.formatThaiBreakdown(totalSeconds)
        val sessionClock = PlaytimeFormatter.formatStopwatch(sessionSeconds)
        val startedAtLabel = SimpleDateFormat("d MMM HH:mm", Locale.getDefault()).format(Date(sessionStartMs))

        val openAppIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_HOME,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "home")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getService(
            context,
            REQ_STOP_PLAYTIME,
            Intent(context, PlaytimeTrackingService::class.java).apply {
                action = PlaytimeTrackingService.ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val bigText = buildString {
            append("เล่นสะสมทั้งหมด: $totalBreakdown\n")
            append("รอบนี้เล่นไปแล้ว: $sessionClock (เริ่ม $startedAtLabel)")
        }

        return NotificationCompat.Builder(context, CHANNEL_PLAYTIME)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("กำลังเล่น Stardew Valley • $sessionClock")
            .setContentText("สะสม $totalBreakdown")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(sessionStartMs)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, "หยุดจับเวลา", stopIntent)
            .build()
    }

    fun notifyPlaytimeUpdate(
        accumulatedBeforeSessionSeconds: Long,
        sessionStartMs: Long,
    ) {
        if (!canPostNotifications()) return
        val notification = buildPlaytimeNotification(
            accumulatedBeforeSessionSeconds = accumulatedBeforeSessionSeconds,
            sessionStartMs = sessionStartMs,
        )
        runCatching {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_PLAYTIME, notification)
        }
    }

    fun cancelPlaytimeNotification() {
        runCatching {
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID_PLAYTIME)
        }
    }

    fun buildDownloadProgressNotification(
        activeCount: Int,
        primaryTask: DownloadTask?,
    ): Notification {
        ensureChannels()
        val openDownloadsIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_DOWNLOADS,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "downloads")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val progress = ((primaryTask?.progressFraction ?: 0f) * 100).toInt().coerceIn(0, 100)
        val indeterminate = primaryTask == null || primaryTask.totalBytes <= 0L
        val title = if (activeCount > 1) {
            "กำลังดาวน์โหลด $activeCount มอด (${primaryTask?.modName.orEmpty()})"
        } else {
            "กำลังดาวน์โหลด ${primaryTask?.modName ?: "ไฟล์มอด"}"
        }
        val subtitle = if (indeterminate) {
            "กำลังดาวน์โหลดในพื้นหลัง..."
        } else {
            "$progress% • ${primaryTask?.fileName.orEmpty()}"
        }

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_PROGRESS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(openDownloadsIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, indeterminate)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
    }

    fun showDownloadCompletedAlert(task: DownloadTask) {
        if (!canPostNotifications()) return
        ensureChannels()

        val openDownloadsIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "downloads")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sizeLabel = formatReadableSize(task.totalBytes)
        val title = "ดาวน์โหลดเสร็จสิ้น: ${task.modName}"
        val body = "ไฟล์ ${task.fileName} ($sizeLabel) พร้อมติดตั้งแล้ว แตะเพื่อเปิดหน้าดาวน์โหลด"

        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openDownloadsIntent)
            .build()

        val alertId = NOTIFICATION_ID_DOWNLOAD_ALERT_BASE + (task.id.hashCode() and 0x0FFF)
        runCatching {
            NotificationManagerCompat.from(context).notify(alertId, notification)
        }
    }

    fun showDownloadFailedAlert(task: DownloadTask) {
        if (!canPostNotifications()) return
        ensureChannels()

        val openDownloadsIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode() xor 0x33,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "downloads")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = task.errorMessage ?: "ไม่สามารถดาวน์โหลด ${task.fileName} ได้ กรุณาลองใหม่"
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("ดาวน์โหลดไม่สำเร็จ: ${task.modName}")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openDownloadsIntent)
            .build()

        val alertId = NOTIFICATION_ID_DOWNLOAD_ALERT_BASE + (task.id.hashCode() and 0x0FFF)
        runCatching {
            NotificationManagerCompat.from(context).notify(alertId, notification)
        }
    }

    private fun formatReadableSize(bytes: Long): String {
        if (bytes <= 0L) return "พร้อมใช้งาน"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    companion object {
        const val CHANNEL_PLAYTIME = "kpzi_playtime_channel"
        const val CHANNEL_DOWNLOAD_PROGRESS = "kpzi_downloads_progress_channel"
        const val CHANNEL_DOWNLOAD_ALERT = "kpzi_downloads_complete_channel"

        const val NOTIFICATION_ID_PLAYTIME = 1001
        const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 2001
        const val NOTIFICATION_ID_DOWNLOAD_ALERT_BASE = 3000

        const val EXTRA_TARGET_TAB = "kpzi_target_tab"
        private const val REQ_OPEN_HOME = 10
        private const val REQ_STOP_PLAYTIME = 11
        private const val REQ_OPEN_DOWNLOADS = 12
    }
}
