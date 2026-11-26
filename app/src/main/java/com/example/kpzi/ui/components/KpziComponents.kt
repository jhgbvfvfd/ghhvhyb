package com.example.kpzi.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.SubcomposeAsyncImage
import com.example.R
import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.NexusModSummary
import com.example.kpzi.engine.installer.PendingModInstall
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors
import java.util.Locale

@Composable
fun KpziCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val ext = LocalKpziColors.current
    val clickableModifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier
    Surface(
        modifier = clickableModifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.8.dp, ext.border),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(14.dp), content = content)
    }
}

/**
 * iOS-inspired squircle icon badge for crisp SF-Symbol-like visual hierarchy.
 */
@Composable
fun IosSquircleIcon(
    icon: ImageVector,
    containerColor: Color,
    iconTint: Color,
    size: Dp = 32.dp,
    iconSize: Dp = 18.dp,
    cornerRadius: Dp = 9.dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * iOS-style Segmented Control with a floating elevated thumb inside a muted pill track.
 */
@Composable
fun IosSegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.5.dp, ext.border),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val bg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "seg_bg",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) MaterialTheme.colorScheme.onSurface else ext.mutedText,
                    label = "seg_fg",
                )
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = bg,
                    shadowElevation = if (selected) 1.dp else 0.dp,
                    border = if (selected) BorderStroke(0.5.dp, ext.border) else null,
                    onClick = { onSelect(index) },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = containerColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = contentColor,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
