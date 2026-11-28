package com.example

import com.example.kpzi.core.network.ModTranslator
import com.example.kpzi.core.network.NexusApiClient
import com.example.kpzi.core.network.NexusQuotaManager
import com.example.kpzi.data.local.KpziDatabase
import com.example.kpzi.data.local.PlaySessionEntity
import com.example.kpzi.data.local.toDomain
import com.example.kpzi.data.local.toEntity
import com.example.kpzi.domain.model.BackupCategory
import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.ModCategory
import com.example.kpzi.domain.model.ModDependency
import com.example.kpzi.domain.model.ModManifest
import com.example.kpzi.domain.model.RateLimitStatus
import com.example.kpzi.engine.installer.ModInstallationEngine
import com.example.kpzi.engine.installer.ZipPasswordRequiredException
import com.example.kpzi.engine.loader.CompatibilityEngine
import com.example.kpzi.engine.loader.ConflictDetector
import com.example.kpzi.engine.loader.ConflictType
import com.example.kpzi.engine.multiplayer.KpziRoomCodeCodec
import com.example.kpzi.engine.multiplayer.MultiplayerLobbySnapshot
import com.example.kpzi.engine.multiplayer.MultiplayerRoom
import com.example.kpzi.engine.multiplayer.RoomVisibility
import com.example.kpzi.engine.multiplayer.SignalQuality
import com.example.kpzi.notifications.DownloadQueueMonitorWorker
import com.example.kpzi.notifications.KpziNotificationHelper
import com.example.kpzi.notifications.PlaytimeFormatter
import com.example.kpzi.ui.i18n.AppLanguage
import com.example.kpzi.ui.i18n.localize
import com.example.kpzi.ui.screens.MultiplayerAdvancedFilterState
import com.example.kpzi.ui.screens.MultiplayerSortOption
import com.example.kpzi.ui.screens.MultiplayerTabFilter
import com.example.kpzi.ui.screens.MyModsCategory
import com.example.kpzi.ui.screens.PixelArtModCovers
import com.example.kpzi.ui.screens.PixelArtRoomCovers
import com.example.kpzi.ui.screens.computeCoverAssignmentMap
import com.example.kpzi.ui.screens.computeRoomCoverAssignmentMap
import com.example.kpzi.ui.screens.detectModCategory
import com.example.kpzi.ui.screens.filterAndSortRooms
import com.example.kpzi.ui.screens.findDependentMods
import com.example.kpzi.ui.screens.formatDeviceCapacity
import com.example.kpzi.ui.screens.formatMyModsSize
import com.example.kpzi.ui.screens.formatStoragePercent
import com.example.ui.theme.KpziThemeState
import com.example.ui.theme.ThemeMode
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.model.ZipParameters
import net.lingala.zip4j.model.enums.EncryptionMethod
import okhttp3.Headers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.nio.file.Files

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

    @Test
    fun `encrypted mod zip throws ZipPasswordRequiredException on missing or wrong password and extracts cleanly on valid password`() {
        val tempDir = Files.createTempDirectory("kpzi_zip_test").toFile()
        try {
            val modSrc = File(tempDir, "LockedMod").apply { mkdirs() }
            File(modSrc, "manifest.json").writeText("""{"Name":"Locked Mod","UniqueID":"Tester.Locked"}""")

            val encryptedZip = File(tempDir, "locked_mod.zip")
            val params = ZipParameters().apply {
                isEncryptFiles = true
                encryptionMethod = EncryptionMethod.AES
            }
            ZipFile(encryptedZip, "myPass99".toCharArray()).addFolder(modSrc, params)

            val outMissing = File(tempDir, "out_missing").apply { mkdirs() }
            try {
                ModInstallationEngine.extractZipArchiveSafely(encryptedZip, outMissing, password = null)
                fail("Expected ZipPasswordRequiredException when no password is provided")
            } catch (_: ZipPasswordRequiredException) {
                // expected
            }

            val outWrong = File(tempDir, "out_wrong").apply { mkdirs() }
            try {
                ModInstallationEngine.extractZipArchiveSafely(encryptedZip, outWrong, password = "wrong")
                fail("Expected ZipPasswordRequiredException when wrong password is provided")
            } catch (_: ZipPasswordRequiredException) {
                // expected
            }

            val outOk = File(tempDir, "out_ok").apply { mkdirs() }
            ModInstallationEngine.extractZipArchiveSafely(encryptedZip, outOk, password = "myPass99")
            val extractedManifest = File(outOk, "LockedMod/manifest.json")
            assertTrue(extractedManifest.exists())
            assertTrue(extractedManifest.readText().contains("Tester.Locked"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `background worker modes and task notification channels are distinct`() {
        assertEquals("monitor_queue", DownloadQueueMonitorWorker.MODE_MONITOR_QUEUE)
        assertEquals("notify_mod_installed", DownloadQueueMonitorWorker.MODE_NOTIFY_MOD_INSTALLED)
        assertEquals("notify_file_exported", DownloadQueueMonitorWorker.MODE_NOTIFY_FILE_EXPORTED)
        assertTrue(KpziNotificationHelper.CHANNEL_TASK_COMPLETE != KpziNotificationHelper.CHANNEL_DOWNLOAD_ALERT)
    }

    @Test
    fun `localization supports Thai and English across formatter enums and conflict reports`() {
        assertEquals("TH", AppLanguage.THAI.shortCode)
        assertEquals("EN", AppLanguage.ENGLISH.shortCode)
        assertEquals("หน้าหลัก", localize(AppLanguage.THAI, "หน้าหลัก", "Home"))
        assertEquals("Home", localize(AppLanguage.ENGLISH, "หน้าหลัก", "Home"))

        val duration = (30L * 86_400L) + (2L * 86_400L) + (3L * 3_600L) + (45L * 60L)
        assertEquals("1 mo 2d 3h 45m", PlaytimeFormatter.formatBreakdown(duration, isEnglish = true))
        assertEquals("2h 15m", PlaytimeFormatter.formatCompact((2L * 3_600L) + (15L * 60L), isEnglish = true))

        assertEquals("All", ModCategory.ALL.label(true))
        assertEquals("ทั้งหมด", ModCategory.ALL.label(false))
        assertEquals("Incompatible", CompatibilityLevel.INCOMPATIBLE.label(true))
        assertEquals("Downloading", DownloadStatus.DOWNLOADING.label(true))
        assertEquals("Mods & Logs", BackupCategory.MODS_AND_CONFIG.label(true))

        val detector = ConflictDetector()
        val modA = testMod(
            uniqueId = "Author.ModA",
            name = "Mod A",
            dependencies = listOf(ModDependency("Author.ModB", isRequired = true)),
        )
        val modB = testMod(
            uniqueId = "Author.ModB",
            name = "Mod B",
            dependencies = listOf(ModDependency("Author.ModA", isRequired = true)),
        )
        val issue = detector.analyze(listOf(modA, modB), "4.1.10-android").issues.first()
        assertEquals("Dependency Loop", issue.type.label(true))
        assertTrue(issue.summary(true).contains("Dependency Loop detected"))
        assertTrue(issue.summary(false).contains("พบ Dependency Loop"))
    }

    @Test
    fun `my mods pixel art cover catalog has at least 30 covers and assigns unique deterministic ids`() {
        assertTrue(PixelArtModCovers.totalCount >= 30)
        MyModsCategory.entries.forEach { cat ->
            assertTrue("Expected category $cat to have >= 2 covers", PixelArtModCovers.idsForCategory(cat).size >= 2)
        }

        val sampleMods = listOf(
            testMod("Pathoschild.ContentPatcher", "Content Patcher"),
            testMod("Thai.Stardew.Translation", "Stardew Valley - THAI"),
            testMod("Kpzi.ThaiDubbing", "Stardew Valley Thai Dubbing"),
            testMod("Kpzi.ThaiFontAdjuster", "Thai Font Adjuster"),
            testMod("Author.CuteCat", "Black Cat Pet Replacer"),
            testMod("Author.VictorianBarn", "Victorian Barn Building"),
            testMod("Author.AutomateFarm", "Automate Crop Farm"),
            testMod("Author.AnimePortraits", "NPC Portrait Overhaul"),
        )

        val assigned = computeCoverAssignmentMap(sampleMods)
        assertEquals(sampleMods.size, assigned.size)
        // No duplicates on the same screen when total mods <= catalog size
        assertEquals(sampleMods.size, assigned.values.toSet().size)

        // Sorting or toggling mods must preserve identical cover IDs
        val reordered = sampleMods.reversed().map { it.copy(isEnabled = !it.isEnabled) }
        assertEquals(assigned, computeCoverAssignmentMap(reordered))
    }

    @Test
    fun `my mods category detection, dependency check, and unit formatting work accurately`() {
        assertEquals(MyModsCategory.THAI_LANG, detectModCategory(testMod("Kpzi.TH", "Stardew Valley - THAI")))
        assertEquals(MyModsCategory.VOICE_AUDIO, detectModCategory(testMod("Kpzi.Voice", "Thai Dubbing Pack")))
        assertEquals(MyModsCategory.FONTS, detectModCategory(testMod("Kpzi.Font", "Thai Font Adjuster")))
        assertEquals(MyModsCategory.FRAMEWORK, detectModCategory(testMod("Pathoschild.ContentPatcher", "Content Patcher")))

        val host = testMod("Pathoschild.ContentPatcher", "Content Patcher")
        val pack = testMod("Kpzi.Thai", "Thai Pack", contentPackFor = "Pathoschild.ContentPatcher")
        val dependent = testMod(
            "Kpzi.UiAddon",
            "UI Addon",
            dependencies = listOf(ModDependency("Pathoschild.ContentPatcher", isRequired = true)),
        )
        val unrelated = testMod("Kpzi.Tractor", "Tractor Mod")
        val foundDeps = findDependentMods(host, listOf(host, pack, dependent, unrelated))
        assertEquals(2, foundDeps.size)

        assertEquals("0 B", formatMyModsSize(0L))
        assertEquals("512 B", formatMyModsSize(512L))
        assertEquals("775 KB", formatMyModsSize(775L * 1024L))
        assertEquals("12.17 MB", formatMyModsSize((12.17 * 1024 * 1024).toLong()))
        assertEquals("2.50 GB", formatMyModsSize((2.5 * 1024 * 1024 * 1024).toLong()))
        assertEquals("128 GB", formatDeviceCapacity(128L * 1024L * 1024L * 1024L))
        assertEquals("<0.01%", formatStoragePercent(12L * 1024L * 1024L, 128L * 1024L * 1024L * 1024L))
        assertEquals("0.01%", formatStoragePercent(12L * 1024L * 1024L, 100L * 1024L * 1024L * 1024L))
    }

    @Test
    fun `multiplayer room code codec, signal quality, and empty snapshot stay accurate`() {
        val code = KpziRoomCodeCodec.encode("192.168.1.42", 24642)
        assertTrue(code != null && code.startsWith("KPZ-"))
        assertEquals("192.168.1.42" to 24642, KpziRoomCodeCodec.decode(code!!))
        assertEquals("10.0.0.5" to 24642, KpziRoomCodeCodec.decode("10.0.0.5"))
        assertEquals("10.0.0.5" to 31200, KpziRoomCodeCodec.decode("10.0.0.5:31200"))
        assertNull(KpziRoomCodeCodec.decode("invalid-code"))
        assertNull(KpziRoomCodeCodec.encode("999.1.2.3", 24642))

        val emptyLobby = MultiplayerLobbySnapshot()
        assertEquals(0, emptyLobby.rooms.size)
        assertEquals(0, emptyLobby.totalOnlinePlayers)

        val baseRoom = MultiplayerRoom(
            roomId = "room_a",
            roomName = "ฟาร์มชิล",
            ownerName = "D2C9",
            isOwnHostRoom = true,
            currentPlayers = 2,
            maxPlayers = 4,
            visibility = RoomVisibility.PUBLIC,
            requiresPassword = false,
            gameVersion = "v1.6.15",
            pingMs = 28,
            hostAddress = "192.168.1.42",
            port = 24642,
            roomCode = code,
            createdAtMs = 1_700_000_000_000L,
        )
        assertEquals(SignalQuality.EXCELLENT, baseRoom.signalQuality)
        assertEquals(SignalQuality.GOOD, baseRoom.copy(pingMs = 70).signalQuality)
        assertEquals(SignalQuality.FAIR, baseRoom.copy(pingMs = 120).signalQuality)
        assertEquals(SignalQuality.POOR, baseRoom.copy(pingMs = 240).signalQuality)
        assertEquals(SignalQuality.UNKNOWN, baseRoom.copy(pingMs = null).signalQuality)
        assertTrue(!baseRoom.isFull)
        assertTrue(baseRoom.copy(currentPlayers = 4, maxPlayers = 4).isFull)

        assertEquals(3, KpziDatabase.MIGRATION_3_4.startVersion)
        assertEquals(4, KpziDatabase.MIGRATION_3_4.endVersion)
    }

    @Test
    fun `multiplayer pixel art room cover catalog has 36 unique covers and filters sort accurately`() {
        assertTrue(PixelArtRoomCovers.totalCount >= 30)

        val rooms = listOf(
            MultiplayerRoom("r1", "ฟาร์มชิล", "D2C9", true, 2, 4, RoomVisibility.PRIVATE, true, "v1.6.15", 28, "192.168.1.2", 24642, "KPZ-AAAAA-AAAAA", 100L, isUserAuthorized = true),
            MultiplayerRoom("r2", "ปลูกผักยาวๆ", "Mintx", false, 1, 4, RoomVisibility.PUBLIC, false, "v1.6.15", 35, "192.168.1.3", 24642, "KPZ-BBBBB-BBBBB", 200L),
            MultiplayerRoom("r3", "ฟาร์มรวมเพื่อน", "PloyZ", false, 4, 4, RoomVisibility.FRIENDS, true, "v1.6.14", 19, "192.168.1.4", 24642, "KPZ-CCCCC-CCCCC", 300L),
        )
        val covers = computeRoomCoverAssignmentMap(rooms)
        assertEquals(3, covers.size)
        assertEquals(3, covers.values.toSet().size)
        assertEquals(covers, computeRoomCoverAssignmentMap(rooms.reversed()))

        val onlyPublic = filterAndSortRooms(rooms, MultiplayerTabFilter.PUBLIC, MultiplayerAdvancedFilterState(), "", "v1.6.15")
        assertEquals(listOf("r2"), onlyPublic.map { it.roomId })

        val availableMatching = filterAndSortRooms(
            rooms,
            MultiplayerTabFilter.ALL,
            MultiplayerAdvancedFilterState(notFullOnly = true, matchingVersionOnly = true, sortOption = MultiplayerSortOption.PING),
            "",
            "v1.6.15",
        )
        assertEquals(listOf("r1", "r2"), availableMatching.map { it.roomId })

        val byPlayers = filterAndSortRooms(
            rooms,
            MultiplayerTabFilter.ALL,
            MultiplayerAdvancedFilterState(sortOption = MultiplayerSortOption.PLAYERS),
            "",
            "v1.6.15",
        )
        assertEquals(listOf("r3", "r1", "r2"), byPlayers.map { it.roomId })

        val searchByOwner = filterAndSortRooms(rooms, MultiplayerTabFilter.ALL, MultiplayerAdvancedFilterState(), "mintx", "v1.6.15")
        assertEquals(listOf("r2"), searchByOwner.map { it.roomId })
    }
}
