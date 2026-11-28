package com.example.kpzi.ui.screens

import android.os.Environment
import android.os.StatFs
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.LocalKpziColors
import com.example.ui.theme.NotoSansThaiFontFamily
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
fun MyModsScreen(
    mods: List<InstalledMod>,
    onToggleMod: (String) -> Unit,
    onUninstallMod: (String) -> Unit,
    onImportZip: () -> Unit,
    onImportFolder: () -> Unit,
    modifier: Modifier = Modifier,
    totalDeviceStorageBytes: Long? = null,
) {
    val ext = LocalKpziColors.current
    val palette = remember(ext.isDark) { myModsPalette(ext.isDark) }
    val isEnglish = isEnglishLanguage()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var filterOption by rememberSaveable { mutableStateOf(MyModsFilterOption.ALL) }
    var sortOption by rememberSaveable { mutableStateOf(MyModsSortOption.LATEST) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showSortMenu by remember { mutableStateOf(false) }

    var inspectingMod by remember { mutableStateOf<InstalledMod?>(null) }
    var modPendingDelete by remember { mutableStateOf<InstalledMod?>(null) }
    var pendingCoverTargetUniqueId by remember { mutableStateOf<String?>(null) }
    var coverRevision by remember { mutableIntStateOf(0) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        val targetId = pendingCoverTargetUniqueId
        pendingCoverTargetUniqueId = null
        if (uri != null && !targetId.isNullOrBlank()) {
            coroutineScope.launch {
                val saved = MyModsCoverStore.saveCompressedCustomCover(context, targetId, uri)
                if (saved) {
                    coverRevision += 1
                }
            }
        }
    }

    val launchCoverPicker: (String) -> Unit = { uniqueId ->
        pendingCoverTargetUniqueId = uniqueId
        photoPickerLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
    }

    val effectiveCapacityBytes = remember(totalDeviceStorageBytes) {
        if (totalDeviceStorageBytes != null && totalDeviceStorageBytes > 0L) {
            totalDeviceStorageBytes
        } else {
            runCatching { StatFs(Environment.getDataDirectory().path).totalBytes }.getOrDefault(0L)
        }
    }
    val enabledCount by remember(mods) { derivedStateOf { mods.count { it.isEnabled } } }
    val disabledCount by remember(mods) { derivedStateOf { mods.count { !it.isEnabled } } }
    val enabledBytes by remember(mods) {
        derivedStateOf { mods.asSequence().filter { it.isEnabled }.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    }
    val disabledBytes by remember(mods) {
        derivedStateOf { mods.asSequence().filter { !it.isEnabled }.sumOf { it.sizeBytes.coerceAtLeast(0L) } }
    }
    val totalModBytes by remember(enabledBytes, disabledBytes) {
        derivedStateOf { enabledBytes + disabledBytes }
    }

    val coverAssignmentMap = remember(mods) { computeCoverAssignmentMap(mods) }

    val displayedMods = remember(mods, filterOption, sortOption, searchQuery) {
        val queryTrimmed = searchQuery.trim().lowercase(Locale.ROOT)
        val filtered = mods.filter { mod ->
            val matchesFilter = when (filterOption) {
                MyModsFilterOption.ALL -> true
                MyModsFilterOption.ENABLED -> mod.isEnabled
                MyModsFilterOption.DISABLED -> !mod.isEnabled
            }
            if (!matchesFilter) return@filter false
            if (queryTrimmed.isEmpty()) return@filter true
            mod.manifest.name.lowercase(Locale.ROOT).contains(queryTrimmed) ||
                mod.uniqueId.lowercase(Locale.ROOT).contains(queryTrimmed) ||
                mod.manifest.author.lowercase(Locale.ROOT).contains(queryTrimmed)
        }

        when (sortOption) {
            MyModsSortOption.LATEST -> filtered.sortedByDescending { it.installedAtMs }
            MyModsSortOption.NAME_AZ -> filtered.sortedBy { it.manifest.name.lowercase(Locale.ROOT) }
            MyModsSortOption.SIZE_DESC -> filtered.sortedByDescending { it.sizeBytes }
            MyModsSortOption.STATUS -> filtered.sortedWith(
                compareByDescending<InstalledMod> { it.isEnabled }
                    .thenBy { it.manifest.name.lowercase(Locale.ROOT) },
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        PixelArtFarmSilhouette(
            palette = palette,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(145.dp),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            MyModsCompactHeader(
                palette = palette,
                isSearchOpen = isSearchOpen,
                onToggleSearch = {
                    isSearchOpen = !isSearchOpen
                    if (!isSearchOpen) searchQuery = ""
                },
                onImportZip = onImportZip,
                onImportFolder = onImportFolder,
            )

            AnimatedVisibility(
                visible = isSearchOpen || searchQuery.isNotEmpty(),
                enter = expandVertically(tween(180)) + fadeIn(tween(180)),
                exit = shrinkVertically(tween(160)) + fadeOut(tween(160)),
            ) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = tr("ค้นหาชื่อมอด, ผู้พัฒนา, UniqueID...", "Search mod name, author, ID..."),
                            fontFamily = NotoSansThaiFontFamily,
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Search,
                            contentDescription = null,
                            tint = palette.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(
                                    imageVector = Icons.Rounded.Close,
                                    contentDescription = tr("ล้างคำค้นหา", "Clear search"),
                                    tint = palette.textSecondary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    },
                    textStyle = TextStyle(
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 12.sp,
                        color = palette.textPrimary,
                    ),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = palette.surface,
                        unfocusedContainerColor = palette.surface,
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                        .testTag("mymods_search_input"),
                )
            }

            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val gridColumns = remember(isGridView, maxWidth) {
                    when {
                        !isGridView -> 1
                        maxWidth >= 600.dp -> 3
                        else -> 2
                    }
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 12.dp,
                        end = 12.dp,
                        top = 2.dp,
                        bottom = 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    item(key = "top_section", span = { GridItemSpan(maxLineSpan) }) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            StardewGameBanner(
                                totalModsCount = mods.size,
                                palette = palette,
                            )
                            Spacer(Modifier.height(6.dp))
                            ModUsageStatsRow(
                                enabledCount = enabledCount,
                                enabledBytes = enabledBytes,
                                disabledCount = disabledCount,
                                disabledBytes = disabledBytes,
                                totalModBytes = totalModBytes,
                                deviceCapacityBytes = effectiveCapacityBytes,
                                palette = palette,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy((-8).dp)) {
                                ModFilterPillsRow(
                                    selected = filterOption,
                                    totalCount = mods.size,
                                    enabledCount = enabledCount,
                                    disabledCount = disabledCount,
                                    isEnglish = isEnglish,
                                    palette = palette,
                                    onSelect = { filterOption = it },
                                )
                                ModManagementToolbar(
                                    sortOption = sortOption,
                                    isGridView = isGridView,
                                    showSortMenu = showSortMenu,
                                    isEnglish = isEnglish,
                                    palette = palette,
                                    onOpenSortMenu = { showSortMenu = true },
                                    onDismissSortMenu = { showSortMenu = false },
                                    onSelectSort = {
                                        sortOption = it
                                        showSortMenu = false
                                    },
                                    onSetGridView = { isGridView = it },
                                )
                            }
                        }
                    }

                    if (displayedMods.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            MyModsEmptyStateCard(
                                hasAnyMods = mods.isNotEmpty(),
                                searchQuery = searchQuery,
                                filterOption = filterOption,
                                palette = palette,
                                onImportZip = onImportZip,
                                onResetFilters = {
                                    filterOption = MyModsFilterOption.ALL
                                    searchQuery = ""
                                },
                            )
                        }
                    } else {
                        items(
                            items = displayedMods,
                            key = { "${it.uniqueId}_${it.folderName}" },
                        ) { mod ->
                            val assignedCoverId = coverAssignmentMap[mod.uniqueId.lowercase(Locale.ROOT)] ?: 0
                            if (isGridView) {
                                MyModGridItemCard(
                                    mod = mod,
                                    assignedCoverId = assignedCoverId,
                                    coverRevision = coverRevision,
                                    palette = palette,
                                    onToggle = { onToggleMod(mod.uniqueId) },
                                    onRequestDelete = { modPendingDelete = mod },
                                    onPickCover = { launchCoverPicker(mod.uniqueId) },
                                    onInspect = { inspectingMod = mod },
                                    modifier = Modifier.animateItem(),
                                )
                            } else {
                                MyModListItemCard(
                                    mod = mod,
                                    allMods = mods,
                                    assignedCoverId = assignedCoverId,
                                    coverRevision = coverRevision,
                                    palette = palette,
                                    onToggle = { onToggleMod(mod.uniqueId) },
                                    onRequestDelete = { modPendingDelete = mod },
                                    onPickCover = { launchCoverPicker(mod.uniqueId) },
                                    onInspect = { inspectingMod = mod },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    modPendingDelete?.let { target ->
        val dependents = remember(target.uniqueId, mods) { findDependentMods(target, mods) }
        DeleteModConfirmationDialog(
            mod = target,
            dependentMods = dependents,
            palette = palette,
            onDismiss = { modPendingDelete = null },
            onConfirmDelete = {
                MyModsCoverStore.deleteCustomCover(context, target.uniqueId)
                onUninstallMod(target.uniqueId)
                modPendingDelete = null
                if (inspectingMod?.uniqueId == target.uniqueId) {
                    inspectingMod = null
                }
            },
        )
    }

    inspectingMod?.let { mod ->
        val assignedCoverId = coverAssignmentMap[mod.uniqueId.lowercase(Locale.ROOT)] ?: 0
        InspectModDetailDialog(
            mod = mod,
            assignedCoverId = assignedCoverId,
            coverRevision = coverRevision,
            palette = palette,
            onPickCover = { launchCoverPicker(mod.uniqueId) },
            onResetCustomCover = {
                MyModsCoverStore.deleteCustomCover(context, mod.uniqueId)
                coverRevision += 1
            },
            onRequestDelete = {
                inspectingMod = null
                modPendingDelete = mod
            },
            onDismiss = { inspectingMod = null },
        )
    }
}
