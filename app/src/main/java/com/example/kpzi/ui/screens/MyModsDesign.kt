package com.example.kpzi.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Agriculture
import androidx.compose.material.icons.rounded.Checkroom
import androidx.compose.material.icons.rounded.Construction
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.FontDownload
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.HomeWork
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Park
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.example.kpzi.domain.model.InstalledMod
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

internal data class MyModsPalette(
    val primary: Color,
    val primarySoftBg: Color,
    val primarySoftText: Color,
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val deleteRed: Color,
    val deleteRedBg: Color,
    val progressTrack: Color,
    val disabledBar: Color,
    val hillBack: Color,
    val hillMid: Color,
    val hillFront: Color,
    val windmillColor: Color,
    val isDark: Boolean,
)

internal fun myModsPalette(isDark: Boolean): MyModsPalette = if (isDark) {
    MyModsPalette(
        primary = Color(0xFF9F67FF),
        primarySoftBg = Color(0xFF281E42),
        primarySoftText = Color(0xFFD7C6FF),
        background = Color(0xFF13111B),
        surface = Color(0xFF1D1929),
        surfaceSubtle = Color(0xFF252035),
        border = Color(0xFF322B47),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFFA1A1AA),
        deleteRed = Color(0xFFF87171),
        deleteRedBg = Color(0xFF3D1D24),
        progressTrack = Color(0xFF33294D),
        disabledBar = Color(0xFF47415C),
        hillBack = Color(0xFF221C35).copy(alpha = 0.65f),
        hillMid = Color(0xFF2A2242).copy(alpha = 0.72f),
        hillFront = Color(0xFF31274D).copy(alpha = 0.80f),
        windmillColor = Color(0xFF433568).copy(alpha = 0.75f),
        isDark = true,
    )
} else {
    MyModsPalette(
        primary = Color(0xFF7C3AED),
        primarySoftBg = Color(0xFFF1EBFE),
        primarySoftText = Color(0xFF6D28D9),
        background = Color(0xFFF8F7FC),
        surface = Color(0xFFFFFFFF),
        surfaceSubtle = Color(0xFFF3F0FA),
        border = Color(0xFFE7E2F3),
        textPrimary = Color(0xFF18181B),
        textSecondary = Color(0xFF71717A),
        deleteRed = Color(0xFFEF4444),
        deleteRedBg = Color(0xFFFEF2F2),
        progressTrack = Color(0xFFE9E2FA),
        disabledBar = Color(0xFFD4D4D8),
        hillBack = Color(0xFFEDE8FA).copy(alpha = 0.80f),
        hillMid = Color(0xFFE3DBFA).copy(alpha = 0.82f),
        hillFront = Color(0xFFD8CEFA).copy(alpha = 0.85f),
        windmillColor = Color(0xFFC9BAF7).copy(alpha = 0.85f),
        isDark = false,
    )
}

internal enum class MyModsFilterOption(val labelTh: String, val labelEn: String) {
    ALL("ทั้งหมด", "All"),
    ENABLED("เปิดใช้งาน", "Enabled"),
    DISABLED("ปิดใช้งาน", "Disabled");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
}

internal enum class MyModsSortOption(val labelTh: String, val labelEn: String) {
    LATEST("ล่าสุด", "Latest"),
    NAME_AZ("ชื่อ A–Z", "Name A–Z"),
    SIZE_DESC("ขนาดไฟล์", "File Size"),
    STATUS("สถานะ", "Status");

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
}

