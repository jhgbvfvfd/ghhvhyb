package com.example.kpzi.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.domain.model.DownloadTask
import com.example.kpzi.ui.components.IosSegmentedControl
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.ModCoverThumb
import com.example.kpzi.ui.components.StatusPill
import com.example.kpzi.ui.components.formatEta
import com.example.kpzi.ui.components.formatFileSize
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.LocalKpziColors
import kotlin.math.roundToInt

@Composable
fun DownloadsScreen(
    downloads: List<DownloadTask>,
    onPause: (String) -> Unit,
    onResumeOrRetry: (String) -> Unit,
    onCancel: (String) -> Unit,
    onInstallCompleted: (DownloadTask) -> Unit,
    onRemoveHistory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Active, 1 = History

    val activeList = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.QUEUED ||
                it.status == DownloadStatus.DOWNLOADING ||
                it.status == DownloadStatus.PAUSED ||
                it.status == DownloadStatus.VERIFYING
        }
    }
    val historyList = remember(downloads) {
        downloads.filter {
            it.status == DownloadStatus.COMPLETED ||
                it.status == DownloadStatus.FAILED ||
                it.status == DownloadStatus.CANCELLED
        }
    }

    val displayed = if (selectedTab == 0) activeList else historyList

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = tr("ดาวน์โหลด", "Downloads"),
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )

        IosSegmentedControl(
            options = listOf(
                tr("กำลังดาวน์โหลด (${activeList.size})", "Active (${activeList.size})"),
                tr("เสร็จสิ้น / ประวัติ (${historyList.size})", "Completed / History (${historyList.size})"),
            ),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        if (displayed.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_mascot_chick),
                            contentDescription = null,
                            modifier = Modifier
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = if (selectedTab == 0) {
                                tr("ไม่มีรายการที่กำลังดาวน์โหลด", "No active downloads")
                            } else {
                                tr("ยังไม่มีประวัติดาวน์โหลด", "No download history yet")
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = tr(
                                "เลือกดาวน์โหลดมอดจากคลังมอด หรือใช้ NXM Link จาก Nexus Mods",
                                "Download mods from the Catalog or use a Nexus Mods NXM link.",
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayed, key = { it.id }) { task ->
                    DownloadItemCard(
                        task = task,
                        onPause = { onPause(task.id) },
                        onResumeOrRetry = { onResumeOrRetry(task.id) },
                        onCancel = { onCancel(task.id) },
                        onInstall = { onInstallCompleted(task) },
                        onRemove = { onRemoveHistory(task.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    task: DownloadTask,
    onPause: () -> Unit,
    onResumeOrRetry: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
    onRemove: () -> Unit,
) {
    val ext = LocalKpziColors.current
    val isEnglish = isEnglishLanguage()
    val percent = (task.progressFraction * 100f).roundToInt()

    KpziCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ModCoverThumb(
                pictureUrl = task.pictureUrl,
                contentDescription = task.modName,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = task.modName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${task.fileName} • v${task.version}",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (task.status == DownloadStatus.DOWNLOADING || task.status == DownloadStatus.PAUSED) {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }

        if (task.status == DownloadStatus.DOWNLOADING ||
            task.status == DownloadStatus.PAUSED ||
            task.status == DownloadStatus.QUEUED ||
            task.status == DownloadStatus.VERIFYING
        ) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { task.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(RoundedCornerShape(50)),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val speedText = if (task.speedBytesPerSec > 0L) "${formatFileSize(task.speedBytesPerSec)}/s" else task.status.label(isEnglish)
                val etaText = formatEta(task.etaSeconds, isEnglish = isEnglish)
                val sizeInfo = "${formatFileSize(task.downloadedBytes)} / ${if (task.totalBytes > 0L) formatFileSize(task.totalBytes) else "?"}"
                Text(
                    text = listOf(sizeInfo, speedText, etaText).filter { it.isNotBlank() }.joinToString(" • "),
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                    modifier = Modifier.weight(1f),
                )
                Row {
                    if (task.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = onPause, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.Pause, contentDescription = tr("หยุดชั่วคราว", "Pause"))
                        }
                    } else if (task.status == DownloadStatus.PAUSED) {
                        IconButton(onClick = onResumeOrRetry, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = tr("ดาวน์โหลดต่อ", "Resume"))
                        }
                    }
                    IconButton(onClick = onCancel, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.Close, contentDescription = tr("ยกเลิก", "Cancel"))
                    }
                }
            }
        } else {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (task.status) {
                    DownloadStatus.COMPLETED -> {
                        StatusPill(
                            text = tr(
                                "ตรวจไฟล์ผ่าน (${formatFileSize(task.totalBytes)})",
                                "Verified (${formatFileSize(task.totalBytes)})",
                            ),
                            containerColor = ext.successContainer,
                            contentColor = ext.success,
                            icon = Icons.Rounded.CheckCircle,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Button(
                                onClick = onInstall,
                                shape = RoundedCornerShape(11.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("install_downloaded_${task.id}"),
                            ) {
                                Icon(Icons.Rounded.InstallMobile, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(tr("ติดตั้งทันที", "Install Now"), style = MaterialTheme.typography.labelMedium)
                            }
                            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = tr("ลบประวัติ", "Remove"),
                                    tint = ext.mutedText,
                                )
                            }
                        }
                    }
                    else -> {
                        StatusPill(
                            text = if (task.isCorrupted) {
                                tr("ไฟล์เสียหาย", "File Corrupted")
                            } else {
                                task.errorMessage ?: task.status.label(isEnglish)
                            },
                            containerColor = ext.errorContainer,
                            contentColor = ext.error,
                            icon = Icons.Rounded.WarningAmber,
                            modifier = Modifier.weight(1f),
                        )
                        Row {
                            IconButton(onClick = onResumeOrRetry, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Rounded.Refresh, contentDescription = tr("ลองใหม่", "Retry"))
                            }
                            IconButton(onClick = onRemove, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = tr("ลบประวัติ", "Remove"),
                                    tint = ext.mutedText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
