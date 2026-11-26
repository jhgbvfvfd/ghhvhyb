package com.example

import com.example.kpzi.core.network.ModTranslator
import com.example.kpzi.core.network.NexusApiClient
import com.example.kpzi.core.network.NexusQuotaManager
import com.example.kpzi.data.local.KpziDatabase
import com.example.kpzi.data.local.PlaySessionEntity
import com.example.kpzi.data.local.toDomain
import com.example.kpzi.data.local.toEntity
import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.ModDependency
import com.example.kpzi.domain.model.ModManifest
import com.example.kpzi.domain.model.RateLimitStatus
import com.example.kpzi.engine.loader.CompatibilityEngine
import com.example.kpzi.engine.loader.ConflictDetector
import com.example.kpzi.engine.loader.ConflictType
import com.example.kpzi.notifications.PlaytimeFormatter
import com.example.ui.theme.KpziThemeState
import com.example.ui.theme.ThemeMode
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KpziCoreUnitTest {

    @Test
    fun `quota manager reads real response headers and 429 retry after`() {
        val quotaManager = NexusQuotaManager()
        val headers = Headers.headersOf(
            "X-RL-Daily-Limit", "2500",
            "X-RL-Daily-Remaining", "2450",
            "X-RL-Daily-Reset", "2026-11-26T00:00:00+00:00",
            "X-RL-Hourly-Limit", "100",
            "X-RL-Hourly-Remaining", "95",
            "X-RL-Hourly-Reset", "2026-11-26T02:00:00+00:00",
        )
        quotaManager.updateFromHeaders(headers, 200)
        val healthy = quotaManager.quotaState.value
        assertEquals(2500, healthy.dailyLimit)
        assertEquals(2450, healthy.dailyRemaining)
        assertEquals(100, healthy.hourlyLimit)
        assertEquals(95, healthy.hourlyRemaining)
        assertEquals(RateLimitStatus.HEALTHY, healthy.status)

        val limitedHeaders = Headers.headersOf("Retry-After", "45")
        quotaManager.updateFromHeaders(limitedHeaders, 429)
        val limited = quotaManager.quotaState.value
        assertEquals(RateLimitStatus.RATE_LIMITED, limited.status)
        assertEquals(45, limited.retryAfterSeconds)
    }

    @Test
    fun `nexus api client parses mod id and url`() {
        assertEquals(20985L, NexusApiClient.parseModIdFromInput("20985"))
        assertEquals(
            1915L,
            NexusApiClient.parseModIdFromInput("https://www.nexusmods.com/stardewvalley/mods/1915?tab=files"),
        )
        assertNull(NexusApiClient.parseModIdFromInput("Stardew Valley Expanded"))
    }

    @Test
    fun `compatibility engine evaluates 4 honest tiers`() {
        val engine = CompatibilityEngine()
        val contentPack = ModManifest(
            uniqueId = "Kpzi.CropPack",
            name = "Crop Pack",
            author = "Tester",
            version = "1.0.0",
            description = "",
            minimumApiVersion = "4.0.0",
            entryDll = null,
            contentPackForUniqueId = "Pathoschild.ContentPatcher",
            dependencies = emptyList(),
            updateKeys = emptyList(),
        )
        val (missingLevel, missingMsg) = engine.evaluateInstalledMod(contentPack, emptySet(), "4.1.10-android")
        assertEquals(CompatibilityLevel.INCOMPATIBLE, missingLevel)
        assertTrue(missingMsg!!.contains("Pathoschild.ContentPatcher"))

        val (okLevel, okMsg) = engine.evaluateInstalledMod(
            contentPack,
            setOf("Pathoschild.ContentPatcher"),
            "4.1.10-android",
        )
        assertEquals(CompatibilityLevel.LIKELY_COMPATIBLE, okLevel)
        assertNull(okMsg)

        val dllMod = contentPack.copy(
            contentPackForUniqueId = null,
            entryDll = "Some.dll",
            dependencies = emptyList(),
        )
        val (dllLevel, _) = engine.evaluateInstalledMod(dllMod, emptySet(), "4.1.10-android")
        assertEquals(CompatibilityLevel.UNVERIFIED, dllLevel)

        val depMod = dllMod.copy(
            dependencies = listOf(ModDependency("Spacechase0.SpaceCore", isRequired = true)),
        )
        val (depMissingLevel, _) = engine.evaluateInstalledMod(depMod, emptySet(), "4.1.10-android")
        assertEquals(CompatibilityLevel.INCOMPATIBLE, depMissingLevel)
    }

    @Test
    fun `mod translator preserves original details non-destructively`() {
        val original = "Requires SMAPI and Content Patcher for Stardew Valley 1.6."
        val translated = ModTranslator.translatePreservingOriginal(original)
        assertTrue(translated.contains("ต้องใช้ SMAPI"))
        assertTrue(translated.contains("Stardew Valley เวอร์ชัน 1.6"))
        assertEquals("Requires SMAPI and Content Patcher for Stardew Valley 1.6.", original)
    }

    @Test
    fun `installed mod entity roundtrip preserves uniqueId version localPath and status`() {
        val original = InstalledMod(
            uniqueId = "Kpzi.TestPack",
            folderName = "TestPack",
            folderPath = "/storage/emulated/0/Android/data/com.aistudio.kpzimodmanager/files/Mods/.TestPack",
            manifest = ModManifest(
                uniqueId = "Kpzi.TestPack",
                name = "Kpzi Test Pack",
                author = "Tester",
                version = "2.1.4",
                description = "Sample description",
                minimumApiVersion = "4.0.0",
                entryDll = null,
                contentPackForUniqueId = "Pathoschild.ContentPatcher",
                dependencies = listOf(ModDependency("Spacechase0.SpaceCore", "2.0.0", isRequired = true)),
                updateKeys = listOf("Nexus:9999"),
            ),
            isEnabled = false,
            compatibility = CompatibilityLevel.LIKELY_COMPATIBLE,
            issueMessage = null,
            updateAvailableVersion = "2.2.0",
            nexusModId = 9999L,
            sizeBytes = 4096L,
            installedAtMs = 1700000000000L,
        )

        val entity = original.toEntity()
        assertEquals("Kpzi.TestPack", entity.uniqueId)
        assertEquals("2.1.4", entity.version)
        assertEquals(original.folderPath, entity.localPath)
        assertEquals(false, entity.isEnabled)

        val restored = entity.toDomain()
        assertEquals(original.uniqueId, restored.uniqueId)
        assertEquals(original.manifest.version, restored.manifest.version)
        assertEquals(original.folderPath, restored.folderPath)
        assertEquals(original.isEnabled, restored.isEnabled)
        assertEquals(1, restored.manifest.dependencies.size)
        assertEquals("Spacechase0.SpaceCore", restored.manifest.dependencies.first().uniqueId)
        assertEquals(listOf("Nexus:9999"), restored.manifest.updateKeys)
        assertEquals(1, KpziDatabase.MIGRATION_1_2.startVersion)
        assertEquals(2, KpziDatabase.MIGRATION_1_2.endVersion)
    }

    private fun testMod(
        uniqueId: String,
        name: String = uniqueId,
        version: String = "1.0.0",
        isEnabled: Boolean = true,
        minApi: String? = null,
        contentPackFor: String? = null,
        dependencies: List<ModDependency> = emptyList(),
        folderName: String = uniqueId,
    ): InstalledMod = InstalledMod(
        uniqueId = uniqueId,
        folderName = folderName,
        folderPath = "/mods/$folderName",
        manifest = ModManifest(
            uniqueId = uniqueId,
            name = name,
            author = "Tester",
            version = version,
            description = "",
            minimumApiVersion = minApi,
            entryDll = null,
            contentPackForUniqueId = contentPackFor,
            dependencies = dependencies,
            updateKeys = emptyList(),
        ),
        isEnabled = isEnabled,
        compatibility = CompatibilityLevel.LIKELY_COMPATIBLE,
        issueMessage = null,
        updateAvailableVersion = null,
        nexusModId = null,
        sizeBytes = 1024L,
        installedAtMs = 1700000000000L,
    )

    @Test
    fun `conflict detector identifies circular dependency loops`() {
        val detector = ConflictDetector()
        val modA = testMod(
            uniqueId = "Author.ModA",
            name = "Mod A",
            dependencies = listOf(ModDependency("Author.ModB", isRequired = true)),
        )
        val modB = testMod(
            uniqueId = "Author.ModB",
            name = "Mod B",
            dependencies = listOf(ModDependency("Author.ModC", isRequired = true)),
        )
        val modC = testMod(
            uniqueId = "Author.ModC",
            name = "Mod C",
            dependencies = listOf(ModDependency("Author.ModA", isRequired = true)),
        )

        val report = detector.analyze(listOf(modA, modB, modC), "4.1.10-android")
        assertEquals(1, report.loopCount)
        val loopIssue = report.issues.first { it.type == ConflictType.DEPENDENCY_LOOP }
        assertTrue(loopIssue.relatedModUniqueIds.containsAll(listOf("Author.ModA", "Author.ModB", "Author.ModC")))
    }

    @Test
    fun `conflict detector catches dependency and smapi version mismatches`() {
        val detector = ConflictDetector()
        val host = testMod(
            uniqueId = "Pathoschild.ContentPatcher",
            name = "Content Patcher",
            version = "1.8.0-beta.1",
        )
        val pack = testMod(
            uniqueId = "Kpzi.Pack",
            name = "Kpzi Pack",
            minApi = "4.2.0",
            contentPackFor = "Pathoschild.ContentPatcher",
            dependencies = listOf(ModDependency("Pathoschild.ContentPatcher", minimumVersion = "2.0.0", isRequired = true)),
        )

        val report = detector.analyze(listOf(host, pack), "4.1.10-android")
        assertEquals(2, report.versionMismatchCount)
        assertTrue(report.issues.any { it.type == ConflictType.VERSION_MISMATCH && it.requiredVersion == "2.0.0" })
        assertTrue(report.issues.any { it.type == ConflictType.SMAPI_VERSION_MISMATCH && it.requiredVersion == "4.2.0" })
    }

    @Test
    fun `conflict detector flags disabled dependencies missing hosts duplicates and clean graphs`() {
        val detector = ConflictDetector()
        val disabledHost = testMod(
            uniqueId = "Spacechase0.SpaceCore",
            name = "SpaceCore",
            version = "2.1.0",
            isEnabled = false,
        )
        val dependent = testMod(
            uniqueId = "Kpzi.CropPack",
            name = "Crop Pack",
            contentPackFor = "Pathoschild.ContentPatcher",
            dependencies = listOf(ModDependency("Spacechase0.SpaceCore", minimumVersion = "2.0.0", isRequired = true)),
        )
        val dup1 = testMod(uniqueId = "Kpzi.Dup", name = "Dup 1", folderName = "Dup1")
        val dup2 = testMod(uniqueId = "Kpzi.Dup", name = "Dup 2", folderName = "Dup2")

        val badReport = detector.analyze(listOf(disabledHost, dependent, dup1, dup2), "4.1.10-android")
        assertEquals(1, badReport.disabledDependencyCount)
        assertEquals(1, badReport.missingDependencyCount)
        assertEquals(1, badReport.duplicateIdCount)
        assertEquals(0, badReport.loopCount)

        val enabledHost = disabledHost.copy(isEnabled = true)
        val cleanDependent = dependent.copy(
            manifest = dependent.manifest.copy(contentPackForUniqueId = null),
        )
        val cleanReport = detector.analyze(listOf(enabledHost, cleanDependent), "4.1.10-android")
        assertEquals(0, cleanReport.issues.size)
        assertTrue(!cleanReport.hasIssues)
    }

    @Test
    fun `theme state context toggles cleanly between light and dark modes`() {
        var currentMode = ThemeMode.LIGHT
        val lightState = KpziThemeState(
            themeMode = currentMode,
            isDark = false,
            setThemeMode = { currentMode = it },
            toggleTheme = { currentMode = ThemeMode.DARK },
        )
        lightState.toggleTheme()
        assertEquals(ThemeMode.DARK, currentMode)

        val darkState = KpziThemeState(
            themeMode = currentMode,
            isDark = true,
            setThemeMode = { currentMode = it },
            toggleTheme = { currentMode = ThemeMode.LIGHT },
        )
        darkState.toggleTheme()
        assertEquals(ThemeMode.LIGHT, currentMode)
    }

    @Test
    fun `playtime formatter breaks down months days hours and minutes accurately`() {
        val oneMonthTwoDaysThreeHoursFortyFiveMin =
            (30L * 86_400L) + (2L * 86_400L) + (3L * 3_600L) + (45L * 60L) + 12L
        val breakdown = PlaytimeFormatter.breakdown(oneMonthTwoDaysThreeHoursFortyFiveMin)
        assertEquals(1L, breakdown.months)
        assertEquals(2L, breakdown.days)
        assertEquals(3L, breakdown.hours)
        assertEquals(45L, breakdown.minutes)
        assertEquals(12L, breakdown.seconds)

        assertEquals(
            "1 เดือน 2 วัน 3 ชม. 45 นาที",
            PlaytimeFormatter.formatThaiBreakdown(oneMonthTwoDaysThreeHoursFortyFiveMin),
        )
        assertEquals("01:05:09", PlaytimeFormatter.formatStopwatch(3_909L))
        assertEquals("2 ชม. 15 นาที", PlaytimeFormatter.formatCompactThai((2L * 3_600L) + (15L * 60L)))
    }

    @Test
    fun `database migration v2 to v3 and play session entity store local stats`() {
        assertEquals(2, KpziDatabase.MIGRATION_2_3.startVersion)
        assertEquals(3, KpziDatabase.MIGRATION_2_3.endVersion)

        val session = PlaySessionEntity(
            id = 7L,
            startedAtMs = 1_700_000_000_000L,
            endedAtMs = 1_700_001_800_000L,
            durationSeconds = 1800L,
            activeModCount = 12,
            launchMode = "SMAPI",
        )
        assertEquals(1800L, session.durationSeconds)
        assertEquals(12, session.activeModCount)
        assertEquals("SMAPI", session.launchMode)
    }
}
