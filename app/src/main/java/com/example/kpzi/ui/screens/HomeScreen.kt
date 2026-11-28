package com.example.kpzi.ui.screens

import android.os.Environment
import android.os.StatFs
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpzi.data.local.PlaySessionEntity
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.domain.model.SmapiStatus
import com.example.kpzi.ui.i18n.LocalAppLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors
import com.example.ui.theme.LocalThemeState
import com.example.ui.theme.NotoSansThaiFontFamily
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    detection: GameDetectionResult?,
    smapiStatus: SmapiStatus,
    installedMods: List<InstalledMod>,
    userProfile: NexusUserProfile?,
    totalPlaytimeSeconds: Long,
    activePlaytimeStartMs: Long?,
    totalLaunchCount: Int,
    lastSessionSeconds: Long,
    longestSessionSeconds: Long,
    lastPlayedEpochMs: Long?,
    recentSessions: List<PlaySessionEntity>,
    onLaunchGame: () -> Unit,
    onOpenSmapiManager: () -> Unit,
    onRefreshStats: () -> Unit,
    onOpenAccount: () -> Unit,
    onResetPlayStats: () -> Unit,
    onToggleLanguage: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    val themeState = LocalThemeState.current
    val palette = remember(ext.isDark) { homePalette(ext.isDark) }
    val bgColor by animateColorAsState(palette.background, tween(260), label = "home_bg_anim")
    val currentLang = LocalAppLanguage.current

    var showResetConfirm by remember { mutableStateOf(false) }
    var isManualRefreshing by remember { mutableStateOf(false) }
    var lastManualRefreshMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(isManualRefreshing) {
        if (isManualRefreshing) {
            delay(550L)
            isManualRefreshing = false
        }
    }

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
    val effectiveLongestSeconds = maxOf(longestSessionSeconds, activeElapsedSeconds)

    val enabledCount by remember(installedMods) {
        derivedStateOf { installedMods.count { it.isEnabled } }
    }
    val disabledCount by remember(installedMods, enabledCount) {
        derivedStateOf { installedMods.size - enabledCount }
    }
    val totalModBytes by remember(installedMods) {
        derivedStateOf { installedMods.asSequence().sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    }
    var isByteUpdatePulsing by remember { mutableStateOf(false) }
    var lastObservedBytes by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(totalModBytes) {
        if (lastObservedBytes >= 0L && lastObservedBytes != totalModBytes) {
            isByteUpdatePulsing = true
            delay(950L)
            isByteUpdatePulsing = false
        }
        lastObservedBytes = totalModBytes
    }
    val isStorageSyncing = detection == null || isManualRefreshing || isByteUpdatePulsing
    val effectiveCapacityBytes = remember(detection?.totalStorageBytes) {
        val reported = detection?.totalStorageBytes
        if (reported != null && reported > 0L) {
            reported
        } else {
            runCatching { StatFs(Environment.getDataDirectory().path).totalBytes }.getOrDefault(0L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor),
    ) {
        HomeFarmBackgroundSilhouette(
            palette = palette,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(148.dp),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            HomeCompactHeader(
                userProfile = userProfile,
                langShortCode = currentLang.shortCode,
                isDark = themeState.isDark,
                palette = palette,
                onToggleLanguage = onToggleLanguage,
                onToggleTheme = themeState.toggleTheme,
                onOpenAccount = onOpenAccount,
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item(key = "home_banner") {
                    HomeGameBanner(detection = detection, palette = palette)
                }

                item(key = "home_smapi_card") {
                    if (detection == null) {
                        HomeDiagnosticsSkeleton(palette = palette)
                    } else {
                        HomeSmapiLaunchCard(
                            detection = detection,
                            smapiStatus = smapiStatus,
                            palette = palette,
                            onLaunchGame = onLaunchGame,
                            onOpenSmapiManager = onOpenSmapiManager,
                        )
                    }
                }

                item(key = "stats_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = tr("สถิติการเล่นของคุณ", "Your Play Statistics"),
                            fontFamily = NotoSansThaiFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.5.sp,
                            color = palette.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        val spinTransition = rememberInfiniteTransition(label = "refresh_spin")
                        val spinAngle by spinTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 360f,
                            animationSpec = infiniteRepeatable(tween(700, easing = LinearEasing)),
                            label = "spin_deg",
                        )

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = palette.surface,
                                border = BorderStroke(0.75.dp, palette.border),
                                modifier = Modifier
                                    .kpziPressClickable {
                                        isManualRefreshing = true
                                        lastManualRefreshMs = System.currentTimeMillis()
                                        onRefreshStats()
                                    }
                                    .testTag("home_refresh_stats_button"),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Refresh,
                                        contentDescription = tr("อัปเดตข้อมูล", "Refresh"),
                                        tint = palette.primary,
                                        modifier = Modifier
                                            .size(13.dp)
                                            .rotate(if (isManualRefreshing) spinAngle else 0f),
                                    )
                                    Text(
                                        text = if (lastManualRefreshMs > 0L) {
                                            tr("อัปเดต: ${formatPlayedDateTime(lastManualRefreshMs).substringAfter(" ")}", "Updated")
                                        } else {
                                            tr("อัปเดตล่าสุด", "Update Stats")
                                        },
                                        fontFamily = NotoSansThaiFontFamily,
                                        fontSize = 10.5.sp,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                    )
                                }
                            }
                            if (liveTotalSeconds > 0L || totalLaunchCount > 0 || recentSessions.isNotEmpty()) {
                                Surface(
                                    shape = CircleShape,
                                    color = palette.surface,
                                    border = BorderStroke(0.75.dp, palette.border),
                                    modifier = Modifier
                                        .size(26.dp)
                                        .kpziPressClickable { showResetConfirm = true }
                                        .testTag("home_reset_play_stats_button"),
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = tr("รีเซ็ตสถิติการเล่น", "Reset play statistics"),
                                            tint = palette.textSecondary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                item(key = "mod_stats_3col") {
                    HomeModStatsRow(
                        totalCount = installedMods.size,
                        enabledCount = enabledCount,
                        disabledCount = disabledCount,
                        palette = palette,
                    )
                }

                item(key = "storage_card") {
                    HomeModStorageCard(
                        totalModBytes = totalModBytes,
                        deviceCapacityBytes = effectiveCapacityBytes,
                        palette = palette,
                        isSyncing = isStorageSyncing,
                    )
                }

                item(key = "playtime_4col") {
                    HomePlaytimeFourColumnCard(
                        liveTotalSeconds = liveTotalSeconds,
                        activeElapsedSeconds = activeElapsedSeconds,
                        isPlayingNow = activePlaytimeStartMs != null,
                        lastSessionSeconds = lastSessionSeconds,
                        longestSessionSeconds = effectiveLongestSeconds,
                        lastPlayedEpochMs = lastPlayedEpochMs,
                        palette = palette,
                    )
                }
            }
        }
    }

    if (showResetConfirm) {
        AlertDialog(
            onDismissRequest = { showResetConfirm = false },
            containerColor = palette.surface,
            title = {
                Text(
                    text = tr("รีเซ็ตสถิติการเล่น?", "Reset Play Statistics?"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    color = palette.textPrimary,
                )
            },
            text = {
                Text(
                    text = tr(
                        "เวลาเล่นสะสม จำนวนครั้งที่เปิดเกม ($totalLaunchCount ครั้ง) และสถิติที่บันทึกไว้ในเครื่องทั้งหมดจะถูกล้าง",
                        "All accumulated playtime, launch count ($totalLaunchCount), and local play statistics will be cleared.",
                    ),
                    fontFamily = NotoSansThaiFontFamily,
                    color = palette.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showResetConfirm = false
                        onResetPlayStats()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = palette.error),
                ) {
                    Text(tr("รีเซ็ต", "Reset"), fontFamily = NotoSansThaiFontFamily, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetConfirm = false }) {
                    Text(tr("ยกเลิก", "Cancel"), fontFamily = NotoSansThaiFontFamily, color = palette.textSecondary)
                }
            },
        )
    }
}

