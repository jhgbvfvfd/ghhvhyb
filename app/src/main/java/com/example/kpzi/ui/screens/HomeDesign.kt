package com.example.kpzi.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

internal data class HomePalette(
    val primary: Color,
    val primarySoftBg: Color,
    val primarySoftText: Color,
    val background: Color,
    val surface: Color,
    val surfaceSubtle: Color,
    val border: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val success: Color,
    val successSoftBg: Color,
    val successSoftText: Color,
    val warning: Color,
    val warningSoftBg: Color,
    val blueClock: Color,
    val goldTrophy: Color,
    val error: Color,
    val progressTrack: Color,
    val hillBack: Color,
    val hillMid: Color,
    val hillFront: Color,
    val windmillColor: Color,
    val fenceColor: Color,
    val isDark: Boolean,
)

internal fun homePalette(isDark: Boolean): HomePalette = if (isDark) {
    HomePalette(
        primary = Color(0xFF9F67FF),
        primarySoftBg = Color(0xFF281E42),
        primarySoftText = Color(0xFFD7C6FF),
        background = Color(0xFF13111B),
        surface = Color(0xFF1D1929),
        surfaceSubtle = Color(0xFF252035),
        border = Color(0xFF322B47),
        textPrimary = Color(0xFFF4F4F5),
        textSecondary = Color(0xFFA1A1AA),
        success = Color(0xFF22C55E),
        successSoftBg = Color(0xFF173623),
        successSoftText = Color(0xFF86EFAC),
        warning = Color(0xFFFBBF24),
        warningSoftBg = Color(0xFF3A2B12),
        blueClock = Color(0xFF60A5FA),
        goldTrophy = Color(0xFFFBBF24),
        error = Color(0xFFF87171),
        progressTrack = Color(0xFF33294D),
        hillBack = Color(0xFF221C35).copy(alpha = 0.65f),
        hillMid = Color(0xFF2A2242).copy(alpha = 0.72f),
        hillFront = Color(0xFF31274D).copy(alpha = 0.80f),
        windmillColor = Color(0xFF433568).copy(alpha = 0.75f),
        fenceColor = Color(0xFF3D315E).copy(alpha = 0.72f),
        isDark = true,
    )
} else {
    HomePalette(
        primary = Color(0xFF7C3AED),
        primarySoftBg = Color(0xFFF1EBFE),
        primarySoftText = Color(0xFF6D28D9),
        background = Color(0xFFF8F7FC),
        surface = Color(0xFFFFFFFF),
        surfaceSubtle = Color(0xFFF3F0FA),
        border = Color(0xFFEDE9F5),
        textPrimary = Color(0xFF18181B),
        textSecondary = Color(0xFF71717A),
        success = Color(0xFF22C55E),
        successSoftBg = Color(0xFFDCFCE7),
        successSoftText = Color(0xFF15803D),
        warning = Color(0xFFD97706),
        warningSoftBg = Color(0xFFFEF3C7),
        blueClock = Color(0xFF3B82F6),
        goldTrophy = Color(0xFFF59E0B),
        error = Color(0xFFEF4444),
        progressTrack = Color(0xFFE9E2FA),
        hillBack = Color(0xFFEDE8FA).copy(alpha = 0.80f),
        hillMid = Color(0xFFE3DBFA).copy(alpha = 0.82f),
        hillFront = Color(0xFFD8CEFA).copy(alpha = 0.85f),
        windmillColor = Color(0xFFC9BAF7).copy(alpha = 0.85f),
        fenceColor = Color(0xFFCEBFF8).copy(alpha = 0.82f),
        isDark = false,
    )
}

internal fun Modifier.kpziPressClickable(
    enabled: Boolean = true,
    role: Role = Role.Button,
    onClick: () -> Unit,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val isPressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.95f else 1f,
        animationSpec = tween(110),
        label = "kpzi_press_scale",
    )
    this
        .scale(scale)
        .clickable(
            enabled = enabled,
            interactionSource = interaction,
            indication = null,
            role = role,
            onClick = onClick,
        )
}

