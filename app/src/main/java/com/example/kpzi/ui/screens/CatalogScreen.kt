package com.example.kpzi.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.kpzi.CatalogUiState
import com.example.kpzi.domain.model.ApiQuotaState
import com.example.kpzi.domain.model.ModCategory
import com.example.kpzi.domain.model.NexusModSummary
import com.example.kpzi.ui.components.CompactModRow
import com.example.kpzi.ui.components.IosSquircleIcon
import com.example.kpzi.ui.components.KpziCard
import com.example.kpzi.ui.components.StatusPill
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.LocalKpziColors

@Composable
fun CatalogScreen(
    state: CatalogUiState,
    quotaState: ApiQuotaState,
    onQueryChange: (String) -> Unit,
    onCategorySelect: (ModCategory) -> Unit,
    onLoadMore: () -> Unit,
    onRefresh: () -> Unit,
    onOpenAccount: () -> Unit,
    onModClick: (Long) -> Unit,
    onModDownloadClick: (NexusModSummary) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    val isEnglish = isEnglishLanguage()
    var searchText by remember { mutableStateOf(state.query) }
    val listState = rememberLazyListState()

    val shouldLoadMore by remember(state.items.size, state.hasMore, state.isLoadingMore) {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            state.hasMore && !state.isLoadingMore && !state.isLoading &&
                state.items.isNotEmpty() && lastVisible >= state.items.size - 6
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        // Header + Quota status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = tr("คลังมอด", "Mod Catalog"),
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Nexus Mods • Stardew Valley",
                    style = MaterialTheme.typography.labelSmall,
                    color = ext.mutedText,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusPill(
                    text = quotaState.status.label(isEnglish),
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.primary,
                )
                IconButton(onClick = onOpenAccount) {
                    IosSquircleIcon(
                        icon = Icons.Rounded.Notifications,
                        containerColor = MaterialTheme.colorScheme.surface,
                        iconTint = MaterialTheme.colorScheme.primary,
                        size = 34.dp,
                        iconSize = 18.dp,
                        cornerRadius = 11.dp,
                    )
                }
            }
        }

        // iOS Spotlight-inspired filled search bar
        OutlinedTextField(
            value = searchText,
            onValueChange = {
                searchText = it
                onQueryChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .testTag("catalog_search_input"),
            placeholder = {
                Text(
                    text = tr(
                        "ค้นหาชื่อมอด, Mod ID หรือวางลิงก์ Nexus...",
                        "Search mod name, Mod ID, or Nexus URL...",
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = ext.mutedText,
                )
            },
            leadingIcon = {
                Icon(Icons.Rounded.Search, contentDescription = null, tint = ext.mutedText, modifier = Modifier.size(20.dp))
            },
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    IconButton(onClick = {
                        searchText = ""
                        onQueryChange("")
                    }) {
                        Icon(
                            Icons.Rounded.Cancel,
                            contentDescription = tr("ล้างคำค้นหา", "Clear search"),
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

        // Category Filter Chips
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(ModCategory.entries) { category ->
                val selected = state.selectedCategory == category
                FilterChip(
                    selected = selected,
                    onClick = { onCategorySelect(category) },
                    label = {
                        Text(
                            text = category.label(isEnglish),
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

        if (state.isOfflineCache && state.items.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                shape = MaterialTheme.shapes.small,
                color = ext.warningContainer,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = tr(
                            "แสดงข้อมูลแคชออฟไลน์ (${state.items.size} รายการ)",
                            "Showing offline cache (${state.items.size} items)",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = ext.warning,
                    )
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = tr("ลองโหลดใหม่", "Retry"),
                            tint = ext.warning,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        if (state.isLoading && state.items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator()
                    Text(
                        text = tr("กำลังโหลดรายการมอดจาก Nexus Mods...", "Loading mods from Nexus Mods..."),
                        style = MaterialTheme.typography.bodySmall,
                        color = ext.mutedText,
                    )
                }
            }
        } else if (state.items.isEmpty()) {
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
                                .size(76.dp)
                                .clip(RoundedCornerShape(22.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Text(
                            text = tr("ไม่พบมอดที่ตรงกับเงื่อนไข", "No matching mods found"),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (!state.errorMessage.isNullOrBlank()) {
                            Text(
                                text = state.errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = ext.mutedText,
                            )
                        }
                        Button(onClick = onRefresh, shape = RoundedCornerShape(12.dp)) {
                            Text(tr("ลองค้นหาอีกครั้ง", "Search Again"))
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.items, key = { it.modId }) { mod ->
                    CompactModRow(
                        mod = mod,
                        onModClick = { onModClick(mod.modId) },
                        onDownloadClick = { onModDownloadClick(mod) },
                    )
                }
                if (state.isLoadingMore) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }
        }
    }
}
