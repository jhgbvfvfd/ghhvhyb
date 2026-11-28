package com.example.kpzi.ui.screens

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.abs

/**
 * Generates 37 distinct 24x24 crisp Pixel Art cover artworks mapped across all 14 mod categories.
 * Every artwork has a multi-layered dithered pixel backdrop + hand-authored 16x16 pixel sprite.
 */
internal object PixelArtModCovers {

    internal enum class BackdropTheme(
        val skyTop: Int,
        val skyBottom: Int,
        val groundTop: Int,
        val groundBottom: Int,
        val accentPixel: Int,
    ) {
        TWILIGHT_FARM(0xFF2E1065.toInt(), 0xFF6D28D9.toInt(), 0xFF3F6212.toInt(), 0xFF1A2E05.toInt(), 0xFFFDE68A.toInt()),
        SUNNY_MEADOW(0xFF38BDF8.toInt(), 0xFFBAE6FD.toInt(), 0xFF65A30D.toInt(), 0xFF3F6212.toInt(), 0xFFFFFFFF.toInt()),
        LAVENDER_BLOOM(0xFF7E22CE.toInt(), 0xFFF5D0FE.toInt(), 0xFF86EFAC.toInt(), 0xFF15803D.toInt(), 0xFFFBCFE8.toInt()),
        SAKURA_SHRINE(0xFFBE185D.toInt(), 0xFFFBCFE8.toInt(), 0xFF4D7C0F.toInt(), 0xFF365314.toInt(), 0xFFFDF2F8.toInt()),
        STARRY_NIGHT(0xFF0F172A.toInt(), 0xFF312E81.toInt(), 0xFF1E1B4B.toInt(), 0xFF090D16.toInt(), 0xFFFACC15.toInt()),
        AUTUMN_WOODS(0xFFEA580C.toInt(), 0xFFFDE68A.toInt(), 0xFFB45309.toInt(), 0xFF78350F.toInt(), 0xFFFB923C.toInt()),
        OCEAN_BREEZE(0xFF0284C7.toInt(), 0xFF7DD3FC.toInt(), 0xFF0369A1.toInt(), 0xFF0C4A6E.toInt(), 0xFFE0F2FE.toInt()),
        COZY_CABIN(0xFF451A03.toInt(), 0xFF78350F.toInt(), 0xFF92400E.toInt(), 0xFF451A03.toInt(), 0xFFFBBF24.toInt()),
        GOLDEN_HARVEST(0xFFF59E0B.toInt(), 0xFFFEF3C7.toInt(), 0xFFCA8A04.toInt(), 0xFF713F12.toInt(), 0xFFFFFBEB.toInt()),
        EMERALD_VALLEY(0xFF059669.toInt(), 0xFFA7F3D0.toInt(), 0xFF16A34A.toInt(), 0xFF14532D.toInt(), 0xFFECFDF5.toInt()),
        PASTEL_BAKERY(0xFFF472B6.toInt(), 0xFFFCE7F3.toInt(), 0xFFFDE68A.toInt(), 0xFFD97706.toInt(), 0xFFFFFFFF.toInt()),
        ARCANE_FORGE(0xFF3B0764.toInt(), 0xFF7E22CE.toInt(), 0xFF27272A.toInt(), 0xFF18181B.toInt(), 0xFFE879F9.toInt()),
    }

    internal data class PixelCoverSpec(
        val id: Int,
        val category: MyModsCategory,
        val theme: BackdropTheme,
        val palette: Map<Char, Int>,
        val sprite: List<String>,
    )

    private val specs: List<PixelCoverSpec> = buildSpecs()

    private val categoryIndices: Map<MyModsCategory, List<Int>> by lazy {
        specs.groupBy { it.category }.mapValues { entry -> entry.value.map { it.id } }
    }

    private val bitmapCache = LruCache<Int, ImageBitmap>(64)

    val totalCount: Int get() = specs.size

    fun idsForCategory(category: MyModsCategory): List<Int> =
        categoryIndices[category].orEmpty()

