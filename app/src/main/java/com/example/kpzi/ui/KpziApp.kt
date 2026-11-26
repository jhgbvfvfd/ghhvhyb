package com.example.kpzi.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.kpzi.KpziViewModel
import com.example.kpzi.UiEvent
import com.example.kpzi.domain.model.BackupCategory
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.domain.model.SaveBackupInfo
import com.example.kpzi.ui.components.KpziInstallSuccessDialog
import com.example.kpzi.ui.components.KpziModInstallConfirmDialog
import com.example.kpzi.ui.components.KpziSplashOverlay
import com.example.kpzi.ui.screens.CatalogScreen
import com.example.kpzi.ui.screens.ConflictDetectionScreen
import com.example.kpzi.ui.screens.DownloadsScreen
import com.example.kpzi.ui.screens.HomeScreen
import com.example.kpzi.ui.screens.LogsScreen
import com.example.kpzi.ui.screens.ModDetailScreen
import com.example.kpzi.ui.screens.MyModsScreen
import com.example.kpzi.ui.screens.NexusAccountScreen
import com.example.kpzi.ui.screens.SaveBackupScreen
import com.example.kpzi.ui.screens.SettingsScreen
import com.example.kpzi.ui.screens.SmapiManagerScreen
import com.example.ui.theme.KpziTheme
import com.example.ui.theme.LocalKpziColors

object KpziRoutes {
    const val HOME = "home"
    const val CATALOG = "catalog"
    const val MY_MODS = "my_mods"
    const val DOWNLOADS = "downloads"
    const val SETTINGS = "settings"
    const val MOD_DETAIL = "mod_detail/{modId}"
    const val NEXUS_ACCOUNT = "nexus_account"
    const val SMAPI_MANAGER = "smapi_manager"
    const val SAVE_BACKUP = "save_backup"
    const val LOG_VIEWER = "log_viewer"
    const val CONFLICT_DETECTION = "conflict_detection"

    fun modDetail(modId: Long) = "mod_detail/$modId"
}

