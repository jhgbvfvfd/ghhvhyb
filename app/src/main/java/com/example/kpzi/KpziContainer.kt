package com.example.kpzi

import android.content.Context
import com.example.kpzi.core.network.NexusApiClient
import com.example.kpzi.core.network.NexusQuotaManager
import com.example.kpzi.core.security.SecureKeyVault
import com.example.kpzi.data.local.InstalledModRepository
import com.example.kpzi.data.local.KpziDatabase
import com.example.kpzi.data.local.PlayStatsRepository
import com.example.kpzi.data.local.SettingsPreferences
import com.example.kpzi.engine.download.ModDownloadManager
import com.example.kpzi.engine.installer.ModInstallationEngine
import com.example.kpzi.engine.installer.SaveBackupManager
import com.example.kpzi.engine.loader.AssetManager
import com.example.kpzi.engine.loader.CompatibilityEngine
import com.example.kpzi.engine.loader.ConflictDetector
import com.example.kpzi.engine.loader.GameDetector
import com.example.kpzi.engine.loader.GameLauncher
import com.example.kpzi.engine.loader.LogManager
import com.example.kpzi.engine.loader.ModRuntime
import com.example.kpzi.engine.loader.SmapiIntegration
import com.example.kpzi.notifications.KpziNotificationHelper
import com.example.kpzi.notifications.ModDownloadService

class KpziContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val database = KpziDatabase.getInstance(appContext)
    val settingsPreferences = SettingsPreferences(appContext)
    val secureKeyVault = SecureKeyVault(appContext)

    val quotaManager = NexusQuotaManager()
    val nexusApiClient = NexusApiClient(quotaManager)

    val assetManager = AssetManager(appContext)
    val logManager = LogManager(appContext, assetManager)
    val notificationHelper = KpziNotificationHelper(appContext)
    val compatibilityEngine = CompatibilityEngine()
    val conflictDetector = ConflictDetector(compatibilityEngine)
    val gameDetector = GameDetector(appContext)
    val smapiIntegration = SmapiIntegration(appContext)
    val modRuntime = ModRuntime(appContext, assetManager)
    val gameLauncher = GameLauncher(appContext)

    val installedModRepository = InstalledModRepository(database.modDao())
    val playStatsRepository = PlayStatsRepository(database.playSessionDao())

    val modInstallationEngine = ModInstallationEngine(
        context = appContext,
        assetManager = assetManager,
        compatibilityEngine = compatibilityEngine,
        logManager = logManager,
        installedModRepository = installedModRepository,
    )

    val modDownloadManager = ModDownloadManager(
        httpClient = nexusApiClient.httpClient,
        assetManager = assetManager,
        downloadDao = database.downloadRecordDao(),
        logManager = logManager,
        onBackgroundStateChanged = { isActive ->
            if (isActive) {
                ModDownloadService.start(appContext)
            }
        },
        onDownloadOutcome = { task, isSuccess ->
            if (isSuccess) {
                notificationHelper.showDownloadCompletedAlert(task)
            } else {
                notificationHelper.showDownloadFailedAlert(task)
            }
        },
    )

    val saveBackupManager = SaveBackupManager(
        context = appContext,
        assetManager = assetManager,
        logManager = logManager,
    )

    companion object {
        @Volatile
        private var instance: KpziContainer? = null

        fun getInstance(context: Context): KpziContainer {
            return instance ?: synchronized(this) {
                instance ?: KpziContainer(context.applicationContext).also { instance = it }
            }
        }
    }
}
