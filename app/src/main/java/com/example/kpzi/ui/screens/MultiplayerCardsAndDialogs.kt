package com.example.kpzi.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kpzi.data.local.CoopHistoryEntity
import com.example.kpzi.engine.multiplayer.MultiplayerRoom
import com.example.kpzi.engine.multiplayer.RoomVisibility
import com.example.kpzi.ui.i18n.isEnglishLanguage
import com.example.kpzi.ui.i18n.tr
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.NotoSansThaiFontFamily
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun MultiplayerRoomCard(
    room: MultiplayerRoom,
    coverId: Int,
    isFavorite: Boolean,
    isConnecting: Boolean,
    palette: MultiplayerPalette,
    onToggleFavorite: () -> Unit,
    onJoinClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isEnglish = isEnglishLanguage()
    val coverBitmap = remember(coverId) { PixelArtRoomCovers.getCoverBitmap(coverId) }
    val joinInteraction = remember { MutableInteractionSource() }
    val isJoinPressed by joinInteraction.collectIsPressedAsState()
    val joinScale by animateFloatAsState(
        targetValue = if (isJoinPressed) 0.94f else 1f,
        animationSpec = tween(120),
        label = "join_btn_scale",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("room_card_${room.roomId}"),
        shape = RoundedCornerShape(13.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left Pixel Art Room Cover
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.surfaceElevated)
                    .border(0.75.dp, palette.border, RoundedCornerShape(10.dp)),
            ) {
                Image(
                    bitmap = coverBitmap,
                    contentDescription = room.roomName,
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Spacer(Modifier.width(9.dp))

            // Middle room details
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = room.roomName,
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.5.sp,
                    lineHeight = 18.sp,
                    color = palette.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = if (room.isOwnHostRoom) Icons.Rounded.WorkspacePremium else Icons.Rounded.Group,
                        contentDescription = null,
                        tint = if (room.isOwnHostRoom) palette.crownGold else palette.primary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = tr("เจ้าของ: ${room.ownerName}", "Host: ${room.ownerName}"),
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 11.5.sp,
                        lineHeight = 14.sp,
                        color = palette.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Bottom metadata strip: 2/4 | Visibility | Version | Signal + Ping
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Group,
                            contentDescription = null,
                            tint = palette.textSecondary,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "${room.currentPlayers}/${room.maxPlayers}",
                            fontFamily = NotoSansThaiFontFamily,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp,
                            color = palette.textPrimary,
                        )
                    }

                    // Visibility chip
                    val isPrivate = room.visibility == RoomVisibility.PRIVATE
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = if (isPrivate) palette.privateLockBg else palette.chipBg,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Icon(
                                imageVector = if (isPrivate) Icons.Rounded.Lock else Icons.Rounded.Public,
                                contentDescription = null,
                                tint = if (isPrivate) palette.privateLockRed else palette.publicGlobePurple,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                text = room.visibility.label(isEnglish),
                                fontFamily = NotoSansThaiFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 10.sp,
                                color = if (isPrivate) palette.privateLockRed else palette.textPrimary,
                                maxLines = 1,
                            )
                        }
                    }

                    if (room.gameVersion.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = palette.chipBg,
                        ) {
                            Text(
                                text = room.gameVersion,
                                fontFamily = NotoSansThaiFontFamily,
                                fontSize = 10.sp,
                                color = palette.textSecondary,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.5.dp),
                            )
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        SignalBarsIndicator(quality = room.signalQuality, palette = palette)
                        Text(
                            text = room.pingMs?.let { "$it ms" } ?: "-- ms",
                            fontFamily = NotoSansThaiFontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.5.sp,
                            color = palette.textSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }

            Spacer(Modifier.width(4.dp))

            // Right side: favorite star (top) + Join/Full button (bottom)
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.height(66.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            role = Role.Checkbox,
                            onClick = onToggleFavorite,
                        )
                        .testTag("fav_room_${room.roomId}"),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = tr("บันทึกห้องโปรด", "Favorite room"),
                        tint = if (isFavorite) palette.starGold else palette.textSecondary,
                        modifier = Modifier.size(17.dp),
                    )
                }

                val canJoin = !room.isFull && !isConnecting
                val btnBg by animateColorAsState(
                    targetValue = if (room.isFull) palette.fullButtonBg else palette.primary,
                    animationSpec = tween(160),
                    label = "room_join_bg",
                )
                Box(
                    modifier = Modifier
                        .scale(joinScale)
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(50))
                        .clickable(
                            enabled = canJoin,
                            interactionSource = joinInteraction,
                            indication = null,
                            role = Role.Button,
                            onClick = onJoinClick,
                        )
                        .testTag("join_room_${room.roomId}"),
                    contentAlignment = Alignment.CenterEnd,
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = btnBg,
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 13.dp, vertical = 5.5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (isConnecting) {
                                CircularProgressIndicator(
                                    color = palette.primaryContent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(14.dp),
                                )
                            } else {
                                Text(
                                    text = when {
                                        room.isFull -> tr("เต็ม", "Full")
                                        room.isOwnHostRoom -> tr("เปิดเกม", "Launch")
                                        else -> tr("เข้าร่วม", "Join")
                                    },
                                    fontFamily = NotoSansThaiFontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.5.sp,
                                    color = if (room.isFull) palette.fullButtonText else palette.primaryContent,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun MultiplayerRoomSkeletonCard(palette: MultiplayerPalette) {
    val transition = rememberInfiniteTransition(label = "room_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.75f,
        animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    val blockColor = palette.chipBg.copy(alpha = alpha)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp),
        shape = RoundedCornerShape(13.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 9.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 58.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(blockColor),
            )
            Spacer(Modifier.width(9.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .height(14.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.38f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(11.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(blockColor),
                )
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(width = 62.dp, height = 28.dp)
                    .clip(RoundedCornerShape(50))
                    .background(blockColor),
            )
        }
    }
}

@Composable
internal fun MultiplayerEmptyStateCard(
    palette: MultiplayerPalette,
    onCreateRoom: () -> Unit,
) {
    val farmhouseArt = remember { PixelArtRoomCovers.getCoverBitmap(0) }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("multiplayer_empty_state"),
        shape = RoundedCornerShape(13.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 66.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(0.75.dp, palette.border, RoundedCornerShape(12.dp)),
            ) {
                Image(
                    bitmap = farmhouseArt,
                    contentDescription = tr("บ้านฟาร์ม Pixel Art", "Pixel art farmhouse"),
                    contentScale = ContentScale.Crop,
                    filterQuality = FilterQuality.None,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            Text(
                text = tr("ยังไม่มีห้องที่เปิดอยู่ตอนนี้", "No rooms open right now"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = palette.textPrimary,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = onCreateRoom,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.primaryContent,
                ),
                contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                modifier = Modifier.testTag("empty_create_room_btn"),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(5.dp))
                Text(
                    text = tr("สร้างห้องใหม่", "Create New Room"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                )
            }
        }
    }
}

@Composable
internal fun CreateHostRoomDialog(
    defaultOwnerName: String,
    palette: MultiplayerPalette,
    onDismiss: () -> Unit,
    onCreate: (roomName: String, ownerName: String, maxPlayers: Int, visibility: RoomVisibility, password: String) -> Unit,
) {
    val isEnglish = isEnglishLanguage()
    val defaultRoomTitle = tr("ฟาร์มชิล Co-op", "Cozy Co-op Farm")
    var roomName by remember { mutableStateOf(defaultRoomTitle) }
    var ownerName by remember { mutableStateOf(defaultOwnerName.ifBlank { "Farmer" }) }
    var maxPlayers by remember { mutableIntStateOf(4) }
    var visibility by remember { mutableStateOf(RoomVisibility.PUBLIC) }
    var password by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Text(
                text = tr("สร้างห้องเล่นด้วยกัน", "Create Co-op Room"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = palette.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = roomName,
                    onValueChange = { roomName = it },
                    singleLine = true,
                    label = { Text(tr("ชื่อห้อง", "Room Name"), fontFamily = NotoSansThaiFontFamily) },
                    textStyle = TextStyle(fontFamily = NotoSansThaiFontFamily, color = palette.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("create_room_name_input"),
                )
                OutlinedTextField(
                    value = ownerName,
                    onValueChange = { ownerName = it },
                    singleLine = true,
                    label = { Text(tr("ชื่อเจ้าของห้อง", "Host Name"), fontFamily = NotoSansThaiFontFamily) },
                    textStyle = TextStyle(fontFamily = NotoSansThaiFontFamily, color = palette.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(
                    text = tr("จำนวนผู้เล่นสูงสุด: $maxPlayers คน", "Max Players: $maxPlayers"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 4, 6, 8).forEach { count ->
                        val selected = maxPlayers == count
                        Surface(
                            onClick = { maxPlayers = count },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) palette.primary else palette.surfaceElevated,
                            border = BorderStroke(0.75.dp, if (selected) palette.primary else palette.border),
                        ) {
                            Text(
                                text = "$count",
                                fontFamily = NotoSansThaiFontFamily,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = if (selected) palette.primaryContent else palette.textPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Text(
                    text = tr("ประเภทห้อง", "Visibility"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                    color = palette.textPrimary,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RoomVisibility.entries.forEach { vis ->
                        val selected = visibility == vis
                        Surface(
                            onClick = { visibility = vis },
                            shape = RoundedCornerShape(8.dp),
                            color = if (selected) palette.primary else palette.surfaceElevated,
                            border = BorderStroke(0.75.dp, if (selected) palette.primary else palette.border),
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = vis.label(isEnglish),
                                fontFamily = NotoSansThaiFontFamily,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = if (selected) palette.primaryContent else palette.textPrimary,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(vertical = 7.dp),
                            )
                        }
                    }
                }

                if (visibility == RoomVisibility.PRIVATE) {
                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        label = { Text(tr("รหัสผ่านเข้าห้อง", "Room Password"), fontFamily = NotoSansThaiFontFamily) },
                        textStyle = TextStyle(fontFamily = NotoSansThaiFontFamily, color = palette.textPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = palette.primary,
                            unfocusedBorderColor = palette.border,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("ยกเลิก", "Cancel"), fontFamily = NotoSansThaiFontFamily, color = palette.textSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreate(roomName, ownerName, maxPlayers, visibility, password) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.primaryContent,
                ),
                modifier = Modifier.testTag("confirm_create_room_btn"),
            ) {
                Text(tr("สร้างห้อง", "Create"), fontFamily = NotoSansThaiFontFamily, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
internal fun JoinByCodeDialog(
    palette: MultiplayerPalette,
    onDismiss: () -> Unit,
    onJoin: (codeOrIp: String) -> Unit,
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
                Text(
                    text = tr("เข้าร่วมด้วยรหัสห้อง", "Join by Room Code"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = palette.textPrimary,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = tr(
                        "กรอกรหัสห้อง (KPZ-XXXXX-XXXXX) หรือ IP:Port ในเครือข่าย Wi-Fi เดียวกัน",
                        "Enter a room code (KPZ-XXXXX-XXXXX) or IP:Port on the same Wi-Fi/LAN.",
                    ),
                    fontFamily = NotoSansThaiFontFamily,
                    fontSize = 11.5.sp,
                    color = palette.textSecondary,
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = {
                        Text("KPZ-XXXXX-XXXXX / 192.168.1.10:24642", fontSize = 12.sp, color = palette.textSecondary)
                    },
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFontFamily, fontSize = 13.sp, color = palette.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("join_code_input"),
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("ยกเลิก", "Cancel"), fontFamily = NotoSansThaiFontFamily, color = palette.textSecondary)
            }
        },
        confirmButton = {
            Button(
                enabled = code.trim().isNotEmpty(),
                onClick = { onJoin(code.trim()) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.primaryContent,
                ),
                modifier = Modifier.testTag("confirm_join_code_btn"),
            ) {
                Text(tr("เชื่อมต่อ", "Connect"), fontFamily = NotoSansThaiFontFamily, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
internal fun PrivateRoomPasswordDialog(
    room: MultiplayerRoom,
    errorMessage: String?,
    palette: MultiplayerPalette,
    onDismiss: () -> Unit,
    onSubmitPassword: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = palette.privateLockRed, modifier = Modifier.size(19.dp))
                Text(
                    text = tr("ห้องส่วนตัว: ${room.roomName}", "Private Room: ${room.roomName}"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = palette.textPrimary,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    label = { Text(tr("กรอกรหัสผ่านห้อง", "Enter Room Password"), fontFamily = NotoSansThaiFontFamily) },
                    isError = errorMessage != null,
                    textStyle = TextStyle(fontFamily = NotoSansThaiFontFamily, color = palette.textPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = palette.primary,
                        unfocusedBorderColor = palette.border,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("private_room_password_input"),
                )
                if (errorMessage != null) {
                    Text(
                        text = errorMessage,
                        fontFamily = NotoSansThaiFontFamily,
                        fontSize = 11.5.sp,
                        color = palette.privateLockRed,
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("ยกเลิก", "Cancel"), fontFamily = NotoSansThaiFontFamily, color = palette.textSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPassword(password) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.primaryContent,
                ),
            ) {
                Text(tr("ยืนยัน", "Confirm"), fontFamily = NotoSansThaiFontFamily, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
internal fun FilterAndSortRoomsDialog(
    initial: MultiplayerAdvancedFilterState,
    palette: MultiplayerPalette,
    onDismiss: () -> Unit,
    onApply: (MultiplayerAdvancedFilterState) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val isEnglish = isEnglishLanguage()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Text(
                text = tr("ตัวกรองและการเรียงลำดับ", "Filter & Sort Rooms"),
                fontFamily = NotoSansThaiFontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = palette.textPrimary,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FilterCheckboxRow(
                    label = tr("ห้องที่ยังไม่เต็ม", "Available slots only"),
                    checked = draft.notFullOnly,
                    palette = palette,
                    onToggle = { draft = draft.copy(notFullOnly = !draft.notFullOnly) },
                )
                FilterCheckboxRow(
                    label = tr("ห้องสาธารณะ", "Public rooms only"),
                    checked = draft.publicOnly,
                    palette = palette,
                    onToggle = { draft = draft.copy(publicOnly = !draft.publicOnly) },
                )
                FilterCheckboxRow(
                    label = tr("ห้องส่วนตัวที่มีสิทธิ์เข้าถึง", "Accessible private rooms"),
                    checked = draft.accessiblePrivateOnly,
                    palette = palette,
                    onToggle = { draft = draft.copy(accessiblePrivateOnly = !draft.accessiblePrivateOnly) },
                )
                FilterCheckboxRow(
                    label = tr("ห้องที่ใช้เวอร์ชันเกมตรงกัน", "Matching game version"),
                    checked = draft.matchingVersionOnly,
                    palette = palette,
                    onToggle = { draft = draft.copy(matchingVersionOnly = !draft.matchingVersionOnly) },
                )

                Spacer(Modifier.height(6.dp))
                Text(
                    text = tr("เรียงตาม", "Sort by"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.5.sp,
                    color = palette.textPrimary,
                )
                MultiplayerSortOption.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { draft = draft.copy(sortOption = option) }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(
                            selected = draft.sortOption == option,
                            onClick = { draft = draft.copy(sortOption = option) },
                            colors = RadioButtonDefaults.colors(selectedColor = palette.primary),
                        )
                        Text(
                            text = option.label(isEnglish),
                            fontFamily = NotoSansThaiFontFamily,
                            fontSize = 13.sp,
                            color = palette.textPrimary,
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { onApply(MultiplayerAdvancedFilterState()) }) {
                Text(tr("รีเซ็ต", "Reset"), fontFamily = NotoSansThaiFontFamily, color = palette.textSecondary)
            }
        },
        confirmButton = {
            Button(
                onClick = { onApply(draft) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = palette.primary,
                    contentColor = palette.primaryContent,
                ),
            ) {
                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text(tr("ใช้งาน", "Apply"), fontFamily = NotoSansThaiFontFamily, fontWeight = FontWeight.Bold)
            }
        },
    )
}

@Composable
private fun FilterCheckboxRow(
    label: String,
    checked: Boolean,
    palette: MultiplayerPalette,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = palette.primary,
                checkmarkColor = palette.primaryContent,
            ),
        )
        Text(
            text = label,
            fontFamily = NotoSansThaiFontFamily,
            fontSize = 12.5.sp,
            color = palette.textPrimary,
        )
    }
}

@Composable
internal fun CoopPlayHistoryDialog(
    history: List<CoopHistoryEntity>,
    palette: MultiplayerPalette,
    onClearHistory: () -> Unit,
    onDismiss: () -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Rounded.History, contentDescription = null, tint = palette.primary, modifier = Modifier.size(20.dp))
                Text(
                    text = tr("ประวัติการเล่น Co-op", "Co-op Play History"),
                    fontFamily = NotoSansThaiFontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = palette.textPrimary,
                )
            }
        },
        text = {
            if (history.isEmpty()) {
                Text(
                    text = tr("ยังไม่มีประวัติการสร้างหรือเข้าร่วมห้อง", "No co-op rooms joined or hosted yet."),
                    fontFamily = NotoSansThaiFontFamily,
                    fontSize = 12.5.sp,
                    color = palette.textSecondary,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(240.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(history, key = { it.id }) { item ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = palette.surfaceElevated,
                            border = BorderStroke(0.75.dp, palette.border),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text(
                                        text = item.roomName,
                                        fontFamily = NotoSansThaiFontFamily,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = palette.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    Text(
                                        text = "${item.role} • ${item.ownerName} • ${formatter.format(Date(item.playedAtMs))}",
                                        fontFamily = NotoSansThaiFontFamily,
                                        fontSize = 11.sp,
                                        color = palette.textSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                                item.pingMs?.let { ms ->
                                    Text(
                                        text = "$ms ms",
                                        fontFamily = NotoSansThaiFontFamily,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = palette.primary,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        dismissButton = {
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Icon(Icons.Rounded.DeleteSweep, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(tr("ล้างประวัติ", "Clear"), fontFamily = NotoSansThaiFontFamily, color = palette.privateLockRed)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(tr("ปิด", "Close"), fontFamily = NotoSansThaiFontFamily, color = palette.primary)
            }
        },
    )
}
