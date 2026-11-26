package com.example.kpzi.engine.loader

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.LoaderModuleDiagnostic
import com.example.kpzi.domain.model.ModManifest
import com.example.kpzi.domain.model.SmapiStatus

/**
 * Clean-room CompatibilityEngine: evaluates mod compatibility honestly across 4 tiers.
 * Never marks a mod as Android-verified without explicit evidence.
 */
class CompatibilityEngine {

    fun evaluateInstalledMod(
        manifest: ModManifest,
        installedUniqueIds: Set<String>,
        smapiVersion: String?,
    ): Pair<CompatibilityLevel, String?> {
        // 1. Check missing required dependencies
        val missingDeps = manifest.dependencies
            .filter { it.isRequired && it.uniqueId.isNotBlank() }
            .filter { dep -> installedUniqueIds.none { it.equals(dep.uniqueId, ignoreCase = true) } }

        if (missingDeps.isNotEmpty()) {
            return CompatibilityLevel.INCOMPATIBLE to
                "ขาดมอดที่ต้องใช้: ${missingDeps.joinToString { it.uniqueId }}"
        }

        // 2. Check ContentPackFor dependency
        val contentPackTarget = manifest.contentPackForUniqueId
        if (!contentPackTarget.isNullOrBlank() &&
            installedUniqueIds.none { it.equals(contentPackTarget, ignoreCase = true) }
        ) {
            return CompatibilityLevel.INCOMPATIBLE to
                "ต้องติดตั้งมอดหลัก: $contentPackTarget"
        }

        // 3. Check MinimumApiVersion against configured SMAPI version
        val minApi = manifest.minimumApiVersion
        if (!minApi.isNullOrBlank() && !smapiVersion.isNullOrBlank()) {
            val cleanSmapi = smapiVersion.substringBefore("-")
            if (compareVersions(cleanSmapi, minApi) < 0) {
                return CompatibilityLevel.INCOMPATIBLE to
                    "ต้องการ SMAPI เวอร์ชัน $minApi ขึ้นไป (ปัจจุบัน $cleanSmapi)"
            }
        }

        // 4. Content Packs without custom DLLs are typically portable across platforms
        if (manifest.entryDll.isNullOrBlank() && !contentPackTarget.isNullOrBlank()) {
            return CompatibilityLevel.LIKELY_COMPATIBLE to null
        }

        // 5. DLL mods without explicit Android verification remain UNVERIFIED
        return CompatibilityLevel.UNVERIFIED to null
    }

    fun compareVersions(v1: String, v2: String): Int {
        val p1 = v1.trim().split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val p2 = v2.trim().split(".").map { it.filter(Char::isDigit).toIntOrNull() ?: 0 }
        val maxLen = maxOf(p1.size, p2.size)
        for (i in 0 until maxLen) {
            val a = p1.getOrElse(i) { 0 }
            val b = p2.getOrElse(i) { 0 }
            if (a != b) return a.compareTo(b)
        }
        return 0
    }
}

/**
 * Clean-room SmapiIntegration: inspects installed SMAPI Android loader packages
 * without bundling any external binaries or bypassing Android package restrictions.
 */
class SmapiIntegration(private val context: Context) {

    fun inspectSmapi(preferredVersion: String, detection: GameDetectionResult): SmapiStatus {
        val pm = context.packageManager
        val loaderPkg = SMAPI_LOADER_PACKAGES.firstNotNullOfOrNull { pkg ->
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(pkg, 0)
                }
            }.getOrNull()
        }

        return when {
            loaderPkg != null -> SmapiStatus(
                isInstalled = true,
                loaderPackageName = loaderPkg.packageName,
                smapiVersion = loaderPkg.versionName ?: preferredVersion,
                gameLoaderStateLabel = "พร้อมเปิดเกมผ่าน SMAPI",
                isReadyToLaunch = detection.isGameInstalled,
            )
            detection.isGameInstalled -> SmapiStatus(
                isInstalled = false,
                loaderPackageName = null,
                smapiVersion = preferredVersion,
                gameLoaderStateLabel = "พบตัวเกม (ยังไม่มี SMAPI Loader แยก)",
                isReadyToLaunch = true,
            )
            else -> SmapiStatus(
                isInstalled = false,
                loaderPackageName = null,
                smapiVersion = preferredVersion,
                gameLoaderStateLabel = "ไม่พบเกม Stardew Valley บนเครื่อง",
                isReadyToLaunch = false,
            )
        }
    }

    companion object {
        val SMAPI_LOADER_PACKAGES = listOf(
            "com.zane.smapiinstaller",
            "com.nrtnarathip.smapiloader",
            "com.smapi.loader",
        )
    }
}

data class PreLaunchReport(
    val canLaunch: Boolean,
    val activeModCount: Int,
    val issueCount: Int,
    val diagnostics: List<LoaderModuleDiagnostic>,
    val blockingReason: String?,
)

