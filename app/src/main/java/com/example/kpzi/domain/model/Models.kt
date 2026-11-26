package com.example.kpzi.domain.model

enum class CompatibilityLevel(val labelTh: String) {
    VERIFIED_WORKING("ทดสอบแล้วว่าใช้งานได้"),
    LIKELY_COMPATIBLE("คาดว่ารองรับ"),
    UNVERIFIED("ยังไม่ได้ตรวจสอบ"),
    INCOMPATIBLE("ไม่รองรับ"),
}

enum class ModCategory(val labelTh: String, val queryKeyword: String?) {
    ALL("ทั้งหมด", null),
    POPULAR("ยอดนิยม", null),
    NEW("มาใหม่", null),
    RECENTLY_UPDATED("อัปเดตล่าสุด", null),
    GRAPHICS("กราฟิก", "visual"),
    GAMEPLAY("ระบบเกม", "gameplay"),
    CHARACTERS("ตัวละคร", "portrait"),
    FARM("ฟาร์ม", "farm"),
    BUILDINGS("อาคาร", "building"),
    THAI_LANG("ภาษาไทย", "thai"),
    TOOLS("เครื่องมือ", "framework"),
    CHEATS("มอดโกง", "cheat"),
}

enum class RateLimitStatus(val labelTh: String) {
    UNAVAILABLE("รอการเชื่อมต่อ API"),
    HEALTHY("ปกติ"),
    LOW("ใกล้เต็มโควตา"),
    EXHAUSTED("โควตาหมดชั่วคราว"),
    RATE_LIMITED("ถูกจำกัดความถี่ (429)"),
}

data class ApiQuotaState(
    val dailyLimit: Int? = null,
    val dailyRemaining: Int? = null,
    val dailyReset: String? = null,
    val hourlyLimit: Int? = null,
    val hourlyRemaining: Int? = null,
    val hourlyReset: String? = null,
    val retryAfterSeconds: Int? = null,
    val lastUpdatedEpochMs: Long? = null,
    val status: RateLimitStatus = RateLimitStatus.UNAVAILABLE,
)

data class NexusUserProfile(
    val userId: Long,
    val username: String,
    val email: String? = null,
    val profileUrl: String? = null,
    val isPremium: Boolean = false,
    val isSupporter: Boolean = false,
    val isConnected: Boolean = true,
)

data class NexusModSummary(
    val modId: Long,
    val name: String,
    val summary: String,
    val author: String,
    val version: String,
    val categoryName: String,
    val pictureUrl: String?,
    val downloads: Long,
    val endorsements: Long,
    val updatedEpochSec: Long,
    val fileSizeBytes: Long? = null,
    val compatibility: CompatibilityLevel = CompatibilityLevel.UNVERIFIED,
    val feedTag: String = "catalog",
)

data class NexusModFile(
    val fileId: Long,
    val name: String,
    val version: String,
    val fileName: String,
    val sizeBytes: Long,
    val categoryName: String,
    val uploadedEpochSec: Long,
    val isPrimary: Boolean = false,
    val contentPreviewLink: String? = null,
)

data class ModChangelogEntry(
    val version: String,
    val changes: List<String>,
)

data class NexusModDetail(
    val summary: NexusModSummary,
    val description: String,
    val previewImageUrls: List<String>,
    val files: List<NexusModFile>,
    val dependencies: List<String>,
    val supportedGameVersions: List<String>,
    val requiredSmapiVersion: String?,
    val changelogs: List<ModChangelogEntry>,
    val licenseName: String,
    val nexusUrl: String,
    val isOfflineCached: Boolean = false,
)

data class ModDependency(
    val uniqueId: String,
    val minimumVersion: String? = null,
    val isRequired: Boolean = true,
)

data class ModManifest(
    val uniqueId: String,
    val name: String,
    val author: String,
    val version: String,
    val description: String,
    val minimumApiVersion: String?,
    val entryDll: String?,
    val contentPackForUniqueId: String?,
    val dependencies: List<ModDependency>,
    val updateKeys: List<String>,
)

data class InstalledMod(
    val uniqueId: String,
    val folderName: String,
    val folderPath: String,
    val manifest: ModManifest,
    val isEnabled: Boolean,
    val compatibility: CompatibilityLevel,
    val issueMessage: String? = null,
    val updateAvailableVersion: String? = null,
    val nexusModId: Long? = null,
    val sizeBytes: Long = 0L,
    val installedAtMs: Long = 0L,
)

enum class DownloadStatus(val labelTh: String) {
    QUEUED("รอคิว"),
    DOWNLOADING("กำลังดาวน์โหลด"),
    PAUSED("หยุดชั่วคราว"),
    VERIFYING("ตรวจสอบไฟล์"),
    COMPLETED("เสร็จสิ้น"),
    FAILED("ล้มเหลว"),
    CANCELLED("ยกเลิกแล้ว"),
}

data class DownloadTask(
    val id: String,
    val modId: Long,
    val fileId: Long,
    val modName: String,
    val fileName: String,
    val version: String,
    val pictureUrl: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val speedBytesPerSec: Long,
    val etaSeconds: Long?,
    val status: DownloadStatus,
    val localFilePath: String?,
    val sha256: String?,
    val isCorrupted: Boolean = false,
    val errorMessage: String? = null,
    val supportsResume: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

data class GameDetectionResult(
    val isGameInstalled: Boolean,
    val packageName: String?,
    val versionName: String?,
    val versionCode: Long?,
    val supportedAbis: List<String>,
    val isArm64Supported: Boolean,
    val androidVersionLabel: String,
    val androidSdkInt: Int,
    val availableStorageBytes: Long,
    val totalStorageBytes: Long,
    val baseApkPath: String?,
    val splitApkPaths: List<String>,
    val runtimeType: String,
    val hasSafModsPermission: Boolean,
    val safModsUri: String?,
)

data class SmapiStatus(
    val isInstalled: Boolean,
    val loaderPackageName: String?,
    val smapiVersion: String?,
    val gameLoaderStateLabel: String,
    val isReadyToLaunch: Boolean,
)

data class LoaderModuleDiagnostic(
    val moduleName: String,
    val statusOk: Boolean,
    val summaryTh: String,
    val detail: String,
)

enum class LogLevel(val tag: String) {
    TRACE("TRACE"),
    DEBUG("DEBUG"),
    INFO("INFO"),
    WARN("WARN"),
    ERROR("ERROR"),
    ALERT("ALERT"),
}

data class LogEntry(
    val id: Long,
    val timestamp: String,
    val source: String,
    val level: LogLevel,
    val message: String,
)

enum class BackupCategory(val labelTh: String) {
    MODS_AND_CONFIG("มอดและบันทึกระบบ"),
    SAVE_FILES("เซฟเกม (SAF)"),
}

data class SaveBackupInfo(
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val createdAtMs: Long,
    val category: BackupCategory = if (fileName.startsWith("KpziModsBackup_")) {
        BackupCategory.MODS_AND_CONFIG
    } else {
        BackupCategory.SAVE_FILES
    },
)
