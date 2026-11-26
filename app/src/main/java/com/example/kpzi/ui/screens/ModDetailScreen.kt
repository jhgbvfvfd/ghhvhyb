package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.kpzi.ModDetailUiState
import com.example.kpzi.core.network.ModTranslator
import com.example.kpzi.domain.model.NexusModDetail
import com.example.kpzi.domain.model.NexusModFile
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.ui.components.CompatibilityBadge
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.ModCoverThumb
import com.example.kpzi.ui.components.StatusPill
import com.example.kpzi.ui.components.formatCompactCount
import com.example.kpzi.ui.components.formatFileSize
import com.example.ui.theme.LocalKpziColors

@Composable
fun ModDetailScreen(
    state: ModDetailUiState,
    userProfile: NexusUserProfile?,
    onBack: () -> Unit,
    onToggleTranslate: () -> Unit,
    onDownloadFile: (NexusModDetail, NexusModFile?) -> Unit,
    onOpenOriginalUrl: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    val detail = state.detail

    if (detail == null && state.isLoading) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    if (detail == null) {
        Box(modifier = modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(state.errorMessage ?: "ไม่พบข้อมูลมอด", style = MaterialTheme.typography.titleMedium)
                OutlinedButton(onClick = onBack) { Text("กลับ") }
            }
        }
        return
    }

    val displayedDescription = if (state.translateToThai) {
        ModTranslator.translatePreservingOriginal(detail.description)
    } else {
        detail.description
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 92.dp),
        ) {
            // Top Cover Hero
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                ) {
                    ModCoverThumb(
                        pictureUrl = detail.summary.pictureUrl,
                        contentDescription = detail.summary.name,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(0.dp)),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0x88000000), Color.Transparent, Color(0xAA000000)),
                                ),
                            ),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x99292723),
                        ) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag("detail_back_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "ย้อนกลับ",
                                    tint = Color.White,
                                )
                            }
                        }
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = Color(0x99292723),
                        ) {
                            IconButton(onClick = { onOpenOriginalUrl(detail.nexusUrl) }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                    contentDescription = "เปิดหน้าต้นฉบับ Nexus Mods",
                                    tint = Color.White,
                                )
                            }
                        }
                    }
                }
            }

            // Main Summary Card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = detail.summary.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "ผู้พัฒนา: ${detail.summary.author} • Mod ID #${detail.summary.modId}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        StatusPill(
                            text = detail.summary.categoryName,
                            containerColor = ext.accentContainer,
                            contentColor = ext.onAccentContainer,
                        )
                        CompatibilityBadge(level = detail.summary.compatibility)
                    }

                    // Stats row
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            StatMetaItem("ดาวน์โหลด", formatCompactCount(detail.summary.downloads))
                            StatMetaItem(
                                "ขนาดไฟล์",
                                detail.summary.fileSizeBytes?.takeIf { it > 0L }?.let(::formatFileSize) ?: "ดูรายการไฟล์",
                            )
                            StatMetaItem("เวอร์ชัน", "v${detail.summary.version}")
                        }
                    }

                    if (!state.errorMessage.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = ext.warningContainer,
                        ) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.warning,
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            // Description + Non-destructive Thai Translation Toggle
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("รายละเอียด", style = MaterialTheme.typography.titleLarge)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Text("แปลไทย", style = MaterialTheme.typography.labelMedium)
                            Switch(
                                checked = state.translateToThai,
                                onCheckedChange = { onToggleTranslate() },
                                modifier = Modifier.testTag("detail_translate_switch"),
                            )
                        }
                    }
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = displayedDescription.ifBlank { "ไม่มีคำอธิบายเพิ่มเติมจากผู้พัฒนา" },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            // Preview Images
            if (detail.previewImageUrls.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "ภาพตัวอย่าง",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(detail.previewImageUrls) { url ->
                                ModCoverThumb(
                                    pictureUrl = url,
                                    contentDescription = "ภาพตัวอย่างมอด",
                                    modifier = Modifier
                                        .width(180.dp)
                                        .height(106.dp),
                                )
                            }
                        }
                    }
                }
            }

            // Compatibility, Dependencies, License
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("ข้อมูลความเข้ากันได้และลิขสิทธิ์", style = MaterialTheme.typography.titleMedium)
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        DetailInfoRow("เวอร์ชันเกมที่ระบุ", detail.supportedGameVersions.joinToString(", "))
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ext.border)
                        DetailInfoRow("เวอร์ชัน SMAPI", detail.requiredSmapiVersion ?: "ไม่ระบุในรายละเอียด")
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ext.border)
                        DetailInfoRow(
                            "Dependencies",
                            if (detail.dependencies.isEmpty()) "ไม่พบการระบุเพิ่มเติม" else detail.dependencies.joinToString(", "),
                        )
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ext.border)
                        DetailInfoRow("License", detail.licenseName)
                        HorizontalDivider(Modifier.padding(vertical = 6.dp), color = ext.border)
                        OutlinedButton(
                            onClick = { onOpenOriginalUrl(detail.nexusUrl) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(0.8.dp, ext.border),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("เปิดลิงก์ต้นฉบับบน Nexus Mods")
                        }
                    }
                }
            }

            // Files List
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("รายการไฟล์ (${detail.files.size})", style = MaterialTheme.typography.titleMedium)
                    if (detail.files.isEmpty()) {
                        KpziCard(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = if (state.isLoading) "กำลังโหลดรายการไฟล์..." else "กดปุ่มด้านล่างเพื่อเปิดหน้าดาวน์โหลดไฟล์บน Nexus Mods",
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                    }
                }
            }

            items(detail.files, key = { it.fileId }) { file ->
                KpziCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "v${file.version} • ${formatFileSize(file.sizeBytes)} • ${file.categoryName}",
                                style = MaterialTheme.typography.labelSmall,
                                color = ext.mutedText,
                            )
                        }
                        Button(
                            onClick = { onDownloadFile(detail, file) },
                            shape = RoundedCornerShape(11.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(if (userProfile?.isPremium == true) "โหลดตรง" else "หน้าเว็บ")
                        }
                    }
                }
            }

            // Changelogs
            if (detail.changelogs.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("ประวัติอัปเดต", style = MaterialTheme.typography.titleMedium)
                        detail.changelogs.take(5).forEach { entry ->
                            KpziCard(modifier = Modifier.fillMaxWidth()) {
                                Text("เวอร์ชัน ${entry.version}", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                entry.changes.forEach { line ->
                                    Text("• $line", style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Sticky Bottom CTA
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, ext.border),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Button(
                    onClick = { onDownloadFile(detail, detail.files.firstOrNull()) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("detail_install_button"),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (userProfile?.isPremium == true && detail.files.isNotEmpty()) {
                            "ดาวน์โหลดและติดตั้งมอด"
                        } else {
                            "เปิดหน้าดาวน์โหลด Nexus Mods"
                        },
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatMetaItem(label: String, value: String) {
    val ext = LocalKpziColors.current
    Column {
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelSmall, color = ext.mutedText)
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    val ext = LocalKpziColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = ext.mutedText)
        Spacer(Modifier.width(12.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f, fill = false),
        )
    }
}
