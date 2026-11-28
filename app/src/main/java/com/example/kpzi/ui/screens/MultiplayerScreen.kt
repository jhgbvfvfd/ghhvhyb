package com.example.kpzi.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CropFree
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SportsEsports
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.kpzi.data.local.CoopHistoryEntity
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.engine.multiplayer.MultiplayerLobbySnapshot
import com.example.kpzi.engine.multiplayer.MultiplayerRoom
import com.example.kpzi.engine.multiplayer.RoomVisibility
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalKpziColors
import com.example.ui.theme.NotoSansThaiFontFamily
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MultiplayerScreen(
    snapshot: MultiplayerLobbySnapshot,
    favoriteRoomIds: Set<String>,
    coopHistory: List<CoopHistoryEntity>,
    userProfile: NexusUserProfile?,
    localGameVersion: String,
    pendingPasswordRoom: MultiplayerRoom?,
    joinPasswordError: String?,
    onRefreshRooms: () -> Unit,
    onHostRoom: (roomName: String, ownerName: String, maxPlayers: Int, visibility: RoomVisibility, password: String) -> Unit,
    onStopHosting: () -> Unit,
    onJoinRoom: (MultiplayerRoom, String?) -> Unit,
    onJoinByCode: (String, String?) -> Unit,
    onDismissPasswordPrompt: () -> Unit,
    onToggleFavorite: (MultiplayerRoom) -> Unit,
    onClearHistory: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalKpziColors.current
    val palette = remember(ext.isDark) { multiplayerPalette(ext.isDark) }
    val isEnglish = isEnglishLanguage()
    val clipboard = LocalClipboardManager.current

    var tabFilter by rememberSaveable { mutableStateOf(MultiplayerTabFilter.ALL) }
    var advancedFilter by remember { mutableStateOf(MultiplayerAdvancedFilterState()) }
    var isSearchOpen by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinCodeDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        onRefreshRooms()
    }

    val coverAssignmentMap = remember(snapshot.rooms) {
        computeRoomCoverAssignmentMap(snapshot.rooms)
    }

    val filteredRooms = remember(snapshot.rooms, tabFilter, advancedFilter, searchQuery, localGameVersion) {
        filterAndSortRooms(
            rooms = snapshot.rooms,
            tabFilter = tabFilter,
            advancedFilter = advancedFilter,
            searchQuery = searchQuery,
            localGameVersion = localGameVersion,
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(palette.background),
    ) {
        MultiplayerCompactHeader(
            palette = palette,
            isSearchOpen = isSearchOpen,
            activeFilterCount = advancedFilter.activeFilterCount,
            onJoinByCodeClick = { showJoinCodeDialog = true },
            onToggleSearch = {
                isSearchOpen = !isSearchOpen
                if (!isSearchOpen) searchQuery = ""
            },
            onOpenFilters = { showFilterDialog = true },
            onCreateRoomClick = { showCreateDialog = true },
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
                        text = tr("ค้นหาชื่อห้อง, เจ้าของห้อง, หรือรหัสห้อง...", "Search room name, host, or code..."),
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 12.sp,
                        color = palette.textSecondary,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = palette.textSecondary, modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = tr("ล้างคำค้น", "Clear"), tint = palette.textSecondary, modifier = Modifier.size(16.dp))
                        }
                    }
                },
                textStyle = TextStyle(fontFamily = NotoSansThaiFontFamily, fontSize = 13.sp, color = palette.textPrimary),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = palette.surface,
                    unfocusedContainerColor = palette.surface,
                    focusedBorderColor = palette.primary,
                    unfocusedBorderColor = palette.border,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp)
                    .padding(bottom = 6.dp)
                    .testTag("multiplayer_search_input"),
            )
        }

        PullToRefreshBox(
            isRefreshing = snapshot.isScanning,
            onRefresh = onRefreshRooms,
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 2.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                item(key = "banner") {
                    MultiplayerHeroBanner(
                        onlinePlayerCount = snapshot.totalOnlinePlayers,
                        palette = palette,
                    )
                }

                snapshot.activeHostRoom?.let { hosted ->
                    item(key = "active_host_strip") {
                        ActiveHostRoomStrip(
                            room = hosted,
                            palette = palette,
                            onCopyCode = {
                                clipboard.setText(AnnotatedString(hosted.roomCode))
                                onShowMessage(
                                    if (isEnglish) "Copied room code ${hosted.roomCode}" else "คัดลอกรหัสห้อง ${hosted.roomCode} แล้ว",
                                )
                            },
                            onStopHosting = onStopHosting,
                        )
                    }
                }

                item(key = "quick_actions") {
                    MultiplayerQuickMenuRow(
                        palette = palette,
                        onCreateRoom = { showCreateDialog = true },
                        onJoinByCode = { showJoinCodeDialog = true },
                        onFindPublicRooms = {
                            tabFilter = MultiplayerTabFilter.PUBLIC
                            onRefreshRooms()
                        },
                        onOpenHistory = { showHistoryDialog = true },
                    )
                }

                item(key = "rooms_header") {
                    OpenRoomsSectionHeader(
                        roomCount = filteredRooms.size,
                        selectedTab = tabFilter,
                        palette = palette,
                        onSelectTab = { tabFilter = it },
                    )
                }

                if (snapshot.isScanning && filteredRooms.isEmpty()) {
                    items(3, key = { idx -> "skeleton_$idx" }) {
                        MultiplayerRoomSkeletonCard(palette = palette)
                    }
                } else if (filteredRooms.isEmpty()) {
                    item(key = "empty_state") {
                        MultiplayerEmptyStateCard(
                            palette = palette,
                            onCreateRoom = { showCreateDialog = true },
                        )
                    }
                } else {
                    items(filteredRooms, key = { it.roomId }) { room ->
                        val coverId = coverAssignmentMap[room.roomId.lowercase(Locale.ROOT)] ?: 0
                        MultiplayerRoomCard(
                            room = room,
                            coverId = coverId,
                            isFavorite = room.roomId in favoriteRoomIds,
                            isConnecting = snapshot.isConnectingRoomId == room.roomId,
                            palette = palette,
                            onToggleFavorite = { onToggleFavorite(room) },
                            onJoinClick = { onJoinRoom(room, null) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreateHostRoomDialog(
            defaultOwnerName = userProfile?.username ?: "",
            palette = palette,
            onDismiss = { showCreateDialog = false },
            onCreate = { roomName, ownerName, maxPlayers, visibility, password ->
                showCreateDialog = false
                onHostRoom(roomName, ownerName, maxPlayers, visibility, password)
            },
        )
    }

    if (showJoinCodeDialog) {
        JoinByCodeDialog(
            palette = palette,
            onDismiss = { showJoinCodeDialog = false },
            onJoin = { code ->
                showJoinCodeDialog = false
                onJoinByCode(code, null)
            },
        )
    }

    pendingPasswordRoom?.let { lockedRoom ->
        PrivateRoomPasswordDialog(
            room = lockedRoom,
            errorMessage = joinPasswordError,
            palette = palette,
            onDismiss = onDismissPasswordPrompt,
            onSubmitPassword = { entered ->
                onJoinRoom(lockedRoom, entered)
            },
        )
    }

    if (showFilterDialog) {
        FilterAndSortRoomsDialog(
            initial = advancedFilter,
            palette = palette,
            onDismiss = { showFilterDialog = false },
            onApply = { updated ->
                advancedFilter = updated
                showFilterDialog = false
            },
        )
    }

    if (showHistoryDialog) {
        CoopPlayHistoryDialog(
            history = coopHistory,
            palette = palette,
            onClearHistory = onClearHistory,
            onDismiss = { showHistoryDialog = false },
        )
    }
}

@Composable
private fun MultiplayerCompactHeader(
    palette: MultiplayerPalette,
    isSearchOpen: Boolean,
    activeFilterCount: Int,
    onJoinByCodeClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onOpenFilters: () -> Unit,
    onCreateRoomClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = palette.primarySoftBg,
            border = BorderStroke(0.75.dp, palette.border),
            modifier = Modifier.size(34.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.SportsEsports,
                    contentDescription = null,
                    tint = palette.primary,
                    modifier = Modifier.size(19.dp),
                )
            }
        }

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tr("เล่นด้วยกัน", "Co-op"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.5.sp,
                lineHeight = 22.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = tr("เชื่อมต่อเล่น Stardew Valley กับเพื่อน", "Play Stardew Valley together"),
                fontFamily = NotoSansThaiFontFamily,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(Modifier.width(6.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HeaderCircleActionButton(
                icon = Icons.Rounded.CropFree,
                contentDescription = tr("เข้าร่วมด้วยรหัส", "Join by code"),
                containerColor = palette.surface,
                contentColor = palette.textPrimary,
                borderColor = palette.border,
                onClick = onJoinByCodeClick,
                testTag = "header_join_code_btn",
            )
            HeaderCircleActionButton(
                icon = Icons.Rounded.Search,
                contentDescription = tr("ค้นหาห้อง", "Search rooms"),
                containerColor = if (isSearchOpen) palette.primarySoftBg else palette.surface,
                contentColor = if (isSearchOpen) palette.primarySoftText else palette.textPrimary,
                borderColor = if (isSearchOpen) palette.primary else palette.border,
                onClick = onToggleSearch,
                testTag = "header_search_rooms_btn",
            )
            Box {
                HeaderCircleActionButton(
                    icon = Icons.Rounded.Tune,
                    contentDescription = tr("ตัวกรองห้อง", "Filter rooms"),
                    containerColor = if (activeFilterCount > 0) palette.primarySoftBg else palette.surface,
                    contentColor = if (activeFilterCount > 0) palette.primarySoftText else palette.textPrimary,
                    borderColor = if (activeFilterCount > 0) palette.primary else palette.border,
                    onClick = onOpenFilters,
                    testTag = "header_filter_rooms_btn",
                )
                if (activeFilterCount > 0) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-1).dp, y = 6.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(palette.primary),
                    )
                }
            }
            HeaderCircleActionButton(
                icon = Icons.Rounded.Add,
                contentDescription = tr("สร้างห้องใหม่", "Create room"),
                containerColor = palette.primary,
                contentColor = palette.primaryContent,
                borderColor = palette.primary,
                onClick = onCreateRoomClick,
                testTag = "header_create_room_btn",
            )
        }
    }
}