private data class BottomTab(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

private val mainTabs = listOf(
    BottomTab(KpziRoutes.HOME, "หน้าหลัก", Icons.Rounded.Home),
    BottomTab(KpziRoutes.CATALOG, "คลังมอด", Icons.Rounded.Explore),
    BottomTab(KpziRoutes.MY_MODS, "มอดของฉัน", Icons.Rounded.Inventory2),
    BottomTab(KpziRoutes.DOWNLOADS, "ดาวน์โหลด", Icons.Rounded.CloudDownload),
    BottomTab(KpziRoutes.LOG_VIEWER, "Log", Icons.Rounded.ReceiptLong),
    BottomTab(KpziRoutes.SETTINGS, "ตั้งค่า", Icons.Rounded.Settings),
)

@Composable
fun KpziApp(viewModel: KpziViewModel) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val isSplashVisible by viewModel.isSplashVisible.collectAsStateWithLifecycle()
    val detection by viewModel.gameDetection.collectAsStateWithLifecycle()
    val smapiStatus by viewModel.smapiStatus.collectAsStateWithLifecycle()
    val preLaunchReport by viewModel.preLaunchReport.collectAsStateWithLifecycle()
    val installedMods by viewModel.installedMods.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val quotaState by viewModel.quotaState.collectAsStateWithLifecycle()
    val isValidatingAccount by viewModel.isValidatingAccount.collectAsStateWithLifecycle()
    val accountError by viewModel.accountError.collectAsStateWithLifecycle()
    val recentPlaySessions by viewModel.recentPlaySessions.collectAsStateWithLifecycle()
    val catalogState by viewModel.catalogState.collectAsStateWithLifecycle()
    val modDetailState by viewModel.modDetailState.collectAsStateWithLifecycle()
    val pendingInstall by viewModel.pendingInstall.collectAsStateWithLifecycle()
    val installedSuccessMod by viewModel.installedSuccessMod.collectAsStateWithLifecycle()
    val saveBackups by viewModel.saveBackups.collectAsStateWithLifecycle()
    val isCreatingBackup by viewModel.isCreatingBackup.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val activeLogSource by viewModel.activeLogSource.collectAsStateWithLifecycle()
    val isReloadingLogs by viewModel.isReloadingLogs.collectAsStateWithLifecycle()
    val conflictReport by viewModel.conflictReport.collectAsStateWithLifecycle()
    val isScanningConflicts by viewModel.isScanningConflicts.collectAsStateWithLifecycle()
    val conflictCount = conflictReport?.issues?.size ?: 0
    val pendingNavigateTab by viewModel.pendingNavigateTab.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    var launchBlockedReason by remember { mutableStateOf<String?>(null) }
    var pendingRestoreBackup by remember { mutableStateOf<SaveBackupInfo?>(null) }
    var pendingExportBackup by remember { mutableStateOf<SaveBackupInfo?>(null) }

    val zipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importZipUri(it) }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { viewModel.importFolderUri(it) }
    }

    val safModsFolderLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.saveSafModsFolder(it)
        }
    }

    val backupTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        uri?.let { viewModel.backupSavesFromUri(it) }
    }

    val restoreTreeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        val backup = pendingRestoreBackup
        pendingRestoreBackup = null
        if (uri != null && backup != null) {
            viewModel.restoreSaveBackup(backup, uri)
        }
    }

    val logImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.importSmapiLog(it)
        }
    }

    val exportBackupZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip"),
    ) { uri ->
        val backup = pendingExportBackup
        pendingExportBackup = null
        if (uri != null && backup != null) {
            viewModel.exportBackupZipToUri(backup, uri)
        }
    }

    val importBackupZipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        uri?.let { viewModel.importModsBackupZipUri(it) }
    }

    var notificationsAllowed by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                NotificationManagerCompat.from(context).areNotificationsEnabled()
            },
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsAllowed = granted && NotificationManagerCompat.from(context).areNotificationsEnabled()
        if (!granted) {
            viewModel.showUiMessage("หากต้องการแถบแจ้งเตือนเวลาเล่นและแจ้งเตือนดาวน์โหลด กรุณาอนุญาตการแจ้งเตือนในตั้งค่า")
        }
    }

    LaunchedEffect(isSplashVisible) {
        if (!isSplashVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(pendingNavigateTab) {
        val target = pendingNavigateTab ?: return@LaunchedEffect
        val route = when (target) {
            "downloads" -> KpziRoutes.DOWNLOADS
            "home" -> KpziRoutes.HOME
            else -> target
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
        viewModel.consumeNavigateTab()
    }

    LaunchedEffect(Unit) {
        viewModel.uiEvents.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.OpenExternalUrl -> {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(event.url)))
                    }.onFailure {
                        snackbarHostState.showSnackbar("ไม่พบเบราว์เซอร์สำหรับเปิดลิงก์: ${event.url}")
                    }
                }
                is UiEvent.LaunchBlocked -> launchBlockedReason = event.reason
                is UiEvent.PromptExportBackupZip -> {
                    pendingExportBackup = event.backup
                    exportBackupZipLauncher.launch(event.backup.fileName)
                }
            }
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showMainNav = currentRoute in mainTabs.map { it.route }
    val activeDownloadCount = downloads.count {
        it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED
    }

    KpziTheme(
        themeMode = settings.themeMode,
        onThemeModeChange = viewModel::setThemeMode,
    ) {
        val ext = LocalKpziColors.current

        Box(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val useNavigationRail = maxWidth >= 600.dp

                Scaffold(
                    containerColor = MaterialTheme.colorScheme.background,
                    contentWindowInsets = WindowInsets.safeDrawing,
                    snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
                    bottomBar = {
                        if (showMainNav && !useNavigationRail) {
                            IosBottomTabBar(
                                tabs = mainTabs,
                                currentHierarchy = navBackStackEntry?.destination?.hierarchy,
                                activeDownloadCount = activeDownloadCount,
                                borderColor = ext.border,
                                mutedColor = ext.mutedText,
                                onSelectTab = { route ->
                                    navController.navigate(route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                            )
                        }
                    },
                ) { innerPadding ->
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                    ) {
                        if (showMainNav && useNavigationRail) {
                            NavigationRail(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxHeight(),
                            ) {
                                mainTabs.forEach { tab ->
                                    val selected = navBackStackEntry?.destination?.hierarchy?.any {
                                        it.route == tab.route
                                    } == true
                                    NavigationRailItem(
                                        selected = selected,
                                        onClick = {
                                            navController.navigate(tab.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        icon = {
                                            Icon(tab.icon, contentDescription = tab.label)
                                        },
                                        label = { Text(tab.label, style = MaterialTheme.typography.labelSmall) },
                                    )
                                }
                            }
                        }

                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.TopCenter,
                        ) {
                            NavHost(
                                navController = navController,
                                startDestination = KpziRoutes.HOME,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .widthIn(max = 840.dp),
                            ) {
                                composable(KpziRoutes.HOME) {
                                    HomeScreen(
                                        detection = detection,
                                        smapiStatus = smapiStatus,
                                        userProfile = userProfile,
                                        totalPlaytimeSeconds = settings.totalPlaytimeSeconds,
                                        activePlaytimeStartMs = settings.activePlaytimeStartMs,
                                        totalLaunchCount = settings.totalLaunchCount,
                                        lastSessionSeconds = settings.lastSessionSeconds,
                                        longestSessionSeconds = settings.longestSessionSeconds,
                                        lastPlayedEpochMs = settings.lastPlayedEpochMs,
                                        recentSessions = recentPlaySessions,
                                        onLaunchGame = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            }
                                            viewModel.launchGame()
                                        },
                                        onOpenAccount = { navController.navigate(KpziRoutes.NEXUS_ACCOUNT) },
                                        onResetPlayStats = viewModel::resetPlayStatistics,
                                    )
                                }

                                composable(KpziRoutes.CATALOG) {
                                    CatalogScreen(
                                        state = catalogState,
                                        quotaState = quotaState,
                                        onQueryChange = viewModel::onSearchQueryChanged,
                                        onCategorySelect = viewModel::onCategorySelected,
                                        onLoadMore = { viewModel.refreshCatalog(reset = false) },
                                        onRefresh = { viewModel.refreshCatalog(reset = true) },
                                        onOpenAccount = { navController.navigate(KpziRoutes.NEXUS_ACCOUNT) },
                                        onModClick = { modId -> navController.navigate(KpziRoutes.modDetail(modId)) },
                                        onModDownloadClick = { mod -> navController.navigate(KpziRoutes.modDetail(mod.modId)) },
                                    )
                                }

                                composable(KpziRoutes.MY_MODS) {
                                    MyModsScreen(
                                        mods = installedMods,
                                        onToggleMod = viewModel::toggleModEnabled,
                                        onUninstallMod = viewModel::uninstallMod,
                                        onImportZip = {
                                            zipPickerLauncher.launch(arrayOf("application/zip", "application/x-zip-compressed", "*/*"))
                                        },
                                        onImportFolder = { folderPickerLauncher.launch(null) },
                                    )
                                }

                                composable(KpziRoutes.DOWNLOADS) {
                                    DownloadsScreen(
                                        downloads = downloads,
                                        onPause = viewModel::pauseDownload,
                                        onResumeOrRetry = viewModel::resumeDownload,
                                        onCancel = viewModel::cancelDownload,
                                        onInstallCompleted = viewModel::installFromCompletedDownload,
                                        onRemoveHistory = viewModel::removeDownloadRecord,
                                    )
                                }

                                composable(KpziRoutes.SETTINGS) {
                                    SettingsScreen(
                                        settings = settings,
                                        detection = detection,
                                        smapiStatus = smapiStatus,
                                        userProfile = userProfile,
                                        conflictCount = conflictCount,
                                        notificationsEnabled = notificationsAllowed,
                                        onConfigureNotifications = {
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationsAllowed) {
                                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                            } else {
                                                runCatching {
                                                    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                                        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                                    }
                                                    context.startActivity(intent)
                                                }
                                            }
                                        },
                                        onPickGameModsFolder = { safModsFolderLauncher.launch(null) },
                                        onOpenSmapiManager = { navController.navigate(KpziRoutes.SMAPI_MANAGER) },
                                        onOpenConflictDetection = {
                                            viewModel.runConflictDetection()
                                            navController.navigate(KpziRoutes.CONFLICT_DETECTION)
                                        },
                                        onOpenNexusAccount = { navController.navigate(KpziRoutes.NEXUS_ACCOUNT) },
                                        onOpenSaveBackup = { navController.navigate(KpziRoutes.SAVE_BACKUP) },
                                        onOpenLogs = {
                                            navController.navigate(KpziRoutes.LOG_VIEWER) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onThemeModeChange = viewModel::setThemeMode,
                                    )
                                }

                                composable(
                                    route = KpziRoutes.MOD_DETAIL,
                                    arguments = listOf(navArgument("modId") { type = NavType.LongType }),
                                ) { backStackEntry ->
                                    val modId = backStackEntry.arguments?.getLong("modId") ?: 0L
                                    LaunchedEffect(modId) {
                                        viewModel.loadModDetail(modId)
                                    }
                                    ModDetailScreen(
                                        state = modDetailState,
                                        userProfile = userProfile,
                                        onBack = { navController.popBackStack() },
                                        onToggleTranslate = viewModel::toggleDetailThaiTranslation,
                                        onDownloadFile = viewModel::startModFileDownload,
                                        onOpenOriginalUrl = { url ->
                                            runCatching {
                                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                            }
                                        },
                                    )
                                }

                                composable(KpziRoutes.NEXUS_ACCOUNT) {
                                    NexusAccountScreen(
                                        userProfile = userProfile,
                                        quotaState = quotaState,
                                        isValidating = isValidatingAccount,
                                        errorMessage = accountError,
                                        onBack = { navController.popBackStack() },
                                        onConnectApiKey = viewModel::connectNexusApiKey,
                                        onDisconnect = viewModel::disconnectNexusAccount,
                                        onOpenNexusKeyPage = {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://www.nexusmods.com/users/myaccount?tab=api"),
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                }

                                composable(KpziRoutes.SMAPI_MANAGER) {
                                    SmapiManagerScreen(
                                        detection = detection,
                                        smapiStatus = smapiStatus,
                                        report = preLaunchReport,
                                        preferredSmapiVersion = settings.preferredSmapiVersion,
                                        onRefresh = viewModel::refreshSystemDiagnostics,
                                        onSelectSmapiVersion = viewModel::setPreferredSmapiVersion,
                                        onBack = { navController.popBackStack() },
                                    )
                                }

                                composable(KpziRoutes.SAVE_BACKUP) {
                                    SaveBackupScreen(
                                        backups = saveBackups,
                                        installedModCount = installedMods.size,
                                        isCreatingBackup = isCreatingBackup,
                                        onCreateModsBackupZip = { viewModel.createModsBackupZip(downloadImmediately = true) },
                                        onImportModsBackupZip = {
                                            importBackupZipLauncher.launch(
                                                arrayOf("application/zip", "application/x-zip-compressed", "*/*"),
                                            )
                                        },
                                        onCreateSaveBackup = { backupTreeLauncher.launch(null) },
                                        onDownloadBackupZip = { backup ->
                                            pendingExportBackup = backup
                                            exportBackupZipLauncher.launch(backup.fileName)
                                        },
                                        onShareBackupZip = { backup ->
                                            runCatching {
                                                val shareUri = viewModel.getBackupShareUri(backup)
                                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                                    type = "application/zip"
                                                    putExtra(Intent.EXTRA_STREAM, shareUri)
                                                    putExtra(Intent.EXTRA_SUBJECT, backup.fileName)
                                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                                }
                                                context.startActivity(Intent.createChooser(shareIntent, "แชร์ไฟล์สำรอง ZIP"))
                                            }.onFailure {
                                                pendingExportBackup = backup
                                                exportBackupZipLauncher.launch(backup.fileName)
                                            }
                                        },
                                        onRestoreBackup = { backup ->
                                            if (backup.category == BackupCategory.MODS_AND_CONFIG) {
                                                viewModel.restoreModsBackup(backup)
                                            } else {
                                                pendingRestoreBackup = backup
                                                restoreTreeLauncher.launch(null)
                                            }
                                        },
                                        onDeleteBackup = viewModel::deleteSaveBackup,
                                        onBack = { navController.popBackStack() },
                                    )
                                }

                                composable(KpziRoutes.LOG_VIEWER) {
                                    LogsScreen(
                                        logs = logs,
                                        activeLogSource = activeLogSource,
                                        isReloading = isReloadingLogs,
                                        onReloadLogs = viewModel::reloadLocalLogs,
                                        onImportLog = { logImportLauncher.launch(arrayOf("text/plain", "*/*")) },
                                        onShareLogs = {
                                            val text = viewModel.exportLogsText()
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, "Kpzi Mod Manager Logs")
                                                putExtra(Intent.EXTRA_TEXT, text)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "แชร์บันทึก Log"))
                                        },
                                        onClearLogs = viewModel::clearLogs,
                                        onCopyMessage = viewModel::showUiMessage,
                                        onBack = null,
                                    )
                                }

                                composable(KpziRoutes.CONFLICT_DETECTION) {
                                    ConflictDetectionScreen(
                                        report = conflictReport,
                                        isScanning = isScanningConflicts,
                                        onRunScan = viewModel::runConflictDetection,
                                        onEnableDisabledMod = viewModel::toggleModEnabled,
                                        onOpenMyMods = {
                                            navController.navigate(KpziRoutes.MY_MODS) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                        onBack = { navController.popBackStack() },
                                    )
                                }
                            }
                        }
                    }
                }
            }

            pendingInstall?.let { pending ->
                KpziModInstallConfirmDialog(
                    pending = pending,
                    onConfirm = viewModel::confirmPendingInstall,
                    onDismiss = viewModel::cancelPendingInstall,
                )
            }

            installedSuccessMod?.let { mod ->
                KpziInstallSuccessDialog(
                    mod = mod,
                    onLaunchGame = {
                        viewModel.dismissInstallSuccess()
                        viewModel.launchGame()
                    },
                    onBackToCatalog = {
                        viewModel.dismissInstallSuccess()
                    },
                )
            }

            launchBlockedReason?.let { reason ->
                AlertDialog(
                    onDismissRequest = { launchBlockedReason = null },
                    containerColor = MaterialTheme.colorScheme.surface,
                    title = { Text("ยังไม่สามารถเปิดเกมได้", style = MaterialTheme.typography.titleLarge) },
                    text = { Text(reason, style = MaterialTheme.typography.bodyMedium) },
                    confirmButton = {
                        TextButton(onClick = {
                            launchBlockedReason = null
                            navController.navigate(KpziRoutes.SMAPI_MANAGER)
                        }) {
                            Text("ตรวจสอบสถานะเกม")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { launchBlockedReason = null }) {
                            Text("ปิด")
                        }
                    },
                )
            }

            KpziSplashOverlay(visible = isSplashVisible)
        }
    }
}

