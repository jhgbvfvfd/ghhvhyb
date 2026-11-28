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
import java.util.concurrent.ConcurrentHashMap

class KpziNotificationHelper(private val context: Context) {

    @Volatile
    var isEnglish: Boolean = false

    private val notifiedDownloadIds = ConcurrentHashMap.newKeySet<String>()

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

    fun ensureChannels(english: Boolean = isEnglish) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val playtimeChannel = NotificationChannel(
            CHANNEL_PLAYTIME,
            if (english) "Stardew Valley Playtime" else "เวลาเล่นเกม Stardew Valley",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = if (english) {
                "Shows playtime in months, days, hours, and minutes in the notification bar while playing"
            } else {
                "แสดงเวลาที่เล่นเกมเป็น เดือน / วัน / ชั่วโมง / นาที บนแถบแจ้งเตือนขณะกำลังเล่น"
            }
            setShowBadge(false)
        }

        val downloadProgressChannel = NotificationChannel(
            CHANNEL_DOWNLOAD_PROGRESS,
            if (english) "Mod Download Progress" else "สถานะกำลังดาวน์โหลดมอด",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = if (english) {
                "Shows progress while downloading mods in the background"
            } else {
                "แสดงความคืบหน้าขณะดาวน์โหลดมอดในพื้นหลัง"
            }
            setShowBadge(false)
        }

        val downloadAlertChannel = NotificationChannel(
            CHANNEL_DOWNLOAD_ALERT,
            if (english) "Download Completed Alerts" else "แจ้งเตือนดาวน์โหลดเสร็จสิ้น",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = if (english) {
                "Notifies immediately when a mod download finishes even after leaving the app"
            } else {
                "เด้งแจ้งเตือนพร้อมเสียงทันทีเมื่อดาวน์โหลดมอดเสร็จสิ้นแม้ออกจากแอปแล้ว"
            }
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        val taskCompleteChannel = NotificationChannel(
            CHANNEL_TASK_COMPLETE,
            if (english) "Mod Install & File Export Alerts" else "แจ้งเตือนติดตั้งมอดและส่งออกไฟล์",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = if (english) {
                "Notifies when mod installation or backup ZIP export completes"
            } else {
                "แจ้งเตือนเมื่อติดตั้งมอดหรือส่งออกไฟล์ ZIP สำรองเสร็จสมบูรณ์"
            }
            enableVibration(true)
            enableLights(true)
            setShowBadge(true)
        }

        manager.createNotificationChannels(
            listOf(playtimeChannel, downloadProgressChannel, downloadAlertChannel, taskCompleteChannel),
        )
    }

    fun buildPlaytimeNotification(
        accumulatedBeforeSessionSeconds: Long,
        sessionStartMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        english: Boolean = isEnglish,
    ): Notification {
        ensureChannels(english)
        val sessionSeconds = ((nowMs - sessionStartMs).coerceAtLeast(0L)) / 1000L
        val totalSeconds = accumulatedBeforeSessionSeconds + sessionSeconds
        val totalBreakdown = PlaytimeFormatter.formatBreakdown(totalSeconds, isEnglish = english)
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

        val bigText = if (english) {
            "Total Playtime: $totalBreakdown\nCurrent Session: $sessionClock (Started $startedAtLabel)"
        } else {
            "เล่นสะสมทั้งหมด: $totalBreakdown\nรอบนี้เล่นไปแล้ว: $sessionClock (เริ่ม $startedAtLabel)"
        }

        return NotificationCompat.Builder(context, CHANNEL_PLAYTIME)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(
                if (english) "Playing Stardew Valley • $sessionClock" else "กำลังเล่น Stardew Valley • $sessionClock",
            )
            .setContentText(if (english) "Total $totalBreakdown" else "สะสม $totalBreakdown")
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setUsesChronometer(true)
            .setWhen(sessionStartMs)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .addAction(0, if (english) "Stop Timer" else "หยุดจับเวลา", stopIntent)
            .build()
    }

