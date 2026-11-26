package com.example.kpzi.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpzi.data.local.UserSettings
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.domain.model.SmapiStatus
import com.example.kpzi.ui.components.IosSquircleIcon
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.StatusPill
import com.example.ui.theme.LocalKpziColors
import com.example.ui.theme.LocalThemeState
import com.example.ui.theme.ThemeMode

@Composable
fun SettingsScreen(
    settings: UserSettings,
    detection: GameDetectionResult?,
    smapiStatus: SmapiStatus,
    userProfile: NexusUserProfile?,
    conflictCount: Int,
    notificationsEnabled: Boolean,
    onConfigureNotifications: () -> Unit,
    onPickGameModsFolder: () -> Unit,
    onOpenSmapiManager: () -> Unit,
    onOpenConflictDetection: () -> Unit,
    onOpenNexusAccount: () -> Unit,
    onOpenSaveBackup: () -> Unit,
    onOpenLogs: () -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    var showThemeDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text("ตั้งค่า", style = MaterialTheme.typography.headlineMedium)
        }

        // iOS Grouped Settings: Game Settings
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ตั้งค่าเกม",
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.mutedText,
                    modifier = Modifier.padding(start = 6.dp),
                )
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    IosSettingsRow(
                        icon = Icons.Rounded.FolderOpen,
                        badgeBg = ext.infoContainer,
                        badgeTint = ext.info,
                        title = "ตำแหน่งโฟลเดอร์มอด (SAF)",
                        subtitle = settings.safModsUri ?: (detection?.packageName ?: "ใช้โฟลเดอร์แอปอัตโนมัติ (แตะเพื่อเลือกผ่าน SAF)"),
                        onClick = onPickGameModsFolder,
                        modifier = Modifier.testTag("settings_saf_folder"),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 44.dp, top = 8.dp, bottom = 8.dp),
                        thickness = 0.6.dp,
                        color = ext.border,
                    )
                    IosSettingsRow(
                        icon = Icons.Rounded.Build,
                        badgeBg = MaterialTheme.colorScheme.primaryContainer,
                        badgeTint = MaterialTheme.colorScheme.primary,
                        title = "จัดการ SMAPI & Game Loader",
                        subtitle = "เวอร์ชันเป้าหมาย ${smapiStatus.smapiVersion ?: "-"}",
                        trailingBadge = {
                            StatusPill(
                                text = if (smapiStatus.isInstalled) "ติดตั้งแล้ว" else "ตรวจสอบ",
                                containerColor = if (smapiStatus.isInstalled) ext.successContainer else ext.accentContainer,
                                contentColor = if (smapiStatus.isInstalled) ext.success else ext.onAccentContainer,
                            )
                        },
                        onClick = onOpenSmapiManager,
                        modifier = Modifier.testTag("settings_smapi_manager"),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 44.dp, top = 8.dp, bottom = 8.dp),
                        thickness = 0.6.dp,
                        color = ext.border,
                    )
                    IosSettingsRow(
                        icon = Icons.Rounded.Shield,
                        badgeBg = if (conflictCount > 0) ext.errorContainer else ext.successContainer,
                        badgeTint = if (conflictCount > 0) ext.error else ext.success,
                        title = "ตรวจจับความขัดแย้งมอด",
                        subtitle = "สแกน manifest.json หา Dependency Loop และเวอร์ชันไม่ตรง",
                        trailingBadge = {
                            if (conflictCount > 0) {
                                StatusPill(
                                    text = "$conflictCount ปัญหา",
                                    containerColor = ext.errorContainer,
                                    contentColor = ext.error,
                                )
                            }
                        },
                        onClick = onOpenConflictDetection,
                        modifier = Modifier.testTag("settings_conflict_detection"),
                    )
                }
            }
        }

        // iOS Grouped Settings: Nexus Account
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "บัญชีและโควตา API",
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.mutedText,
                    modifier = Modifier.padding(start = 6.dp),
                )
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    IosSettingsRow(
                        icon = Icons.Rounded.AccountCircle,
                        badgeBg = ext.accentContainer,
                        badgeTint = ext.onAccentContainer,
                        title = "บัญชี Nexus Mods",
                        subtitle = if (userProfile != null) {
                            "${userProfile.username} (${if (userProfile.isPremium) "Premium" else "Free"})"
                        } else {
                            "ยังไม่ได้เชื่อมต่อ — แตะเพื่อใส่ API Key"
                        },
                        trailingBadge = {
                            StatusPill(
                                text = if (userProfile != null) "เชื่อมต่อแล้ว" else "ยังไม่เชื่อม",
                                containerColor = if (userProfile != null) ext.successContainer else ext.warningContainer,
                                contentColor = if (userProfile != null) ext.success else ext.warning,
                            )
                        },
                        onClick = onOpenNexusAccount,
                        modifier = Modifier.testTag("settings_nexus_account"),
                    )
                }
            }
        }

        // iOS Grouped Settings: General
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "ทั่วไป",
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.mutedText,
                    modifier = Modifier.padding(start = 6.dp),
                )
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    val themeState = LocalThemeState.current
                    IosSettingsRow(
                        icon = if (themeState.isDark) Icons.Rounded.DarkMode else Icons.Rounded.LightMode,
                        badgeBg = MaterialTheme.colorScheme.surfaceVariant,
                        badgeTint = if (themeState.isDark) ext.accent else MaterialTheme.colorScheme.primary,
                        title = "ธีม",
                        subtitle = when (themeState.themeMode) {
                            ThemeMode.SYSTEM -> if (themeState.isDark) "ตามระบบ (กำลังใช้โหมดมืด)" else "ตามระบบ (กำลังใช้โหมดสว่าง)"
                            ThemeMode.LIGHT -> "สว่าง (Cream & Green)"
                            ThemeMode.DARK -> "มืด (Dark Timber)"
                        },
                        trailingBadge = {
                            StatusPill(
                                text = if (themeState.isDark) "Dark" else "Light",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        },
                        onClick = { showThemeDialog = true },
                        modifier = Modifier.testTag("settings_theme"),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 44.dp, top = 8.dp, bottom = 8.dp),
                        thickness = 0.6.dp,
                        color = ext.border,
                    )
                    IosSettingsRow(
                        icon = Icons.Rounded.Backup,
                        badgeBg = ext.successContainer,
                        badgeTint = ext.success,
                        title = "สำรองข้อมูลเป็นไฟล์ ZIP",
                        subtitle = "สำรองมอดและเซฟเป็น ZIP เพื่อดาวน์โหลดหรือกู้คืน",
                        onClick = onOpenSaveBackup,
                        modifier = Modifier.testTag("settings_save_backup"),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 44.dp, top = 8.dp, bottom = 8.dp),
                        thickness = 0.6.dp,
                        color = ext.border,
                    )
                    IosSettingsRow(
                        icon = Icons.Rounded.ReceiptLong,
                        badgeBg = ext.warningContainer,
                        badgeTint = ext.warning,
                        title = "ตรวจสอบ Log",
                        subtitle = "ดูบันทึกการทำงานและนำเข้า SMAPI-latest.txt",
                        onClick = onOpenLogs,
                        modifier = Modifier.testTag("settings_logs"),
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 44.dp, top = 8.dp, bottom = 8.dp),
                        thickness = 0.6.dp,
                        color = ext.border,
                    )
                    IosSettingsRow(
                        icon = Icons.Rounded.NotificationsActive,
                        badgeBg = if (notificationsEnabled) ext.infoContainer else ext.warningContainer,
                        badgeTint = if (notificationsEnabled) ext.info else ext.warning,
                        title = "แจ้งเตือนเวลาเล่นและดาวน์โหลด",
                        subtitle = "แถบจับเวลาเล่นเกมและแจ้งเตือนเด้งเมื่อโหลดมอดเสร็จ",
                        trailingBadge = {
                            StatusPill(
                                text = if (notificationsEnabled) "เปิดอยู่" else "อนุญาต",
                                containerColor = if (notificationsEnabled) ext.successContainer else ext.warningContainer,
                                contentColor = if (notificationsEnabled) ext.success else ext.warning,
                            )
                        },
                        onClick = onConfigureNotifications,
                        modifier = Modifier.testTag("settings_notifications"),
                    )
                }
            }
        }

        // iOS Grouped Settings: About
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "เกี่ยวกับ",
                    style = MaterialTheme.typography.labelLarge,
                    color = ext.mutedText,
                    modifier = Modifier.padding(start = 6.dp),
                )
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    IosSettingsRow(
                        icon = Icons.Rounded.Info,
                        badgeBg = ext.infoContainer,
                        badgeTint = ext.info,
                        title = "เกี่ยวกับแอปและลิขสิทธิ์",
                        subtitle = "Kpzi Mod Manager เวอร์ชัน 1.0.0 (Clean-room)",
                        onClick = { showAboutDialog = true },
                        modifier = Modifier.testTag("settings_about"),
                    )
                }
            }
        }
    }

    if (showThemeDialog) {
        AlertDialog(
            onDismissRequest = { showThemeDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("เลือกธีมหน้าจอ", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column {
                    listOf(
                        ThemeMode.SYSTEM to "ตามการตั้งค่าระบบ",
                        ThemeMode.LIGHT to "โหมดสว่าง (Cream & Forest)",
                        ThemeMode.DARK to "โหมดมืด (Dark Timber)",
                    ).forEach { (mode, label) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.themeMode == mode,
                                onClick = {
                                    onThemeModeChange(mode)
                                    showThemeDialog = false
                                },
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(label, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showThemeDialog = false }) { Text("ปิด") }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Kpzi Mod Manager v1.0.0", style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "พัฒนาแบบ Clean-room บน Native Android (Kotlin & Jetpack Compose) โดยไม่รวมไฟล์เกม Stardew Valley และไม่ข้ามระบบตรวจสอบสิทธิ์ของตัวเกม",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = "ข้อมูลมอดและ API เชื่อมต่อผ่าน Nexus Mods REST API v1 และ GraphQL v2 อย่างเป็นทางการ พร้อมระบบเข้ารหัส KeyStore สำหรับ API Key ส่วนบุคคล",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText,
                    )
                    Text(
                        text = "Open-source libraries: Jetpack Compose (Apache 2.0), AndroidX Room/Navigation/DataStore (Apache 2.0), OkHttp (Apache 2.0), Coil (Apache 2.0).",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.mutedText,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) { Text("ตกลง") }
            },
        )
    }
}

@Composable
private fun IosSettingsRow(
    icon: ImageVector,
    badgeBg: Color,
    badgeTint: Color,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailingBadge: (@Composable () -> Unit)? = null,
) {
    val ext = LocalKpziColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        IosSquircleIcon(
            icon = icon,
            containerColor = badgeBg,
            iconTint = badgeTint,
            size = 32.dp,
            iconSize = 18.dp,
            cornerRadius = 9.dp,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = ext.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailingBadge != null) {
            trailingBadge()
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = ext.mutedText,
            modifier = Modifier.size(20.dp),
        )
    }
}