/**
 * Cached 20x20 Stardew-inspired Pixel Art farmer portrait for the SMAPI launch card.
 */
internal object HomePixelAvatarCache {
    private val cache = LruCache<String, ImageBitmap>(4)

    fun getFarmerAvatar(): ImageBitmap {
        cache.get("farmer")?.let { return it }
        val bmp = renderFarmerBitmap().asImageBitmap()
        cache.put("farmer", bmp)
        return bmp
    }

    private fun renderFarmerBitmap(): Bitmap {
        val size = 20
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = false }

        // Soft pixel meadow backdrop
        for (y in 0 until size) {
            for (x in 0 until size) {
                paint.color = when {
                    y < 11 -> if (y < 6) 0xFF7DD3FC.toInt() else 0xFFBAE6FD.toInt()
                    y < 15 -> 0xFF86EFAC.toInt()
                    else -> 0xFF4ADE80.toInt()
                }
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }

        // Crisp farmer sprite centered inside 20x20 canvas
        val sprite = listOf(
            "......OOOOOO........",
            ".....OHHHHHHO.......",
            "....OHHYYYYHHO......",
            "...OOHHHHHHHHOO.....",
            "..OOCCCCCCCCCCOO....",
            "...OOBBBBBBBBOO.....",
            "....OSSSOOSSSO......",
            "....OSSOSSOSSO......",
            "....OSSSSSSSSO......",
            ".....OSSOOSSO.......",
            ".....OBBBBBBO.......",
            "....OORRRRRROO......",
            "...ORRDDDDDDRRO.....",
            "...ORRDYYYYDRRO.....",
            "...ORRDDDDDDRRO.....",
            "....OODDDDDDOO......",
            ".....OBB..BBO.......",
            ".....OBB..BBO.......",
        )
        val palette = mapOf(
            'O' to 0xFF291D18.toInt(),
            'H' to 0xFFFACC15.toInt(),
            'Y' to 0xFFFDE047.toInt(),
            'C' to 0xFFB45309.toInt(),
            'B' to 0xFF78350F.toInt(),
            'S' to 0xFFFED7AA.toInt(),
            'R' to 0xFFEA580C.toInt(),
            'D' to 0xFF2563EB.toInt(),
        )
        sprite.forEachIndexed { yIdx, row ->
            val y = yIdx + 1
            if (y >= size) return@forEachIndexed
            row.forEachIndexed { x, ch ->
                if (x >= size || ch == '.') return@forEachIndexed
                val c = palette[ch] ?: return@forEachIndexed
                paint.color = c
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }
        return bmp
    }
}

@Composable
internal fun PixelArtFarmerAvatar(
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    val avatar = remember { HomePixelAvatarCache.getFarmerAvatar() }
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceSubtle)
            .border(0.75.dp, palette.border, RoundedCornerShape(12.dp)),
    ) {
        Image(
            bitmap = avatar,
            contentDescription = "Stardew Valley Farmer Pixel Art",
            contentScale = ContentScale.Crop,
            filterQuality = FilterQuality.None,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Pastel pixel-art Stardew Valley background horizon: mountains, trees, windmill, and wooden fence rails.
 */
@Composable
internal fun HomeFarmBackgroundSilhouette(
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val px = (w / 76f).coerceAtLeast(3f)

        fun snap(v: Float): Float = ((v / px).toInt()) * px
        fun DrawScope.pixelRect(x: Float, y: Float, rw: Float, rh: Float, color: Color) {
            drawRect(
                color = color,
                topLeft = Offset(snap(x), snap(y)),
                size = Size(snap(rw).coerceAtLeast(px), snap(rh).coerceAtLeast(px)),
            )
        }

        // Rolling mountain ridges
        val backPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.56f)
            quadraticTo(w * 0.24f, h * 0.40f, w * 0.50f, h * 0.52f)
            quadraticTo(w * 0.77f, h * 0.62f, w, h * 0.43f)
            lineTo(w, h)
            close()
        }
        drawPath(backPath, palette.hillBack)

        val midPath = Path().apply {
            moveTo(0f, h)
            lineTo(0f, h * 0.68f)
            quadraticTo(w * 0.30f, h * 0.60f, w * 0.58f, h * 0.72f)
            quadraticTo(w * 0.82f, h * 0.80f, w, h * 0.66f)
            lineTo(w, h)
            close()
        }
        drawPath(midPath, palette.hillMid)

        // Pixel trees along skyline
        val treeSpots = listOf(0.05f, 0.13f, 0.21f, 0.35f, 0.60f, 0.88f, 0.94f)
        treeSpots.forEachIndexed { idx, frac ->
            val tx = w * frac
            val ty = h * (if (idx % 2 == 0) 0.50f else 0.56f)
            val treeColor = if (idx % 2 == 0) palette.hillFront else palette.windmillColor
            pixelRect(tx - px * 0.4f, ty + px * 3f, px * 1.1f, px * 2.6f, treeColor)
            pixelRect(tx - px * 1.8f, ty + px * 1f, px * 4.0f, px * 2.2f, treeColor)
            pixelRect(tx - px * 1.2f, ty - px * 0.6f, px * 2.8f, px * 2.0f, treeColor)
        }

        // Wooden pixel fence rails along left-center meadow
        val fenceTopY = h * 0.67f
        pixelRect(w * 0.44f, fenceTopY + px * 0.8f, w * 0.24f, px * 0.8f, palette.fenceColor)
        pixelRect(w * 0.44f, fenceTopY + px * 2.2f, w * 0.24f, px * 0.8f, palette.fenceColor)
        var postX = w * 0.45f
        while (postX <= w * 0.67f) {
            pixelRect(postX, fenceTopY, px * 1.0f, px * 3.8f, palette.fenceColor)
            postX += px * 3.6f
        }

        // Pixel windmill on right hillside
        val millX = snap(w * 0.76f)
        val millBaseY = snap(h * 0.69f)
        val millTopY = snap(h * 0.35f)
        pixelRect(millX - px * 2f, millTopY + px * 2f, px * 4.5f, millBaseY - millTopY, palette.windmillColor)
        pixelRect(millX - px * 2.5f, millTopY + px, px * 5.5f, px * 1.4f, palette.windmillColor)
        pixelRect(millX - px * 1.5f, millTopY, px * 3.5f, px * 1.2f, palette.windmillColor)
        pixelRect(millX - px * 0.3f, millTopY + px * 4.5f, px, px * 1.4f, palette.background)
        val hubX = millX + px * 0.2f
        val hubY = millTopY + px * 2.2f
        listOf(35.0, 125.0, 215.0, 305.0).forEach { deg ->
            val rad = Math.toRadians(deg)
            for (step in 1..6) {
                val bx = hubX + (cos(rad) * step * px * 1.1f).toFloat()
                val by = hubY + (sin(rad) * step * px * 1.1f).toFloat()
                pixelRect(bx, by, px * 1.15f, px * 1.15f, palette.windmillColor)
            }
        }

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

@Composable
internal fun HomeDiagnosticsSkeleton(palette: HomePalette) {
    val transition = rememberInfiniteTransition(label = "home_skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.38f,
        targetValue = 0.78f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "skeleton_alpha",
    )
    val shimmer = palette.surfaceSubtle.copy(alpha = alpha)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(106.dp),
        shape = RoundedCornerShape(15.dp),
        color = palette.surface,
        border = BorderStroke(0.75.dp, palette.border),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(shimmer),
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(14.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmer),
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.35f)
                            .height(11.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(shimmer),
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(0.58f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(shimmer),
                )
                Box(
                    modifier = Modifier
                        .weight(0.42f)
                        .height(38.dp)
                        .clip(RoundedCornerShape(50))
                        .background(shimmer),
                )
            }
        }
    }
}