@Composable
private fun IosBottomTabBar(
    tabs: List<BottomTab>,
    currentHierarchy: Sequence<androidx.navigation.NavDestination>?,
    activeDownloadCount: Int,
    borderColor: androidx.compose.ui.graphics.Color,
    mutedColor: androidx.compose.ui.graphics.Color,
    onSelectTab: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.navigationBarsPadding()) {
            HorizontalDivider(thickness = 0.6.dp, color = borderColor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                tabs.forEach { tab ->
                    val selected = currentHierarchy?.any { it.route == tab.route } == true
                    val scale by animateFloatAsState(
                        targetValue = if (selected) 1.05f else 1.0f,
                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                        label = "tab_scale",
                    )
                    val tint = if (selected) MaterialTheme.colorScheme.primary else mutedColor

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .minimumInteractiveComponentSize()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = { onSelectTab(tab.route) },
                            )
                            .padding(vertical = 4.dp)
                            .testTag("nav_${tab.route}"),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp, Alignment.CenterVertically),
                    ) {
                        Box(
                            modifier = Modifier
                                .scale(scale)
                                .background(
                                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                                )
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (tab.route == KpziRoutes.DOWNLOADS && activeDownloadCount > 0) {
                                BadgedBox(badge = { Badge { Text(activeDownloadCount.toString()) } }) {
                                    Icon(
                                        imageVector = tab.icon,
                                        contentDescription = tab.label,
                                        tint = tint,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            } else {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = tab.label,
                                    tint = tint,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                        Text(
                            text = tab.label,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = tint,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
