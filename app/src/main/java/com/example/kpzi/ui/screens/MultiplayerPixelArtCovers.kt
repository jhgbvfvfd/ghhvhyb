package com.example.kpzi.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.example.kpzi.engine.multiplayer.MultiplayerRoom
import java.util.Locale
import kotlin.math.abs

/**
 * 36 distinct Pixel Art landscape room covers across 12 Stardew-inspired biomes/themes.
 * Deterministically assigned by Room ID so rooms have varied artwork and stay stable on refresh.
 */
internal object PixelArtRoomCovers {

    internal enum class RoomTheme {
        SPRING_FARM,
        SUMMER_FARM,
        AUTUMN_FARM,
        WINTER_FARM,
        WOODEN_COTTAGE,
        BARN_SILO,
        WINDMILL,
        RIVER,
        FOREST,
        NIGHT_FARM,
        BEACH_FARM,
        FLOWER_GARDEN,
    }

    private data class SceneSpec(
        val id: Int,
        val theme: RoomTheme,
        val skyTop: Int,
        val skyBottom: Int,
        val hillColor: Int,
        val groundTop: Int,
        val groundBottom: Int,
        val accentPixel: Int,
        val palette: Map<Char, Int>,
        val sprite: List<String>,
        val flipSprite: Boolean = false,
    )

    private val specs: List<SceneSpec> = buildScenes()
    private val cache = LruCache<Int, ImageBitmap>(48)

    val totalCount: Int get() = specs.size

    fun getCoverBitmap(coverId: Int): ImageBitmap {
        val safeId = ((coverId % specs.size) + specs.size) % specs.size
        cache.get(safeId)?.let { return it }
        val rendered = renderScene(specs[safeId]).asImageBitmap()
        cache.put(safeId, rendered)
        return rendered
    }

    private fun renderScene(spec: SceneSpec): Bitmap {
        val w = 28
        val h = 22
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = false }

        // 1. Dithered sky, distant hills, and foreground meadow
        for (y in 0 until h) {
            for (x in 0 until w) {
                val hillHeight = 11 + ((x * 3 + spec.id * 2) % 5) / 2
                val color = when {
                    y < hillHeight -> {
                        val t = y.toFloat() / 12f
                        val useBottom = t > 0.62f || (t > 0.34f && (x + y) % 2 == 0)
                        if (useBottom) spec.skyBottom else spec.skyTop
                    }
                    y < 15 -> spec.hillColor
                    else -> {
                        val useBottom = y > 18 || (y > 16 && (x + y) % 2 == 0)
                        if (useBottom) spec.groundBottom else spec.groundTop
                    }
                }
                paint.color = color
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }

        // 2. Sky stars / blossom / snow / sun glint
        val seed = abs(spec.id * 4871 + 19)
        paint.color = spec.accentPixel
        for (i in 0..5) {
            val sx = (seed + i * 9) % (w - 2) + 1
            val sy = (seed / (i + 1) + i * 3) % 8 + 1
            canvas.drawPoint(sx.toFloat(), sy.toFloat(), paint)
        }

        // 3. 18x14 centered pixel landmark sprite
        val ox = 5
        val oy = 5
        spec.sprite.forEachIndexed { rowIdx, row ->
            if (rowIdx >= 14) return@forEachIndexed
            val rowWidth = minOf(18, row.length)
            for (colIdx in 0 until rowWidth) {
                val srcCol = if (spec.flipSprite) (rowWidth - 1 - colIdx) else colIdx
                val ch = row[srcCol]
                if (ch == '.') continue
                val c = spec.palette[ch] ?: continue
                paint.color = c
                canvas.drawPoint((ox + colIdx).toFloat(), (oy + rowIdx).toFloat(), paint)
            }
        }
        return bmp
    }

