package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.ui.components.CompatibilityBadge
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NotoSansThaiFontFamily

@Composable
internal fun MyModListItemCard(
    mod: InstalledMod,
    allMods: List<InstalledMod>,
    assignedCoverId: Int,
    coverRevision: Int,
    palette: MyModsPalette,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
    onPickCover: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnglish = isEnglishLanguage()
    val category = remember(mod.uniqueId, mod.manifest.name, mod.folderName) { detectModCategory(mod) }
    val roleTag = remember(mod.uniqueId, category, allMods.size) {
        detectSecondaryRoleTag(mod, category, allMods)
    }

    Surface(
        onClick = onInspect,
        modifier = modifier
            .fillMaxWidth()
            .testTag("mod_card_${mod.uniqueId}"),
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        shadowElevation = if (palette.isDark) 0.dp else 1.dp,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left purple status bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(38.dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (mod.isEnabled) palette.primary else palette.disabledBar),
            )

            Spacer(Modifier.width(7.dp))

            // Cover image + small corner camera badge (48dp >= 48dp touch target)
            MyModCoverThumbnail(
                mod = mod,
                assignedCoverId = assignedCoverId,
                coverRevision = coverRevision,
                palette = palette,
                size = 48.dp,
                onPickCover = onPickCover,
                cornerRadius = 10.dp,
                badgeSize = 16.dp,
            )

            Spacer(Modifier.width(8.dp))

            // Center metadata column — weight(1f) keeps long names clear of right controls
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = mod.manifest.name,
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    lineHeight = 17.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(11.dp),
                    )
                    Text(
                        text = "v${mod.manifest.version}  •  ${formatMyModsSize(mod.sizeBytes)}",
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        lineHeight = 13.5.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallModPill(
                        text = category.label(isEnglish),
                        bgColor = category.bgColor(palette.isDark),
                        fgColor = category.fgColor(palette.isDark),
                        icon = category.icon,
                    )
                    SmallModPill(
                        text = roleTag.label(isEnglish),
                        bgColor = roleTag.bg(palette.isDark),
                        fgColor = roleTag.fg(palette.isDark),
                        icon = Icons.Rounded.SettingsSuggest,
                    )
                }
            }

            // Right-side controls: compact toggle switch + red trash delete button (each >= 48dp touch area)
            KpziPurpleSwitch(
                checked = mod.isEnabled,
                onCheckedChange = { onToggle() },
                palette = palette,
                modifier = Modifier.testTag("toggle_mod_${mod.uniqueId}"),
            )

            IconButton(
                onClick = onRequestDelete,
                modifier = Modifier.testTag("delete_mod_${mod.uniqueId}"),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.deleteRedBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = tr("ลบมอด ${mod.manifest.name}", "Delete ${mod.manifest.name}"),
                        tint = palette.deleteRed,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun MyModGridItemCard(
    mod: InstalledMod,
    assignedCoverId: Int,
    coverRevision: Int,
    palette: MyModsPalette,
    onToggle: () -> Unit,
    onRequestDelete: () -> Unit,
    onPickCover: () -> Unit,
    onInspect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnglish = isEnglishLanguage()
    val category = remember(mod.uniqueId, mod.manifest.name, mod.folderName) { detectModCategory(mod) }

    Surface(
        onClick = onInspect,
        modifier = modifier
            .fillMaxWidth()
            .testTag("mod_grid_card_${mod.uniqueId}"),
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        shadowElevation = if (palette.isDark) 0.dp else 1.dp,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top horizontal status indicator bar matching list left-bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(if (mod.isEnabled) palette.primary else palette.disabledBar),
            )

            Column(
                modifier = Modifier.padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    MyModCoverThumbnail(
                        mod = mod,
                        assignedCoverId = assignedCoverId,
                        coverRevision = coverRevision,
                        palette = palette,
                        size = 48.dp,
                        onPickCover = onPickCover,
                        cornerRadius = 10.dp,
                        badgeSize = 16.dp,
                    )

                    IconButton(
                        onClick = onRequestDelete,
                        modifier = Modifier.testTag("delete_mod_${mod.uniqueId}"),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(palette.deleteRedBg),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = tr("ลบมอด ${mod.manifest.name}", "Delete ${mod.manifest.name}"),
                                tint = palette.deleteRed,
                                modifier = Modifier.size(15.dp),
                            )
                        }
                    }
                }

                Text(
                    text = mod.manifest.name,
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.5.sp,
                    lineHeight = 16.5.sp,
                    color = palette.textPrimary,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = "v${mod.manifest.version} • ${formatMyModsSize(mod.sizeBytes)}",
                    fontFamily = NotoSansThaiFontFamily,
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallModPill(
                        text = category.label(isEnglish),
                        bgColor = category.bgColor(palette.isDark),
                        fgColor = category.fgColor(palette.isDark),
                        icon = category.icon,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    KpziPurpleSwitch(
                        checked = mod.isEnabled,
                        onCheckedChange = { onToggle() },
                        palette = palette,
                        modifier = Modifier.testTag("toggle_mod_${mod.uniqueId}"),
                    )
                }
            }
        }
    }
}