    fun notifyPlaytimeUpdate(
        accumulatedBeforeSessionSeconds: Long,
        sessionStartMs: Long,
        english: Boolean = isEnglish,
    ) {
        if (!canPostNotifications()) return
        val notification = buildPlaytimeNotification(
            accumulatedBeforeSessionSeconds = accumulatedBeforeSessionSeconds,
            sessionStartMs = sessionStartMs,
            english = english,
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
        english: Boolean = isEnglish,
    ): Notification {
        ensureChannels(english)
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
        val title = if (english) {
            if (activeCount > 1) {
                "Downloading $activeCount mods (${primaryTask?.modName.orEmpty()})"
            } else {
                "Downloading ${primaryTask?.modName ?: "mod file"}"
            }
        } else {
            if (activeCount > 1) {
                "กำลังดาวน์โหลด $activeCount มอด (${primaryTask?.modName.orEmpty()})"
            } else {
                "กำลังดาวน์โหลด ${primaryTask?.modName ?: "ไฟล์มอด"}"
            }
        }
        val subtitle = if (indeterminate) {
            if (english) "Downloading in background..." else "กำลังดาวน์โหลดในพื้นหลัง..."
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

    fun showDownloadCompletedAlert(task: DownloadTask, english: Boolean = isEnglish) {
        if (!notifiedDownloadIds.add("ok_${task.id}")) return
        if (!canPostNotifications()) return
        ensureChannels(english)

        val openDownloadsIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode(),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "downloads")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sizeLabel = formatReadableSize(task.totalBytes, english)
        val title = if (english) "Download Complete: ${task.modName}" else "ดาวน์โหลดเสร็จสิ้น: ${task.modName}"
        val body = if (english) {
            "File ${task.fileName} ($sizeLabel) is ready to install. Tap to open Downloads."
        } else {
            "ไฟล์ ${task.fileName} ($sizeLabel) พร้อมติดตั้งแล้ว แตะเพื่อเปิดหน้าดาวน์โหลด"
        }

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

    fun showDownloadFailedAlert(task: DownloadTask, english: Boolean = isEnglish) {
        if (!notifiedDownloadIds.add("fail_${task.id}")) return
        if (!canPostNotifications()) return
        ensureChannels(english)

        val openDownloadsIntent = PendingIntent.getActivity(
            context,
            task.id.hashCode() xor 0x33,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "downloads")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val body = task.errorMessage ?: if (english) {
            "Failed to download ${task.fileName}. Please try again."
        } else {
            "ไม่สามารถดาวน์โหลด ${task.fileName} ได้ กรุณาลองใหม่"
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD_ALERT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (english) "Download Failed: ${task.modName}" else "ดาวน์โหลดไม่สำเร็จ: ${task.modName}")
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

    fun showModInstallCompletedAlert(
        modName: String,
        version: String,
        uniqueId: String,
        wasUpdate: Boolean,
        english: Boolean = isEnglish,
    ) {
        if (!canPostNotifications()) return
        ensureChannels(english)

        val openMyModsIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_MY_MODS,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "my_mods")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val title = if (english) {
            if (wasUpdate) "Mod Updated: $modName" else "Mod Installed: $modName"
        } else {
            if (wasUpdate) "อัปเดตมอดสำเร็จ: $modName" else "ติดตั้งมอดสำเร็จ: $modName"
        }
        val body = if (english) {
            "Version $version ($uniqueId) is ready in-game. Tap to view in My Mods."
        } else {
            "เวอร์ชัน $version ($uniqueId) พร้อมใช้งานในเกมแล้ว แตะเพื่อดูในมอดของฉัน"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_COMPLETE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openMyModsIntent)
            .build()

        val alertId = NOTIFICATION_ID_TASK_COMPLETE_BASE + (uniqueId.hashCode() and 0x0FFF)
        runCatching {
            NotificationManagerCompat.from(context).notify(alertId, notification)
        }
    }

    fun showFileExportCompletedAlert(
        fileName: String,
        exportLabel: String,
        sizeBytes: Long,
        english: Boolean = isEnglish,
    ) {
        if (!canPostNotifications()) return
        ensureChannels(english)

        val openBackupIntent = PendingIntent.getActivity(
            context,
            REQ_OPEN_BACKUP,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_TARGET_TAB, "save_backup")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val sizeLabel = if (sizeBytes > 0L) " (${formatReadableSize(sizeBytes, english)})" else ""
        val title = if (english) "$exportLabel Completed" else "$exportLabel เสร็จสมบูรณ์"
        val body = if (english) {
            "Saved $fileName$sizeLabel to device storage."
        } else {
            "บันทึกไฟล์ $fileName$sizeLabel ลงเครื่องเรียบร้อยแล้ว"
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_TASK_COMPLETE)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(openBackupIntent)
            .build()

        val alertId = NOTIFICATION_ID_TASK_COMPLETE_BASE + 0x0800 + (fileName.hashCode() and 0x07FF)
        runCatching {
            NotificationManagerCompat.from(context).notify(alertId, notification)
        }
    }

    private fun formatReadableSize(bytes: Long, english: Boolean = isEnglish): String {
        if (bytes <= 0L) return if (english) "Ready" else "พร้อมใช้งาน"
        val kb = bytes / 1024.0
        if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
        val mb = kb / 1024.0
        return String.format(Locale.US, "%.1f MB", mb)
    }

    companion object {
        const val CHANNEL_PLAYTIME = "kpzi_playtime_channel"
        const val CHANNEL_DOWNLOAD_PROGRESS = "kpzi_downloads_progress_channel"
        const val CHANNEL_DOWNLOAD_ALERT = "kpzi_downloads_complete_channel"
        const val CHANNEL_TASK_COMPLETE = "kpzi_task_complete_channel"

        const val NOTIFICATION_ID_PLAYTIME = 1001
        const val NOTIFICATION_ID_DOWNLOAD_PROGRESS = 2001
        const val NOTIFICATION_ID_DOWNLOAD_ALERT_BASE = 3000
        const val NOTIFICATION_ID_TASK_COMPLETE_BASE = 4000

        const val EXTRA_TARGET_TAB = "kpzi_target_tab"
        private const val REQ_OPEN_HOME = 10
        private const val REQ_STOP_PLAYTIME = 11
        private const val REQ_OPEN_DOWNLOADS = 12
        private const val REQ_OPEN_MY_MODS = 13
        private const val REQ_OPEN_BACKUP = 14
    }
}
