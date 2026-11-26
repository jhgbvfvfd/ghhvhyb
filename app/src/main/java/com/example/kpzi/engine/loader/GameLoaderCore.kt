package com.example.kpzi.engine.loader

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.LoaderModuleDiagnostic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/**
 * Clean-room implementation of GameDetector: uses Android PackageManager and StatFs
 * strictly within OS permission boundaries. Never redistributes game files.
 */
class GameDetector(private val context: Context) {

    suspend fun detect(safModsUri: String?): GameDetectionResult = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val matchedPkg = STARDEW_PACKAGES.firstNotNullOfOrNull { pkg ->
            runCatching {
                @Suppress("DEPRECATION")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    pm.getPackageInfo(pkg, 0)
                }
            }.getOrNull()
        }

        val abis = Build.SUPPORTED_ABIS?.toList().orEmpty()
        val isArm64 = abis.any { it.equals("arm64-v8a", ignoreCase = true) }

        val stat = StatFs(Environment.getDataDirectory().path)
        val availBytes = stat.availableBytes
        val totalBytes = stat.totalBytes

        val appInfo = matchedPkg?.applicationInfo
        val baseApk = appInfo?.sourceDir
        val splitApks = appInfo?.splitSourceDirs?.toList().orEmpty()

        val pkgSummary = if (baseApk != null) {
            PackageReader().inspectApk(baseApk, splitApks)
        } else {
            null
        }
        val runtimeType = if (pkgSummary != null) {
            AssemblyReader().detectRuntime(baseApk, splitApks, pkgSummary)
        } else {
            "ไม่พบตัวเกมบนอุปกรณ์"
        }

        val hasSafPermission = !safModsUri.isNullOrBlank() &&
            context.contentResolver.persistedUriPermissions.any {
                it.uri.toString() == safModsUri && it.isReadPermission
            }

        val versionCode = matchedPkg?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) it.longVersionCode
            else @Suppress("DEPRECATION") it.versionCode.toLong()
        }

        GameDetectionResult(
            isGameInstalled = matchedPkg != null,
            packageName = matchedPkg?.packageName,
            versionName = matchedPkg?.versionName,
            versionCode = versionCode,
            supportedAbis = abis,
            isArm64Supported = isArm64,
            androidVersionLabel = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            androidSdkInt = Build.VERSION.SDK_INT,
            availableStorageBytes = availBytes,
            totalStorageBytes = totalBytes,
            baseApkPath = baseApk,
            splitApkPaths = splitApks,
            runtimeType = runtimeType,
            hasSafModsPermission = hasSafPermission,
            safModsUri = safModsUri,
        )
    }

    companion object {
        val STARDEW_PACKAGES = listOf(
            "com.chucklefish.stardewvalley",
            "com.concernedape.stardewvalley",
        )
    }
}

data class ApkInspectionSummary(
    val hasMonoLib: Boolean,
    val hasDotNetLib: Boolean,
    val hasAssembliesFolder: Boolean,
    val nativeAbiFolders: List<String>,
    val dllCount: Int,
)

/**
 * Clean-room PackageReader: inspects installed Base APK and Split APKs read-only via ZipFile
 * to identify runtime layout without modifying or copying protected game assets.
 */
class PackageReader {
    fun inspectApk(baseApkPath: String, splitApkPaths: List<String>): ApkInspectionSummary {
        var hasMono = false
        var hasDotNet = false
        var hasAssemblies = false
        var dllCount = 0
        val abiFolders = mutableSetOf<String>()

        val allPaths = listOf(baseApkPath) + splitApkPaths
        for (path in allPaths) {
            runCatching {
                ZipFile(File(path)).use { zip ->
                    val entries = zip.entries()
                    while (entries.hasMoreElements()) {
                        val name = entries.nextElement().name
                        if (name.contains("libmonosgen", ignoreCase = true) || name.contains("libmonodroid", ignoreCase = true)) {
                            hasMono = true
                        }
                        if (name.contains("libcoreclr", ignoreCase = true) || name.contains("libhostpolicy", ignoreCase = true)) {
                            hasDotNet = true
                        }
                        if (name.startsWith("assemblies/") && name.endsWith(".dll", ignoreCase = true)) {
                            hasAssemblies = true
                            dllCount++
                        }
                        if (name.startsWith("lib/")) {
                            val parts = name.split("/")
                            if (parts.size >= 2 && parts[1].isNotBlank()) {
                                abiFolders.add(parts[1])
                            }
                        }
                    }
                }
            }
        }
        return ApkInspectionSummary(
            hasMonoLib = hasMono,
            hasDotNetLib = hasDotNet,
            hasAssembliesFolder = hasAssemblies,
            nativeAbiFolders = abiFolders.sorted(),
            dllCount = dllCount,
        )
    }
}

