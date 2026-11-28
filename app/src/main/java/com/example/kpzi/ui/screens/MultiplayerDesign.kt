package com.example.kpzi.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.kpzi.engine.multiplayer.MultiplayerRoom
import com.example.kpzi.engine.multiplayer.RoomVisibility
import com.example.kpzi.engine.multiplayer.SignalQuality

internal data class MultiplayerPalette(
    val primary: Color,
    val primaryContent: Color,
    val primarySoftBg: Color,
    val primarySoftText: Color,
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val chipBg: Color,
    val fullButtonBg: Color,
    val fullButtonText: Color,
    val crownGold: Color,
    val privateLockBg: Color,
    val privateLockRed: Color,
    val publicGlobePurple: Color,
    val signalGood: Color,
    val signalFair: Color,
    val signalPoor: Color,
    val signalMuted: Color,
    val starGold: Color,
    val isDark: Boolean,
)

internal fun multiplayerPalette(isDark: Boolean): MultiplayerPalette = if (isDark) {
    MultiplayerPalette(
        primary = Color(0xFF65C438),
        primaryContent = Color(0xFF0A1407),
        primarySoftBg = Color(0xFF1B2B16),
        primarySoftText = Color(0xFF8BE25C),
        background = Color(0xFF0E1110),
        surface = Color(0xFF171B19),
        surfaceElevated = Color(0xFF1F2422),
        border = Color(0xFF282E2B),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFF9CA3AF),
        chipBg = Color(0xFF222725),
        fullButtonBg = Color(0xFF2C312E),
        fullButtonText = Color(0xFF9CA3AF),
        crownGold = Color(0xFFFACC15),
        privateLockBg = Color(0xFF3A1D24),
        privateLockRed = Color(0xFFF87171),
        publicGlobePurple = Color(0xFFA78BFA),
        signalGood = Color(0xFF65C438),
        signalFair = Color(0xFFFBBF24),
        signalPoor = Color(0xFFEF4444),
        signalMuted = Color(0xFF4B5563),
        starGold = Color(0xFFFBBF24),
        isDark = true,
    )
} else {
    MultiplayerPalette(
        primary = Color(0xFF7C3AED),
        primaryContent = Color(0xFFFFFFFF),
        primarySoftBg = Color(0xFFF1EBFE),
        primarySoftText = Color(0xFF6D28D9),
        background = Color(0xFFF8F7FC),
        surface = Color(0xFFFFFFFF),
        surfaceElevated = Color(0xFFF3F0FA),
        border = Color(0xFFE7E2F3),
        textPrimary = Color(0xFF18181B),
        textSecondary = Color(0xFF71717A),
        chipBg = Color(0xFFF2EFFA),
        fullButtonBg = Color(0xFFE4E4E7),
        fullButtonText = Color(0xFF71717A),
        crownGold = Color(0xFFD97706),
        privateLockBg = Color(0xFFFEE2E2),
        privateLockRed = Color(0xFFDC2626),
        publicGlobePurple = Color(0xFF7C3AED),
        signalGood = Color(0xFF16A34A),
        signalFair = Color(0xFFD97706),
        signalPoor = Color(0xFFDC2626),
        signalMuted = Color(0xFFD4D4D8),
        starGold = Color(0xFFF59E0B),
        isDark = false,
    )
}

internal enum class MultiplayerTabFilter(val labelTh: String, val labelEn: String) {
    ALL("ทั้งหมด", "All"),
    FRIENDS("เพื่อน", "Friends"),
    PUBLIC("สาธารณะ", "Public");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
}

internal enum class MultiplayerSortOption(val labelTh: String, val labelEn: String) {
    PING("ค่า Ping", "Ping"),
    PLAYERS("จำนวนผู้เล่น", "Players"),
    LATEST("ห้องล่าสุด", "Latest");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
}

internal data class MultiplayerAdvancedFilterState(
    val notFullOnly: Boolean = false,
    val publicOnly: Boolean = false,
    val accessiblePrivateOnly: Boolean = false,
    val matchingVersionOnly: Boolean = false,
    val sortOption: MultiplayerSortOption = MultiplayerSortOption.LATEST,
) {
    val activeFilterCount: Int
        get() = listOf(notFullOnly, publicOnly, accessiblePrivateOnly, matchingVersionOnly).count { it }
}