/**
 * Clean-room ModRuntime: validates enabled mods, dependencies, and duplicate UniqueIDs before launch.
 */
class ModRuntime(
    private val context: Context,
    private val assetManager: AssetManager,
) {
    fun buildDiagnostics(
        detection: GameDetectionResult,
        smapiStatus: SmapiStatus,
        installedMods: List<InstalledMod>,
    ): PreLaunchReport {
        val runtimeDiag = RuntimeManager().evaluate(detection)
        val assetDiag = assetManager.describeWorkspace()
        val nativeDiag = NativeLibraryManager().inspectNativeLibs(detection)

        val enabledMods = installedMods.filter { it.isEnabled }
        val issueMods = enabledMods.filter { it.issueMessage != null }

        val duplicateIds = enabledMods
            .groupBy { it.uniqueId.lowercase() }
            .filter { it.key.isNotBlank() && it.value.size > 1 }
            .keys

        val modRuntimeDiag = LoaderModuleDiagnostic(
            moduleName = "ModRuntime",
            statusOk = issueMods.isEmpty() && duplicateIds.isEmpty(),
            summaryTh = when {
                duplicateIds.isNotEmpty() -> "พบ UniqueID ซ้ำซ้อน ${duplicateIds.size} รายการ"
                issueMods.isNotEmpty() -> "พบมอดที่มีปัญหา ${issueMods.size} รายการ"
                else -> "ตรวจสอบมอดที่เปิดใช้งาน ${enabledMods.size} ตัวแล้ว"
            },
            detail = "เปิดใช้งาน ${enabledMods.size} / ทั้งหมด ${installedMods.size} มอด",
        )

        val smapiDiag = LoaderModuleDiagnostic(
            moduleName = "SmapiIntegration",
            statusOk = smapiStatus.isInstalled,
            summaryTh = smapiStatus.gameLoaderStateLabel,
            detail = smapiStatus.loaderPackageName ?: "Target SMAPI ${smapiStatus.smapiVersion ?: "-"}",
        )

        val blocking = when {
            !detection.isGameInstalled -> "ไม่พบเกม Stardew Valley บนอุปกรณ์นี้ กรุณาติดตั้งตัวเกมแท้จาก Google Play Store ก่อนเปิดเกม"
            duplicateIds.isNotEmpty() -> "พบมอดที่มี UniqueID ซ้ำกัน กรุณาปิดหรือลบมอดที่ซ้ำก่อนเริ่มเกม"
            else -> null
        }

        return PreLaunchReport(
            canLaunch = detection.isGameInstalled && blocking == null,
            activeModCount = enabledMods.size,
            issueCount = issueMods.size + duplicateIds.size,
            diagnostics = listOf(runtimeDiag, nativeDiag, assetDiag, smapiDiag, modRuntimeDiag),
            blockingReason = blocking,
        )
    }
}

sealed interface LaunchOutcome {
    data class Launched(val targetPackage: String, val viaSmapi: Boolean) : LaunchOutcome
    data class Blocked(val reason: String) : LaunchOutcome
}

/**
 * Clean-room GameLauncher: launches SMAPI Loader if installed, otherwise launches official Stardew Valley package.
 */
class GameLauncher(private val context: Context) {

    fun launch(
        detection: GameDetectionResult,
        smapiStatus: SmapiStatus,
        logManager: LogManager,
    ): LaunchOutcome {
        val targetPackage = smapiStatus.loaderPackageName ?: detection.packageName
        if (targetPackage.isNullOrBlank()) {
            val msg = "ไม่พบแพ็กเกจเกม Stardew Valley หรือ SMAPI Loader บนเครื่อง"
            logManager.warn("GameLauncher", msg)
            return LaunchOutcome.Blocked(msg)
        }

        val launchIntent = context.packageManager.getLaunchIntentForPackage(targetPackage)
        if (launchIntent == null) {
            val msg = "ระบบ Android ไม่อนุญาตให้เปิดแพ็กเกจ $targetPackage โดยตรง"
            logManager.error("GameLauncher", msg)
            return LaunchOutcome.Blocked(msg)
        }

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching {
            context.startActivity(launchIntent)
            val viaSmapi = smapiStatus.loaderPackageName == targetPackage
            logManager.info(
                "GameLauncher",
                if (viaSmapi) "เปิดเกมผ่าน SMAPI Loader ($targetPackage)" else "เปิดเกม Stardew Valley ($targetPackage)",
            )
            LaunchOutcome.Launched(targetPackage = targetPackage, viaSmapi = viaSmapi)
        }.getOrElse { err ->
            val msg = "ไม่สามารถเปิดเกมได้: ${err.localizedMessage ?: "Unknown error"}"
            logManager.error("GameLauncher", msg)
            LaunchOutcome.Blocked(msg)
        }
    }
}