/**
 * Clean-room AssemblyReader: determines managed runtime (.NET / Mono / Xamarin) from inspected APK entries.
 */
class AssemblyReader {
    fun detectRuntime(
        baseApkPath: String?,
        splitApkPaths: List<String>,
        summary: ApkInspectionSummary,
    ): String {
        if (baseApkPath == null) return "ไม่สามารถอ่านแพ็กเกจเกมได้"
        return when {
            summary.hasDotNetLib -> ".NET Android Runtime (${summary.dllCount} assemblies)"
            summary.hasMonoLib -> "Mono / Xamarin Runtime (${summary.dllCount} assemblies)"
            summary.hasAssembliesFolder -> "Managed Assemblies (${summary.dllCount} .dll)"
            splitApkPaths.isNotEmpty() -> "Split APK Bundle (${splitApkPaths.size + 1} ไฟล์)"
            else -> "Android Native APK"
        }
    }
}

/**
 * Clean-room RuntimeManager: checks device ABI, Android API level, and storage readiness.
 */
class RuntimeManager {
    fun evaluate(detection: GameDetectionResult): LoaderModuleDiagnostic {
        val ok = detection.isArm64Supported && detection.availableStorageBytes >= MIN_REQUIRED_BYTES
        val summary = when {
            !detection.isArm64Supported -> "อุปกรณ์ไม่รองรับ ARM64 (64-bit)"
            detection.availableStorageBytes < MIN_REQUIRED_BYTES -> "พื้นที่ว่างเหลือน้อยกว่า 500 MB"
            else -> "พร้อมรองรับรันไทม์ 64-bit (${detection.supportedAbis.firstOrNull() ?: "arm64-v8a"})"
        }
        return LoaderModuleDiagnostic(
            moduleName = "RuntimeManager",
            statusOk = ok,
            summaryTh = summary,
            detail = "ABI: ${detection.supportedAbis.joinToString()} | ${detection.androidVersionLabel}",
        )
    }

    private companion object {
        const val MIN_REQUIRED_BYTES = 500L * 1024L * 1024L
    }
}

/**
 * Clean-room AssetManager: manages Kpzi's isolated Mods, Staging, Download, and Backup directories.
 */
class AssetManager(private val context: Context) {
    val modsRootDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "Mods").apply { mkdirs() }

    val downloadsDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "Downloads").apply { mkdirs() }

    val modBackupsDir: File
        get() = File(context.filesDir, "ModBackups").apply { mkdirs() }

    val saveBackupsDir: File
        get() = File(context.getExternalFilesDir(null) ?: context.filesDir, "SaveBackups").apply { mkdirs() }

    val logsDir: File
        get() = File(context.filesDir, "Logs").apply { mkdirs() }

    fun describeWorkspace(): LoaderModuleDiagnostic {
        val modFolders = modsRootDir.listFiles()?.count { it.isDirectory } ?: 0
        return LoaderModuleDiagnostic(
            moduleName = "AssetManager",
            statusOk = modsRootDir.canWrite(),
            summaryTh = "โฟลเดอร์มอดพร้อมใช้งาน ($modFolders โฟลเดอร์)",
            detail = modsRootDir.absolutePath,
        )
    }
}

/**
 * Clean-room NativeLibraryManager: inspects native ABI slices inside installed game APK.
 */
class NativeLibraryManager {
    fun inspectNativeLibs(detection: GameDetectionResult): LoaderModuleDiagnostic {
        val base = detection.baseApkPath
        if (!detection.isGameInstalled || base == null) {
            return LoaderModuleDiagnostic(
                moduleName = "NativeLibraryManager",
                statusOk = false,
                summaryTh = "รอการติดตั้งเกม Stardew Valley",
                detail = "ไม่พบ Base APK เพื่อตรวจสอบไลบรารี .so",
            )
        }
        val summary = PackageReader().inspectApk(base, detection.splitApkPaths)
        val hasArm64Slice = summary.nativeAbiFolders.any { it.contains("arm64") }
        return LoaderModuleDiagnostic(
            moduleName = "NativeLibraryManager",
            statusOk = hasArm64Slice || summary.nativeAbiFolders.isEmpty(),
            summaryTh = if (summary.nativeAbiFolders.isNotEmpty()) {
                "พบ ABI: ${summary.nativeAbiFolders.joinToString(", ")}"
            } else {
                "ตรวจสอบ Base APK แล้ว"
            },
            detail = "Split APKs: ${detection.splitApkPaths.size} ไฟล์",
        )
    }
}