@Composable
private fun HeaderCircleActionButton(
    icon: ImageVector,
    contentDescription: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
    testTag: String,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = tween(110),
        label = "header_icon_scale",
    )

    Box(
        modifier = Modifier
            .defaultMinSize(minWidth = 36.dp, minHeight = 48.dp)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(testTag),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .size(34.dp)
                .scale(scale),
            shape = CircleShape,
            color = containerColor,
            border = BorderStroke(0.75.dp, borderColor),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = contentColor,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MultiplayerHeroBanner(
    onlinePlayerCount: Int,
    palette: MultiplayerPalette,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(0.75.dp, palette.border, RoundedCornerShape(14.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.img_multiplayer_banner),
            contentDescription = tr("แบนเนอร์ฟาร์ม Stardew Valley", "Stardew Valley Co-op Farm Banner"),
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colorStops = arrayOf(
                            0.0f to Color(0xD90A0D0C),
                            0.46f to Color(0xA60A0D0C),
                            1.0f to Color(0x1E0A0D0C),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = Color(0x661C2E1B),
                border = BorderStroke(0.6.dp, palette.primary.copy(alpha = 0.5f)),
            ) {
                Text(
                    text = "Stardew Valley",
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.sp,
                    color = Color(0xFFC5F7AD),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
            Text(
                text = tr("เล่นด้วยกัน", "Play Co-op"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 19.sp,
                lineHeight = 23.sp,
                color = Color.White,
            )
            Text(
                text = tr("สร้างหรือเข้าร่วมห้องเพื่อเล่นกับเพื่อน", "Create or join a room with friends"),
                fontFamily = NotoSansThaiFontFamily,
                fontSize = 11.sp,
                color = Color(0xFFE5E7EB),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Surface(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
                .testTag("online_players_badge"),
            shape = RoundedCornerShape(50),
            color = Color(0xBF0E1110),
            border = BorderStroke(0.6.dp, Color.White.copy(alpha = 0.16f)),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (onlinePlayerCount > 0) palette.primary else Color(0xFF9CA3AF)),
                )
                Text(
                    text = tr("ออนไลน์ $onlinePlayerCount คน", "$onlinePlayerCount online"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ActiveHostRoomStrip(
    room: MultiplayerRoom,
    palette: MultiplayerPalette,
    onCopyCode: () -> Unit,
    onStopHosting: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_host_strip"),
        shape = RoundedCornerShape(12.dp),
        color = palette.primarySoftBg,
        border = BorderStroke(0.75.dp, palette.primary.copy(alpha = 0.55f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr("ห้องของคุณเปิดอยู่: ${room.roomName}", "Hosting: ${room.roomName}"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = palette.primarySoftText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = room.roomCode,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 11.sp,
                    color = palette.textPrimary,
                )
            }
            IconButton(onClick = onCopyCode) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = tr("คัดลอกรหัสห้อง", "Copy room code"),
                    tint = palette.primarySoftText,
                    modifier = Modifier.size(16.dp),
                )
            }
            TextButton(onClick = onStopHosting) {
                Text(
                    text = tr("ปิดห้อง", "Close"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.5.sp,
                    color = palette.privateLockRed,
                )
            }
        }
    }
}

@Composable
private fun MultiplayerQuickMenuRow(
    palette: MultiplayerPalette,
    onCreateRoom: () -> Unit,
    onJoinByCode: () -> Unit,
    onFindPublicRooms: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        QuickActionTile(
            label = tr("สร้างห้อง", "Host Room"),
            icon = Icons.Rounded.AddCircleOutline,
            tint = palette.primary,
            palette = palette,
            onClick = onCreateRoom,
            testTag = "quick_host_room",
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            label = tr("เข้าร่วมด้วยรหัส", "Join Code"),
            icon = Icons.Rounded.Group,
            tint = palette.textSecondary,
            palette = palette,
            onClick = onJoinByCode,
            testTag = "quick_join_code",
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            label = tr("หาห้องสาธารณะ", "Public Rooms"),
            icon = Icons.Rounded.Public,
            tint = palette.textSecondary,
            palette = palette,
            onClick = onFindPublicRooms,
            testTag = "quick_find_public",
            modifier = Modifier.weight(1f),
        )
        QuickActionTile(
            label = tr("ประวัติการเล่น", "History"),
            icon = Icons.Rounded.History,
            tint = palette.textSecondary,
            palette = palette,
            onClick = onOpenHistory,
            testTag = "quick_history",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickActionTile(
    label: String,
    icon: ImageVector,
    tint: Color,
    palette: MultiplayerPalette,
    onClick: () -> Unit,
    testTag: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = tween(110),
        label = "quick_tile_scale",
    )

    Surface(
        modifier = modifier
            .scale(scale)
            .height(60.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            )
            .testTag(testTag),
        shape = RoundedCornerShape(12.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = tint,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Medium,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                color = palette.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun OpenRoomsSectionHeader(
    roomCount: Int,
    selectedTab: MultiplayerTabFilter,
    palette: MultiplayerPalette,
    onSelectTab: (MultiplayerTabFilter) -> Unit,
) {
    val isEnglish = isEnglishLanguage()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.SportsEsports,
                contentDescription = null,
                tint = palette.primary,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = tr("ห้องที่เปิดอยู่", "Open Rooms"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = palette.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Surface(
                shape = CircleShape,
                color = palette.primarySoftBg,
            ) {
                Text(
                    text = "$roomCount",
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = palette.primarySoftText,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 1.5.dp),
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MultiplayerTabFilter.entries.forEach { tab ->
                val selected = tab == selectedTab
                val bg by animateColorAsState(
                    targetValue = if (selected) palette.primary else palette.surface,
                    animationSpec = tween(160),
                    label = "tab_pill_bg",
                )
                val fg by animateColorAsState(
                    targetValue = if (selected) palette.primaryContent else palette.textSecondary,
                    animationSpec = tween(160),
                    label = "tab_pill_fg",
                )
                Surface(
                    onClick = { onSelectTab(tab) },
                    shape = RoundedCornerShape(50),
                    color = bg,
                    border = BorderStroke(0.75.dp, if (selected) palette.primary else palette.border),
                    modifier = Modifier.testTag("room_filter_${tab.name.lowercase(Locale.ROOT)}"),
                ) {
                    Text(
                        text = tab.label(isEnglish),
                        fontFamily = NotoSansThaiFontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 10.5.sp,
                        color = fg,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                    )
                }
            }
        }
    }
}