internal fun filterAndSortRooms(
    rooms: List<MultiplayerRoom>,
    tabFilter: MultiplayerTabFilter,
    advancedFilter: MultiplayerAdvancedFilterState,
    searchQuery: String,
    localGameVersion: String,
): List<MultiplayerRoom> {
    val q = searchQuery.trim().lowercase()
    val filtered = rooms.filter { room ->
        val matchesTab = when (tabFilter) {
            MultiplayerTabFilter.ALL -> true
            MultiplayerTabFilter.FRIENDS -> room.visibility == RoomVisibility.FRIENDS
            MultiplayerTabFilter.PUBLIC -> room.visibility == RoomVisibility.PUBLIC
        }
        if (!matchesTab) return@filter false

        if (q.isNotEmpty()) {
            val hit = room.roomName.lowercase().contains(q) ||
                room.ownerName.lowercase().contains(q) ||
                room.roomCode.lowercase().contains(q)
            if (!hit) return@filter false
        }

        if (advancedFilter.notFullOnly && room.isFull) return@filter false
        if (advancedFilter.publicOnly && room.visibility != RoomVisibility.PUBLIC) return@filter false
        if (advancedFilter.accessiblePrivateOnly &&
            !(room.visibility == RoomVisibility.PRIVATE && (room.isOwnHostRoom || !room.requiresPassword || room.isUserAuthorized))
        ) {
            return@filter false
        }
        if (advancedFilter.matchingVersionOnly && localGameVersion.isNotBlank() &&
            room.gameVersion.isNotBlank() &&
            !room.gameVersion.equals(localGameVersion, ignoreCase = true)
        ) {
            return@filter false
        }
        true
    }

    return when (advancedFilter.sortOption) {
        MultiplayerSortOption.PING -> filtered.sortedWith(
            compareBy<MultiplayerRoom> { it.pingMs ?: Int.MAX_VALUE }
                .thenByDescending { it.isOwnHostRoom },
        )
        MultiplayerSortOption.PLAYERS -> filtered.sortedWith(
            compareByDescending<MultiplayerRoom> { it.currentPlayers }
                .thenBy { it.pingMs ?: Int.MAX_VALUE },
        )
        MultiplayerSortOption.LATEST -> filtered.sortedWith(
            compareByDescending<MultiplayerRoom> { it.isOwnHostRoom }
                .thenByDescending { it.createdAtMs },
        )
    }
}

@Composable
internal fun SignalBarsIndicator(
    quality: SignalQuality,
    palette: MultiplayerPalette,
    modifier: Modifier = Modifier,
) {
    val activeBars = when (quality) {
        SignalQuality.EXCELLENT -> 4
        SignalQuality.GOOD -> 3
        SignalQuality.FAIR -> 2
        SignalQuality.POOR -> 1
        SignalQuality.UNKNOWN -> 0
    }
    val activeColor = when (quality) {
        SignalQuality.EXCELLENT, SignalQuality.GOOD -> palette.signalGood
        SignalQuality.FAIR -> palette.signalFair
        SignalQuality.POOR -> palette.signalPoor
        SignalQuality.UNKNOWN -> palette.signalMuted
    }

    Canvas(modifier = modifier.size(width = 14.dp, height = 12.dp)) {
        val barWidth = size.width / 5.5f
        val gap = (size.width - barWidth * 4f) / 3f
        val fractions = listOf(0.32f, 0.54f, 0.76f, 1.0f)
        fractions.forEachIndexed { idx, frac ->
            val h = size.height * frac
            val x = idx * (barWidth + gap)
            val y = size.height - h
            drawRoundRect(
                color = if (idx < activeBars) activeColor else palette.signalMuted,
                topLeft = Offset(x, y),
                size = Size(barWidth, h),
                cornerRadius = CornerRadius(1.5f, 1.5f),
            )
        }
    }
}