internal enum class MyModsCategory(
    val labelTh: String,
    val labelEn: String,
    val icon: ImageVector,
    val lightBg: Color,
    val lightFg: Color,
    val darkBg: Color,
    val darkFg: Color,
) {
    FRAMEWORK(
        labelTh = "ระบบ",
        labelEn = "Framework",
        icon = Icons.Rounded.Layers,
        lightBg = Color(0xFFF1EBFE),
        lightFg = Color(0xFF6D28D9),
        darkBg = Color(0xFF2D214A),
        darkFg = Color(0xFFD8C6FF),
    ),
    THAI_LANG(
        labelTh = "ภาษาไทย",
        labelEn = "Thai Lang",
        icon = Icons.Rounded.Translate,
        lightBg = Color(0xFFE0F2FE),
        lightFg = Color(0xFF0369A1),
        darkBg = Color(0xFF173046),
        darkFg = Color(0xFF7DD3FC),
    ),
    VOICE_AUDIO(
        labelTh = "เสียงพากย์",
        labelEn = "Voice/Audio",
        icon = Icons.Rounded.GraphicEq,
        lightBg = Color(0xFFDCFCE7),
        lightFg = Color(0xFF15803D),
        darkBg = Color(0xFF183626),
        darkFg = Color(0xFF86EFAC),
    ),
    FONTS(
        labelTh = "ฟอนต์",
        labelEn = "Fonts",
        icon = Icons.Rounded.FontDownload,
        lightBg = Color(0xFFFEF3C7),
        lightFg = Color(0xFFB45309),
        darkBg = Color(0xFF3B2B14),
        darkFg = Color(0xFFFCD34D),
    ),
    CHARACTERS(
        labelTh = "ตัวละคร",
        labelEn = "Characters",
        icon = Icons.Rounded.Face,
        lightBg = Color(0xFFFCE7F3),
        lightFg = Color(0xFFBE185D),
        darkBg = Color(0xFF3E1C2E),
        darkFg = Color(0xFFF9A8D4),
    ),
    PETS(
        labelTh = "สัตว์เลี้ยง",
        labelEn = "Pets",
        icon = Icons.Rounded.Pets,
        lightBg = Color(0xFFFFEDD5),
        lightFg = Color(0xFFC2410C),
        darkBg = Color(0xFF3D2414),
        darkFg = Color(0xFFFDBA74),
    ),
    BUILDINGS(
        labelTh = "สิ่งปลูกสร้าง",
        labelEn = "Buildings",
        icon = Icons.Rounded.HomeWork,
        lightBg = Color(0xFFE0E7FF),
        lightFg = Color(0xFF4338CA),
        darkBg = Color(0xFF21244A),
        darkFg = Color(0xFFA5B4FC),
    ),
    FARM(
        labelTh = "ฟาร์ม",
        labelEn = "Farm",
        icon = Icons.Rounded.Agriculture,
        lightBg = Color(0xFFECFCCB),
        lightFg = Color(0xFF4D7C0F),
        darkBg = Color(0xFF223616),
        darkFg = Color(0xFFBEF264),
    ),
    NATURE(
        labelTh = "ธรรมชาติ",
        labelEn = "Nature",
        icon = Icons.Rounded.Park,
        lightBg = Color(0xFFD1FAE5),
        lightFg = Color(0xFF047857),
        darkBg = Color(0xFF15382C),
        darkFg = Color(0xFF6EE7B7),
    ),
    WEAPONS_TOOLS(
        labelTh = "อาวุธ/เครื่องมือ",
        labelEn = "Tools/Gear",
        icon = Icons.Rounded.Construction,
        lightBg = Color(0xFFFEE2E2),
        lightFg = Color(0xFFB91C1C),
        darkBg = Color(0xFF3E1C20),
        darkFg = Color(0xFFFCA5A5),
    ),
    CLOTHING(
        labelTh = "เสื้อผ้า",
        labelEn = "Clothing",
        icon = Icons.Rounded.Checkroom,
        lightBg = Color(0xFFF5D0FE),
        lightFg = Color(0xFF86198F),
        darkBg = Color(0xFF3B1B42),
        darkFg = Color(0xFFF0ABFC),
    ),
    MAPS(
        labelTh = "แผนที่",
        labelEn = "Maps",
        icon = Icons.Rounded.Map,
        lightBg = Color(0xFFCCFBF1),
        lightFg = Color(0xFF0F766E),
        darkBg = Color(0xFF143634),
        darkFg = Color(0xFF5EEAD4),
    ),
    FOOD(
        labelTh = "อาหาร",
        labelEn = "Food",
        icon = Icons.Rounded.Restaurant,
        lightBg = Color(0xFFFFE4E6),
        lightFg = Color(0xFFBE123C),
        darkBg = Color(0xFF3D1924),
        darkFg = Color(0xFFFDA4AF),
    ),
    GRAPHICS(
        labelTh = "กราฟิก",
        labelEn = "Graphics",
        icon = Icons.Rounded.Palette,
        lightBg = Color(0xFFF3E8FF),
        lightFg = Color(0xFF7E22CE),
        darkBg = Color(0xFF2E1E46),
        darkFg = Color(0xFFD8B4FE),
    );

    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
    fun bgColor(isDark: Boolean): Color = if (isDark) darkBg else lightBg
    fun fgColor(isDark: Boolean): Color = if (isDark) darkFg else lightFg
}