@Composable
private fun HomeCompactHeader(
    userProfile: NexusUserProfile?,
    langShortCode: String,
    isDark: Boolean,
    palette: HomePalette,
    onToggleLanguage: () -> Unit,
    onToggleTheme: () -> Unit,
    onOpenAccount: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.primary,
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (userProfile != null) {
                    tr("สวัสดี ${userProfile.username}", "Hello, ${userProfile.username}")
                } else {
                    tr("สวัสดีชาวฟาร์ม", "Hello, Farmer")
                },
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 22.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "Kpzi Mod Manager",
                fontFamily = NotoSansThaiFontFamily,
                fontSize = 11.sp,
                lineHeight = 13.sp,
                color = palette.textSecondary,
                maxLines = 1,
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = 48.dp)
                    .kpziPressClickable(onClick = onToggleLanguage)
                    .testTag("home_language_toggle_button"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = palette.surface,
                    border = BorderStroke(0.75.dp, palette.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = langShortCode,
                            fontFamily = JetBrainsMonoFontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = palette.primary,
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = 48.dp)
                    .kpziPressClickable(onClick = onToggleTheme)
                    .testTag("home_theme_toggle_button"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = palette.surface,
                    border = BorderStroke(0.75.dp, palette.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        AnimatedContent(
                            targetState = isDark,
                            transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(180)) },
                            label = "theme_icon_anim",
                        ) { dark ->
                            Icon(
                                imageVector = if (dark) Icons.Rounded.LightMode else Icons.Rounded.DarkMode,
                                contentDescription = if (dark) {
                                    tr("สลับเป็นโหมดสว่าง", "Switch to Light Mode")
                                } else {
                                    tr("สลับเป็นโหมดมืด", "Switch to Dark Mode")
                                },
                                tint = if (dark) Color(0xFFFACC15) else Color(0xFFF59E0B),
                                modifier = Modifier.size(19.dp),
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .defaultMinSize(minWidth = 36.dp, minHeight = 48.dp)
                    .kpziPressClickable(onClick = onOpenAccount)
                    .testTag("home_account_button"),
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier.size(34.dp),
                    shape = CircleShape,
                    color = palette.surface,
                    border = BorderStroke(0.75.dp, palette.border),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Person,
                            contentDescription = tr("บัญชี Nexus Mods", "Nexus Mods Account"),
                            tint = if (userProfile != null) palette.primary else palette.textSecondary,
                            modifier = Modifier.size(19.dp),
                        )
                        if (userProfile != null) {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(palette.success),
                            )
                        }
                    }
                }
            }
        }
    }
}
