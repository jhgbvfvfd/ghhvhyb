package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.kpzi.data.local.PlaySessionEntity
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.domain.model.SmapiStatus
import com.example.kpzi.notifications.PlaytimeFormatter
import com.example.kpzi.ui.components.IosSquircleIcon
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors
import com.example.ui.theme.LocalThemeState
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    detection: GameDetectionResult?,
    smapiStatus: SmapiStatus,
    userProfile: NexusUserProfile?,
    totalPlaytimeSeconds: Long,
    activePlaytimeStartMs: Long?,
    totalLaunchCount: Int,
    lastSessionSeconds: Long,
    longestSessionSeconds: Long,
    lastPlayedEpochMs: Long?,
    recentSessions: List<PlaySessionEntity>,
    onLaunchGame: () -> Unit,
    onOpenAccount: () -> Unit,
    onResetPlayStats: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    val themeState = LocalThemeState.current
    var showResetConfirm by remember { mutableStateOf(false) }

    var nowMs by remember(activePlaytimeStartMs) { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(activePlaytimeStartMs) {
        if (activePlaytimeStartMs != null) {
            while (true) {
                nowMs = System.currentTimeMillis()
                delay(1000L)
            }
        }
    }

    val activeElapsedSeconds = if (activePlaytimeStartMs != null) {
        ((nowMs - activePlaytimeStartMs).coerceAtLeast(0L)) / 1000L
    } else {
        0L
    }
    val liveTotalSeconds = totalPlaytimeSeconds + activeElapsedSeconds
    val liveBreakdown = PlaytimeFormatter.breakdown(liveTotalSeconds)
    val effectiveLongestSeconds = maxOf(longestSessionSeconds, activeElapsedSeconds)
    val hasAnyStats = liveTotalSeconds > 0L || totalLaunchCount > 0 || recentSessions.isNotEmpty()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (userProfile != null) "สวัสดี ${userProfile.username}" else "สวัสดีชาวฟาร์ม",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        text = "Kpzi Mod Manager",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ext.mutedText,
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.8.dp, ext.border, RoundedCornerShape(14.dp))
                            .testTag("home_theme_toggle_button")
                            .clickable(onClick = themeState.toggleTheme),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (themeState.isDark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                            contentDescription = if (themeState.isDark) "สลับเป็นโหมดสว่าง" else "สลับเป็นโหมดมืด",
                            tint = ext.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .border(0.8.dp, ext.border, RoundedCornerShape(14.dp))
                            .testTag("home_account_button")
                            .clickable(onClick = onOpenAccount),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = "บัญชี Nexus Mods",
                            tint = if (userProfile != null) MaterialTheme.colorScheme.primary else ext.mutedText,
                            modifier = Modifier.size(21.dp),
                        )
                        if (userProfile != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(7.dp)
                                    .size(7.dp)
                                    .background(ext.success, CircleShape),
                            )
                        }
                    }
                }
            }
        }

        // Pixel Art Farm Banner
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .clip(MaterialTheme.shapes.large)
                    .border(0.8.dp, ext.border, MaterialTheme.shapes.large),
            ) {
                Image(
                    painter = painterResource(R.drawable.img_farm_banner),
                    contentDescription = "แบนเนอร์ฟาร์ม Pixel Art",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xC01C2814), Color(0x14000000)),
                            ),
                        ),
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xCC42672D),
                    ) {
                        Text(
                            text = "Kpzi Mod Manager",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 3.dp),
                        )
                    }
                    Text(
                        text = "Stardew Valley",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFFFFF8E7),
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (detection?.isGameInstalled == true) {
                            "ตรวจพบตัวเกม v${detection.versionName ?: "-"} พร้อมเล่น"
                        } else {
                            "พร้อมจัดการมอดและตรวจสอบระบบเกม"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFEDE4D2),
                    )
                }
            }
        }

        // Clean Game Card + Launch Action
        item {
            KpziCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_app_icon),
                            contentDescription = null,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Column {
                            Text(
                                text = "Stardew Valley",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = "เกม: ${detection?.versionName ?: "ไม่พบตัวเกม"} • SMAPI: ${smapiStatus.smapiVersion ?: "-"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    StatusPill(
                        text = if (smapiStatus.isReadyToLaunch) "พร้อมใช้งาน" else "รอตัวเกม",
                        containerColor = if (smapiStatus.isReadyToLaunch) ext.successContainer else ext.warningContainer,
                        contentColor = if (smapiStatus.isReadyToLaunch) ext.success else ext.warning,
                        icon = if (smapiStatus.isReadyToLaunch) Icons.Rounded.CheckCircle else Icons.Rounded.WarningAmber,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = onLaunchGame,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("launch_game_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("เปิดเกม", style = MaterialTheme.typography.titleSmall)
                }
            }
        }

        // On-Device Play Statistics Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "สถิติการเล่นของคุณ",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    StatusPill(
                        text = if (activePlaytimeStartMs != null) "กำลังเล่น" else "บันทึกในเครื่อง",
                        containerColor = if (activePlaytimeStartMs != null) ext.successContainer else ext.infoContainer,
                        contentColor = if (activePlaytimeStartMs != null) ext.success else ext.info,
                        icon = if (activePlaytimeStartMs != null) Icons.Rounded.SportsEsports else Icons.Rounded.Schedule,
                    )
                }
                if (hasAnyStats) {
                    IconButton(
                        onClick = { showResetConfirm = true },
                        modifier = Modifier.testTag("home_reset_play_stats_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = "รีเซ็ตสถิติการเล่น",
                            tint = ext.mutedText,
                        )
                    }
                }
            }
        }

        item {
            KpziCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_play_stats_card"),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        IosSquircleIcon(
                            icon = Icons.Rounded.AccessTime,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.primary,
                            size = 40.dp,
                            iconSize = 21.dp,
                            cornerRadius = 12.dp,
                        )
                        Column {
                            Text(
                                text = "เวลาเล่นสะสมทั้งหมด",
                                style = MaterialTheme.typography.labelMedium,
                                color = ext.mutedText,
                            )
                            Text(
                                text = PlaytimeFormatter.formatThaiBreakdown(liveTotalSeconds),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                    }

                    if (activePlaytimeStartMs != null) {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = ext.successContainer,
                            border = BorderStroke(0.6.dp, ext.success.copy(alpha = 0.35f)),
                        ) {
                            Text(
                                text = PlaytimeFormatter.formatStopwatch(activeElapsedSeconds),
                                style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = ext.success,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                // 4-part unit breakdown pills (เดือน / วัน / ชม. / นาที)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayUnitTile(
                        value = liveBreakdown.months.toString(),
                        unit = "เดือน",
                        modifier = Modifier.weight(1f),
                    )
                    PlayUnitTile(
                        value = liveBreakdown.days.toString(),
                        unit = "วัน",
                        modifier = Modifier.weight(1f),
                    )
                    PlayUnitTile(
                        value = liveBreakdown.hours.toString(),
                        unit = "ชั่วโมง",
                        modifier = Modifier.weight(1f),
                    )
                    PlayUnitTile(
                        value = liveBreakdown.minutes.toString(),
                        unit = "นาที",
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 2x2 summary metrics grid
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayStatMetricTile(
                            icon = Icons.Rounded.PlayCircleOutline,
                            label = "เปิดเกมทั้งหมด",
                            value = "$totalLaunchCount ครั้ง",
                            tint = MaterialTheme.colorScheme.primary,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.weight(1f),
                        )
                        PlayStatMetricTile(
                            icon = Icons.Rounded.History,
                            label = "รอบล่าสุด",
                            value = when {
                                activePlaytimeStartMs != null -> PlaytimeFormatter.formatStopwatch(activeElapsedSeconds)
                                lastSessionSeconds > 0L -> PlaytimeFormatter.formatCompactThai(lastSessionSeconds)
                                else -> "-"
                            },
                            tint = ext.info,
                            containerColor = ext.infoContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        PlayStatMetricTile(
                            icon = Icons.Rounded.EmojiEvents,
                            label = "นานที่สุดต่อรอบ",
                            value = if (effectiveLongestSeconds > 0L) {
                                PlaytimeFormatter.formatCompactThai(effectiveLongestSeconds)
                            } else {
                                "-"
                            },
                            tint = ext.accent,
                            containerColor = ext.accentContainer,
                            modifier = Modifier.weight(1f),
                        )
                        PlayStatMetricTile(
                            icon = Icons.Rounded.CalendarMonth,
                            label = "เล่นล่าสุด",
                            value = when {
                                activePlaytimeStartMs != null -> "กำลังเล่นตอนนี้"
                                lastPlayedEpochMs != null -> formatPlayedDateTime(lastPlayedEpochMs)
                                else -> "ยังไม่เคยเปิดเกม"
                            },
                            tint = ext.success,
                            containerColor = ext.successContainer,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }

        // Recent Play Sessions History (stored in local Room DB)
        item {
            Text(
                text = "ประวัติการเล่นล่าสุด",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        if (recentSessions.isEmpty()) {
            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IosSquircleIcon(
                            icon = Icons.Rounded.SportsEsports,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = ext.mutedText,
                            size = 36.dp,
                            iconSize = 18.dp,
                        )
                        Text(
                            text = "ยังไม่มีประวัติการเล่น — กดปุ่มเปิดเกมด้านบนเพื่อเริ่มบันทึกสถิติไว้ในเครื่อง",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        } else {
            items(recentSessions.take(5), key = { "session_${it.id}" }) { session ->
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IosSquircleIcon(
                            icon = Icons.Rounded.SportsEsports,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            iconTint = MaterialTheme.colorScheme.primary,
                            size = 36.dp,
                            iconSize = 18.dp,
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = session.launchMode,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${formatPlayedDateTime(session.endedAtMs)} • เปิดใช้งาน ${session.activeModCount} มอด",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text = if (session.durationSeconds < 60L) {
                                    "${session.durationSeconds} วิ"
                                } else {
                                    PlaytimeFormatter.formatCompactThai(session.durationSeconds)
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("รีเซ็ตสถิติการเล่น?", style = MaterialTheme.typography.titleLarge) },
            text = {
                Text(
                    "เวลาเล่นสะสม จำนวนครั้งที่เปิดเกม และประวัติการเล่นที่บันทึกไว้ในเครื่องทั้งหมดจะถูกล้าง",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetPlayStats()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ext.error),
                ) {
                    Text("รีเซ็ต")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text("ยกเลิก")
                }
            },
        )
    }
}

@Composable
private fun PlayUnitTile(
    value: String,
    unit: String,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(0.6.dp, ext.border),
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = unit,
                style = MaterialTheme.typography.labelSmall,
                color = ext.mutedText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun PlayStatMetricTile(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    containerColor: Color,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(13.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
        border = BorderStroke(0.6.dp, ext.border),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IosSquircleIcon(
                icon = icon,
                containerColor = containerColor,
                iconTint = tint,
                size = 30.dp,
                iconSize = 16.dp,
                cornerRadius = 8.dp,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatPlayedDateTime(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
    return sdf.format(Date(epochMs))
}