internal data class ModRoleTag(
    val labelTh: String,
    val labelEn: String,
    val bgLight: Color = Color(0xFFF4F4F5),
    val fgLight: Color = Color(0xFF52525B),
    val bgDark: Color = Color(0xFF27272A),
    val fgDark: Color = Color(0xFFD4D4D8),
) {
    fun label(isEnglish: Boolean): String = if (isEnglish) labelEn else labelTh
    fun bg(isDark: Boolean): Color = if (isDark) bgDark else bgLight
    fun fg(isDark: Boolean): Color = if (isDark) fgDark else fgLight
}

internal fun detectModCategory(mod: InstalledMod): MyModsCategory {
    val text = buildString {
        append(mod.manifest.name)
        append(' ')
        append(mod.uniqueId)
        append(' ')
        append(mod.manifest.description)
        append(' ')
        append(mod.folderName)
    }.lowercase(Locale.ROOT)

    return when {
        text.contains("dub") || text.contains("voice") || text.contains("audio") ||
            text.contains("sound") || text.contains("music") || text.contains("พากย์") ||
            text.contains("เสียง") -> MyModsCategory.VOICE_AUDIO

        text.contains("font") || text.contains("typeface") || text.contains("ฟอนต์") ||
            text.contains("อักษร") -> MyModsCategory.FONTS

        text.contains("thai") || text.contains("ภาษาไทย") || text.contains("แปลไทย") ||
            text.contains("th_") || text.endsWith(".th") || text.contains("- th") -> MyModsCategory.THAI_LANG

        text.contains("pet") || text.contains("cat") || text.contains("dog") ||
            text.contains("horse") || text.contains("chicken") || text.contains("cow") ||
            text.contains("animal") || text.contains("สัตว์") -> MyModsCategory.PETS

        text.contains("portrait") || text.contains("npc") || text.contains("character") ||
            text.contains("dialogue") || text.contains("hair") || text.contains("ตัวละคร") ||
            text.contains("sve") && text.contains("npc") -> MyModsCategory.CHARACTERS

        text.contains("cloth") || text.contains("fashion") || text.contains("outfit") ||
            text.contains("dress") || text.contains("hat") || text.contains("shirt") ||
            text.contains("เสื้อ") || text.contains("ชุด") -> MyModsCategory.CLOTHING

        text.contains("weapon") || text.contains("sword") || text.contains("tool") ||
            text.contains("axe") || text.contains("pickaxe") || text.contains("tractor") ||
            text.contains("อาวุธ") || text.contains("เครื่องมือ") -> MyModsCategory.WEAPONS_TOOLS

        text.contains("food") || text.contains("cook") || text.contains("recipe") ||
            text.contains("cake") || text.contains("crop") && text.contains("dish") ||
            text.contains("อาหาร") || text.contains("ขนม") -> MyModsCategory.FOOD

        text.contains("building") || text.contains("house") || text.contains("farmhouse") ||
            text.contains("cabin") || text.contains("barn") || text.contains("silo") ||
            text.contains("greenhouse") || text.contains("บ้าน") || text.contains("อาคาร") -> MyModsCategory.BUILDINGS

        text.contains("map") || text.contains("expanded") || text.contains("ridgeline") ||
            text.contains("ridgeside") || text.contains("east scarp") || text.contains("location") ||
            text.contains("แผนที่") -> MyModsCategory.MAPS

        text.contains("tree") || text.contains("foliage") || text.contains("nature") ||
            text.contains("flower") || text.contains("season") || text.contains("forest") ||
            text.contains("ต้นไม้") || text.contains("ธรรมชาติ") -> MyModsCategory.NATURE

        text.contains("farm") || text.contains("crop") || text.contains("harvest") ||
            text.contains("automate") || text.contains("sprinkler") || text.contains("ฟาร์ม") -> MyModsCategory.FARM

        text.contains("recolor") || text.contains("shader") || text.contains("visual") ||
            text.contains("texture") || text.contains("lighting") || text.contains("ui ") ||
            text.contains("interface") || text.contains("กราฟิก") -> MyModsCategory.GRAPHICS

        else -> MyModsCategory.FRAMEWORK
    }
}