    private fun buildScenes(): List<SceneSpec> {
        val list = mutableListOf<SceneSpec>()
        fun add(
            theme: RoomTheme,
            skyTop: Int,
            skyBottom: Int,
            hillColor: Int,
            groundTop: Int,
            groundBottom: Int,
            accent: Int,
            palette: Map<Char, Int>,
            sprite: List<String>,
            flip: Boolean = false,
        ) {
            list.add(
                SceneSpec(
                    id = list.size,
                    theme = theme,
                    skyTop = skyTop,
                    skyBottom = skyBottom,
                    hillColor = hillColor,
                    groundTop = groundTop,
                    groundBottom = groundBottom,
                    accentPixel = accent,
                    palette = palette,
                    sprite = sprite,
                    flipSprite = flip,
                ),
            )
        }

        val cottageSprite = listOf(
            ".....OOOOOO.......",
            "...OORRRRRROO.....",
            "..ORRRRRRRRRRO....",
            ".ORRRRRRRRRRRRO...",
            ".OOBBBBBBBBBBOO...",
            "..OBBYYBYYBBBO..OO",
            "..OBBYYBYYBBBO.OGG",
            "..OBBBBBBBBBBOOGGG",
            "..OBBDDDDDDBBOGGGG",
            "..OBBDDYYDDBBO.OG.",
            "..OBBDDYYDDBBO.OB.",
            "..OOOOOOOOOOOO.OB.",
            "..GGGGGGGGGGGGGGGG",
            "..................",
        )
        val barnSprite = listOf(
            "......OOOO........",
            "....OORRRROO......",
            "...ORRRRRRRRO..OOO",
            "..ORRRRRRRRRROOSSS",
            "..ORWWWWWWWWROOSSS",
            "..ORWRRRRRRWROOSSS",
            "..ORWRWKKWRWROOSSS",
            "..ORWRWKKWRWROOSSS",
            "..ORWWWWWWWWROOSSS",
            "..ORRRRRRRRRROOSSS",
            "..ORROOOOOORROOSSS",
            "..ORROYOOYORROOSSS",
            "..OOOOOOOOOOOOOSSS",
            "..................",
        )
        val windmillSprite = listOf(
            ".......OO.........",
            "..OO..ORRO..OO....",
            "...OOORRRROOO.....",
            "....OOWWWWOO......",
            "...OOWWWWWWOO.....",
            "..OO.OWYYWO.OO....",
            ".OO..OWWWWO..OO...",
            ".....OWBBWO.......",
            ".....OWBBWO.......",
            "....OOWWWWOO......",
            "....OWWDDWWO......",
            "....OWWDDWWO......",
            "...OOOOOOOOOO.....",
            "..................",
        )
        val riverSprite = listOf(
            "..OOOO......OOOO..",
            ".OGGGGO....OGGGGO.",
            "OGGGGGGO..OGGGGGGO",
            ".OOBBOO....OOBBOO.",
            "...OB..CCCC..BO...",
            "..OOBOCCCCCCOBOO..",
            ".OOOOOBBBBBBOOOOO.",
            "OBBBBBBBBBBBBBBBBO",
            "..OCCCCCCCCCCCCO..",
            "...OCCCCWWCCCCO...",
            "....OCCCCCCCCO....",
            ".....OCCCCCCCO....",
            "....OCCCCCCCCO....",
            "..................",
        )
        val pineSprite = listOf(
            "....OO......OO....",
            "...OGGO....OGGO...",
            "..OGGGGO..OGGGGO..",
            ".OGGGGGGOOGGGGGGO.",
            "..OOGGOO..OOGGOO..",
            ".OGGGGGGOOGGGGGGO.",
            "OGGGGGGGGOOGGGGGGO",
            "...OBBO....OBBO...",
            "...OBBO....OBBO...",
            "GGGGOBBOGGGGGOBBOG",
            "GGGGGGGGGGGGGGGGGG",
            "..................",
            "..................",
            "..................",
        )
        val beachSprite = listOf(
            "......OOOOOO......",
            "....OOGGGGGGOO....",
            "...OGG.OYYO.GGO...",
            ".......OBBO.......",
            ".......OBBO.......",
            "......OBBBO.......",
            ".....OBBBO........",
            "YYYYYYYYYYYYYYYYYY",
            "YYYYYYWWYYYYYYYYYY",
            "CCCCCCCCCCCCCCCCCC",
            "CCCCWWCCCCCCCCWWCC",
            "BBBBBBBBBBBBBBBBBB",
            "BBBBBBBBBBBBBBBBBB",
            "..................",
        )
        val flowerSprite = listOf(
            "...OO........OO...",
            "..OPPO..OO..OPPO..",
            ".OPWWPOPYYPOOPWWPO",
            "..OPPO.PYYP..OPPO.",
            "...OG...GG....OG..",
            "...OG.OO..OO..OG..",
            "..OOGORROORROOGOO.",
            "..OGGORWROWRROGG..",
            "..OGG.ORROORROGG..",
            "...OG...GG....OG..",
            "GGGGGGGGGGGGGGGGGG",
            "GGGGGGGGGGGGGGGGGG",
            "..................",
            "..................",
        )
        val fenceCropSprite = listOf(
            "..................",
            "...OO........OO...",
            "..OYYO......OYYO..",
            "..OBBO.OOOO.OBBO..",
            "OOOOOOBBBBBBOOOOOO",
            "..OBBO.OOOO.OBBO..",
            "..OGGO.OGGO.OGGO..",
            ".OGGGGOOGGGOOGGGGO",
            "..ORRO.ORRO.ORRO..",
            ".ORWWROORWWROORWWR",
            "..ORRO.ORRO.ORRO..",
            "BBBBBBBBBBBBBBBBBB",
            "..................",
            "..................",
        )

        val O = 0xFF141815.toInt()
        val W = 0xFFFFFFFF.toInt()
        val K = 0xFF0F172A.toInt()

        // 1. SPRING_FARM (3)
        add(RoomTheme.SPRING_FARM, 0xFF38BDF8.toInt(), 0xFFBAE6FD.toInt(), 0xFF4ADE80.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFFFBCFE8.toInt(),
            mapOf('O' to O, 'R' to 0xFF9333EA.toInt(), 'B' to 0xFFD97706.toInt(), 'Y' to 0xFFFDE047.toInt(), 'D' to 0xFF78350F.toInt(), 'G' to 0xFFF472B6.toInt()), cottageSprite)
        add(RoomTheme.SPRING_FARM, 0xFF60A5FA.toInt(), 0xFFDBEAFE.toInt(), 0xFF34D399.toInt(), 0xFF16A34A.toInt(), 0xFF14532D.toInt(), W,
            mapOf('O' to O, 'Y' to 0xFFFBBF24.toInt(), 'B' to 0xFF92400E.toInt(), 'G' to 0xFF22C55E.toInt(), 'R' to 0xFFEC4899.toInt(), 'W' to W), fenceCropSprite)
        add(RoomTheme.SPRING_FARM, 0xFF7DD3FC.toInt(), 0xFFE0F2FE.toInt(), 0xFF86EFAC.toInt(), 0xFF4ADE80.toInt(), 0xFF16A34A.toInt(), 0xFFFDF2F8.toInt(),
            mapOf('O' to O, 'R' to 0xFFEC4899.toInt(), 'B' to 0xFFB45309.toInt(), 'Y' to 0xFFFEF08A.toInt(), 'D' to 0xFF78350F.toInt(), 'G' to 0xFFF9A8D4.toInt()), cottageSprite, flip = true)

        // 2. SUMMER_FARM (3)
        add(RoomTheme.SUMMER_FARM, 0xFF0284C7.toInt(), 0xFF7DD3FC.toInt(), 0xFF65A30D.toInt(), 0xFF84CC16.toInt(), 0xFF3F6212.toInt(), 0xFFFACC15.toInt(),
            mapOf('O' to O, 'Y' to 0xFFFDE047.toInt(), 'B' to 0xFF78350F.toInt(), 'G' to 0xFF16A34A.toInt(), 'R' to 0xFFEF4444.toInt(), 'W' to 0xFFFECACA.toInt()), fenceCropSprite)
        add(RoomTheme.SUMMER_FARM, 0xFFF59E0B.toInt(), 0xFFFEF08A.toInt(), 0xFF84CC16.toInt(), 0xFF65A30D.toInt(), 0xFF365314.toInt(), 0xFFFFFBEB.toInt(),
            mapOf('O' to O, 'R' to 0xFFDC2626.toInt(), 'W' to W, 'K' to 0xFF78350F.toInt(), 'Y' to 0xFFFACC15.toInt(), 'S' to 0xFF94A3B8.toInt()), barnSprite)
        add(RoomTheme.SUMMER_FARM, 0xFF0EA5E9.toInt(), 0xFFFEF3C7.toInt(), 0xFF4D7C0F.toInt(), 0xFF65A30D.toInt(), 0xFF3F6212.toInt(), W,
            mapOf('O' to O, 'P' to 0xFFF59E0B.toInt(), 'W' to 0xFFFEF08A.toInt(), 'Y' to 0xFFFACC15.toInt(), 'G' to 0xFF15803D.toInt(), 'R' to 0xFFEF4444.toInt()), flowerSprite)

        // 3. AUTUMN_FARM (3)
        add(RoomTheme.AUTUMN_FARM, 0xFFEA580C.toInt(), 0xFFFDE68A.toInt(), 0xFFD97706.toInt(), 0xFFB45309.toInt(), 0xFF78350F.toInt(), 0xFFFB923C.toInt(),
            mapOf('O' to O, 'R' to 0xFFDC2626.toInt(), 'B' to 0xFF92400E.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'D' to 0xFF451A03.toInt(), 'G' to 0xFFF97316.toInt()), cottageSprite)
        add(RoomTheme.AUTUMN_FARM, 0xFF9A3412.toInt(), 0xFFFDBA74.toInt(), 0xFFB45309.toInt(), 0xFFCA8A04.toInt(), 0xFF713F12.toInt(), 0xFFFDE047.toInt(),
            mapOf('O' to O, 'Y' to 0xFFF59E0B.toInt(), 'B' to 0xFF78350F.toInt(), 'G' to 0xFFD97706.toInt(), 'R' to 0xFFEA580C.toInt(), 'W' to 0xFFFDE68A.toInt()), fenceCropSprite)
        add(RoomTheme.AUTUMN_FARM, 0xFFC2410C.toInt(), 0xFFFED7AA.toInt(), 0xFF9A3412.toInt(), 0xFFD97706.toInt(), 0xFF7C2D12.toInt(), 0xFFFBBF24.toInt(),
            mapOf('O' to O, 'G' to 0xFFEA580C.toInt(), 'B' to 0xFF451A03.toInt()), pineSprite)

        // 4. WINTER_FARM (3)
        add(RoomTheme.WINTER_FARM, 0xFF1E3A8A.toInt(), 0xFF93C5FD.toInt(), 0xFFCBD5E1.toInt(), 0xFFF1F5F9.toInt(), 0xFF94A3B8.toInt(), W,
            mapOf('O' to O, 'R' to 0xFFE2E8F0.toInt(), 'B' to 0xFF475569.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'D' to 0xFF334155.toInt(), 'G' to 0xFF38BDF8.toInt()), cottageSprite)
        add(RoomTheme.WINTER_FARM, 0xFF0F172A.toInt(), 0xFF475569.toInt(), 0xFF94A3B8.toInt(), 0xFFE2E8F0.toInt(), 0xFF64748B.toInt(), W,
            mapOf('O' to O, 'G' to 0xFF93C5FD.toInt(), 'B' to 0xFF475569.toInt()), pineSprite)
        add(RoomTheme.WINTER_FARM, 0xFF3B82F6.toInt(), 0xFFDBEAFE.toInt(), 0xFFBFDBFE.toInt(), 0xFFF8FAFC.toInt(), 0xFFCBD5E1.toInt(), W,
            mapOf('O' to O, 'R' to 0xFFE2E8F0.toInt(), 'W' to 0xFF38BDF8.toInt(), 'K' to 0xFF1E293B.toInt(), 'Y' to 0xFFFACC15.toInt(), 'S' to 0xFFCBD5E1.toInt()), barnSprite)

        // 5. WOODEN_COTTAGE (3)
        add(RoomTheme.WOODEN_COTTAGE, 0xFF312E81.toInt(), 0xFF818CF8.toInt(), 0xFF166534.toInt(), 0xFF22C55E.toInt(), 0xFF14532D.toInt(), 0xFFFDE047.toInt(),
            mapOf('O' to O, 'R' to 0xFFB45309.toInt(), 'B' to 0xFF92400E.toInt(), 'Y' to 0xFFFACC15.toInt(), 'D' to 0xFF451A03.toInt(), 'G' to 0xFF16A34A.toInt()), cottageSprite)
        add(RoomTheme.WOODEN_COTTAGE, 0xFF065F46.toInt(), 0xFF6EE7B7.toInt(), 0xFF15803D.toInt(), 0xFF4ADE80.toInt(), 0xFF166534.toInt(), W,
            mapOf('O' to O, 'R' to 0xFF7C2D12.toInt(), 'B' to 0xFFD97706.toInt(), 'Y' to 0xFFFEF08A.toInt(), 'D' to 0xFF78350F.toInt(), 'G' to 0xFF22C55E.toInt()), cottageSprite, flip = true)
        add(RoomTheme.WOODEN_COTTAGE, 0xFF4C1D95.toInt(), 0xFFC4B5FD.toInt(), 0xFF3F6212.toInt(), 0xFF65A30D.toInt(), 0xFF365314.toInt(), 0xFFFDE68A.toInt(),
            mapOf('O' to O, 'R' to 0xFF9A3412.toInt(), 'B' to 0xFFB45309.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'D' to 0xFF451A03.toInt(), 'G' to 0xFF84CC16.toInt()), cottageSprite)

        // 6. BARN_SILO (3)
        add(RoomTheme.BARN_SILO, 0xFF0284C7.toInt(), 0xFFBAE6FD.toInt(), 0xFF16A34A.toInt(), 0xFF4ADE80.toInt(), 0xFF15803D.toInt(), W,
            mapOf('O' to O, 'R' to 0xFFDC2626.toInt(), 'W' to W, 'K' to 0xFF78350F.toInt(), 'Y' to 0xFFFACC15.toInt(), 'S' to 0xFF94A3B8.toInt()), barnSprite)
        add(RoomTheme.BARN_SILO, 0xFF1D4ED8.toInt(), 0xFF93C5FD.toInt(), 0xFF4D7C0F.toInt(), 0xFF65A30D.toInt(), 0xFF365314.toInt(), W,
            mapOf('O' to O, 'R' to 0xFFB91C1C.toInt(), 'W' to 0xFFFEF3C7.toInt(), 'K' to 0xFF451A03.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'S' to 0xFFCBD5E1.toInt()), barnSprite, flip = true)
        add(RoomTheme.BARN_SILO, 0xFF7E22CE.toInt(), 0xFFF5D0FE.toInt(), 0xFF15803D.toInt(), 0xFF22C55E.toInt(), 0xFF14532D.toInt(), 0xFFFDE047.toInt(),
            mapOf('O' to O, 'R' to 0xFFEA580C.toInt(), 'W' to 0xFFFFFBEB.toInt(), 'K' to 0xFF78350F.toInt(), 'Y' to 0xFFFACC15.toInt(), 'S' to 0xFF94A3B8.toInt()), barnSprite)

        // 7. WINDMILL (3)
        add(RoomTheme.WINDMILL, 0xFF1E1B4B.toInt(), 0xFF6366F1.toInt(), 0xFF14532D.toInt(), 0xFF16A34A.toInt(), 0xFF052E16.toInt(), 0xFFFDE047.toInt(),
            mapOf('O' to O, 'R' to 0xFFB45309.toInt(), 'W' to 0xFFFEF3C7.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'B' to 0xFF38BDF8.toInt(), 'D' to 0xFF78350F.toInt()), windmillSprite)
        add(RoomTheme.WINDMILL, 0xFF0284C7.toInt(), 0xFFE0F2FE.toInt(), 0xFF65A30D.toInt(), 0xFF84CC16.toInt(), 0xFF3F6212.toInt(), W,
            mapOf('O' to O, 'R' to 0xFFDC2626.toInt(), 'W' to W, 'Y' to 0xFFFACC15.toInt(), 'B' to 0xFF0EA5E9.toInt(), 'D' to 0xFF92400E.toInt()), windmillSprite)
        add(RoomTheme.WINDMILL, 0xFFF97316.toInt(), 0xFFFDE68A.toInt(), 0xFF4D7C0F.toInt(), 0xFF65A30D.toInt(), 0xFF365314.toInt(), 0xFFFFFBEB.toInt(),
            mapOf('O' to O, 'R' to 0xFF7C3AED.toInt(), 'W' to 0xFFF5F3FF.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'B' to 0xFF60A5FA.toInt(), 'D' to 0xFF4C1D95.toInt()), windmillSprite, flip = true)

        // 8. RIVER (3)
        add(RoomTheme.RIVER, 0xFF0EA5E9.toInt(), 0xFFBAE6FD.toInt(), 0xFF15803D.toInt(), 0xFF4ADE80.toInt(), 0xFF16A34A.toInt(), W,
            mapOf('O' to O, 'G' to 0xFF16A34A.toInt(), 'B' to 0xFF92400E.toInt(), 'C' to 0xFF0284C7.toInt(), 'W' to 0xFFE0F2FE.toInt()), riverSprite)
        add(RoomTheme.RIVER, 0xFF065F46.toInt(), 0xFFA7F3D0.toInt(), 0xFF14532D.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFFECFDF5.toInt(),
            mapOf('O' to O, 'G' to 0xFF10B981.toInt(), 'B' to 0xFF78350F.toInt(), 'C' to 0xFF0EA5E9.toInt(), 'W' to W), riverSprite)
        add(RoomTheme.RIVER, 0xFF312E81.toInt(), 0xFF818CF8.toInt(), 0xFF1E3A8A.toInt(), 0xFF15803D.toInt(), 0xFF052E16.toInt(), 0xFFFDE047.toInt(),
            mapOf('O' to O, 'G' to 0xFF22C55E.toInt(), 'B' to 0xFF78350F.toInt(), 'C' to 0xFF2563EB.toInt(), 'W' to 0xFF93C5FD.toInt()), riverSprite)

        // 9. FOREST (3)
        add(RoomTheme.FOREST, 0xFF064E3B.toInt(), 0xFF34D399.toInt(), 0xFF065F46.toInt(), 0xFF15803D.toInt(), 0xFF052E16.toInt(), 0xFFA7F3D0.toInt(),
            mapOf('O' to O, 'G' to 0xFF16A34A.toInt(), 'B' to 0xFF78350F.toInt()), pineSprite)
        add(RoomTheme.FOREST, 0xFF0F172A.toInt(), 0xFF1E293B.toInt(), 0xFF064E3B.toInt(), 0xFF14532D.toInt(), 0xFF052E16.toInt(), 0xFF4ADE80.toInt(),
            mapOf('O' to O, 'G' to 0xFF22C55E.toInt(), 'B' to 0xFF451A03.toInt()), pineSprite)
        add(RoomTheme.FOREST, 0xFF0284C7.toInt(), 0xFFA7F3D0.toInt(), 0xFF166534.toInt(), 0xFF22C55E.toInt(), 0xFF14532D.toInt(), W,
            mapOf('O' to O, 'G' to 0xFF15803D.toInt(), 'B' to 0xFF92400E.toInt()), pineSprite, flip = true)

        // 10. NIGHT_FARM (3)
        add(RoomTheme.NIGHT_FARM, 0xFF090D16.toInt(), 0xFF1E1B4B.toInt(), 0xFF0F172A.toInt(), 0xFF14532D.toInt(), 0xFF052E16.toInt(), 0xFFFACC15.toInt(),
            mapOf('O' to O, 'R' to 0xFF4C1D95.toInt(), 'B' to 0xFF78350F.toInt(), 'Y' to 0xFFFDE047.toInt(), 'D' to 0xFF451A03.toInt(), 'G' to 0xFF15803D.toInt()), cottageSprite)
        add(RoomTheme.NIGHT_FARM, 0xFF0F172A.toInt(), 0xFF312E81.toInt(), 0xFF1E1B4B.toInt(), 0xFF166534.toInt(), 0xFF052E16.toInt(), 0xFFFDE68A.toInt(),
            mapOf('O' to O, 'R' to 0xFF991B1B.toInt(), 'W' to 0xFFCBD5E1.toInt(), 'K' to 0xFF451A03.toInt(), 'Y' to 0xFFFACC15.toInt(), 'S' to 0xFF475569.toInt()), barnSprite)
        add(RoomTheme.NIGHT_FARM, 0xFF020617.toInt(), 0xFF1E293B.toInt(), 0xFF0F172A.toInt(), 0xFF1E3A8A.toInt(), 0xFF0F172A.toInt(), 0xFF7DD3FC.toInt(),
            mapOf('O' to O, 'G' to 0xFF10B981.toInt(), 'B' to 0xFF78350F.toInt(), 'C' to 0xFF1D4ED8.toInt(), 'W' to 0xFF93C5FD.toInt()), riverSprite)

        // 11. BEACH_FARM (3)
        add(RoomTheme.BEACH_FARM, 0xFF0284C7.toInt(), 0xFF7DD3FC.toInt(), 0xFF38BDF8.toInt(), 0xFFFDE68A.toInt(), 0xFF0EA5E9.toInt(), W,
            mapOf('O' to O, 'G' to 0xFF22C55E.toInt(), 'Y' to 0xFFFDE68A.toInt(), 'B' to 0xFF92400E.toInt(), 'W' to W, 'C' to 0xFF0284C7.toInt()), beachSprite)
        add(RoomTheme.BEACH_FARM, 0xFFF97316.toInt(), 0xFFFDE047.toInt(), 0xFFFB923C.toInt(), 0xFFFCD34D.toInt(), 0xFF0369A1.toInt(), 0xFFFFFBEB.toInt(),
            mapOf('O' to O, 'G' to 0xFF15803D.toInt(), 'Y' to 0xFFFDE047.toInt(), 'B' to 0xFF78350F.toInt(), 'W' to 0xFFFEF08A.toInt(), 'C' to 0xFF0EA5E9.toInt()), beachSprite, flip = true)
        add(RoomTheme.BEACH_FARM, 0xFF0369A1.toInt(), 0xFFBAE6FD.toInt(), 0xFF0284C7.toInt(), 0xFFFEF3C7.toInt(), 0xFF0284C7.toInt(), W,
            mapOf('O' to O, 'G' to 0xFF16A34A.toInt(), 'Y' to 0xFFFEF08A.toInt(), 'B' to 0xFF78350F.toInt(), 'W' to W, 'C' to 0xFF38BDF8.toInt()), beachSprite)

        // 12. FLOWER_GARDEN (3)
        add(RoomTheme.FLOWER_GARDEN, 0xFFBE185D.toInt(), 0xFFFBCFE8.toInt(), 0xFF4ADE80.toInt(), 0xFF22C55E.toInt(), 0xFF15803D.toInt(), 0xFFFDF2F8.toInt(),
            mapOf('O' to O, 'P' to 0xFFEC4899.toInt(), 'W' to W, 'Y' to 0xFFFDE047.toInt(), 'G' to 0xFF16A34A.toInt(), 'R' to 0xFF9333EA.toInt()), flowerSprite)
        add(RoomTheme.FLOWER_GARDEN, 0xFF38BDF8.toInt(), 0xFFE0F2FE.toInt(), 0xFF86EFAC.toInt(), 0xFF4ADE80.toInt(), 0xFF16A34A.toInt(), W,
            mapOf('O' to O, 'P' to 0xFFA855F7.toInt(), 'W' to 0xFFF5F3FF.toInt(), 'Y' to 0xFFFACC15.toInt(), 'G' to 0xFF15803D.toInt(), 'R' to 0xFFEC4899.toInt()), flowerSprite)
        add(RoomTheme.FLOWER_GARDEN, 0xFF7C3AED.toInt(), 0xFFDDD6FE.toInt(), 0xFF34D399.toInt(), 0xFF10B981.toInt(), 0xFF065F46.toInt(), 0xFFFBCFE8.toInt(),
            mapOf('O' to O, 'P' to 0xFFF43F5E.toInt(), 'W' to 0xFFFFF1F2.toInt(), 'Y' to 0xFFFDE047.toInt(), 'G' to 0xFF16A34A.toInt(), 'R' to 0xFF3B82F6.toInt()), flowerSprite, flip = true)

        return list
    }
}

/**
 * Assigns a deterministic, screen-unique Pixel Art room cover ID per Room ID.
 * Never changes when rooms refresh or update player counts.
 */
internal fun computeRoomCoverAssignmentMap(rooms: List<MultiplayerRoom>): Map<String, Int> {
    val total = PixelArtRoomCovers.totalCount
    if (rooms.isEmpty() || total <= 0) return emptyMap()
    val canonical = rooms.sortedBy { it.roomId.lowercase(Locale.ROOT) }
    val used = HashSet<Int>(canonical.size)
    val out = HashMap<String, Int>(canonical.size)
    for (room in canonical) {
        val key = room.roomId.lowercase(Locale.ROOT)
        if (out.containsKey(key)) continue
        val start = abs(key.hashCode()) % total
        var chosen: Int? = null
        for (offset in 0 until total) {
            val cand = (start + offset) % total
            if (cand !in used) {
                chosen = cand
                break
            }
        }
        val finalId = chosen ?: start
        used.add(finalId)
        out[key] = finalId
    }
    return out
}