@Composable
internal fun SmallModPill(
    text: String,
    bgColor: Color,
    fgColor: Color,
    icon: ImageVector? = null,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = bgColor,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = fgColor,
                    modifier = Modifier.size(10.dp),
                )
            }
            Text(
                text = text,
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.5.sp,
                lineHeight = 12.sp,
                color = fgColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun DeleteModConfirmationDialog(
    mod: InstalledMod,
    dependentMods: List<InstalledMod>,
    palette: MyModsPalette,
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val sizeText = remember(mod.sizeBytes) { formatMyModsSize(mod.sizeBytes) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.deleteRedBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = palette.deleteRed,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Text(
                    text = tr("ยืนยันการลบมอด", "Confirm Mod Removal"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = palette.textPrimary,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.surfaceSubtle,
                    border = BorderStroke(0.75.dp, palette.border),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        DialogMetaRow(
                            label = tr("ชื่อมอด", "Mod Name"),
                            value = mod.manifest.name,
                            palette = palette,
                            boldValue = true,
                        )
                        DialogMetaRow(
                            label = tr("เวอร์ชัน", "Version"),
                            value = "v${mod.manifest.version}",
                            palette = palette,
                        )
                        DialogMetaRow(
                            label = tr("ขนาดไฟล์", "File Size"),
                            value = sizeText,
                            palette = palette,
                        )
                        DialogMetaRow(
                            label = tr("พื้นที่ที่จะได้รับคืน", "Space Reclaimed"),
                            value = "+ $sizeText",
                            palette = palette,
                            valueColor = palette.primary,
                            boldValue = true,
                        )
                    }
                }

                if (dependentMods.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (palette.isDark) Color(0xFF3A2812) else Color(0xFFFFFBEB),
                        border = BorderStroke(
                            0.8.dp,
                            if (palette.isDark) Color(0xFF92400E) else Color(0xFFFCD34D),
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WarningAmber,
                                contentDescription = null,
                                tint = if (palette.isDark) Color(0xFFFBBF24) else Color(0xFFD97706),
                                modifier = Modifier.size(18.dp),
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = tr(
                                        "พบ ${dependentMods.size} มอดที่ต้องพึ่งพามอดนี้:",
                                        "${dependentMods.size} installed mod(s) depend on this mod:",
                                    ),
                                    fontFamily = NotoSansThaiFontFamily,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = palette.textPrimary,
                                )
                                dependentMods.take(4).forEach { dep ->
                                    Text(
                                        text = "• ${dep.manifest.name} (v${dep.manifest.version})",
                                        fontFamily = NotoSansThaiFontFamily,
                                        fontSize = 11.5.sp,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                if (dependentMods.size > 4) {
                                    Text(
                                        text = tr(
                                            "และอีก ${dependentMods.size - 4} รายการ",
                                            "and ${dependentMods.size - 4} more",
                                        ),
                                        fontFamily = NotoSansThaiFontFamily,
                                        fontSize = 11.sp,
                                        color = palette.textSecondary,
                                    )
                                }
                            }
                        }
                    }
                }

                Text(
                    text = tr(
                        "ระบบจะลบเฉพาะโฟลเดอร์มอดนี้เท่านั้น ไม่กระทบไฟล์เกมหรือไฟล์เซฟของคุณ",
                        "Only this mod folder will be removed. Your game and save files will never be modified.",
                    ),
                    fontFamily = NotoSansThaiFontFamily,
                    fontSize = 11.5.sp,
                    color = palette.textSecondary,
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("confirm_delete_cancel"),
            ) {
                Text(
                    text = tr("ยกเลิก", "Cancel"),
                    fontFamily = NotoSansThaiFontFamily,
                    color = palette.textSecondary,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.deleteRed,
                    contentColor = Color.White,
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("confirm_delete_proceed"),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = tr("ลบมอด", "Delete Mod"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
    )
}

@Composable
private fun DialogMetaRow(
    label: String,
    value: String,
    palette: MyModsPalette,
    valueColor: Color = palette.textPrimary,
    boldValue: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontFamily = NotoSansThaiFontFamily,
            fontSize = 12.sp,
            color = palette.textSecondary,
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = NotoSansThaiFontFamily,
            fontWeight = if (boldValue) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 12.5.sp,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun InspectModDetailDialog(
    mod: InstalledMod,
    assignedCoverId: Int,
    coverRevision: Int,
    palette: MyModsPalette,
    onPickCover: () -> Unit,
    onResetCustomCover: () -> Unit,
    onRequestDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val hasCustom = remember(mod.uniqueId, coverRevision) {
        MyModsCoverStore.hasCustomCover(context, mod.uniqueId)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                MyModCoverThumbnail(
                    mod = mod,
                    assignedCoverId = assignedCoverId,
                    coverRevision = coverRevision,
                    palette = palette,
                    size = 48.dp,
                    onPickCover = onPickCover,
                    cornerRadius = 11.dp,
                    badgeSize = 16.dp,
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mod.manifest.name,
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = palette.textPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${mod.manifest.version} • ${formatMyModsSize(mod.sizeBytes)}",
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 11.5.sp,
                        color = palette.textSecondary,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                CompatibilityBadge(mod.compatibility)
                Text(
                    text = "UniqueID: ${mod.uniqueId}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    color = palette.textPrimary,
                )
                Text(
                    text = tr("ผู้พัฒนา: ${mod.manifest.author}", "Author: ${mod.manifest.author}"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                )
                if (!mod.manifest.entryDll.isNullOrBlank()) {
                    Text(
                        text = "EntryDll: ${mod.manifest.entryDll}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        color = palette.primary,
                    )
                }
                if (!mod.manifest.contentPackForUniqueId.isNullOrBlank()) {
                    Text(
                        text = "ContentPackFor: ${mod.manifest.contentPackForUniqueId}",
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                    )
                }
                if (mod.manifest.description.isNotBlank()) {
                    Text(
                        text = mod.manifest.description,
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                    )
                }

                Spacer(Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onPickCover,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                        border = BorderStroke(0.8.dp, palette.border),
                    ) {
                        Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            text = tr("เปลี่ยนภาพปก", "Change Cover"),
                            fontFamily = NotoSansThaiFontFamily,
                            fontSize = 12.sp,
                        )
                    }
                    if (hasCustom) {
                        TextButton(onClick = onResetCustomCover) {
                            Icon(Icons.Rounded.Restore, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = tr("รีเซ็ตภาพปก", "Reset Cover"),
                                fontFamily = NotoSansThaiFontFamily,
                                fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("ปิด", "Close"), fontFamily = NotoSansThaiFontFamily)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onRequestDelete,
                colors = ButtonDefaults.textButtonColors(contentColor = palette.deleteRed),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("ลบมอด", "Delete Mod"), fontFamily = NotoSansThaiFontFamily)
            }
        },
    )
}