internal fun detectSecondaryRoleTag(
    mod: InstalledMod,
    category: MyModsCategory,
    allMods: List<InstalledMod>,
): ModRoleTag {
    val isDependedOnByOthers = allMods.any { other ->
        !other.uniqueId.equals(mod.uniqueId, ignoreCase = true) && (
            other.manifest.contentPackForUniqueId.equals(mod.uniqueId, ignoreCase = true) ||
                other.manifest.dependencies.any { it.uniqueId.equals(mod.uniqueId, ignoreCase = true) }
            )
    }
    return when {
        isDependedOnByOthers || mod.uniqueId.contains("ContentPatcher", ignoreCase = true) ||
            mod.uniqueId.contains("SpaceCore", ignoreCase = true) -> ModRoleTag(
            labelTh = "มอดหลัก",
            labelEn = "Core Mod",
            bgLight = Color(0xFFF1EBFE),
            fgLight = Color(0xFF6D28D9),
            bgDark = Color(0xFF2D214A),
            fgDark = Color(0xFFD8C6FF),
        )
        category == MyModsCategory.THAI_LANG -> ModRoleTag("แปลภาษา", "Translation")
        category == MyModsCategory.VOICE_AUDIO -> ModRoleTag("เสียง", "Audio")
        category == MyModsCategory.FONTS -> ModRoleTag("ปรับแต่ง", "Typography")
        !mod.manifest.contentPackForUniqueId.isNullOrBlank() -> ModRoleTag("Content Pack", "Content Pack")
        !mod.manifest.entryDll.isNullOrBlank() -> ModRoleTag("SMAPI DLL", "SMAPI DLL")
        else -> ModRoleTag("ปรับแต่ง", "Addon")
    }
}

internal fun findDependentMods(
    targetMod: InstalledMod,
    allMods: List<InstalledMod>,
): List<InstalledMod> {
    return allMods.filter { other ->
        !other.uniqueId.equals(targetMod.uniqueId, ignoreCase = true) && (
            other.manifest.contentPackForUniqueId.equals(targetMod.uniqueId, ignoreCase = true) ||
                other.manifest.dependencies.any { it.uniqueId.equals(targetMod.uniqueId, ignoreCase = true) && it.isRequired }
            )
    }
}

internal fun formatMyModsSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) {
        return if (kb >= 100.0) String.format(Locale.US, "%.0f KB", kb)
        else String.format(Locale.US, "%.1f KB", kb)
    }
    val mb = kb / 1024.0
    if (mb < 1024.0) {
        return String.format(Locale.US, "%.2f MB", mb)
    }
    val gb = mb / 1024.0
    return String.format(Locale.US, "%.2f GB", gb)
}

internal fun formatDeviceCapacity(bytes: Long): String {
    if (bytes <= 0L) return "0 GB"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    return if (gb >= 10.0) String.format(Locale.US, "%.0f GB", gb)
    else String.format(Locale.US, "%.1f GB", gb)
}

internal fun formatStoragePercent(usedBytes: Long, totalBytes: Long): String {
    if (usedBytes <= 0L || totalBytes <= 0L) return "0%"
    val pct = (usedBytes.toDouble() / totalBytes.toDouble()) * 100.0
    return when {
        pct < 0.01 -> "<0.01%"
        pct < 1.0 -> String.format(Locale.US, "%.2f%%", pct)
        else -> String.format(Locale.US, "%.1f%%", pct)
    }
}

