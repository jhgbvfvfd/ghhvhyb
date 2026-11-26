package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Loop
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpzi.engine.loader.ConflictDetectionReport
import com.example.kpzi.engine.loader.ConflictSeverity
import com.example.kpzi.engine.loader.ConflictType
import com.example.kpzi.engine.loader.ModConflictIssue
import com.example.kpzi.ui.components.IosSquircleIcon
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.StatusPill
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class ConflictFilter(val label: String) {
    ALL("ทั้งหมด"),
    LOOPS("Dependency Loop"),
    VERSIONS("เวอร์ชันไม่ตรง"),
    DEPENDENCIES("ขาดมอด / ปิดอยู่"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConflictDetectionScreen(
    report: ConflictDetectionReport?,
    isScanning: Boolean,
    onRunScan: () -> Unit,
    onEnableDisabledMod: (String) -> Unit,
    onOpenMyMods: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    var selectedFilter by remember { mutableStateOf(ConflictFilter.ALL) }
    val dateFormat = remember { SimpleDateFormat("HH:mm:ss", Locale.getDefault()) }

    val issues = report?.issues.orEmpty()
    val filteredIssues = remember(issues, selectedFilter) {
        when (selectedFilter) {
            ConflictFilter.ALL -> issues
            ConflictFilter.LOOPS -> issues.filter { it.type == ConflictType.DEPENDENCY_LOOP }
            ConflictFilter.VERSIONS -> issues.filter {
                it.type == ConflictType.VERSION_MISMATCH || it.type == ConflictType.SMAPI_VERSION_MISMATCH
            }
            ConflictFilter.DEPENDENCIES -> issues.filter {
                it.type == ConflictType.MISSING_DEPENDENCY ||
                    it.type == ConflictType.DISABLED_DEPENDENCY ||
                    it.type == ConflictType.DUPLICATE_UNIQUE_ID
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("ตรวจจับความขัดแย้งมอด", style = MaterialTheme.typography.titleLarge) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("conflict_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "ย้อนกลับ")
                    }
                },
                actions = {
                    IconButton(onClick = onRunScan, modifier = Modifier.testTag("conflict_refresh_icon")) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = "สแกนใหม่")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Overview Summary Card
            item {
                KpziCard(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f),
                        ) {
                            val hasIssues = report?.hasIssues == true
                            IosSquircleIcon(
                                icon = if (hasIssues) Icons.Rounded.WarningAmber else Icons.Rounded.Shield,
                                containerColor = if (hasIssues) ext.errorContainer else ext.successContainer,
                                iconTint = if (hasIssues) ext.error else ext.success,
                                size = 42.dp,
                                iconSize = 23.dp,
                                cornerRadius = 13.dp,
                            )
                            Column {
                                Text("วิเคราะห์ manifest.json จากฐานข้อมูล", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    text = if (report != null) {
                                        "สแกนแล้ว ${report.scannedModCount} มอด • ล่าสุด ${dateFormat.format(Date(report.scannedAtMs))}"
                                    } else {
                                        "กำลังเตรียมตรวจสอบมอดที่ติดตั้งในเครื่อง"
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                            }
                        }
                        if (report != null) {
                            StatusPill(
                                text = if (report.hasIssues) "พบ ${report.issues.size} ปัญหา" else "ปกติ",
                                containerColor = if (report.hasIssues) ext.errorContainer else ext.successContainer,
                                contentColor = if (report.hasIssues) ext.error else ext.success,
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        ConflictCounterTile(
                            label = "วนลูป",
                            count = report?.loopCount ?: 0,
                            highlightColor = if ((report?.loopCount ?: 0) > 0) ext.error else null,
                            modifier = Modifier.weight(1f),
                        )
                        ConflictCounterTile(
                            label = "เวอร์ชันผิด",
                            count = report?.versionMismatchCount ?: 0,
                            highlightColor = if ((report?.versionMismatchCount ?: 0) > 0) ext.error else null,
                            modifier = Modifier.weight(1f),
                        )
                        ConflictCounterTile(
                            label = "ขาดมอด",
                            count = report?.missingDependencyCount ?: 0,
                            highlightColor = if ((report?.missingDependencyCount ?: 0) > 0) ext.warning else null,
                            modifier = Modifier.weight(1f),
                        )
                        ConflictCounterTile(
                            label = "ปิด/ซ้ำ",
                            count = (report?.disabledDependencyCount ?: 0) + (report?.duplicateIdCount ?: 0),
                            highlightColor = if (((report?.disabledDependencyCount ?: 0) + (report?.duplicateIdCount ?: 0)) > 0) ext.warning else null,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    Spacer(Modifier.height(10.dp))

                    Button(
                        onClick = onRunScan,
                        enabled = !isScanning,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .testTag("run_conflict_scan_button"),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (isScanning) "กำลังตรวจสอบ manifest.json..." else "สแกนหาความขัดแย้งจาก Room DB")
                    }
                }
            }

            // Filter Chips
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(ConflictFilter.entries) { filter ->
                        val selected = selectedFilter == filter
                        val count = when (filter) {
                            ConflictFilter.ALL -> issues.size
                            ConflictFilter.LOOPS -> issues.count { it.type == ConflictType.DEPENDENCY_LOOP }
                            ConflictFilter.VERSIONS -> issues.count {
                                it.type == ConflictType.VERSION_MISMATCH || it.type == ConflictType.SMAPI_VERSION_MISMATCH
                            }
                            ConflictFilter.DEPENDENCIES -> issues.count {
                                it.type == ConflictType.MISSING_DEPENDENCY ||
                                    it.type == ConflictType.DISABLED_DEPENDENCY ||
                                    it.type == ConflictType.DUPLICATE_UNIQUE_ID
                            }
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { selectedFilter = filter },
                            label = {
                                Text("${filter.label} ($count)", style = MaterialTheme.typography.labelMedium)
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
            }

            if (report == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (filteredIssues.isEmpty()) {
                item {
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            IosSquircleIcon(
                                icon = Icons.Rounded.CheckCircle,
                                containerColor = ext.successContainer,
                                iconTint = ext.success,
                                size = 54.dp,
                                iconSize = 28.dp,
                                cornerRadius = 16.dp,
                            )
                            Text(
                                text = if (report.scannedModCount == 0) {
                                    "ยังไม่มีมอดในฐานข้อมูลสำหรับตรวจสอบ"
                                } else if (selectedFilter == ConflictFilter.ALL) {
                                    "ไม่พบความขัดแย้งของมอด"
                                } else {
                                    "ไม่พบปัญหาในหมวดหมู่นี้"
                                },
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (report.scannedModCount == 0) {
                                    "นำเข้าไฟล์มอด .zip หรือโฟลเดอร์มอดในหน้ามอดของฉัน เพื่อตรวจสอบความเข้ากันได้และ Dependency"
                                } else {
                                    "ตรวจสอบข้อกำหนด manifest.json ทั้ง ${report.scannedModCount} มอดแล้ว ไม่พบ Dependency Loop หรือเวอร์ชันขัดแย้งกัน"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            } else {
                items(filteredIssues, key = { it.id }) { issue ->
                    ConflictIssueCard(
                        issue = issue,
                        onEnableDisabledMod = onEnableDisabledMod,
                        onOpenMyMods = onOpenMyMods,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConflictCounterTile(
    label: String,
    count: Int,
    highlightColor: Color?,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Column(
            modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = count.toString(),
                style = MaterialTheme.typography.titleMedium,
                color = highlightColor ?: MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = ext.mutedText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ConflictIssueCard(
    issue: ModConflictIssue,
    onEnableDisabledMod: (String) -> Unit,
    onOpenMyMods: () -> Unit,
) {
    val ext = LocalKpziColors.current
    val isCritical = issue.severity == ConflictSeverity.CRITICAL
    val accentBg = if (isCritical) ext.errorContainer else ext.warningContainer
    val accentFg = if (isCritical) ext.error else ext.warning
    val icon = when (issue.type) {
        ConflictType.DEPENDENCY_LOOP -> Icons.Rounded.Loop
        ConflictType.VERSION_MISMATCH, ConflictType.SMAPI_VERSION_MISMATCH -> Icons.Rounded.ErrorOutline
        ConflictType.MISSING_DEPENDENCY, ConflictType.DISABLED_DEPENDENCY -> Icons.Rounded.LinkOff
        ConflictType.DUPLICATE_UNIQUE_ID -> Icons.Rounded.WarningAmber
    }

    KpziCard(modifier = Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    IosSquircleIcon(
                        icon = icon,
                        containerColor = accentBg,
                        iconTint = accentFg,
                        size = 34.dp,
                        iconSize = 18.dp,
                        cornerRadius = 10.dp,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = issue.primaryModName,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = issue.primaryModUniqueId,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = ext.mutedText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                StatusPill(
                    text = issue.severity.labelTh,
                    containerColor = accentBg,
                    contentColor = accentFg,
                )
            }

            StatusPill(
                text = issue.type.labelTh,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface,
            )

            Text(
                text = issue.summaryTh,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = accentFg,
            )
            Text(
                text = issue.detailTh,
                style = MaterialTheme.typography.bodySmall,
                color = ext.mutedText,
            )

            if (issue.requiredVersion != null && issue.actualVersion != null) {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text = "ต้องการขั้นต่ำ: v${issue.requiredVersion}  •  เวอร์ชันในเครื่อง: v${issue.actualVersion}",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoFontFamily),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            if (issue.type == ConflictType.DISABLED_DEPENDENCY && issue.relatedModUniqueIds.isNotEmpty()) {
                val targetId = issue.relatedModUniqueIds.first()
                Button(
                    onClick = { onEnableDisabledMod(targetId) },
                    shape = RoundedCornerShape(11.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .testTag("enable_dep_${targetId}"),
                ) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("เปิดใช้งานมอดหลัก ($targetId)", style = MaterialTheme.typography.labelMedium)
                }
            } else {
                OutlinedButton(
                    onClick = onOpenMyMods,
                    shape = RoundedCornerShape(11.dp),
                    border = BorderStroke(0.8.dp, ext.border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp),
                ) {
                    Text("จัดการในหน้ามอดของฉัน", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