    fun getCoverBitmap(coverId: Int): ImageBitmap {
        val safeId = ((coverId % specs.size) + specs.size) % specs.size
        bitmapCache.get(safeId)?.let { return it }
        val bmp = renderPixelArt(specs[safeId]).asImageBitmap()
        bitmapCache.put(safeId, bmp)
        return bmp
    }

    private fun renderPixelArt(spec: PixelCoverSpec): Bitmap {
        val size = 24
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val paint = Paint().apply { isAntiAlias = false }

        // 1. Dithered sky + horizon ground
        for (y in 0 until size) {
            for (x in 0 until size) {
                val color = if (y < 16) {
                    val t = y / 15f
                    val useBottom = t > 0.65f || (t > 0.35f && ((x + y) % 2 == 0))
                    if (useBottom) spec.theme.skyBottom else spec.theme.skyTop
                } else {
                    val useBottom = y > 20 || (y > 17 && ((x + y) % 2 == 0))
                    if (useBottom) spec.theme.groundBottom else spec.theme.groundTop
                }
                paint.color = color
                canvas.drawPoint(x.toFloat(), y.toFloat(), paint)
            }
        }

        // 2. Subtle pixel stars/clouds/fireflies in upper backdrop
        val seed = abs(spec.id * 7919 + 31)
        paint.color = spec.theme.accentPixel
        for (i in 0..4) {
            val sx = (seed + i * 7) % 22 + 1
            val sy = (seed / (i + 1) + i * 3) % 8 + 1
            canvas.drawPoint(sx.toFloat(), sy.toFloat(), paint)
        }

        // 3. Centered 16x16 foreground pixel-art sprite
        val offsetX = 4
        val offsetY = 4
        spec.sprite.forEachIndexed { rowIdx, row ->
            if (rowIdx >= 16) return@forEachIndexed
            for (colIdx in 0 until minOf(16, row.length)) {
                val ch = row[colIdx]
                if (ch == '.') continue
                val color = spec.palette[ch] ?: continue
                paint.color = color
                canvas.drawPoint((offsetX + colIdx).toFloat(), (offsetY + rowIdx).toFloat(), paint)
            }
        }
        return bmp
    }