@Composable
internal fun KpziPurpleSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    palette: MyModsPalette,
    modifier: Modifier = Modifier,
) {
    val trackColor by animateColorAsState(
        targetValue = if (checked) palette.primary else if (palette.isDark) Color(0xFF3B3452) else Color(0xFFDCD8E8),
        animationSpec = tween(durationMillis = 180),
        label = "mymods_switch_track",
    )
    val thumbOffset by animateDpAsState(
        targetValue = if (checked) 18.dp else 2.dp,
        animationSpec = tween(durationMillis = 180),
        label = "mymods_switch_thumb",
    )

    Box(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(width = 38.dp, height = 22.dp)
                .clip(RoundedCornerShape(50))
                .background(trackColor),
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                modifier = Modifier
                    .offset(x = thumbOffset)
                    .size(18.dp)
                    .shadow(1.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

/**
 * Subtle pixel-art lavender farm, trees, and windmill silhouette anchored at the bottom background.
 * Keeps upper reading areas uncluttered and adapts cleanly to both Light and Dark themes.
 */
@Composable
internal fun PixelArtFarmSilhouette(
    palette: MyModsPalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val px = (w / 72f).coerceAtLeast(3f)

        fun snap(v: Float): Float = ((v / px).toInt()) * px

        fun DrawScope.pixelRect(x: Float, y: Float, rw: Float, rh: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(snap(x), snap(y)),
                size = Size(snap(rw).coerceAtLeast(px), snap(rh).coerceAtLeast(px)),
            )
        }

        // Back rolling pixel hills
        val backPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.56f)
            quadraticTo(w * 0.25f, h * 0.42f, w * 0.52f, h * 0.52f)
            quadraticTo(w * 0.78f, h * 0.60f, w, h * 0.44f)
            lineTo(w, h)
            close()
        }
        drawPath(backPath, palette.hillBack)

        // Mid rolling hills
        val midPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.68f)
            quadraticTo(w * 0.30f, h * 0.60f, w * 0.58f, h * 0.72f)
            quadraticTo(w * 0.82f, h * 0.80f, w, h * 0.66f)
            lineTo(w, h)
            close()
        }
        drawPath(midPath, palette.hillMid)

        // Pixel pine & round trees on left & right horizon
        val treeSpots = listOf(0.06f, 0.14f, 0.21f, 0.36f, 0.62f, 0.88f, 0.94f)
        treeSpots.forEachIndexed { idx, frac ->
            val tx = w * frac
            val ty = h * (if (idx % 2 == 0) 0.50f else 0.56f)
            val treeColor = if (idx % 2 == 0) palette.hillFront else palette.windmillColor
            pixelRect(tx - px * 0.4f, ty + px * 3f, px * 1.1f, px * 2.5f, treeColor)
            pixelRect(tx - px * 1.8f, ty + px * 1f, px * 4f, px * 2.2f, treeColor)
            pixelRect(tx - px * 1.2f, ty - px * 0.6f, px * 2.8f, px * 2f, treeColor)
        }

        // Pixel Windmill on right-center hill (matching reference artwork)
        val millX = snap(w * 0.75f)
        val millBaseY = snap(h * 0.68f)
        val millTopY = snap(h * 0.36f)
        // Tower body
        pixelRect(millX - px * 2f, millTopY + px * 2f, px * 4.5f, millBaseY - millTopY, palette.windmillColor)
        // Conical pixel roof
        pixelRect(millX - px * 2.5f, millTopY + px, px * 5.5f, px * 1.4f, palette.windmillColor)
        pixelRect(millX - px * 1.5f, millTopY, px * 3.5f, px * 1.2f, palette.windmillColor)
        // Windmill window cutouts
        pixelRect(millX - px * 0.3f, millTopY + px * 4.5f, px, px * 1.4f, palette.background)
        // Four diagonal pixel blades
        val hubX = millX + px * 0.2f
        val hubY = millTopY + px * 2.2f
        val bladeAngles = listOf(35.0, 125.0, 215.0, 305.0)
        bladeAngles.forEach { deg ->
            val rad = Math.toRadians(deg)
            for (step in 1..6) {
                val bx = hubX + (cos(rad) * step * px * 1.1f).toFloat()
                val by = hubY + (sin(rad) * step * px * 1.1f).toFloat()
                pixelRect(bx, by, px * 1.15f, px * 1.15f, palette.windmillColor)
            }
        }

        // Foreground soft meadow band
        val frontPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.78f)
            quadraticTo(w * 0.45f, h * 0.86f, w, h * 0.74f)
            lineTo(w, h)
            close()
        }
        drawPath(frontPath, palette.hillFront)
    }
}
