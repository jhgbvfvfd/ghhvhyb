package com.example.kpzi.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.InsertDriveFile
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.LayersClear
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.NotoSansThaiFontFamily
import java.util.Locale

@Composable
internal fun MyModsCompactHeader(
    palette: MyModsPalette,
    isSearchOpen: Boolean,
    onToggleSearch: () -> Unit,
    onImportZip: () -> Unit,
    onImportFolder: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Small game icon squircle
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(
                        listOf(
                            palette.primarySoftBg,
                            palette.primary.copy(alpha = 0.22f),
                        ),
                    ),
                )
                .border(0.75.dp, palette.border, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(8.dp))

        // Compact Title + Subtitle
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tr("มอดของฉัน", "My Mods"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 22.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tr("จัดการมอดที่ติดตั้งในเกมของคุณ", "Manage mods installed in your game"),
                fontFamily = NotoSansThaiFontFamily,
                fontSize = 10.5.sp,
                lineHeight = 13.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Compact Search icon button (48dp touch area)
        IconButton(
            onClick = onToggleSearch,
            modifier = Modifier.testTag("mymods_search_toggle"),
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (isSearchOpen) palette.primary else palette.surface)
                    .border(0.75.dp, if (isSearchOpen) palette.primary else palette.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (isSearchOpen) Icons.Rounded.Close else Icons.Rounded.Search,
                    contentDescription = tr("ค้นหามอด", "Search mods"),
                    tint = if (isSearchOpen) Color.White else palette.textPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        HeaderActionChip(
            label = "ZIP",
            icon = Icons.Rounded.FolderZip,
            palette = palette,
            onClick = onImportZip,
            modifier = Modifier.testTag("mymods_import_zip"),
        )

        Spacer(Modifier.width(4.dp))

        HeaderActionChip(
            label = tr("โฟลเดอร์", "Folder"),
            icon = Icons.Rounded.CreateNewFolder,
            palette = palette,
            onClick = onImportFolder,
            modifier = Modifier.testTag("mymods_import_folder"),
        )
    }
}

@Composable
private fun HeaderActionChip(
    label: String,
    icon: ImageVector,
    palette: MyModsPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = palette.surface,
            border = BorderStroke(0.75.dp, palette.border),
            shadowElevation = if (palette.isDark) 0.dp else 0.75.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = label,
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
internal fun StardewGameBanner(
    totalModsCount: Int,
    palette: MyModsPalette,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(0.75.dp, palette.border, RoundedCornerShape(12.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.img_mymods_banner),
            contentDescription = "Stardew Valley",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xD61A1033),
                            Color(0x9E2B1954),
                            Color(0x181A1033),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Eco,
                    contentDescription = null,
                    tint = Color(0xFFDDD6FE),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = "Stardew Valley",
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.5.sp,
                    lineHeight = 18.sp,
                    color = Color.White,
                )
            }
            Text(
                text = tr(
                    "มอดที่ติดตั้งทั้งหมด $totalModsCount รายการ",
                    "$totalModsCount installed mods total",
                ),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = Color.White.copy(alpha = 0.90f),
            )
        }
    }
}

