package com.example.kpzi.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.ui.i18n.tr
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal object MyModsCoverStore {
    private const val CUSTOM_COVER_DIR = "my_mods_custom_covers"
    private const val MAX_CUSTOM_COVER_PX = 256
    private const val CUSTOM_COVER_JPEG_QUALITY = 82

    private val fileBitmapCache = LruCache<String, ImageBitmap>(120)

    private val bundledCoverNames = setOf(
        "icon.png",
        "icon.jpg",
        "icon.jpeg",
        "icon.webp",
        "cover.png",
        "cover.jpg",
        "cover.jpeg",
        "cover.webp",
        "thumbnail.png",
        "thumbnail.jpg",
        "thumbnail.jpeg",
        "preview.png",
        "preview.jpg",
        "banner.png",
        "banner.jpg",
    )

    fun customCoverFile(context: Context, uniqueId: String): File {
        val dir = File(context.filesDir, CUSTOM_COVER_DIR)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "${hashUniqueId(uniqueId)}.jpg")
    }

    fun hasCustomCover(context: Context, uniqueId: String): Boolean {
        val file = customCoverFile(context, uniqueId)
        return file.isFile && file.length() > 0L
    }

    fun deleteCustomCover(context: Context, uniqueId: String): Boolean {
        val file = customCoverFile(context, uniqueId)
        evictFileCache(file.absolutePath)
        return if (file.exists()) file.delete() else false
    }

    suspend fun saveCompressedCustomCover(
        context: Context,
        uniqueId: String,
        sourceUri: Uri,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, boundsOpts)
            }
            if (boundsOpts.outWidth <= 0 || boundsOpts.outHeight <= 0) {
                return@runCatching false
            }

            var sampleSize = 1
            val longest = max(boundsOpts.outWidth, boundsOpts.outHeight)
            while (longest / sampleSize > MAX_CUSTOM_COVER_PX * 2) {
                sampleSize *= 2
            }

            val decodeOpts = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            val decoded = resolver.openInputStream(sourceUri)?.use { input ->
                BitmapFactory.decodeStream(input, null, decodeOpts)
            } ?: return@runCatching false

            val scaled = scaleDownKeepingAspect(decoded, MAX_CUSTOM_COVER_PX)
            val target = customCoverFile(context, uniqueId)
            val temp = File(target.parentFile, "${target.name}.tmp")
            FileOutputStream(temp).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, CUSTOM_COVER_JPEG_QUALITY, out)
                out.flush()
            }
            if (target.exists()) target.delete()
            temp.renameTo(target)

            if (scaled !== decoded) decoded.recycle()
            scaled.recycle()

            evictFileCache(target.absolutePath)
            true
        }.getOrDefault(false)
    }

    fun findBundledModCoverFile(folderPath: String): File? {
        if (folderPath.isBlank()) return null
        val root = File(folderPath)
        if (!root.isDirectory) return null

        val candidateDirs = listOf(
            root,
            File(root, "assets"),
            File(root, "Assets"),
            File(root, "images"),
        )
        for (dir in candidateDirs) {
            if (!dir.isDirectory) continue
            val children = dir.listFiles() ?: continue
            val match = children.firstOrNull { file ->
                file.isFile &&
                    file.length() in 128L..(8L * 1024L * 1024L) &&
                    file.name.lowercase(Locale.ROOT) in bundledCoverNames
            }
            if (match != null) return match
        }
        return null
    }

    suspend fun loadSampledFileBitmap(
        file: File,
        cacheKey: String,
    ): ImageBitmap? = withContext(Dispatchers.IO) {
        fileBitmapCache.get(cacheKey)?.let { return@withContext it }
        if (!file.isFile || file.length() <= 0L) return@withContext null

        runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null

            var sampleSize = 1
            val longest = max(bounds.outWidth, bounds.outHeight)
            while (longest / sampleSize > MAX_CUSTOM_COVER_PX * 2) {
                sampleSize *= 2
            }
            val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val bmp = BitmapFactory.decodeFile(file.absolutePath, opts) ?: return@runCatching null
            val imageBmp = bmp.asImageBitmap()
            fileBitmapCache.put(cacheKey, imageBmp)
            imageBmp
        }.getOrNull()
    }

    private fun evictFileCache(pathPrefix: String) {
        val snapshotKeys = fileBitmapCache.snapshot().keys.toList()
        snapshotKeys.forEach { key ->
            if (key.startsWith(pathPrefix)) {
                fileBitmapCache.remove(key)
            }
        }
    }

    private fun scaleDownKeepingAspect(source: Bitmap, maxEdge: Int): Bitmap {
        val w = source.width
        val h = source.height
        val longest = max(w, h)
        if (longest <= maxEdge) return source
        val scale = maxEdge.toFloat() / longest.toFloat()
        val dstW = (w * scale).roundToInt().coerceAtLeast(1)
        val dstH = (h * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, dstW, dstH, true)
    }

    private fun hashUniqueId(uniqueId: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(uniqueId.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        return digest.take(16).joinToString("") { byte -> "%02x".format(byte) }
    }
}

