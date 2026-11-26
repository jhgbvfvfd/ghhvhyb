package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpzi.domain.model.LogEntry
import com.example.kpzi.domain.model.LogLevel
import com.example.kpzi.ui.components.IosSquircleIcon
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors

private enum class LogFilterOption(val label: String) {
    ALL("ทั้งหมด"),
    ERRORS("ERROR"),
    WARN("WARN"),
    INFO("INFO"),
    DEBUG("DEBUG"),
    TRACE("TRACE"),
}

@Composable
fun LogsScreen(
    logs: List<LogEntry>,
    activeLogSource: String,
    isReloading: Boolean,
    onReloadLogs: () -> Unit,
    onImportLog: () -> Unit,
    onShareLogs: () -> Unit,
    onClearLogs: () -> Unit,
    onCopyMessage: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    val clipboard = LocalClipboardManager.current
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(LogFilterOption.ALL) }

    val errorCount = remember(logs) {
        logs.count { it.level == LogLevel.ERROR || it.level == LogLevel.ALERT }
    }
    val warnCount = remember(logs) {
        logs.count { it.level == LogLevel.WARN }
    }

    val filtered = remember(logs, selectedFilter, searchQuery) {
        val byLevel = when (selectedFilter) {
            LogFilterOption.ALL -> logs
            LogFilterOption.ERRORS -> logs.filter { it.level == LogLevel.ERROR || it.level == LogLevel.ALERT }
            LogFilterOption.WARN -> logs.filter { it.level == LogLevel.WARN }
            LogFilterOption.INFO -> logs.filter { it.level == LogLevel.INFO }
            LogFilterOption.DEBUG -> logs.filter { it.level == LogLevel.DEBUG }
            LogFilterOption.TRACE -> logs.filter { it.level == LogLevel.TRACE }
        }
        val q = searchQuery.trim()
        if (q.isEmpty()) {
            byLevel
        } else {
            byLevel.filter {
                it.message.contains(q, ignoreCase = true) ||
                    it.source.contains(q, ignoreCase = true) ||
                    it.level.tag.contains(q, ignoreCase = true)
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Header + Active file info + Quick action squircles
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f),
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("subscreen_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "ย้อนกลับ")
                    }
                }
                Column {
                    Text("ตรวจสอบ Log", style = MaterialTheme.typography.headlineMedium)
                    Text(
                        text = "$activeLogSource • ${logs.size} รายการ",
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.mutedText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onReloadLogs,
                    modifier = Modifier.testTag("log_reload_button"),
                ) {
                    if (isReloading) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        IosSquircleIcon(
                            icon = Icons.Rounded.Refresh,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            iconTint = MaterialTheme.colorScheme.onSurface,
                            size = 34.dp,
                            iconSize = 18.dp,
                            cornerRadius = 11.dp,
                        )
                    }
                }
                IconButton(
                    onClick = onImportLog,
                    modifier = Modifier.testTag("log_import_button"),
                ) {
                    IosSquircleIcon(
                        icon = Icons.Rounded.UploadFile,
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        iconTint = MaterialTheme.colorScheme.primary,
                        size = 34.dp,
                        iconSize = 18.dp,
                        cornerRadius = 11.dp,
                    )
                }
                IconButton(
                    onClick = onShareLogs,
                    modifier = Modifier.testTag("log_share_button"),
                ) {
                    IosSquircleIcon(
                        icon = Icons.Rounded.Share,
                        containerColor = ext.infoContainer,
                        iconTint = ext.info,
                        size = 34.dp,
                        iconSize = 18.dp,
                        cornerRadius = 11.dp,
                    )
                }
                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.testTag("log_clear_button"),
                ) {
                    IosSquircleIcon(
                        icon = Icons.Rounded.DeleteOutline,
                        containerColor = ext.errorContainer,
                        iconTint = ext.error,
                        size = 34.dp,
                        iconSize = 18.dp,
                        cornerRadius = 11.dp,
                    )
                }
            }
        }

        // iOS Spotlight-style search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("log_search_input"),
            placeholder = {
                Text(
                    text = "ค้นหาใน Log (ชื่อมอด, Error, ข้อความ)...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.mutedText,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = ext.mutedText,
                    modifier = Modifier.size(20.dp),
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = "ล้างคำค้นหา",
                            tint = ext.mutedText,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = Color.Transparent,
            ),
        )

        // Troubleshooting quick diagnostics strip when errors or warnings exist
        if (errorCount > 0 || warnCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (errorCount > 0) {
                    StatusPill(
                        text = "พบ Error $errorCount รายการ",
                        containerColor = ext.errorContainer,
                        contentColor = ext.error,
                        icon = Icons.Rounded.ErrorOutline,
                        modifier = Modifier.clickable { selectedFilter = LogFilterOption.ERRORS },
                    )
                }
                if (warnCount > 0) {
                    StatusPill(
                        text = "คำเตือน $warnCount รายการ",
                        containerColor = ext.warningContainer,
                        contentColor = ext.warning,
                        icon = Icons.Rounded.WarningAmber,
                        modifier = Modifier.clickable { selectedFilter = LogFilterOption.WARN },
                    )
                }
            }
        } else {
            Spacer(Modifier.height(6.dp))
        }

        // Level filter chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(LogFilterOption.entries) { option ->
                val count = when (option) {
                    LogFilterOption.ALL -> logs.size
                    LogFilterOption.ERRORS -> errorCount
                    LogFilterOption.WARN -> warnCount
                    LogFilterOption.INFO -> logs.count { it.level == LogLevel.INFO }
                    LogFilterOption.DEBUG -> logs.count { it.level == LogLevel.DEBUG }
                    LogFilterOption.TRACE -> logs.count { it.level == LogLevel.TRACE }
                }
                val selected = selectedFilter == option
                FilterChip(
                    selected = selected,
                    onClick = { selectedFilter = option },
                    label = {
                        Text(
                            text = "${option.label} ($count)",
                            style = MaterialTheme.typography.labelMedium,
                        )
                    },
                    shape = RoundedCornerShape(50),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                    border = BorderStroke(
                        0.8.dp,
                        if (selected) MaterialTheme.colorScheme.primary else ext.border,
                    ),
                )
            }
        }

        if (filtered.isEmpty()) {
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        IosSquircleIcon(
                            icon = Icons.Rounded.ReceiptLong,
                            containerColor = ext.warningContainer,
                            iconTint = ext.warning,
                            size = 52.dp,
                            iconSize = 28.dp,
                            cornerRadius = 16.dp,
                        )
                        Text(
                            text = if (searchQuery.isNotBlank()) "ไม่พบ Log ที่ตรงกับ \"$searchQuery\"" else "ไม่มีรายการ Log ในตัวกรองนี้",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "เลือกไฟล์ SMAPI-latest.txt จากเครื่องเพื่อตรวจสอบ Error และวิเคราะห์ปัญหาของมอด",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = onImportLog, shape = RoundedCornerShape(12.dp)) {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("เลือก SMAPI Log")
                            }
                            OutlinedButton(
                                onClick = onReloadLogs,
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(0.8.dp, ext.border),
                            ) {
                                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("รีเฟรช")
                            }
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("logs_list"),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filtered, key = { it.id }) { entry ->
                    val (levelColor, levelBg) = when (entry.level) {
                        LogLevel.ERROR, LogLevel.ALERT -> ext.error to ext.errorContainer
                        LogLevel.WARN -> ext.warning to ext.warningContainer
                        LogLevel.INFO -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.primaryContainer
                        else -> ext.mutedText to MaterialTheme.colorScheme.surfaceVariant
                    }
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val text = "[${entry.timestamp} ${entry.level.tag} ${entry.source}] ${entry.message}"
                                clipboard.setText(AnnotatedString(text))
                                onCopyMessage("คัดลอกบรรทัด Log แล้ว")
                            },
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(
                            0.8.dp,
                            if (entry.level == LogLevel.ERROR || entry.level == LogLevel.ALERT) {
                                ext.error.copy(alpha = 0.45f)
                            } else {
                                ext.border
                            },
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.weight(1f),
                                ) {
                                    StatusPill(
                                        text = entry.level.tag,
                                        containerColor = levelBg,
                                        contentColor = levelColor,
                                    )
                                    Text(
                                        text = entry.source,
                                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Text(
                                        text = entry.timestamp,
                                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                        color = ext.mutedText,
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.ContentCopy,
                                        contentDescription = "คัดลอก",
                                        tint = ext.mutedText,
                                        modifier = Modifier.size(14.dp),
                                    )
                                }
                            }
                            Text(
                                text = entry.message,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                                color = if (entry.level == LogLevel.ERROR || entry.level == LogLevel.ALERT) {
                                    ext.error
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
