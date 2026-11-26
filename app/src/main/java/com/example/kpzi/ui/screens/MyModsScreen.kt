package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.ui.components.CompatibilityBadge
import com.example.kpzi.ui.components.IosSegmentedControl
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.ModCoverThumb
import com.example.kpzi.ui.components.StatusPill
import com.example.kpzi.ui.components.formatFileSize
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors

@Composable
fun MyModsScreen(
    mods: List<InstalledMod>,
    onToggleMod: (String) -> Unit,
    onUninstallMod: (String) -> Unit,
    onImportZip: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    var selectedTab by remember { mutableIntStateOf(0) } // 0 = Enabled, 1 = Disabled
    var inspectingMod by remember { mutableStateOf<InstalledMod?>(null) }

    val enabledList = remember(mods) { mods.filter { it.isEnabled } }
    val disabledList = remember(mods) { mods.filter { !it.isEnabled } }
    val displayed = if (selectedTab == 0) enabledList else disabledList

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("มอดของฉัน", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = onImportZip,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, ext.border),
                    modifier = Modifier.testTag("mymods_import_zip"),
                ) {
                    Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ZIP", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = onImportFolder,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(0.8.dp, ext.border),
                    modifier = Modifier.testTag("mymods_import_folder"),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("โฟลเดอร์", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        IosSegmentedControl(
            options = listOf("ที่ติดตั้ง (${enabledList.size})", "ปิดใช้งาน (${disabledList.size})"),
            selectedIndex = selectedTab,
            onSelect = { selectedTab = it },
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Spacer(Modifier.height(6.dp))

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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.img_mascot_chick),
                            contentDescription = null,
                            modifier = Modifier
                                .size(82.dp)
                                .clip(RoundedCornerShape(24.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = if (selectedTab == 0) "ยังไม่มีมอดที่เปิดใช้งาน" else "ไม่มีมอดที่ปิดใช้งาน",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "นำเข้าไฟล์มอด .zip หรือโฟลเดอร์มอดที่มี manifest.json เพื่อเริ่มต้นจัดการมอด",
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                        Button(onClick = onImportZip, shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("นำเข้าไฟล์ ZIP")
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(displayed, key = { it.uniqueId + "_" + it.folderName }) { mod ->
                    KpziCard(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            ModCoverThumb(
                                pictureUrl = null,
                                contentDescription = mod.manifest.name,
                                modifier = Modifier.size(48.dp),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = mod.manifest.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = "v${mod.manifest.version} • ${formatFileSize(mod.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = ext.mutedText,
                                )
                                if (mod.issueMessage != null) {
                                    StatusPill(
                                        text = mod.issueMessage,
                                        containerColor = ext.errorContainer,
                                        contentColor = ext.error,
                                        icon = Icons.Rounded.WarningAmber,
                                    )
                                }
                            }
                            Switch(
                                checked = mod.isEnabled,
                                onCheckedChange = { onToggleMod(mod.uniqueId) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = Color.White,
                                    checkedTrackColor = MaterialTheme.colorScheme.primary,
                                    uncheckedThumbColor = Color.White,
                                    uncheckedTrackColor = ext.border,
                                    uncheckedBorderColor = Color.Transparent,
                                ),
                                modifier = Modifier.testTag("toggle_mod_${mod.uniqueId}"),
                            )
                            IconButton(
                                onClick = { inspectingMod = mod },
                                modifier = Modifier.testTag("inspect_mod_${mod.uniqueId}"),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "ตั้งค่า ${mod.manifest.name}",
                                    tint = ext.mutedText,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    inspectingMod?.let { mod ->
        AlertDialog(
            onDismissRequest = { inspectingMod = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(mod.manifest.name, style = MaterialTheme.typography.titleLarge) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CompatibilityBadge(mod.compatibility)
                    Text(
                        text = "UniqueID: ${mod.uniqueId}",
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                    )
                    Text("ผู้พัฒนา: ${mod.manifest.author}", style = MaterialTheme.typography.bodySmall)
                    Text("เวอร์ชัน: ${mod.manifest.version}", style = MaterialTheme.typography.bodySmall)
                    if (!mod.manifest.entryDll.isNullOrBlank()) {
                        Text(
                            text = "EntryDll: ${mod.manifest.entryDll}",
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = JetBrainsMonoFontFamily),
                            color = ext.warning,
                        )
                    }
                    if (!mod.manifest.contentPackForUniqueId.isNullOrBlank()) {
                        Text(
                            text = "ContentPackFor: ${mod.manifest.contentPackForUniqueId}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    if (mod.manifest.description.isNotBlank()) {
                        Text(
                            text = mod.manifest.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.mutedText,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { inspectingMod = null }) { Text("ปิด") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onUninstallMod(mod.uniqueId)
                        inspectingMod = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = ext.error),
                ) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("ถอนการติดตั้ง")
                }
            },
        )
    }
}