    private fun buildSpecs(): List<PixelCoverSpec> {
        val list = mutableListOf<PixelCoverSpec>()
        fun add(
            category: MyModsCategory,
            theme: BackdropTheme,
            palette: Map<Char, Int>,
            sprite: List<String>,
        ) {
            list.add(PixelCoverSpec(list.size, category, theme, palette, sprite))
        }

        // --- Shared crisp sprite templates with varied palettes/scenes ---
        val barnSprite = listOf(
            ".......OO.......",
            "......ORRO......",
            ".....ORRRRO.....",
            "....ORRRRRRO....",
            "...ORRRRRRRRO...",
            "..OOWWWWWWWWO...",
            "..OWRRRRRRRRWO..",
            "..OWRRWWWRRRWO..",
            "..OWRRWKWWRRWO..",
            "..OWRRWKWWRRWO..",
            "..OWWWWWWWWWWO..",
            "..ORRRRRRRRRRO..",
            "..ORROOOOOORRO..",
            "..ORROYYYYORRO..",
            "..ORROYYYYORRO..",
            "..OOOOOOOOOOOO..",
        )
        val cogSprite = listOf(
            "......OOOO......",
            "....OOGGGGOO....",
            "..OOGGYYYYGGOO..",
            "..OGGYYOOYYGGO..",
            ".OGGYO....OYGGO.",
            "OGGYO.O..O.OYGGO",
            "OGGY..OOOO..YGGO",
            "OGGY.OCCCCO.YGGO",
            "OGGY.OCCCCO.YGGO",
            "OGGY..OOOO..YGGO",
            "OGGYO.O..O.OYGGO",
            ".OGGYO....OYGGO.",
            "..OGGYYOOYYGGO..",
            "..OOGGYYYYGGOO..",
            "....OOGGGGOO....",
            "......OOOO......",
        )
        val crystalSprite = listOf(
            ".......OO.......",
            "......OCCO......",
            ".....OCWWCO.....",
            "....OCWCCWCO....",
            "...OCWCCCCWCO...",
            "..OCWCCCCCCWCO..",
            "..OCWCCWWCCWCO..",
            "..OCWCWWWWCWCO..",
            "..OCWCCWWCCWCO..",
            "..OCWCCCCCCWCO..",
            "...OCWCCCCWCO...",
            "....OCWCCWCO....",
            ".....OCWWCO.....",
            "......OCCO......",
            ".......OO.......",
            "................",
        )
        val portraitSprite = listOf(
            "....OOOOOOOO....",
            "...OHHHHHHHHO...",
            "..OHHHHHHHHHHO..",
            "..OHHSSSSSSHHO..",
            "..OHSSSKKSSSHO..",
            "..OHSSSKKSSSHO..",
            "..OHSSSSSSSSHO..",
            "...OSSSSSSSSO...",
            "....OSSSSSSO....",
            "...OOBBBBBBOO...",
            "..OBBCCCCCCBBO..",
            "..OBBCCCCCCBBO..",
            "..OBBCCCCCCBBO..",
            "...OBBBBBBBBO...",
            "...OOOOOOOOOO...",
            "................",
        )
        val scrollSprite = listOf(
            "...OOOOOOOOOO...",
            "..OYYYYYYYYYYO..",
            "..OYOOOOOOOOYO..",
            "...OOWWWWWWO....",
            "...OWWBBBBWWO...",
            "...OWWWWWWWWO...",
            "...OWWBBBBWWO...",
            "...OWWWWWWWWO...",
            "...OWWBBBWWWO...",
            "...OWWWWWWWWO...",
            "...OWWBBBBWWO...",
            "...OWWWWWWWWO...",
            "..OOWWWWWWWWOO..",
            "..OYYYYYYYYYYO..",
            "...OOOOOOOOOO...",
            "................",
        )
        val micSprite = listOf(
            ".....OOOOOO.....",
            "....OSSSSSSO....",
            "...OSCCCCCCSO...",
            "...OSCCCCCCSO...",
            "...OSOOOOOOSO...",
            "...OSCCCCCCSO...",
            "....OSSSSSSO....",
            ".....OOOOOO.....",
            "...OO..OO..OO...",
            "...OO..OO..OO...",
            "....OOOOOOOO....",
            ".......OO.......",
            ".......OO.......",
            "....OOOOOOOO....",
            "...OCCCCCCCCO...",
            "...OOOOOOOOOO...",
        )
        val shrineSprite = listOf(
            "..OOOOOOOOOOOO..",
            ".OPPPPPPPPPPPPO.",
            "..OOBBBBBBBBOO..",
            "....OB....BO....",
            "....OB.NN.BO....",
            "...OOB.NN.BOO...",
            "..ORRRRRRRRRRO..",
            "..ORWWWRRWWWRRO.",
            "..ORWYYRRWYYRRO.",
            "..ORWWWRRWWWRRO.",
            "..ORRRRRRRRRRO..",
            "...OOWWWWWWO....",
            "...OWWWWWWWWO...",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
        )
        val pickaxeSprite = listOf(
            "........OOOOOO..",
            "......OOBBBBBBO.",
            "....OOBBCCCCBBO.",
            "...OBBCCWWWWBBO.",
            "..OBBCWW...OBO..",
            "..OBBO....OYYO..",
            "..OBO....OYYO...",
            "...O....OYYO....",
            ".......OYYO.....",
            "......OYYO......",
            ".....OYYO.......",
            "....OYYO........",
            "...OYYO.........",
            "..OYYO..........",
            "..OOO...........",
            "................",
        )
        val fontLetterSprite = listOf(
            "......OOOO......",
            ".....OYYYYO.....",
            "....OYYOOYYO....",
            "...OYYO..OYYO...",
            "..OYYO....OYYO..",
            "..OYYOOOOOOYYO..",
            "..OYYYYYYYYYYO..",
            "..OYYOOOOOOYYO..",
            "..OYYO....OYYO..",
            "..OYYO....OYYO..",
            "..OOO......OOO..",
            "................",
            "..OOOOOOOOOOOO..",
            "..OCCCCCCCCCCO..",
            "..OOOOOOOOOOOO..",
            "................",
        )
        val catSprite = listOf(
            "..OO........OO..",
            ".OYYO......OYYO.",
            ".OYPYOOOOOOYPYO.",
            ".OYYYYYYYYYYYYO.",
            ".OYYKYYYYYYKYYO.",
            ".OYYYYPYYPYYYYO.",
            "..OYYYYYYYYYYO..",
            "...OYYYYYYYYO...",
            "..OYYRRRRRRYYO..",
            "..OYYYYYYYYYYO..",
            "..OYYYYYYYYYYO..",
            "..OYYOYYYYOYYO..",
            "...OO..OO..OO...",
            "..OOOO....OOOO..",
            "................",
            "................",
        )
        val dogSprite = listOf(
            "..OOO......OOO..",
            ".OBBBOOOOOOBBBO.",
            ".OBBYYYYYYYYBBO.",
            ".OBYYKYYYYKYBBO.",
            "..OYYYYKKYYYYO..",
            "..OYYYYPPYYYYO..",
            "...OYYYYYYYYO...",
            "...OORRRRRROO...",
            "..OYYYYYYYYYYO..",
            "..OYYYYYYYYYYO..",
            "..OYYOYYYYOYYO..",
            "...OO..OO..OO...",
            "...OOOO..OOOO...",
            "................",
            "................",
            "................",
        )
        val chickSprite = listOf(
            "......OOOO......",
            ".....OYYYYO.....",
            "....OYYYYYYO....",
            "...OYYKYYKYYO...",
            "...OYYYOOYYYO...",
            "...OYYYYYYYYO...",
            "..OYYYYYYYYYYO..",
            ".OYYYYYYYYYYYYO.",
            ".OYYYYYYYYYYYYO.",
            "..OYYYYYYYYYYO..",
            "...OYYYYYYYYO...",
            "....OOOOOOOO....",
            ".....O....O.....",
            "....OOO..OOO....",
            "................",
            "................",
        )
        val treeSprite = listOf(
            ".....OOOOOO.....",
            "...OOPPPPPPOO...",
            "..OPPPPPPPPPPO..",
            ".OPPPWWPPPPPPPO.",
            ".OPPPPPPPPWWPPO.",
            ".OPPPPPPPPPPPPO.",
            "..OOPPPPPPPPOO..",
            "....OOBBBBOO....",
            ".....OBBBBO.....",
            ".....OBBBBO.....",
            ".....OBBBBO.....",
            "....OOBBBBOO....",
            "...OGGGGGGGGO...",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
        )
        val cropSprite = listOf(
            "....OO....OO....",
            "...OGGO..OGGO...",
            "..OGGGGOOGGGGO..",
            "...OOGGGGGGOO...",
            "....OOGGGGOO....",
            ".....OORROO.....",
            "....ORRRRRRO....",
            "...ORRWWWWRRO...",
            "...ORRRRRRRRO...",
            "....ORRRRRRO....",
            ".....ORRRRO.....",
            "......OOOO......",
            "..OBBBBBBBBBBO..",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
        )
        val swordSprite = listOf(
            "............OOO.",
            "...........OCCO.",
            "..........OCWCO.",
            ".........OCWCO..",
            "........OCWCO...",
            ".......OCWCO....",
            "......OCWCO.....",
            "..OO.OCWCO......",
            "..OGGOCWO.......",
            "...OGGGO........",
            "..OOGYGOO.......",
            ".OGGO.OGGO......",
            ".OOO...OYYO.....",
            ".......OYYO.....",
            "........OO......",
            "................",
        )
        val hatSprite = listOf(
            ".....OOOOOO.....",
            "....OYYYYYYO....",
            "...OYYYYYYYYO...",
            "...ORRRRRRRRO...",
            "..OYYYYYYYYYYO..",
            ".OYYYYYYYYYYYYO.",
            "OYYYYYYYYYYYYYYO",
            ".OOOOOOOOOOOOOO.",
            "................",
            "...OOOOOOOOOO...",
            "..OBBBBBBBBBBO..",
            "..OBBYYYYYYBBO..",
            "..OBBBBBBBBBBO..",
            "...OOOOOOOOOO...",
            "................",
            "................",
        )
        val mapSprite = listOf(
            "..OOOOOOOOOOOO..",
            ".OYYYYYYYYYYYYO.",
            ".OYGGGGYYBBBYYO.",
            ".OYGGGGYYBBRBYO.",
            ".OYYGGYYYBBBYYO.",
            ".OYYYYBBYYYYYYO.",
            ".OYYYBYYBYYYYYO.",
            ".OYYRYYRYYYYYYO.",
            ".OYYYRRYYYYGGYO.",
            ".OYYRYYRYYGGGYO.",
            ".OYYYYYYYYYYYYO.",
            "..OOOOOOOOOOOO..",
            "......OOOO......",
            ".....OCCWCO.....",
            "......OOOO......",
            "................",
        )
        val cakeSprite = listOf(
            "......OOOO......",
            ".....ORRRRO.....",
            ".....ORRWRRO....",
            "...OOOOOOOOOO...",
            "..OWWWWWWWWWWO..",
            "..OWPPWWWWPPWO..",
            "..OPPPPPPPPPPO..",
            "..OYYYYYYYYYYO..",
            "..OYPPPPPPPPYO..",
            "..OYYYYYYYYYYO..",
            "..OOOOOOOOOOOO..",
            ".OCCCCCCCCCCCCO.",
            "..OOOOOOOOOOOO..",
            "................",
            "................",
            "................",
        )
        val lanternSprite = listOf(
            ".......OO.......",
            "......OOOO......",
            "....OOOOOOOO....",
            "...OYYYYYYYYO...",
            "..OYYWWWWWWYYO..",
            "..OYYWWWWWWYYO..",
            "..OYYYYYYYYYYO..",
            "..OYYRRRRRRYYO..",
            "..OYYYYYYYYYYO..",
            "...OYYYYYYYYO...",
            "....OOOOOOOO....",
            ".......OO.......",
            ".......OO.......",
            "................",
            "................",
            "................",
        )

        val O = 0xFF18181B.toInt()
        val W = 0xFFFFFFFF.toInt()
        val K = 0xFF0F172A.toInt()

        // 1. FRAMEWORK (3 covers)
        add(MyModsCategory.FRAMEWORK, BackdropTheme.SUNNY_MEADOW, mapOf('O' to O, 'R' to 0xFFDC2626.toInt(), 'W' to W, 'Y' to 0xFFFACC15.toInt(), 'K' to 0xFF78350F.toInt()), barnSprite)
        add(MyModsCategory.FRAMEWORK, BackdropTheme.STARRY_NIGHT, mapOf('O' to O, 'C' to 0xFF9333EA.toInt(), 'W' to 0xFFE9D5FF.toInt()), crystalSprite)
        add(MyModsCategory.FRAMEWORK, BackdropTheme.ARCANE_FORGE, mapOf('O' to O, 'G' to 0xFF7C3AED.toInt(), 'Y' to 0xFFC4B5FD.toInt(), 'C' to 0xFF38BDF8.toInt()), cogSprite)

        // 2. THAI_LANG (3 covers)
        add(MyModsCategory.THAI_LANG, BackdropTheme.LAVENDER_BLOOM, mapOf('O' to O, 'H' to 0xFF7C3AED.toInt(), 'S' to 0xFFFDE68A.toInt(), 'K' to K, 'B' to 0xFF2563EB.toInt(), 'C' to 0xFF93C5FD.toInt()), portraitSprite)
        add(MyModsCategory.THAI_LANG, BackdropTheme.GOLDEN_HARVEST, mapOf('O' to O, 'Y' to 0xFFF59E0B.toInt(), 'W' to 0xFFFFFBEB.toInt(), 'B' to 0xFF0284C7.toInt()), scrollSprite)
        add(MyModsCategory.THAI_LANG, BackdropTheme.OCEAN_BREEZE, mapOf('O' to O, 'P' to 0xFFF472B6.toInt(), 'W' to W, 'B' to 0xFF15803D.toInt(), 'G' to 0xFF4ADE80.toInt()), treeSprite)

        // 3. VOICE_AUDIO (3 covers)
        add(MyModsCategory.VOICE_AUDIO, BackdropTheme.SAKURA_SHRINE, mapOf('O' to O, 'P' to 0xFFF472B6.toInt(), 'B' to 0xFF78350F.toInt(), 'R' to 0xFFDC2626.toInt(), 'W' to 0xFFFFF1F2.toInt(), 'Y' to 0xFFFDE047.toInt(), 'N' to 0xFF4ADE80.toInt()), shrineSprite)
        add(MyModsCategory.VOICE_AUDIO, BackdropTheme.STARRY_NIGHT, mapOf('O' to O, 'S' to 0xFF94A3B8.toInt(), 'C' to 0xFF34D399.toInt()), micSprite)
        add(MyModsCategory.VOICE_AUDIO, BackdropTheme.EMERALD_VALLEY, mapOf('O' to O, 'S' to 0xFFE2E8F0.toInt(), 'C' to 0xFF10B981.toInt()), micSprite)

        // 4. FONTS (3 covers)
        add(MyModsCategory.FONTS, BackdropTheme.STARRY_NIGHT, mapOf('O' to O, 'B' to 0xFF38BDF8.toInt(), 'C' to 0xFFBAE6FD.toInt(), 'W' to W, 'Y' to 0xFFD97706.toInt()), pickaxeSprite)
        add(MyModsCategory.FONTS, BackdropTheme.COZY_CABIN, mapOf('O' to O, 'Y' to 0xFFFBBF24.toInt(), 'C' to 0xFFFDE68A.toInt()), fontLetterSprite)
        add(MyModsCategory.FONTS, BackdropTheme.TWILIGHT_FARM, mapOf('O' to O, 'Y' to 0xFFE9D5FF.toInt(), 'C' to 0xFFA855F7.toInt()), fontLetterSprite)

        // 5. CHARACTERS (3 covers)
        add(MyModsCategory.CHARACTERS, BackdropTheme.SUNNY_MEADOW, mapOf('O' to O, 'H' to 0xFF92400E.toInt(), 'S' to 0xFFFED7AA.toInt(), 'K' to K, 'B' to 0xFF1D4ED8.toInt(), 'C' to 0xFF60A5FA.toInt()), portraitSprite)
        add(MyModsCategory.CHARACTERS, BackdropTheme.TWILIGHT_FARM, mapOf('O' to O, 'H' to 0xFF4C1D95.toInt(), 'S' to 0xFFFDE68A.toInt(), 'K' to K, 'B' to 0xFF6D28D9.toInt(), 'C' to 0xFFC4B5FD.toInt()), portraitSprite)
        add(MyModsCategory.CHARACTERS, BackdropTheme.SAKURA_SHRINE, mapOf('O' to O, 'H' to 0xFFEC4899.toInt(), 'S' to 0xFFFFEDD5.toInt(), 'K' to K, 'B' to 0xFFBE185D.toInt(), 'C' to 0xFFFBCFE8.toInt()), portraitSprite)

        // 6. PETS (3 covers)
        add(MyModsCategory.PETS, BackdropTheme.COZY_CABIN, mapOf('O' to O, 'Y' to 0xFFFB923C.toInt(), 'P' to 0xFFFDA4AF.toInt(), 'R' to 0xFFEF4444.toInt(), 'K' to K), catSprite)
        add(MyModsCategory.PETS, BackdropTheme.SUNNY_MEADOW, mapOf('O' to O, 'B' to 0xFFB45309.toInt(), 'Y' to 0xFFFCD34D.toInt(), 'P' to 0xFFFB7185.toInt(), 'R' to 0xFFDC2626.toInt(), 'K' to K), dogSprite)
        add(MyModsCategory.PETS, BackdropTheme.GOLDEN_HARVEST, mapOf('O' to O, 'Y' to 0xFFFDE047.toInt(), 'K' to K), chickSprite)

        // 7. BUILDINGS (3 covers)
        add(MyModsCategory.BUILDINGS, BackdropTheme.TWILIGHT_FARM, mapOf('O' to O, 'R' to 0xFF7C3AED.toInt(), 'W' to 0xFFF5F3FF.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'K' to 0xFF581C87.toInt()), barnSprite)
        add(MyModsCategory.BUILDINGS, BackdropTheme.EMERALD_VALLEY, mapOf('O' to O, 'R' to 0xFF0EA5E9.toInt(), 'W' to 0xFFE0F2FE.toInt(), 'Y' to 0xFF4ADE80.toInt(), 'K' to 0xFF0369A1.toInt()), barnSprite)
        add(MyModsCategory.BUILDINGS, BackdropTheme.AUTUMN_WOODS, mapOf('O' to O, 'R' to 0xFFB45309.toInt(), 'W' to 0xFFFEF3C7.toInt(), 'Y' to 0xFFF97316.toInt(), 'K' to 0xFF78350F.toInt()), barnSprite)

        // 8. FARM (3 covers)
        add(MyModsCategory.FARM, BackdropTheme.SUNNY_MEADOW, mapOf('O' to O, 'G' to 0xFF22C55E.toInt(), 'R' to 0xFFEC4899.toInt(), 'W' to 0xFFFBCFE8.toInt(), 'B' to 0xFF92400E.toInt()), cropSprite)
        add(MyModsCategory.FARM, BackdropTheme.GOLDEN_HARVEST, mapOf('O' to O, 'G' to 0xFF84CC16.toInt(), 'R' to 0xFFF59E0B.toInt(), 'W' to 0xFFFEF3C7.toInt(), 'B' to 0xFF78350F.toInt()), cropSprite)
        add(MyModsCategory.FARM, BackdropTheme.EMERALD_VALLEY, mapOf('O' to O, 'G' to 0xFF10B981.toInt(), 'R' to 0xFF3B82F6.toInt(), 'W' to 0xFFDBEAFE.toInt(), 'B' to 0xFF451A03.toInt()), cropSprite)

        // 9. NATURE (3 covers)
        add(MyModsCategory.NATURE, BackdropTheme.SAKURA_SHRINE, mapOf('O' to O, 'P' to 0xFFF472B6.toInt(), 'W' to 0xFFFDF2F8.toInt(), 'B' to 0xFF78350F.toInt(), 'G' to 0xFF65A30D.toInt()), treeSprite)
        add(MyModsCategory.NATURE, BackdropTheme.AUTUMN_WOODS, mapOf('O' to O, 'P' to 0xFFEA580C.toInt(), 'W' to 0xFFFDE047.toInt(), 'B' to 0xFF7C2D12.toInt(), 'G' to 0xFFD97706.toInt()), treeSprite)
        add(MyModsCategory.NATURE, BackdropTheme.EMERALD_VALLEY, mapOf('O' to O, 'P' to 0xFF16A34A.toInt(), 'W' to 0xFF86EFAC.toInt(), 'B' to 0xFF713F12.toInt(), 'G' to 0xFF22C55E.toInt()), treeSprite)

        // 10. WEAPONS_TOOLS (3 covers)
        add(MyModsCategory.WEAPONS_TOOLS, BackdropTheme.ARCANE_FORGE, mapOf('O' to O, 'C' to 0xFFA855F7.toInt(), 'W' to 0xFFF3E8FF.toInt(), 'G' to 0xFFFACC15.toInt(), 'Y' to 0xFF94A3B8.toInt()), swordSprite)
        add(MyModsCategory.WEAPONS_TOOLS, BackdropTheme.COZY_CABIN, mapOf('O' to O, 'B' to 0xFF9333EA.toInt(), 'C' to 0xFFD8B4FE.toInt(), 'W' to W, 'Y' to 0xFF92400E.toInt()), pickaxeSprite)
        add(MyModsCategory.WEAPONS_TOOLS, BackdropTheme.OCEAN_BREEZE, mapOf('O' to O, 'C' to 0xFF38BDF8.toInt(), 'W' to W, 'G' to 0xFF0284C7.toInt(), 'Y' to 0xFFF59E0B.toInt()), swordSprite)

        // 11. CLOTHING (2 covers)
        add(MyModsCategory.CLOTHING, BackdropTheme.SUNNY_MEADOW, mapOf('O' to O, 'Y' to 0xFFFDE047.toInt(), 'R' to 0xFFEF4444.toInt(), 'B' to 0xFF3B82F6.toInt()), hatSprite)
        add(MyModsCategory.CLOTHING, BackdropTheme.LAVENDER_BLOOM, mapOf('O' to O, 'Y' to 0xFFC4B5FD.toInt(), 'R' to 0xFF7C3AED.toInt(), 'B' to 0xFF4C1D95.toInt()), hatSprite)

        // 12. MAPS (2 covers)
        add(MyModsCategory.MAPS, BackdropTheme.GOLDEN_HARVEST, mapOf('O' to O, 'Y' to 0xFFFDE68A.toInt(), 'G' to 0xFF22C55E.toInt(), 'B' to 0xFF38BDF8.toInt(), 'R' to 0xFFEF4444.toInt(), 'C' to 0xFF94A3B8.toInt(), 'W' to W), mapSprite)
        add(MyModsCategory.MAPS, BackdropTheme.OCEAN_BREEZE, mapOf('O' to O, 'Y' to 0xFFFEF9C3.toInt(), 'G' to 0xFF10B981.toInt(), 'B' to 0xFF0284C7.toInt(), 'R' to 0xFFF97316.toInt(), 'C' to 0xFF38BDF8.toInt(), 'W' to W), mapSprite)

        // 13. FOOD (3 covers)
        add(MyModsCategory.FOOD, BackdropTheme.PASTEL_BAKERY, mapOf('O' to O, 'R' to 0xFFEF4444.toInt(), 'W' to W, 'P' to 0xFFF472B6.toInt(), 'Y' to 0xFFFDE68A.toInt(), 'C' to 0xFF93C5FD.toInt()), cakeSprite)
        add(MyModsCategory.FOOD, BackdropTheme.COZY_CABIN, mapOf('O' to O, 'R' to 0xFFF97316.toInt(), 'W' to 0xFFFFEDD5.toInt(), 'P' to 0xFFD97706.toInt(), 'Y' to 0xFFFBBF24.toInt(), 'C' to 0xFFE5E7EB.toInt()), cakeSprite)
        add(MyModsCategory.FOOD, BackdropTheme.GOLDEN_HARVEST, mapOf('O' to O, 'C' to 0xFFFACC15.toInt(), 'W' to 0xFFFEF08A.toInt()), crystalSprite)

        // 14. GRAPHICS (3 covers)
        add(MyModsCategory.GRAPHICS, BackdropTheme.TWILIGHT_FARM, mapOf('O' to O, 'Y' to 0xFFFBBF24.toInt(), 'W' to 0xFFFFFBEB.toInt(), 'R' to 0xFFF97316.toInt()), lanternSprite)
        add(MyModsCategory.GRAPHICS, BackdropTheme.ARCANE_FORGE, mapOf('O' to O, 'C' to 0xFFEC4899.toInt(), 'W' to 0xFFFDF4FF.toInt()), crystalSprite)
        add(MyModsCategory.GRAPHICS, BackdropTheme.STARRY_NIGHT, mapOf('O' to O, 'G' to 0xFF8B5CF6.toInt(), 'Y' to 0xFFC084FC.toInt(), 'C' to 0xFF22D3EE.toInt()), cogSprite)

        return list
    }
}
