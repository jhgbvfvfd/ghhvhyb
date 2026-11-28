package com.example.kpzi.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.SmapiStatus
import com.example.kpzi.notifications.PlaytimeFormatter
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.NotoSansThaiFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HomeGameBanner(
    detection: GameDetectionResult?,
    palette: HomePalette,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(108.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(0.75.dp, palette.border, RoundedCornerShape(16.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.img_farm_banner),
            contentDescription = tr("แบนเนอร์ฟาร์ม Stardew Valley", "Stardew Valley Farm Banner"),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xD60B100A),
                            0.48f to Color(0x940B100A),
                            0.85f to Color(0x140B100A),
                        ),
                    ),
                ),
        )
        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0xCCFFFFFF).copy(alpha = 0.90f),
            ) {
                Text(
                    text = "Kpzi Mod Manager",
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = Color(0xFF18181B),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = "Stardew Valley",
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                lineHeight = 23.sp,
                color = Color.White,
            )
            Text(
                text = when {
                    detection == null -> tr("กำลังตรวจสอบตัวเกมในเครื่อง...", "Checking Stardew Valley installation...")
                    detection.isGameInstalled -> tr(
                        "จัดการมอดสำหรับ v${detection.versionName ?: "-"} ได้ง่ายขึ้น",
                        "Manage mods for v${detection.versionName ?: "-"} • Ready",
                    )
                    else -> tr(
                        "ยังไม่พบตัวเกม Stardew Valley ในเครื่อง",
                        "Stardew Valley not detected on this device",
                    )
                },
                fontFamily = NotoSansThaiFontFamily,
                fontSize = 11.5.sp,
                color = Color(0xFFF3F4F6),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun HomeSmapiLaunchCard(
    detection: GameDetectionResult?,
    smapiStatus: SmapiStatus,
    palette: HomePalette,
    onLaunchGame: () -> Unit,
    onOpenSmapiManager: () -> Unit,
) {
    val isReady = smapiStatus.isReadyToLaunch
    val needSmapiInstall = detection?.isGameInstalled == true && !smapiStatus.isInstalled

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(15.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
        shadowElevation = if (palette.isDark) 0.dp else 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(11.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PixelArtFarmerAvatar(palette = palette)

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = "Stardew Valley",
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.5.sp,
                        color = palette.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = smapiStatus.gameLoaderStateLabel,
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 11.5.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = if (isReady) palette.successSoftBg else palette.warningSoftBg,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isReady) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                            contentDescription = null,
                            tint = if (isReady) palette.success else palette.warning,
                            modifier = Modifier.size(13.dp),
                        )
                        Text(
                            text = when {
                                isReady -> tr("พร้อมเล่น", "Ready")
                                needSmapiInstall -> tr("ยังไม่ติดตั้ง SMAPI", "SMAPI Missing")
                                else -> tr("ไม่พร้อมเล่น", "Not Ready")
                            },
                            fontFamily = NotoSansThaiFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = if (isReady) palette.successSoftText else palette.warning,
                            maxLines = 1,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = if (needSmapiInstall) onOpenSmapiManager else onLaunchGame,
                    modifier = Modifier
                        .weight(0.58f)
                        .height(42.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("launch_game_button"),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = palette.primary,
                        contentColor = Color.White,
                    ),
                    contentPadding = PaddingValues(horizontal = 10.dp),
                ) {
                    Icon(
                        imageVector = if (needSmapiInstall) Icons.Rounded.Build else Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = when {
                            isReady -> tr("เล่นผ่าน SMAPI", "Play via SMAPI")
                            needSmapiInstall -> tr("ติดตั้ง SMAPI", "Install SMAPI")
                            else -> tr("เปิดเกม", "Launch Game")
                        },
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                OutlinedButton(
                    onClick = onOpenSmapiManager,
                    modifier = Modifier
                        .weight(0.42f)
                        .height(42.dp)
                        .minimumInteractiveComponentSize()
                        .testTag("home_manage_smapi_button"),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(0.75.dp, palette.border),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = palette.surfaceSubtle.copy(alpha = 0.55f),
                        contentColor = palette.textPrimary,
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = tr("จัดการ SMAPI", "Manage"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
internal fun HomeModStatsRow(
    totalCount: Int,
    enabledCount: Int,
    disabledCount: Int,
    palette: HomePalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        HomeModStatChipCard(
            title = tr("มอดทั้งหมด", "Total Mods"),
            countText = tr("$totalCount รายการ", "$totalCount mods"),
            icon = Icons.Rounded.Layers,
            iconTint = palette.primary,
            iconBg = palette.surface,
            cardBg = palette.primarySoftBg,
            borderColor = palette.primary.copy(alpha = 0.22f),
            palette = palette,
            modifier = Modifier
                .weight(1f)
                .testTag("home_stat_total_mods"),
        )
        HomeModStatChipCard(
            title = tr("เปิดใช้งาน", "Enabled"),
            countText = tr("$enabledCount รายการ", "$enabledCount mods"),
            icon = Icons.Rounded.PowerSettingsNew,
            iconTint = palette.success,
            iconBg = palette.successSoftBg,
            cardBg = palette.surface,
            borderColor = palette.border,
            palette = palette,
            modifier = Modifier
                .weight(1f)
                .testTag("home_stat_enabled_mods"),
        )
        HomeModStatChipCard(
            title = tr("ปิดใช้งาน", "Disabled"),
            countText = tr("$disabledCount รายการ", "$disabledCount mods"),
            icon = Icons.Rounded.PowerSettingsNew,
            iconTint = palette.textSecondary,
            iconBg = palette.surfaceSubtle,
            cardBg = palette.surface,
            borderColor = palette.border,
            palette = palette,
            modifier = Modifier
                .weight(1f)
                .testTag("home_stat_disabled_mods"),
        )
    }
}

@Composable
private fun HomeModStatChipCard(
    title: String,
    countText: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    cardBg: Color,
    borderColor: Color,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    val animBg by animateColorAsState(cardBg, tween(200), label = "stat_card_bg")
    Surface(
        modifier = modifier.height(54.dp),
        shape = RoundedCornerShape(13.dp),
        color = animBg,
        border = BorderStroke(0.75.dp, borderColor),
        shadowElevation = if (palette.isDark) 0.dp else 0.75.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(16.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    fontFamily = NotoSansThaiFontFamily,
                    fontSize = 10.sp,
                    lineHeight = 12.sp,
                    color = palette.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = countText,
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    lineHeight = 15.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun HomeModStorageCard(
    totalModBytes: Long,
    deviceCapacityBytes: Long,
    palette: HomePalette,
    isSyncing: Boolean = false,
) {
    val ratioTarget = if (deviceCapacityBytes > 0L) {
        (totalModBytes.toFloat() / deviceCapacityBytes.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val animatedRatio by animateFloatAsState(
        targetValue = ratioTarget,
        animationSpec = tween(280),
        label = "home_storage_ratio",
    )

    val syncPulseTransition = rememberInfiniteTransition(label = "storage_sync_pulse")
    val infinitePulse by syncPulseTransition.animateFloat(
        initialValue = 0.42f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "storage_pulse_alpha",
    )
    val fillAlpha by animateFloatAsState(
        targetValue = if (isSyncing) infinitePulse else 1f,
        animationSpec = tween(220),
        label = "storage_fill_alpha",
    )
    val trackPulseAlpha by animateFloatAsState(
        targetValue = if (isSyncing) (0.12f + (1f - infinitePulse) * 0.24f) else 0f,
        animationSpec = tween(220),
        label = "storage_track_pulse_alpha",
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_storage_card"),
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
        shadowElevation = if (palette.isDark) 0.dp else 0.75.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.primarySoftBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        tint = palette.primary,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = tr(
                        "พื้นที่ที่ใช้ ${formatMyModsSize(totalModBytes)} / ${formatDeviceCapacity(deviceCapacityBytes)}",
                        "Storage Used ${formatMyModsSize(totalModBytes)} / ${formatDeviceCapacity(deviceCapacityBytes)}",
                    ),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatStoragePercent(totalModBytes, deviceCapacityBytes),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.primary,
                    maxLines = 1,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(palette.progressTrack)
                    .testTag("home_storage_progress_bar"),
            ) {
                if (trackPulseAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(palette.primary.copy(alpha = trackPulseAlpha)),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedRatio)
                        .height(4.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.primary.copy(alpha = fillAlpha)),
                )
            }
        }
    }
}

@Composable
internal fun HomePlaytimeFourColumnCard(
    liveTotalSeconds: Long,
    activeElapsedSeconds: Long,
    isPlayingNow: Boolean,
    lastSessionSeconds: Long,
    longestSessionSeconds: Long,
    lastPlayedEpochMs: Long?,
    palette: HomePalette,
) {
    val isEnglish = isEnglishLanguage()
    val totalValue = when {
        liveTotalSeconds <= 0L -> tr("0 นาที", "0m")
        liveTotalSeconds < 60L -> tr("$liveTotalSeconds วิ", "${liveTotalSeconds}s")
        else -> PlaytimeFormatter.formatBreakdown(liveTotalSeconds, isEnglish = isEnglish)
    }
    val lastSessionValue = when {
        isPlayingNow -> PlaytimeFormatter.formatStopwatch(activeElapsedSeconds)
        lastSessionSeconds <= 0L -> "-"
        lastSessionSeconds < 60L -> tr("$lastSessionSeconds วิ", "${lastSessionSeconds}s")
        else -> PlaytimeFormatter.formatCompact(lastSessionSeconds, isEnglish = isEnglish)
    }
    val longestValue = when {
        longestSessionSeconds <= 0L -> "-"
        longestSessionSeconds < 60L -> tr("$longestSessionSeconds วิ", "${longestSessionSeconds}s")
        else -> PlaytimeFormatter.formatCompact(longestSessionSeconds, isEnglish = isEnglish)
    }
    val lastPlayedValue = when {
        isPlayingNow -> tr("กำลังเล่น", "Playing")
        lastPlayedEpochMs != null -> formatPlayedDateTime(lastPlayedEpochMs)
        else -> "-"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("home_play_stats_card"),
        shape = RoundedCornerShape(14.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
        shadowElevation = if (palette.isDark) 0.dp else 0.75.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaytimeMetricColumn(
                icon = Icons.Rounded.Schedule,
                iconTint = palette.blueClock,
                label = tr("เวลาเล่นรวม", "Total Play"),
                value = totalValue,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                thickness = 0.75.dp,
                color = palette.border,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
            )
            PlaytimeMetricColumn(
                icon = Icons.Rounded.SportsEsports,
                iconTint = palette.primary,
                label = tr("รอบล่าสุด", "Last Session"),
                value = lastSessionValue,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                thickness = 0.75.dp,
                color = palette.border,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
            )
            PlaytimeMetricColumn(
                icon = Icons.Rounded.EmojiEvents,
                iconTint = palette.goldTrophy,
                label = tr("นานที่สุด", "Longest"),
                value = longestValue,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            VerticalDivider(
                thickness = 0.75.dp,
                color = palette.border,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(vertical = 4.dp),
            )
            PlaytimeMetricColumn(
                icon = Icons.Rounded.CalendarMonth,
                iconTint = palette.primary,
                label = tr("เล่นล่าสุด", "Last Played"),
                value = lastPlayedValue,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaytimeMetricColumn(
    icon: ImageVector,
    iconTint: Color,
    label: String,
    value: String,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = label,
            fontFamily = NotoSansThaiFontFamily,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            color = palette.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = value,
            fontFamily = NotoSansThaiFontFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 11.5.sp,
            lineHeight = 14.sp,
            color = palette.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

internal fun formatPlayedDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