/**
 * Deterministically assigns a unique Pixel Art cover ID to every installed mod.
 * Uses canonical UniqueID order so toggling, filtering, or re-sorting never shifts covers,
 * prefers category-matched covers first, and avoids duplicate artwork on the same screen.
 */
internal fun computeCoverAssignmentMap(mods: List<InstalledMod>): Map<String, Int> {
    val totalCovers = PixelArtModCovers.totalCount
    if (mods.isEmpty() || totalCovers <= 0) return emptyMap()

    val canonical = mods.sortedWith(
        compareBy<InstalledMod> { it.uniqueId.lowercase(Locale.ROOT) }
            .thenBy { it.folderName.lowercase(Locale.ROOT) },
    )
    val usedIds = HashSet<Int>(canonical.size)
    val result = HashMap<String, Int>(canonical.size)

    for (mod in canonical) {
        val key = mod.uniqueId.lowercase(Locale.ROOT)
        if (result.containsKey(key)) continue

        val category = detectModCategory(mod)
        val catPool = PixelArtModCovers.idsForCategory(category)
        val seed = abs(key.hashCode())

        var chosenId: Int? = null
        if (catPool.isNotEmpty()) {
            val startIdx = seed % catPool.size
            for (offset in catPool.indices) {
                val candidate = catPool[(startIdx + offset) % catPool.size]
                if (candidate !in usedIds) {
                    chosenId = candidate
                    break
                }
            }
        }

        if (chosenId == null) {
            val startGlobal = seed % totalCovers
            for (offset in 0 until totalCovers) {
                val candidate = (startGlobal + offset) % totalCovers
                if (candidate !in usedIds) {
                    chosenId = candidate
                    break
                }
            }
        }

        val finalId = chosenId ?: (if (catPool.isNotEmpty()) catPool[seed % catPool.size] else seed % totalCovers)
        usedIds.add(finalId)
        result[key] = finalId
    }
    return result
}

private data class ResolvedCoverBitmap(
    val bitmap: ImageBitmap,
    val isPixelArt: Boolean,
)

@Composable
internal fun MyModCoverThumbnail(
    mod: InstalledMod,
    assignedCoverId: Int,
    coverRevision: Int,
    palette: MyModsPalette,
    size: Dp,
    onPickCover: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    badgeSize: Dp = 20.dp,
) {
    val context = LocalContext.current
    val fallbackPixelArt = remember(assignedCoverId) {
        PixelArtModCovers.getCoverBitmap(assignedCoverId)
    }

    val resolved by produceState(
        initialValue = ResolvedCoverBitmap(fallbackPixelArt, isPixelArt = true),
        key1 = mod.uniqueId,
        key2 = mod.folderPath,
        key3 = Pair(assignedCoverId, coverRevision),
    ) {
        // Priority 1: User-defined compressed custom cover
        val customFile = MyModsCoverStore.customCoverFile(context, mod.uniqueId)
        if (customFile.isFile && customFile.length() > 0L) {
            val loaded = MyModsCoverStore.loadSampledFileBitmap(
                file = customFile,
                cacheKey = "${customFile.absolutePath}:${customFile.lastModified()}:$coverRevision",
            )
            if (loaded != null) {
                value = ResolvedCoverBitmap(loaded, isPixelArt = false)
                return@produceState
            }
        }

        // Priority 2: Cover image bundled inside the mod folder
        val bundledFile = withContext(Dispatchers.IO) {
            MyModsCoverStore.findBundledModCoverFile(mod.folderPath)
        }
        if (bundledFile != null) {
            val loaded = MyModsCoverStore.loadSampledFileBitmap(
                file = bundledFile,
                cacheKey = "${bundledFile.absolutePath}:${bundledFile.lastModified()}",
            )
            if (loaded != null) {
                value = ResolvedCoverBitmap(loaded, isPixelArt = false)
                return@produceState
            }
        }

        // Priority 3 & 4: Category-matched or UniqueID Pixel Art cover
        value = ResolvedCoverBitmap(fallbackPixelArt, isPixelArt = true)
    }

    val changeCoverCd = tr(
        "เปลี่ยนรูปปกมอด ${mod.manifest.name}",
        "Change cover image for ${mod.manifest.name}",
    )

    Box(
        modifier = modifier
            .size(size)
            .semantics { contentDescription = changeCoverCd }
            .clickable(
                role = Role.Button,
                onClick = onPickCover,
            )
            .testTag("cover_pick_${mod.uniqueId}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(cornerRadius))
                .background(palette.surfaceSubtle)
                .border(0.8.dp, palette.border, RoundedCornerShape(cornerRadius)),
        ) {
            Image(
                bitmap = resolved.bitmap,
                contentDescription = mod.manifest.name,
                contentScale = ContentScale.Crop,
                filterQuality = if (resolved.isPixelArt) FilterQuality.None else FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(2.dp)
                .size(badgeSize)
                .clip(CircleShape)
                .background(palette.primary)
                .border(1.5.dp, palette.surface, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(badgeSize * 0.56f),
            )
        }
    }
}