@Composable
internal fun ModUsageStatsRow(
    enabledCount: Int,
    enabledBytes: Long,
    disabledCount: Int,
    disabledBytes: Long,
    totalModBytes: Long,
    deviceCapacityBytes: Long,
    palette: MyModsPalette,
) {
    val storageFractionTarget = if (deviceCapacityBytes > 0L) {
        (totalModBytes.toFloat() / deviceCapacityBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedStorageFraction by animateFloatAsState(
        targetValue = storageFractionTarget,
        animationSpec = tween(250),
        label = "mymods_storage_progress",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Card 1: Enabled
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(68.dp)
                .testTag("stat_enabled_card"),
            shape = RoundedCornerShape(12.dp),
            color = palette.primarySoftBg,
            border = BorderStroke(0.75.dp, palette.primary.copy(alpha = 0.18f)),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Layers,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = tr("เปิดใช้งาน", "Enabled"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "$enabledCount",
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = tr("รายการ", "mods"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                    )
                }
                Text(
                    text = formatMyModsSize(enabledBytes),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    color = palette.primarySoftText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Card 2: Disabled
        Surface(
            modifier = Modifier
                .weight(1f)
                .height(68.dp)
                .testTag("stat_disabled_card"),
            shape = RoundedCornerShape(12.dp),
            color = palette.surface,
            border = BorderStroke(0.75.dp, palette.border),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LayersClear,
                        contentDescription = null,
                        tint = palette.textSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = tr("ปิดใช้งาน", "Disabled"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Row(
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "$disabledCount",
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        lineHeight = 17.sp,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = tr("รายการ", "mods"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 10.sp,
                        color = palette.textSecondary,
                    )
                }
                Text(
                    text = formatMyModsSize(disabledBytes),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.5.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // Card 3: Storage Used
        Surface(
            modifier = Modifier
                .weight(1.12f)
                .height(68.dp)
                .testTag("stat_storage_card"),
            shape = RoundedCornerShape(12.dp),
            color = palette.surface,
            border = BorderStroke(0.75.dp, palette.border),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.InsertDriveFile,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = tr("พื้นที่ที่ใช้", "Storage"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.5.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    Text(
                        text = "${formatMyModsSize(totalModBytes)} / ${formatDeviceCapacity(deviceCapacityBytes)}",
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.5.sp,
                        lineHeight = 13.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatStoragePercent(totalModBytes, deviceCapacityBytes),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        color = palette.primary,
                        maxLines = 1,
                        modifier = Modifier.align(Alignment.End),
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.progressTrack),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(
                                fraction = if (totalModBytes > 0L) {
                                    animatedStorageFraction.coerceAtLeast(0.05f)
                                } else {
                                    0f
                                },
                            )
                            .height(3.5.dp)
                            .clip(RoundedCornerShape(50))
                            .background(palette.primary),
                    )
                }
            }
        }
    }
}

@Composable
internal fun ModFilterPillsRow(
    selected: MyModsFilterOption,
    totalCount: Int,
    enabledCount: Int,
    disabledCount: Int,
    isEnglish: Boolean,
    palette: MyModsPalette,
    onSelect: (MyModsFilterOption) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val entries = listOf(
            Triple(MyModsFilterOption.ALL, totalCount, Icons.Rounded.ViewModule),
            Triple(MyModsFilterOption.ENABLED, enabledCount, Icons.Rounded.Layers),
            Triple(MyModsFilterOption.DISABLED, disabledCount, Icons.Rounded.LayersClear),
        )
        entries.forEach { (option, count, icon) ->
            val isSelected = selected == option
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) palette.primary else palette.surface,
                animationSpec = tween(180),
                label = "filter_bg_${option.name}",
            )
            val fgColor by animateColorAsState(
                targetValue = if (isSelected) Color.White else palette.textPrimary,
                animationSpec = tween(180),
                label = "filter_fg_${option.name}",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.Tab, onClick = { onSelect(option) })
                    .testTag("filter_pill_${option.name.lowercase(Locale.ROOT)}"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    color = bgColor,
                    border = BorderStroke(0.75.dp, if (isSelected) palette.primary else palette.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = if (isSelected) Color.White else palette.textSecondary,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "${option.label(isEnglish)} ($count)",
                            fontFamily = NotoSansThaiFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = fgColor,
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
internal fun ModManagementToolbar(
    sortOption: MyModsSortOption,
    isGridView: Boolean,
    showSortMenu: Boolean,
    isEnglish: Boolean,
    palette: MyModsPalette,
    onOpenSortMenu: () -> Unit,
    onDismissSortMenu: () -> Unit,
    onSelectSort: (MyModsSortOption) -> Unit,
    onSetGridView: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Box(
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .clip(RoundedCornerShape(50))
                    .clickable(role = Role.DropdownList, onClick = onOpenSortMenu)
                    .testTag("mymods_sort_dropdown"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = palette.surface,
                    border = BorderStroke(0.75.dp, palette.border),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapVert,
                            contentDescription = null,
                            tint = palette.textPrimary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = tr(
                                "เรียงตาม: ${sortOption.label(false)}",
                                "Sort: ${sortOption.label(true)}",
                            ),
                            fontFamily = NotoSansThaiFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 11.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                        )
                        Icon(
                            imageVector = Icons.Rounded.KeyboardArrowDown,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = showSortMenu,
                onDismissRequest = onDismissSortMenu,
                containerColor = palette.surface,
            ) {
                MyModsSortOption.entries.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = option.label(isEnglish),
                                fontFamily = NotoSansThaiFontFamily,
                                fontWeight = if (option == sortOption) FontWeight.Bold else FontWeight.Normal,
                                color = if (option == sortOption) palette.primary else palette.textPrimary,
                            )
                        },
                        onClick = { onSelectSort(option) },
                    )
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = palette.surface,
            border = BorderStroke(0.75.dp, palette.border),
        ) {
            Row(
                modifier = Modifier.padding(2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                ViewToggleButton(
                    selected = !isGridView,
                    icon = Icons.AutoMirrored.Rounded.ViewList,
                    contentDesc = tr("แสดงแบบรายการ", "List view"),
                    palette = palette,
                    onClick = { onSetGridView(false) },
                    modifier = Modifier.testTag("mymods_view_list"),
                )
                ViewToggleButton(
                    selected = isGridView,
                    icon = Icons.Rounded.GridView,
                    contentDesc = tr("แสดงแบบตาราง", "Grid view"),
                    palette = palette,
                    onClick = { onSetGridView(true) },
                    modifier = Modifier.testTag("mymods_view_grid"),
                )
            }
        }
    }
}

@Composable
private fun ViewToggleButton(
    selected: Boolean,
    icon: ImageVector,
    contentDesc: String,
    palette: MyModsPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg by animateColorAsState(
        targetValue = if (selected) palette.primary else Color.Transparent,
        animationSpec = tween(180),
        label = "view_toggle_bg",
    )
    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(50))
            .clickable(role = Role.Tab, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(50),
            color = bg,
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDesc,
                    tint = if (selected) Color.White else palette.textSecondary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun MyModsEmptyStateCard(
    hasAnyMods: Boolean,
    searchQuery: String,
    filterOption: MyModsFilterOption,
    palette: MyModsPalette,
    onImportZip: () -> Unit,
    onResetFilters: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.primarySoftBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Layers,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(20.dp),
                )
            }

            Text(
                text = when {
                    searchQuery.isNotBlank() -> tr("ไม่พบมอดที่ตรงกับ \"$searchQuery\"", "No mods match \"$searchQuery\"")
                    hasAnyMods && filterOption == MyModsFilterOption.DISABLED -> tr("ไม่มีมอดที่ปิดใช้งาน", "No disabled mods")
                    hasAnyMods && filterOption == MyModsFilterOption.ENABLED -> tr("ไม่มีมอดที่เปิดใช้งาน", "No enabled mods")
                    else -> tr("ยังไม่มีมอดที่ติดตั้ง", "No mods installed yet")
                },
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
            )

            Text(
                text = if (hasAnyMods) {
                    tr("ลองเปลี่ยนตัวกรองหรือล้างคำค้นหาเพื่อดูรายการทั้งหมด", "Try changing the filter or clearing the search query.")
                } else {
                    tr("นำเข้าไฟล์ .zip หรือเลือกโฟลเดอร์มอดที่มี manifest.json เพื่อเริ่มต้น", "Import a .zip file or select a mod folder containing manifest.json.")
                },
                fontFamily = NotoSansThaiFontFamily,
                fontSize = 11.5.sp,
                color = palette.textSecondary,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(2.dp))

            if (hasAnyMods) {
                OutlinedButton(
                    onClick = onResetFilters,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(0.8.dp, palette.border),
                ) {
                    Text(
                        text = tr("แสดงทั้งหมด", "Show All Mods"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            } else {
                Button(
                    onClick = onImportZip,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = palette.primary),
                ) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = tr("นำเข้าไฟล์ ZIP", "Import ZIP File"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}