fun CompatibilityBadge(level: CompatibilityLevel, modifier: Modifier = Modifier) {
    val ext = LocalKpziColors.current
    val (bg, fg) = when (level) {
        CompatibilityLevel.VERIFIED_WORKING -> ext.successContainer to ext.success
        CompatibilityLevel.LIKELY_COMPATIBLE -> ext.infoContainer to ext.info
        CompatibilityLevel.UNVERIFIED -> ext.accentContainer to ext.onAccentContainer
        CompatibilityLevel.INCOMPATIBLE -> ext.errorContainer to ext.error
    }
    StatusPill(text = level.labelTh, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun ModCoverThumb(
    pictureUrl: String?,
    contentDescription: String,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(13.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .border(0.8.dp, ext.border, RoundedCornerShape(13.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (!pictureUrl.isNullOrBlank()) {
            SubcomposeAsyncImage(
                model = pictureUrl,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = {
                    Image(
                        painter = painterResource(R.drawable.img_farm_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                error = {
                    Image(
                        painter = painterResource(R.drawable.img_farm_banner),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
            )
        } else {
            Image(
                painter = painterResource(R.drawable.img_farm_banner),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun CompactModRow(
    mod: NexusModSummary,
    onModClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mod_row_${mod.modId}")
            .clickable(onClick = onModClick),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(0.8.dp, ext.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 11.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            ModCoverThumb(
                pictureUrl = mod.pictureUrl,
                contentDescription = mod.name,
                modifier = Modifier.size(54.dp),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = mod.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mod.summary.ifBlank { "โดย ${mod.author}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    StatusPill(
                        text = mod.categoryName,
                        containerColor = ext.accentContainer,
                        contentColor = ext.onAccentContainer,
                    )
                    Text(
                        text = "↓ ${formatCompactCount(mod.downloads)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.mutedText,
                    )
                    if (mod.fileSizeBytes != null && mod.fileSizeBytes > 0L) {
                        Text(
                            text = formatFileSize(mod.fileSizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.mutedText,
                        )
                    } else {
                        Text(
                            text = "v${mod.version}",
                            style = MaterialTheme.typography.labelSmall,
                            color = ext.mutedText,
                            maxLines = 1,
                        )
                    }
                }
            }
            // iOS App Store-inspired compact rounded action icon inside 48dp touch target
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .testTag("download_btn_${mod.modId}")
                    .clickable(onClick = onDownloadClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.ArrowDownward,
                    contentDescription = "ดาวน์โหลด ${mod.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }
    }
}

@Composable
fun KpziModInstallConfirmDialog(
    pending: PendingModInstall,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val ext = LocalKpziColors.current
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        icon = {
            IosSquircleIcon(
                icon = if (pending.hasEntryDll) Icons.Rounded.WarningAmber else Icons.Rounded.FolderZip,
                containerColor = if (pending.hasEntryDll) ext.warningContainer else MaterialTheme.colorScheme.primaryContainer,
                iconTint = if (pending.hasEntryDll) ext.warning else MaterialTheme.colorScheme.primary,
                size = 44.dp,
                iconSize = 24.dp,
                cornerRadius = 13.dp,
            )
        },
        title = {
            Text(
                text = if (pending.isUpdate) "ยืนยันการอัปเดตมอด" else "ยืนยันการติดตั้งมอด",
                style = MaterialTheme.typography.titleLarge,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "${pending.manifest.name} (v${pending.manifest.version})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "UniqueID: ${pending.manifest.uniqueId}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = ext.mutedText,
                )
                Text(
                    text = "ผู้พัฒนา: ${pending.manifest.author}",
                    style = MaterialTheme.typography.bodySmall,
                    color = ext.mutedText,
                )
                if (pending.isUpdate && pending.existingVersion != null) {
                    StatusPill(
                        text = "อัปเดตจาก v${pending.existingVersion} → v${pending.manifest.version} (สำรองเดิมอัตโนมัติ)",
                        containerColor = ext.infoContainer,
                        contentColor = ext.info,
                    )
                }
                if (pending.hasEntryDll) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = ext.warningContainer,
                        border = BorderStroke(0.8.dp, ext.warning.copy(alpha = 0.4f)),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = ext.warning,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "คำเตือนความปลอดภัย: มอดนี้มีไฟล์ DLL (${pending.manifest.entryDll ?: "*.dll"}) ซึ่งสามารถรันโค้ดได้ กรุณาติดตั้งเฉพาะจากแหล่งที่เชื่อถือได้เท่านั้น",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.warning,
                            )
                        }
                    }
                }
                if (pending.missingDependencies.isNotEmpty()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = ext.errorContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Info,
                                contentDescription = null,
                                tint = ext.error,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "ยังขาด Dependencies: ${pending.missingDependencies.joinToString()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.error,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.testTag("confirm_install_button"),
            ) {
                Text(if (pending.isUpdate) "อัปเดตมอด" else "ยืนยันติดตั้ง")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("ยกเลิก")
            }
        },
    )
}

@Composable
fun KpziInstallSuccessDialog(
    mod: InstalledMod,
    onLaunchGame: () -> Unit,
    onBackToCatalog: () -> Unit,
) {
    val ext = LocalKpziColors.current
    Dialog(onDismissRequest = onBackToCatalog) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(0.8.dp, ext.border),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_mascot_chick),
                    contentDescription = "มาสคอตไก่ฟาร์ม",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(100.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .border(1.5.dp, ext.border, RoundedCornerShape(26.dp)),
                )
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = ext.success,
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    text = "ติดตั้งมอดสำเร็จ!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                )
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        ModCoverThumb(
                            pictureUrl = null,
                            contentDescription = mod.manifest.name,
                            modifier = Modifier.size(44.dp),
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = mod.manifest.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "v${mod.manifest.version} • ${mod.manifest.author}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Button(
                    onClick = onLaunchGame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("success_launch_game_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("เปิดเกมเลย")
                }
                OutlinedButton(
                    onClick = onBackToCatalog,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("success_back_button"),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(0.8.dp, ext.border),
                ) {
                    Text("กลับไปที่คลังมอด")
                }
            }
        }
    }
}

@Composable
fun KpziSplashOverlay(visible: Boolean) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
    ) {
        val ext = LocalKpziColors.current
        val bg = MaterialTheme.colorScheme.background
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bg),
        ) {
            Image(
                painter = painterResource(R.drawable.img_farm_banner),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                if (ext.isDark) Color(0xAA0D0F0A) else Color(0x881F2E16),
                                bg.copy(alpha = 0.82f),
                                bg.copy(alpha = 0.95f),
                            ),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Spacer(Modifier.height(32.dp))
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_app_icon),
                        contentDescription = "Kpzi Mod Manager",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(104.dp)
                            .clip(RoundedCornerShape(26.dp))
                            .border(2.5.dp, ext.accent, RoundedCornerShape(26.dp)),
                    )
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF5D3E23),
                        border = BorderStroke(1.5.dp, ext.accent),
                    ) {
                        Text(
                            text = "Kpzi Mod Manager",
                            style = MaterialTheme.typography.headlineMedium,
                            color = Color(0xFFFFF8E8),
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        )
                    }
                    Text(
                        text = "จัดการมอด Stardew Valley\nง่าย ครบ จบในแอปเดียว",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth(0.68f)
                            .height(7.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = ext.border,
                    )
                    Text(
                        text = "กำลังเริ่มต้น...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }
            }
        }
    }
}

fun formatCompactCount(value: Long): String = when {
    value >= 1_000_000 -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000 -> String.format(Locale.US, "%.1fK", value / 1_000.0)
    else -> value.toString()
}

fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format(Locale.US, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.2f GB", mb / 1024.0)
}

fun formatEta(seconds: Long?): String {
    if (seconds == null || seconds <= 0L) return ""
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "เหลือ ${mins}น. ${secs}วิ." else "เหลือ ${secs}วิ."
}
