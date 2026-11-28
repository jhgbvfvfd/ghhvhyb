package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpzi.domain.model.ApiQuotaState
import com.example.kpzi.domain.model.BackupCategory
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.domain.model.SaveBackupInfo
import com.example.kpzi.domain.model.SmapiStatus
import com.example.kpzi.engine.loader.PreLaunchReport
import com.example.kpzi.ui.components.IosSquircleIcon
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.StatusPill
import com.example.kpzi.ui.components.formatFileSize
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubScreenTopBar(
    title: String,
    onBack: () -> Unit,
    actions: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.titleLarge) },
        navigationIcon = {
            IconButton(onClick = onBack, modifier = Modifier.testTag("subscreen_back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = tr("ย้อนกลับ", "Back"))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
        ),
    )
}

@Composable
fun NexusAccountScreen(
    userProfile: NexusUserProfile?,
    quotaState: ApiQuotaState,
    isValidating: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    onConnectApiKey: (String) -> Unit,
    onDisconnect: () -> Unit,
    onOpenNexusKeyPage: () -> Unit,
) {
    val ext = LocalKpziColors.current
    val isEnglish = isEnglishLanguage()
    var apiKeyInput by remember { mutableStateOf("") }
    var showKey by remember { mutableStateOf(false) }

    Scaffold(
        topBar = { SubScreenTopBar(tr("บัญชี Nexus Mods", "Nexus Mods Account"), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = userProfile?.username ?: tr("ยังไม่ได้เชื่อมต่อบัญชี", "Account not connected"),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (userProfile != null) {
                                    "User ID: ${userProfile.userId}"
                                } else {
                                    tr(
                                        "เก็บ API Key ด้วย Android KeyStore AES-256",
                                        "API Key stored with Android KeyStore AES-256",
                                    )
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                        if (userProfile != null) {
                            StatusPill(
                                text = if (userProfile.isPremium) "Premium" else "Free",
                                containerColor = if (userProfile.isPremium) ext.accentContainer else ext.infoContainer,
                                contentColor = if (userProfile.isPremium) ext.onAccentContainer else ext.info,
                            )
                        } else {
                            StatusPill(
                                text = tr("ออฟไลน์", "Offline"),
                                containerColor = ext.warningContainer,
                                contentColor = ext.warning,
                            )
                        }
                    }

                    if (userProfile != null) {
                        Spacer(Modifier.height(10.dp))
                        OutlinedButton(
                            onClick = onDisconnect,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ext.error),
                            border = BorderStroke(1.dp, ext.error.copy(alpha = 0.4f)),
                        ) {
                            Text(tr("ตัดการเชื่อมต่อและล้าง Key", "Disconnect & Clear Key"))
                        }
                    }
                }
            }

            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Text(tr("เชื่อมต่อด้วย Personal API Key", "Connect with Personal API Key"), style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr(
                            "ระบบไม่ฝัง API Key ของผู้พัฒนาใน APK และเก็บรหัสของคุณใน KeyStore ที่เข้ารหัสเฉพาะเครื่องนี้",
                            "No developer API key is embedded in the APK. Your personal key is encrypted in the device KeyStore.",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("nexus_api_key_input"),
                        label = { Text("Nexus Personal API Key") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        trailingIcon = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    imageVector = if (showKey) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = tr("แสดงหรือซ่อน Key", "Show or hide API key"),
                                )
                            }
                        },
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                    )
                    if (!errorMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(errorMessage, style = MaterialTheme.typography.bodySmall, color = ext.error)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onConnectApiKey(apiKeyInput) },
                            enabled = !isValidating,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .testTag("connect_nexus_button"),
                        ) {
                            if (isValidating) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Text(tr("ตรวจสอบและเชื่อมต่อ", "Verify & Connect"))
                            }
                        }
                        OutlinedButton(
                            onClick = onOpenNexusKeyPage,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(46.dp),
                            border = BorderStroke(0.8.dp, ext.border),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(tr("รับ Key", "Get Key"))
                        }
                    }
                }
            }

            // Live API Quota Card (from real HTTP Response Headers)
            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(tr("โควตา API (จาก Header จริง)", "API Quota (Live Headers)"), style = MaterialTheme.typography.titleSmall)
                        StatusPill(
                            text = quotaState.status.label(isEnglish),
                            containerColor = ext.infoContainer,
                            contentColor = ext.info,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    QuotaLine("Daily Remaining / Limit", "${quotaState.dailyRemaining ?: "-"} / ${quotaState.dailyLimit ?: "-"}")
                    QuotaLine("Daily Reset", quotaState.dailyReset ?: tr("รอข้อมูลจากเซิร์ฟเวอร์", "Waiting for server data"))
                    HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ext.border)
                    QuotaLine("Hourly Remaining / Limit", "${quotaState.hourlyRemaining ?: "-"} / ${quotaState.hourlyLimit ?: "-"}")
                    QuotaLine("Hourly Reset", quotaState.hourlyReset ?: tr("รอข้อมูลจากเซิร์ฟเวอร์", "Waiting for server data"))
                    if (quotaState.retryAfterSeconds != null) {
                        QuotaLine(
                            "Retry-After",
                            tr("${quotaState.retryAfterSeconds} วินาที", "${quotaState.retryAfterSeconds} sec"),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuotaLine(label: String, value: String) {
    val ext = LocalKpziColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
fun SmapiManagerScreen(
    detection: GameDetectionResult?,
    smapiStatus: SmapiStatus,
    report: PreLaunchReport?,
    preferredSmapiVersion: String,
    onRefresh: () -> Unit,
    onSelectSmapiVersion: (String) -> Unit,
    onBack: () -> Unit,
) {
    val ext = LocalKpziColors.current
    Scaffold(
        topBar = {
            SubScreenTopBar(tr("จัดการ SMAPI & ตัวตรวจจับเกม", "SMAPI & Game Detector"), onBack) {
                IconButton(onClick = onRefresh) {
                    Icon(Icons.Rounded.Refresh, contentDescription = tr("สแกนใหม่", "Rescan"))
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Game Detection Engine", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(6.dp))
                    if (detection == null) {
                        Text(tr("กำลังสแกนระบบ...", "Scanning system..."), style = MaterialTheme.typography.bodySmall)
                    } else {
                        QuotaLine(
                            tr("สถานะเกม", "Game Status"),
                            if (detection.isGameInstalled) tr("ติดตั้งแล้ว", "Installed") else tr("ไม่พบตัวเกม", "Not Found"),
                        )
                        QuotaLine("Package Name", detection.packageName ?: "-")
                        QuotaLine("Version Name", detection.versionName ?: "-")
                        QuotaLine("Version Code", detection.versionCode?.toString() ?: "-")
                        QuotaLine("ABI", detection.supportedAbis.joinToString())
                        QuotaLine(
                            "ARM64",
                            if (detection.isArm64Supported) tr("รองรับ", "Supported") else tr("ไม่รองรับ", "Unsupported"),
                        )
                        QuotaLine("Android", detection.androidVersionLabel)
                        QuotaLine(
                            tr("พื้นที่ว่าง", "Free Storage"),
                            "${formatFileSize(detection.availableStorageBytes)} / ${formatFileSize(detection.totalStorageBytes)}",
                        )
                        QuotaLine("Runtime", detection.runtimeType)
                        QuotaLine(
                            "Split APKs",
                            tr("${detection.splitApkPaths.size} ไฟล์", "${detection.splitApkPaths.size} files"),
                        )
                    }
                }
            }

            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Text("SMAPI Target Version", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("4.1.10-android", "4.0.8-android", "3.18.6-android").forEach { ver ->
                            FilterChip(
                                selected = preferredSmapiVersion == ver,
                                onClick = { onSelectSmapiVersion(ver) },
                                label = { Text(ver, style = MaterialTheme.typography.labelSmall) },
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = tr(
                            "สถานะปัจจุบัน: ${smapiStatus.gameLoaderStateLabel}",
                            "Current Status: ${smapiStatus.gameLoaderStateLabel}",
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText,
                    )
                }
            }

            if (report != null) {
                item {
                    Text(tr("สถาปัตยกรรม Game Loader", "Game Loader Architecture"), style = MaterialTheme.typography.titleMedium)
                }
                items(report.diagnostics, key = { it.moduleName }) { diag ->
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = diag.moduleName,
                                    style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                )
                                Text(diag.summaryTh, style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = diag.detail,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Icon(
                                imageVector = if (diag.statusOk) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (diag.statusOk) ext.success else ext.warning,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SaveBackupScreen(
    backups: List<SaveBackupInfo>,
    installedModCount: Int,
    isCreatingBackup: Boolean,
    onCreateModsBackupZip: () -> Unit,
    onImportModsBackupZip: () -> Unit,
    onCreateSaveBackup: () -> Unit,
    onDownloadBackupZip: (SaveBackupInfo) -> Unit,
    onShareBackupZip: (SaveBackupInfo) -> Unit,
    onRestoreBackup: (SaveBackupInfo) -> Unit,
    onDeleteBackup: (SaveBackupInfo) -> Unit,
    onBack: () -> Unit,
) {
    val ext = LocalKpziColors.current
    val isEnglish = isEnglishLanguage()
    val dateFormat = remember { SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()) }

    Scaffold(
        topBar = { SubScreenTopBar(tr("สำรองข้อมูลเป็นไฟล์ ZIP", "ZIP Backups & Restore"), onBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IosSquircleIcon(
                            icon = Icons.Rounded.FolderZip,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.primary,
                            size = 40.dp,
                            iconSize = 22.dp,
                            cornerRadius = 12.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("สำรองมอดและบันทึกเป็นไฟล์ ZIP", "Backup Mods & Logs as ZIP"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = tr(
                                    "รวมมอดที่ติดตั้งทั้งหมด ($installedModCount ตัว) และ Log ระบบเป็นไฟล์ .zip ให้ดาวน์โหลดเก็บไว้",
                                    "Bundle all installed mods ($installedModCount) and system logs into a downloadable .zip archive.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onCreateModsBackupZip,
                        enabled = !isCreatingBackup,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("create_mods_backup_zip_button"),
                    ) {
                        if (isCreatingBackup) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(tr("กำลังบีบอัดไฟล์ ZIP...", "Compressing ZIP archive..."))
                        } else {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr("สร้างและดาวน์โหลดไฟล์ ZIP", "Create & Export ZIP"))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onImportModsBackupZip,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, ext.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .testTag("import_mods_backup_zip_button"),
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("นำเข้าไฟล์ ZIP สำรองเพื่อกู้คืนมอด", "Import Backup ZIP to Restore Mods"))
                    }
                }
            }

            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IosSquircleIcon(
                            icon = Icons.Rounded.Backup,
                            containerColor = ext.successContainer,
                            iconTint = ext.success,
                            size = 40.dp,
                            iconSize = 22.dp,
                            cornerRadius = 12.dp,
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tr("สำรองโฟลเดอร์เซฟเกม (SAF)", "Backup Game Saves Folder (SAF)"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = tr(
                                    "เลือกโฟลเดอร์เซฟของ Stardew Valley ผ่านระบบ Android เพื่อบีบอัดเป็น ZIP แล้วดาวน์โหลด",
                                    "Select your Stardew Valley saves folder via Android SAF to compress and export as ZIP.",
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onCreateSaveBackup,
                        enabled = !isCreatingBackup,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(0.8.dp, ext.border),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("create_save_backup_button"),
                    ) {
                        Icon(Icons.Rounded.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(tr("เลือกโฟลเดอร์เซฟและสำรองเป็น ZIP", "Select Saves Folder & Export ZIP"))
                    }
                }
            }

            item {
                Text(
                    text = tr("ไฟล์ ZIP สำรองทั้งหมด (${backups.size})", "All Backup ZIPs (${backups.size})"),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (backups.isEmpty()) {
                item {
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = tr(
                                "ยังไม่มีไฟล์สำรอง ZIP ในเครื่อง กดปุ่มด้านบนเพื่อสร้างและดาวน์โหลดได้เลย",
                                "No local ZIP backups yet. Tap above to create and export one.",
                            ),
                            style = MaterialTheme.typography.bodyMedium,
                            color = ext.mutedText,
                        )
                    }
                }
            } else {
                items(backups, key = { it.filePath }) { backup ->
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Text(
                                        text = backup.fileName,
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${formatFileSize(backup.sizeBytes)} • ${dateFormat.format(Date(backup.createdAtMs))}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = ext.mutedText,
                                    )
                                }
                                StatusPill(
                                    text = backup.category.label(isEnglish),
                                    containerColor = if (backup.category == BackupCategory.MODS_AND_CONFIG) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        ext.successContainer
                                    },
                                    contentColor = if (backup.category == BackupCategory.MODS_AND_CONFIG) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        ext.success
                                    },
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = { onDownloadBackupZip(backup) },
                                    shape = RoundedCornerShape(11.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(40.dp)
                                        .testTag("download_backup_${backup.fileName}"),
                                ) {
                                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(5.dp))
                                    Text(tr("ดาวน์โหลด ZIP", "Export ZIP"), style = MaterialTheme.typography.labelMedium)
                                }
                                IconButton(
                                    onClick = { onShareBackupZip(backup) },
                                    modifier = Modifier.testTag("share_backup_${backup.fileName}"),
                                ) {
                                    IosSquircleIcon(
                                        icon = Icons.Rounded.Share,
                                        containerColor = ext.infoContainer,
                                        iconTint = ext.info,
                                        size = 36.dp,
                                        iconSize = 18.dp,
                                        cornerRadius = 10.dp,
                                    )
                                }
                                IconButton(
                                    onClick = { onRestoreBackup(backup) },
                                    modifier = Modifier.testTag("restore_backup_${backup.fileName}"),
                                ) {
                                    IosSquircleIcon(
                                        icon = Icons.Rounded.Restore,
                                        containerColor = ext.accentContainer,
                                        iconTint = ext.onAccentContainer,
                                        size = 36.dp,
                                        iconSize = 18.dp,
                                        cornerRadius = 10.dp,
                                    )
                                }
                                IconButton(
                                    onClick = { onDeleteBackup(backup) },
                                    modifier = Modifier.testTag("delete_backup_${backup.fileName}"),
                                ) {
                                    IosSquircleIcon(
                                        icon = Icons.Rounded.DeleteOutline,
                                        containerColor = ext.errorContainer,
                                        iconTint = ext.error,
                                        size = 36.dp,
                                        iconSize = 18.dp,
                                        cornerRadius = 10.dp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
