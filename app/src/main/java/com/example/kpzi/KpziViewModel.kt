package com.example.kpzi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpzi.core.network.NexusApiClient
import com.example.kpzi.data.local.CoopHistoryEntity
import com.example.kpzi.data.local.PlaySessionEntity
import com.example.kpzi.data.local.UserSettings
import com.example.kpzi.data.local.toCacheEntity
import com.example.kpzi.data.local.toSummary
import com.example.kpzi.domain.model.ApiQuotaState
import com.example.kpzi.domain.model.CompatibilityLevel
import com.example.kpzi.domain.model.DownloadStatus
import com.example.kpzi.domain.model.DownloadTask
import com.example.kpzi.domain.model.GameDetectionResult
import com.example.kpzi.domain.model.InstalledMod
import com.example.kpzi.domain.model.LogEntry
import com.example.kpzi.domain.model.ModCategory
import com.example.kpzi.domain.model.NexusModDetail
import com.example.kpzi.domain.model.NexusModFile
import com.example.kpzi.domain.model.NexusModSummary
import com.example.kpzi.domain.model.NexusUserProfile
import com.example.kpzi.domain.model.SaveBackupInfo
import com.example.kpzi.domain.model.SmapiStatus
import com.example.kpzi.engine.installer.ModInstallResult
import com.example.kpzi.engine.installer.PendingModInstall
import com.example.kpzi.engine.installer.ZipStageResult
import com.example.kpzi.engine.loader.ConflictDetectionReport
import com.example.kpzi.engine.loader.LaunchOutcome
import com.example.kpzi.engine.loader.PreLaunchReport
import com.example.kpzi.engine.multiplayer.HostedRoomConfig
import com.example.kpzi.engine.multiplayer.JoinRoomResult
import com.example.kpzi.engine.multiplayer.MultiplayerLobbySnapshot
import com.example.kpzi.engine.multiplayer.MultiplayerRoom
import com.example.kpzi.engine.multiplayer.RoomVisibility
import com.example.kpzi.notifications.DownloadQueueMonitorWorker
import com.example.kpzi.notifications.PlaytimeTrackingService
import com.example.kpzi.ui.i18n.AppLanguage
import com.example.kpzi.ui.i18n.localize
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

data class CatalogUiState(
    val query: String = "",
    val selectedCategory: ModCategory = ModCategory.ALL,
    val items: List<NexusModSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val isOfflineCache: Boolean = false,
    val errorMessage: String? = null,
)

data class HomeFeedUiState(
    val trending: List<NexusModSummary> = emptyList(),
    val latestAdded: List<NexusModSummary> = emptyList(),
    val latestUpdated: List<NexusModSummary> = emptyList(),
    val isLoading: Boolean = false,
    val isUsingCache: Boolean = false,
    val statusNote: String? = null,
)

data class ModDetailUiState(
    val modId: Long = 0L,
    val detail: NexusModDetail? = null,
    val isLoading: Boolean = false,
    val translateToThai: Boolean = false,
    val errorMessage: String? = null,
    val pendingWebDownloadUrl: String? = null,
)

data class PendingZipPassword(
    val archiveFile: File,
    val sourceLabel: String,
    val isOwnedTempFile: Boolean,
    val errorMessage: String? = null,
    val isExtracting: Boolean = false,
)

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
    data class OpenExternalUrl(val url: String) : UiEvent
    data class LaunchBlocked(val reason: String) : UiEvent
    data class PromptExportBackupZip(val backup: SaveBackupInfo) : UiEvent
}

@OptIn(FlowPreview::class)
class KpziViewModel(
    private val container: KpziContainer,
) : ViewModel() {

    private val _isSplashVisible = MutableStateFlow(true)
    val isSplashVisible: StateFlow<Boolean> = _isSplashVisible.asStateFlow()

    val settings: StateFlow<UserSettings> = container.settingsPreferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UserSettings())

    val quotaState: StateFlow<ApiQuotaState> = container.quotaManager.quotaState

    private val _userProfile = MutableStateFlow(container.secureKeyVault.getSavedProfile())
    val userProfile: StateFlow<NexusUserProfile?> = _userProfile.asStateFlow()

    private val _isValidatingAccount = MutableStateFlow(false)
    val isValidatingAccount: StateFlow<Boolean> = _isValidatingAccount.asStateFlow()

    private val _accountError = MutableStateFlow<String?>(null)
    val accountError: StateFlow<String?> = _accountError.asStateFlow()

    private val _gameDetection = MutableStateFlow<GameDetectionResult?>(null)
    val gameDetection: StateFlow<GameDetectionResult?> = _gameDetection.asStateFlow()

    private val _smapiStatus = MutableStateFlow(
        SmapiStatus(
            isInstalled = false,
            loaderPackageName = null,
            smapiVersion = "4.1.10-android",
            gameLoaderStateLabel = msg("กำลังตรวจสอบ...", "Checking..."),
            isReadyToLaunch = false,
        ),
    )
    val smapiStatus: StateFlow<SmapiStatus> = _smapiStatus.asStateFlow()

    private val _preLaunchReport = MutableStateFlow<PreLaunchReport?>(null)
    val preLaunchReport: StateFlow<PreLaunchReport?> = _preLaunchReport.asStateFlow()

    private val _conflictReport = MutableStateFlow<ConflictDetectionReport?>(null)
    val conflictReport: StateFlow<ConflictDetectionReport?> = _conflictReport.asStateFlow()

    private val _isScanningConflicts = MutableStateFlow(false)
    val isScanningConflicts: StateFlow<Boolean> = _isScanningConflicts.asStateFlow()

    val installedMods: StateFlow<List<InstalledMod>> = container.installedModRepository.allMods
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recentPlaySessions: StateFlow<List<PlaySessionEntity>> = container.playStatsRepository.recentSessions
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val downloads: StateFlow<List<DownloadTask>> = container.modDownloadManager.downloadsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val multiplayerSnapshot: StateFlow<MultiplayerLobbySnapshot> = container.multiplayerLobbyEngine.snapshot
    val favoriteRoomIds: StateFlow<Set<String>> = container.multiplayerLobbyEngine.favoriteRoomsFlow
        .map { list -> list.map { it.roomId }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val coopHistory: StateFlow<List<CoopHistoryEntity>> = container.multiplayerLobbyEngine.coopHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _pendingJoinPasswordRoom = MutableStateFlow<MultiplayerRoom?>(null)
    val pendingJoinPasswordRoom: StateFlow<MultiplayerRoom?> = _pendingJoinPasswordRoom.asStateFlow()
    private val _joinPasswordError = MutableStateFlow<String?>(null)
    val joinPasswordError: StateFlow<String?> = _joinPasswordError.asStateFlow()

    val saveBackups: StateFlow<List<SaveBackupInfo>> = container.saveBackupManager.backups
    private val _isCreatingBackup = MutableStateFlow(false)
    val isCreatingBackup: StateFlow<Boolean> = _isCreatingBackup.asStateFlow()
    val logs: StateFlow<List<LogEntry>> = container.logManager.logs
    val activeLogSource: StateFlow<String> = container.logManager.activeLogSource
    val isReloadingLogs: StateFlow<Boolean> = container.logManager.isReloading

    private val _homeFeeds = MutableStateFlow(HomeFeedUiState())
    val homeFeeds: StateFlow<HomeFeedUiState> = _homeFeeds.asStateFlow()

    private val _catalogState = MutableStateFlow(CatalogUiState())
    val catalogState: StateFlow<CatalogUiState> = _catalogState.asStateFlow()

    private val _modDetailState = MutableStateFlow(ModDetailUiState())
    val modDetailState: StateFlow<ModDetailUiState> = _modDetailState.asStateFlow()

    private val _pendingInstall = MutableStateFlow<PendingModInstall?>(null)
    val pendingInstall: StateFlow<PendingModInstall?> = _pendingInstall.asStateFlow()

    private val _pendingZipPassword = MutableStateFlow<PendingZipPassword?>(null)
    val pendingZipPassword: StateFlow<PendingZipPassword?> = _pendingZipPassword.asStateFlow()

    private val _installedSuccessMod = MutableStateFlow<InstalledMod?>(null)
    val installedSuccessMod: StateFlow<InstalledMod?> = _installedSuccessMod.asStateFlow()

    private val _uiEvents = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val uiEvents: SharedFlow<UiEvent> = _uiEvents.asSharedFlow()

    private val _pendingNavigateTab = MutableStateFlow<String?>(null)
    val pendingNavigateTab: StateFlow<String?> = _pendingNavigateTab.asStateFlow()

    private val searchQueryInput = MutableStateFlow("")
    private var catalogJob: Job? = null
    private var detailJob: Job? = null
    private var currentCatalogOffset = 0

    private fun msg(th: String, en: String): String =
        localize(settings.value.appLanguage, th, en)

    init {
        initializeApp()
        observeSearchDebounce()
        observeCachedCatalog()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            val currentSettings = container.settingsPreferences.settingsFlow.first()
            container.notificationHelper.isEnglish = currentSettings.appLanguage == AppLanguage.ENGLISH
            container.notificationHelper.ensureChannels(currentSettings.appLanguage == AppLanguage.ENGLISH)
            _modDetailState.update { it.copy(translateToThai = currentSettings.autoTranslateDescriptions) }
            container.logManager.reloadLocalLogs(currentSettings.safLogUri)
            currentSettings.activePlaytimeStartMs?.let { activeStart ->
                if (container.notificationHelper.canPostNotifications()) {
                    PlaytimeTrackingService.start(container.appContext, activeStart)
                }
            }
            val queuedOrActive = container.database.downloadRecordDao().observeAll().first().any {
                it.status == DownloadStatus.QUEUED.name ||
                    it.status == DownloadStatus.DOWNLOADING.name ||
                    it.status == DownloadStatus.VERIFYING.name
            }
            if (queuedOrActive) {
                DownloadQueueMonitorWorker.enqueueQueueMonitor(container.appContext)
            }
            refreshSystemDiagnostics()
            container.saveBackupManager.refreshBackups()
            loadHomeFeeds(forceRefresh = false)
            refreshCatalog(reset = true)
            delay(850)
            _isSplashVisible.value = false
        }
    }

    private fun observeSearchDebounce() {
        viewModelScope.launch {
            searchQueryInput
                .debounce(400)
                .distinctUntilChanged()
                .collectLatest { q ->
                    _catalogState.update { it.copy(query = q) }
                    refreshCatalog(reset = true)
                }
        }
    }

    private fun observeCachedCatalog() {
        viewModelScope.launch {
            container.database.modCacheDao().observeAll()
                .map { entities -> entities.map { it.toSummary() } }
                .collectLatest { cached ->
                    if (_catalogState.value.items.isEmpty() && cached.isNotEmpty()) {
                        _catalogState.update {
                            it.copy(items = cached, isOfflineCache = true)
                        }
                    }
                    if (_homeFeeds.value.trending.isEmpty() && cached.isNotEmpty()) {
                        _homeFeeds.update {
                            it.copy(
                                trending = cached.filter { m -> m.feedTag == "trending" }.ifEmpty { cached.take(6) },
                                latestAdded = cached.filter { m -> m.feedTag == "latest_added" },
                                latestUpdated = cached.filter { m -> m.feedTag == "latest_updated" },
                                isUsingCache = true,
                            )
                        }
                    }
                }
        }
    }

    fun refreshSystemDiagnostics() {
        viewModelScope.launch {
            val s = settings.value
            val detected = container.gameDetector.detect(s.safModsUri)
            _gameDetection.value = detected
            val smapi = container.smapiIntegration.inspectSmapi(s.preferredSmapiVersion, detected)
            _smapiStatus.value = smapi
            val mods = container.modInstallationEngine.rescanInstalledMods(smapi.smapiVersion)
            _preLaunchReport.value = container.modRuntime.buildDiagnostics(detected, smapi, mods)
            val roomMods = container.installedModRepository.getAllInstalledModsOnce()
            _conflictReport.value = container.conflictDetector.analyze(roomMods, smapi.smapiVersion)
        }
    }

    fun runConflictDetection() {
        if (_isScanningConflicts.value) return
        viewModelScope.launch {
            _isScanningConflicts.value = true
            val roomMods = container.installedModRepository.getAllInstalledModsOnce()
            val smapiVer = _smapiStatus.value.smapiVersion
            val report = container.conflictDetector.analyze(roomMods, smapiVer)
            _conflictReport.value = report
            if (report.hasIssues) {
                container.logManager.warn(
                    "ConflictDetector",
                    "พบความขัดแย้ง ${report.issues.size} รายการจาก ${report.scannedModCount} มอด (Loop: ${report.loopCount}, Version: ${report.versionMismatchCount})",
                )
            } else {
                container.logManager.info(
                    "ConflictDetector",
                    "ตรวจสอบ manifest.json ทั้ง ${report.scannedModCount} มอดจาก Room DB แล้ว ไม่พบความขัดแย้ง",
                )
            }
            _isScanningConflicts.value = false
        }
    }

    fun loadHomeFeeds(forceRefresh: Boolean = true) {
        viewModelScope.launch {
            val apiKey = container.secureKeyVault.getApiKey()
            _homeFeeds.update { it.copy(isLoading = true, statusNote = null) }
            if (!apiKey.isNullOrBlank()) {
                val trendingRes = runCatching { container.nexusApiClient.fetchFeed("trending", apiKey) }
                val addedRes = runCatching { container.nexusApiClient.fetchFeed("latest_added", apiKey) }
                val updatedRes = runCatching { container.nexusApiClient.fetchFeed("latest_updated", apiKey) }

                val trending = trendingRes.getOrNull().orEmpty()
                val added = addedRes.getOrNull().orEmpty()
                val updated = updatedRes.getOrNull().orEmpty()
                val allFetched = (trending + added + updated).map { it.toCacheEntity() }
                if (allFetched.isNotEmpty()) {
                    container.database.modCacheDao().upsertAll(allFetched)
                    _homeFeeds.value = HomeFeedUiState(
                        trending = trending,
                        latestAdded = added,
                        latestUpdated = updated,
                        isLoading = false,
                        isUsingCache = false,
                    )
                    return@launch
                }
            }

            // Public GraphQL fallback when no Personal API key is configured yet
            val gqlPopular = runCatching {
                container.nexusApiClient.searchCatalogGraphQl("", ModCategory.POPULAR, 0, 30, apiKey)
            }.getOrNull().orEmpty()
            val gqlRecent = runCatching {
                container.nexusApiClient.searchCatalogGraphQl("", ModCategory.RECENTLY_UPDATED, 0, 30, apiKey)
            }.getOrNull().orEmpty()

            if (gqlPopular.isNotEmpty() || gqlRecent.isNotEmpty()) {
                container.database.modCacheDao().upsertAll((gqlPopular + gqlRecent).map { it.toCacheEntity() })
                _homeFeeds.value = HomeFeedUiState(
                    trending = gqlPopular.take(10),
                    latestAdded = gqlPopular.drop(10).take(10),
                    latestUpdated = gqlRecent.take(10),
                    isLoading = false,
                    isUsingCache = false,
                )
            } else {
                val cached = container.database.modCacheDao().observeAll().first().map { it.toSummary() }
                _homeFeeds.value = HomeFeedUiState(
                    trending = cached.take(10),
                    latestAdded = emptyList(),
                    latestUpdated = cached.drop(10).take(10),
                    isLoading = false,
                    isUsingCache = cached.isNotEmpty(),
                    statusNote = when {
                        apiKey.isNullOrBlank() && cached.isEmpty() ->
                            "เชื่อมบัญชี Nexus Mods ในหน้าตั้งค่าเพื่อโหลดฟีด REST API หรือตรวจสอบการเชื่อมต่ออินเทอร์เน็ต"
                        apiKey.isNullOrBlank() ->
                            "เชื่อมบัญชี Nexus Mods ในหน้าตั้งค่าเพื่อโหลดฟีด REST API ส่วนตัว หรือค้นหาสาธารณะผ่าน GraphQL"
                        cached.isNotEmpty() ->
                            "ไม่สามารถเชื่อมต่อ Nexus Mods ชั่วคราว กำลังแสดงข้อมูลจากแคช"
                        else ->
                            "ไม่สามารถเชื่อมต่อ Nexus Mods ชั่วคราว กรุณาลองรีเฟรชใหม่อีกครั้ง"
                    },
                )
            }
        }
    }

    fun onSearchQueryChanged(newQuery: String) {
        searchQueryInput.value = newQuery
    }

    fun onCategorySelected(category: ModCategory) {
        if (_catalogState.value.selectedCategory == category) return
        _catalogState.update { it.copy(selectedCategory = category) }
        refreshCatalog(reset = true)
    }

    fun refreshCatalog(reset: Boolean) {
        catalogJob?.cancel()
        catalogJob = viewModelScope.launch {
            val state = _catalogState.value
            val offset = if (reset) 0 else currentCatalogOffset
            if (!reset && (!state.hasMore || state.isLoadingMore)) return@launch

            _catalogState.update {
                if (reset) it.copy(isLoading = true, errorMessage = null)
                else it.copy(isLoadingMore = true, errorMessage = null)
            }

            val apiKey = container.secureKeyVault.getApiKey()
            val directModId = NexusApiClient.parseModIdFromInput(state.query)
            if (reset && directModId != null && !apiKey.isNullOrBlank()) {
                val directResult = runCatching { container.nexusApiClient.fetchModDetail(directModId, apiKey) }
                if (directResult.isSuccess) {
                    val detail = directResult.getOrThrow()
                    container.database.modCacheDao().upsert(detail.summary.toCacheEntity(detail.description))
                    _catalogState.update {
                        it.copy(
                            items = listOf(detail.summary),
                            isLoading = false,
                            isLoadingMore = false,
                            hasMore = false,
                            isOfflineCache = false,
                        )
                    }
                    return@launch
                }
            }

            val result = runCatching {
                container.nexusApiClient.searchCatalogGraphQl(
                    query = state.query,
                    category = state.selectedCategory,
                    offset = offset,
                    count = 35,
                    apiKey = apiKey,
                )
            }

            if (result.isSuccess) {
                val pageItems = result.getOrThrow()
                if (pageItems.isNotEmpty()) {
                    container.database.modCacheDao().upsertAll(pageItems.map { it.toCacheEntity() })
                }
                val merged = if (reset) pageItems else (_catalogState.value.items + pageItems).distinctBy { it.modId }
                currentCatalogOffset = merged.size
                _catalogState.update {
                    it.copy(
                        items = merged,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = pageItems.size >= 30,
                        isOfflineCache = false,
                        errorMessage = null,
                    )
                }
            } else {
                val err = result.exceptionOrNull()?.localizedMessage ?: msg("โหลดรายการมอดไม่สำเร็จ", "Failed to load mod list")
                val cached = container.database.modCacheDao().observeAll().first().map { it.toSummary() }
                    .filter { mod ->
                        state.query.isBlank() ||
                            mod.name.contains(state.query, ignoreCase = true) ||
                            mod.author.contains(state.query, ignoreCase = true) ||
                            mod.modId.toString() == state.query.trim()
                    }
                _catalogState.update {
                    it.copy(
                        items = if (reset && cached.isNotEmpty()) cached else it.items,
                        isLoading = false,
                        isLoadingMore = false,
                        hasMore = false,
                        isOfflineCache = cached.isNotEmpty(),
                        errorMessage = err,
                    )
                }
            }
        }
    }

    fun loadModDetail(modId: Long) {
        detailJob?.cancel()
        detailJob = viewModelScope.launch {
            val cachedSummary = _catalogState.value.items.firstOrNull { it.modId == modId }
                ?: _homeFeeds.value.trending.firstOrNull { it.modId == modId }
                ?: _homeFeeds.value.latestAdded.firstOrNull { it.modId == modId }
                ?: _homeFeeds.value.latestUpdated.firstOrNull { it.modId == modId }
                ?: container.database.modCacheDao().getById(modId)?.toSummary()

            val cachedEntity = container.database.modCacheDao().getById(modId)
            if (cachedSummary != null) {
                _modDetailState.update {
                    it.copy(
                        modId = modId,
                        isLoading = true,
                        errorMessage = null,
                        detail = NexusModDetail(
                            summary = cachedSummary,
                            description = cachedEntity?.description ?: cachedSummary.summary,
                            previewImageUrls = listOfNotNull(cachedSummary.pictureUrl),
                            files = emptyList(),
                            dependencies = emptyList(),
                            supportedGameVersions = listOf(msg("กำลังตรวจสอบจาก Nexus Mods...", "Checking Nexus Mods...")),
                            requiredSmapiVersion = null,
                            changelogs = emptyList(),
                            licenseName = msg("ตรวจสอบตามหน้าต้นฉบับ Nexus Mods", "See original Nexus Mods page"),
                            nexusUrl = "https://www.nexusmods.com/stardewvalley/mods/$modId",
                            isOfflineCached = true,
                        ),
                    )
                }
            } else {
                _modDetailState.update { it.copy(modId = modId, isLoading = true, errorMessage = null, detail = null) }
            }

            val apiKey = container.secureKeyVault.getApiKey()
            if (apiKey.isNullOrBlank()) {
                _modDetailState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = msg(
                            "กรุณาเชื่อมบัญชี Nexus Mods API Key ในหน้าตั้งค่าเพื่อดึงรายการไฟล์และ Changelog ฉบับเต็ม",
                            "Connect your Nexus Mods API Key in Settings to load the full file list and changelog.",
                        ),
                    )
                }
                return@launch
            }

            val fetched = runCatching { container.nexusApiClient.fetchModDetail(modId, apiKey) }
            if (fetched.isSuccess) {
                val detail = fetched.getOrThrow()
                container.database.modCacheDao().upsert(detail.summary.toCacheEntity(detail.description))
                _modDetailState.update {
                    it.copy(modId = modId, detail = detail, isLoading = false, errorMessage = null)
                }
            } else {
                _modDetailState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = fetched.exceptionOrNull()?.localizedMessage ?: msg("โหลดรายละเอียดไม่สำเร็จ", "Failed to load mod details"),
                    )
                }
            }
        }
    }

    fun toggleDetailThaiTranslation() {
        _modDetailState.update { it.copy(translateToThai = !it.translateToThai) }
    }

    fun startModFileDownload(detail: NexusModDetail, file: NexusModFile?) {
        viewModelScope.launch {
            val apiKey = container.secureKeyVault.getApiKey()
            val profile = _userProfile.value

            if (apiKey.isNullOrBlank() || profile == null) {
                // Unauthenticated: open official Nexus Mods file page
                val url = "${detail.nexusUrl}?tab=files"
                _uiEvents.emit(UiEvent.OpenExternalUrl(url))
                return@launch
            }

            if (!profile.isPremium || file == null) {
                // Free Nexus Mods account must follow the official Nexus website download flow or NXM link
                val targetUrl = if (file != null) {
                    "${detail.nexusUrl}?tab=files&file_id=${file.fileId}"
                } else {
                    "${detail.nexusUrl}?tab=files"
                }
                container.logManager.info(
                    "DownloadFlow",
                    "บัญชี Free: เปิดหน้าดาวน์โหลดอย่างเป็นทางการบน Nexus Mods (${detail.summary.name})",
                )
                _uiEvents.emit(UiEvent.OpenExternalUrl(targetUrl))
                return@launch
            }

            // Premium account: request direct CDN link via Nexus API
            val linksRes = runCatching {
                container.nexusApiClient.requestDownloadLinks(
                    modId = detail.summary.modId,
                    fileId = file.fileId,
                    apiKey = apiKey,
                )
            }
            val directUrl = linksRes.getOrNull()?.firstOrNull()
            if (directUrl.isNullOrBlank()) {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        linksRes.exceptionOrNull()?.localizedMessage
                            ?: msg(
                                "ไม่สามารถรับลิงก์ดาวน์โหลดตรงได้ กรุณาดาวน์โหลดผ่านหน้าเว็บ Nexus Mods",
                                "Unable to get direct download link. Please download via the Nexus Mods website.",
                            ),
                    ),
                )
                return@launch
            }

            container.modDownloadManager.enqueueDownload(
                modId = detail.summary.modId,
                fileId = file.fileId,
                modName = detail.summary.name,
                fileName = file.fileName,
                version = file.version,
                pictureUrl = detail.summary.pictureUrl,
                downloadUrl = directUrl,
                expectedBytes = file.sizeBytes,
            )
            _uiEvents.emit(
                UiEvent.ShowMessage(
                    msg(
                        "เพิ่ม ${detail.summary.name} เข้าคิวดาวน์โหลดแล้ว",
                        "Added ${detail.summary.name} to download queue",
                    ),
                ),
            )
        }
    }

    fun handleNxmDeepLink(uri: Uri) {
        if (!uri.scheme.equals("nxm", ignoreCase = true)) return
        val segments = uri.pathSegments // /mods/{modId}/files/{fileId}
        val modIdx = segments.indexOf("mods")
        val fileIdx = segments.indexOf("files")
        val modId = segments.getOrNull(modIdx + 1)?.toLongOrNull() ?: return
        val fileId = segments.getOrNull(fileIdx + 1)?.toLongOrNull() ?: return
        val key = uri.getQueryParameter("key")
        val expires = uri.getQueryParameter("expires")

        viewModelScope.launch {
            val apiKey = container.secureKeyVault.getApiKey()
            if (apiKey.isNullOrBlank()) {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg(
                            "กรุณาเชื่อม Nexus API Key ก่อนใช้งาน NXM Deep Link",
                            "Please connect your Nexus API Key before using NXM deep links",
                        ),
                    ),
                )
                return@launch
            }
            val detail = runCatching { container.nexusApiClient.fetchModDetail(modId, apiKey) }.getOrNull()
            val targetFile = detail?.files?.firstOrNull { it.fileId == fileId }
            val links = runCatching {
                container.nexusApiClient.requestDownloadLinks(modId, fileId, apiKey, key, expires)
            }.getOrNull().orEmpty()

            val downloadUrl = links.firstOrNull()
            if (downloadUrl == null) {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg(
                            "ลิงก์ NXM หมดอายุหรือไม่ถูกต้อง",
                            "NXM link is invalid or expired",
                        ),
                    ),
                )
                return@launch
            }

            container.modDownloadManager.enqueueDownload(
                modId = modId,
                fileId = fileId,
                modName = detail?.summary?.name ?: "Mod #$modId",
                fileName = targetFile?.fileName ?: "mod_${modId}_${fileId}.zip",
                version = targetFile?.version ?: detail?.summary?.version ?: "1.0",
                pictureUrl = detail?.summary?.pictureUrl,
                downloadUrl = downloadUrl,
                expectedBytes = targetFile?.sizeBytes ?: 0L,
            )
            _uiEvents.emit(
                UiEvent.ShowMessage(
                    msg(
                        "รับลิงก์ NXM และเริ่มดาวน์โหลดแล้ว",
                        "NXM link received, download started",
                    ),
                ),
            )
        }
    }

    fun connectNexusApiKey(apiKeyInput: String) {
        viewModelScope.launch {
            val trimmed = apiKeyInput.trim()
            if (trimmed.isEmpty()) {
                _accountError.value = msg("กรุณากรอก Personal API Key", "Please enter your Personal API Key")
                return@launch
            }
            _isValidatingAccount.value = true
            _accountError.value = null
            val res = runCatching { container.nexusApiClient.validateApiKey(trimmed) }
            if (res.isSuccess) {
                val profile = res.getOrThrow()
                container.secureKeyVault.saveApiKey(trimmed)
                container.secureKeyVault.saveProfile(profile)
                _userProfile.value = profile
                container.logManager.info("NexusAuth", "เชื่อมต่อบัญชี ${profile.username} สำเร็จ")
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg("เชื่อมต่อบัญชี ${profile.username} สำเร็จ", "Connected account ${profile.username}"),
                    ),
                )
                loadHomeFeeds(forceRefresh = true)
            } else {
                val msgErr = res.exceptionOrNull()?.localizedMessage
                    ?: msg("ยืนยัน API Key ไม่สำเร็จ", "Failed to verify API Key")
                _accountError.value = msgErr
                container.logManager.error("NexusAuth", msgErr)
            }
            _isValidatingAccount.value = false
        }
    }

    fun disconnectNexusAccount() {
        container.secureKeyVault.clearSession()
        container.quotaManager.clearQuotaState()
        _userProfile.value = null
        _accountError.value = null
        container.logManager.info("NexusAuth", "ตัดการเชื่อมต่อบัญชี Nexus Mods แล้ว")
    }

    fun importZipUri(uri: Uri) {
        viewModelScope.launch {
            val res = container.modInstallationEngine.stageZipUri(uri)
            handleZipStageResult(
                res,
                fallbackError = msg("ไม่สามารถอ่านไฟล์ ZIP ได้", "Unable to read ZIP file"),
            )
        }
    }

    fun importFolderUri(uri: Uri) {
        viewModelScope.launch {
            val res = container.modInstallationEngine.stageFolderTreeUri(uri)
            if (res.isSuccess) {
                _pendingInstall.value = res.getOrThrow()
            } else {
                val errMsg = res.exceptionOrNull()?.localizedMessage
                    ?: msg("ไม่สามารถอ่านโฟลเดอร์มอดได้", "Unable to read mod folder")
                container.logManager.error("ModInstaller", errMsg)
                _uiEvents.emit(UiEvent.ShowMessage(errMsg))
            }
        }
    }

    fun installFromCompletedDownload(task: DownloadTask) {
        val path = task.localFilePath ?: return
        viewModelScope.launch {
            val res = container.modInstallationEngine.stageLocalZipFile(File(path))
            handleZipStageResult(
                res,
                fallbackError = msg("เปิดไฟล์ติดตั้งไม่สำเร็จ", "Failed to open installer archive"),
            )
        }
    }

    fun submitZipPassword(password: String) {
        val current = _pendingZipPassword.value ?: return
        if (password.isEmpty()) {
            _pendingZipPassword.value = current.copy(
                errorMessage = msg("กรุณากรอกรหัสผ่านของไฟล์ ZIP", "Please enter the ZIP password"),
            )
            return
        }
        viewModelScope.launch {
            _pendingZipPassword.value = current.copy(isExtracting = true, errorMessage = null)
            val res = container.modInstallationEngine.stageArchivedZipFile(
                archiveFile = current.archiveFile,
                sourceLabel = current.sourceLabel,
                password = password,
                isOwnedTempFile = current.isOwnedTempFile,
            )
            if (res.isSuccess) {
                when (val outcome = res.getOrThrow()) {
                    is ZipStageResult.Ready -> {
                        _pendingZipPassword.value = null
                        _pendingInstall.value = outcome.pending
                    }
                    is ZipStageResult.PasswordRequired -> {
                        _pendingZipPassword.value = PendingZipPassword(
                            archiveFile = outcome.archiveFile,
                            sourceLabel = outcome.sourceLabel,
                            isOwnedTempFile = outcome.isOwnedTempFile,
                            errorMessage = msg(
                                "รหัสผ่านไม่ถูกต้อง กรุณาลองใหม่อีกครั้ง",
                                "Incorrect password, please try again",
                            ),
                            isExtracting = false,
                        )
                    }
                }
            } else {
                _pendingZipPassword.value = null
                val errMsg = res.exceptionOrNull()?.localizedMessage
                    ?: msg("ไม่สามารถแตกไฟล์ ZIP ได้", "Failed to extract ZIP archive")
                container.logManager.error("ModInstaller", errMsg)
                _uiEvents.emit(UiEvent.ShowMessage(errMsg))
            }
        }
    }

    fun cancelZipPassword() {
        val current = _pendingZipPassword.value ?: return
        _pendingZipPassword.value = null
        container.modInstallationEngine.discardPasswordPrompt(current.archiveFile, current.isOwnedTempFile)
    }

    private suspend fun handleZipStageResult(res: Result<ZipStageResult>, fallbackError: String) {
        if (res.isSuccess) {
            when (val outcome = res.getOrThrow()) {
                is ZipStageResult.Ready -> _pendingInstall.value = outcome.pending
                is ZipStageResult.PasswordRequired -> {
                    _pendingZipPassword.value = PendingZipPassword(
                        archiveFile = outcome.archiveFile,
                        sourceLabel = outcome.sourceLabel,
                        isOwnedTempFile = outcome.isOwnedTempFile,
                        errorMessage = outcome.errorMessage,
                        isExtracting = false,
                    )
                }
            }
        } else {
            val errMsg = res.exceptionOrNull()?.localizedMessage ?: fallbackError
            container.logManager.error("ModInstaller", errMsg)
            _uiEvents.emit(UiEvent.ShowMessage(errMsg))
        }
    }

    fun confirmPendingInstall() {
        val pending = _pendingInstall.value ?: return
        _pendingInstall.value = null
        viewModelScope.launch {
            when (val result = container.modInstallationEngine.commitPendingInstall(pending, _smapiStatus.value.smapiVersion)) {
                is ModInstallResult.Success -> {
                    _installedSuccessMod.value = result.mod
                    DownloadQueueMonitorWorker.enqueueModInstalledNotification(
                        context = container.appContext,
                        modName = result.mod.manifest.name,
                        version = result.mod.manifest.version,
                        uniqueId = result.mod.uniqueId,
                        wasUpdate = result.wasUpdate,
                    )
                    refreshSystemDiagnostics()
                }
                is ModInstallResult.Failure -> {
                    val rollbackSuffix = if (result.rolledBack) {
                        msg(" (คืนค่าเวอร์ชันเดิมแล้ว)", " (Previous version restored)")
                    } else {
                        ""
                    }
                    _uiEvents.emit(UiEvent.ShowMessage("${result.reason}$rollbackSuffix"))
                }
            }
        }
    }

    fun cancelPendingInstall() {
        val pending = _pendingInstall.value ?: return
        _pendingInstall.value = null
        container.modInstallationEngine.discardPendingInstall(pending)
    }

    fun dismissInstallSuccess() {
        _installedSuccessMod.value = null
    }

    fun toggleModEnabled(uniqueId: String) {
        viewModelScope.launch {
            container.modInstallationEngine.toggleModEnabled(uniqueId, _smapiStatus.value.smapiVersion)
            refreshSystemDiagnostics()
        }
    }

    fun uninstallMod(uniqueId: String) {
        viewModelScope.launch {
            val ok = container.modInstallationEngine.uninstallMod(uniqueId, _smapiStatus.value.smapiVersion)
            if (ok) {
                _uiEvents.emit(UiEvent.ShowMessage(msg("ถอนการติดตั้งมอดแล้ว", "Mod uninstalled")))
                refreshSystemDiagnostics()
            }
        }
    }

    fun launchGame() {
        val detection = _gameDetection.value
        if (detection == null) {
            viewModelScope.launch {
                _uiEvents.emit(UiEvent.ShowMessage(msg("กำลังตรวจสอบสถานะเกม...", "Checking game status...")))
            }
            return
        }
        when (val outcome = container.gameLauncher.launch(detection, _smapiStatus.value, container.logManager)) {
            is LaunchOutcome.Launched -> {
                val activeMods = installedMods.value.count { it.isEnabled }
                val modeLabel = if (outcome.viaSmapi) "SMAPI" else "Stardew Valley"
                startPlaytimeTracking(
                    showToast = false,
                    activeModCount = activeMods,
                    launchMode = modeLabel,
                )
                viewModelScope.launch {
                    val via = if (outcome.viaSmapi) "SMAPI Loader" else "Stardew Valley"
                    _uiEvents.emit(
                        UiEvent.ShowMessage(
                            msg(
                                "กำลังเปิดเกมผ่าน $via และแสดงแถบจับเวลาเล่นเกม",
                                "Launching via $via with playtime timer active",
                            ),
                        ),
                    )
                }
            }
            is LaunchOutcome.Blocked -> {
                viewModelScope.launch { _uiEvents.emit(UiEvent.LaunchBlocked(outcome.reason)) }
            }
        }
    }

    fun startPlaytimeTracking(
        showToast: Boolean = true,
        activeModCount: Int = installedMods.value.count { it.isEnabled },
        launchMode: String = "Stardew Valley",
    ) {
        viewModelScope.launch {
            val startEpoch = container.settingsPreferences.startPlaytimeSession(
                activeModCount = activeModCount,
                launchMode = launchMode,
            )
            PlaytimeTrackingService.start(container.appContext, startEpoch)
            if (showToast) {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg(
                            "เริ่มจับเวลาเล่นเกมบนแถบแจ้งเตือนแล้ว",
                            "Playtime timer started in notification bar",
                        ),
                    ),
                )
            }
        }
    }

    fun stopPlaytimeTracking() {
        viewModelScope.launch {
            val stopped = container.settingsPreferences.stopPlaytimeSession()
            if (stopped != null && stopped.durationSeconds >= 1L) {
                container.playStatsRepository.recordCompletedSession(
                    startedAtMs = stopped.startedAtMs,
                    endedAtMs = stopped.endedAtMs,
                    durationSeconds = stopped.durationSeconds,
                    activeModCount = stopped.activeModCount,
                    launchMode = stopped.launchMode,
                )
            }
            PlaytimeTrackingService.stop(container.appContext)
            container.notificationHelper.cancelPlaytimeNotification()
            _uiEvents.emit(UiEvent.ShowMessage(msg("บันทึกเวลาเล่นเกมแล้ว", "Playtime session saved")))
        }
    }

    fun resetPlayStatistics() {
        viewModelScope.launch {
            PlaytimeTrackingService.stop(container.appContext)
            container.notificationHelper.cancelPlaytimeNotification()
            container.settingsPreferences.resetPlayStats()
            container.playStatsRepository.clearHistory()
            _uiEvents.emit(UiEvent.ShowMessage(msg("รีเซ็ตสถิติการเล่นแล้ว", "Play statistics reset")))
        }
    }

    fun requestNavigateTab(tab: String) {
        _pendingNavigateTab.value = tab
    }

    fun consumeNavigateTab() {
        _pendingNavigateTab.value = null
    }

    fun pauseDownload(taskId: String) = container.modDownloadManager.pauseDownload(taskId)
    fun resumeDownload(taskId: String) = container.modDownloadManager.resumeOrRetryDownload(taskId)
    fun cancelDownload(taskId: String) = container.modDownloadManager.cancelDownload(taskId)
    fun removeDownloadRecord(taskId: String) = container.modDownloadManager.removeRecord(taskId)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { container.settingsPreferences.setThemeMode(mode) }
    }

    fun setAppLanguage(language: AppLanguage) {
        viewModelScope.launch {
            container.settingsPreferences.setAppLanguage(language)
            val english = language == AppLanguage.ENGLISH
            container.notificationHelper.isEnglish = english
            container.notificationHelper.ensureChannels(english)
            val current = settings.value
            current.activePlaytimeStartMs?.let { activeStart ->
                container.notificationHelper.notifyPlaytimeUpdate(
                    accumulatedBeforeSessionSeconds = current.totalPlaytimeSeconds,
                    sessionStartMs = activeStart,
                    english = english,
                )
            }
        }
    }

    fun toggleAppLanguage() {
        val next = if (settings.value.appLanguage == AppLanguage.THAI) {
            AppLanguage.ENGLISH
        } else {
            AppLanguage.THAI
        }
        setAppLanguage(next)
    }

    fun saveSafModsFolder(uri: Uri) {
        viewModelScope.launch {
            container.settingsPreferences.setSafModsUri(uri.toString())
            refreshSystemDiagnostics()
            _uiEvents.emit(UiEvent.ShowMessage(msg("บันทึกสิทธิ์โฟลเดอร์เกมแล้ว", "Game folder permission saved")))
        }
    }

    fun setPreferredSmapiVersion(version: String) {
        viewModelScope.launch {
            container.settingsPreferences.setPreferredSmapiVersion(version)
            refreshSystemDiagnostics()
        }
    }

    fun createModsBackupZip(downloadImmediately: Boolean = true) {
        if (_isCreatingBackup.value) return
        viewModelScope.launch {
            _isCreatingBackup.value = true
            val res = container.saveBackupManager.createModsAndLogsBackupZip()
            _isCreatingBackup.value = false
            if (res.isSuccess) {
                val backup = res.getOrThrow()
                if (downloadImmediately) {
                    _uiEvents.emit(UiEvent.PromptExportBackupZip(backup))
                } else {
                    DownloadQueueMonitorWorker.enqueueFileExportedNotification(
                        context = container.appContext,
                        fileName = backup.fileName,
                        exportLabel = msg("สร้างไฟล์สำรอง ZIP", "Backup ZIP Created"),
                        sizeBytes = backup.sizeBytes,
                    )
                    _uiEvents.emit(
                        UiEvent.ShowMessage(
                            msg("สร้างไฟล์สำรอง ${backup.fileName} แล้ว", "Created backup ${backup.fileName}"),
                        ),
                    )
                }
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        res.exceptionOrNull()?.localizedMessage
                            ?: msg("สร้างไฟล์ ZIP สำรองล้มเหลว", "Failed to create backup ZIP"),
                    ),
                )
            }
        }
    }

    fun exportBackupZipToUri(backup: SaveBackupInfo, destUri: Uri) {
        viewModelScope.launch {
            val res = container.saveBackupManager.exportBackupToUri(backup, destUri)
            if (res.isSuccess) {
                val exportedBytes = res.getOrDefault(backup.sizeBytes)
                DownloadQueueMonitorWorker.enqueueFileExportedNotification(
                    context = container.appContext,
                    fileName = backup.fileName,
                    exportLabel = msg("ส่งออกไฟล์สำรอง ZIP", "Backup ZIP Export"),
                    sizeBytes = exportedBytes,
                )
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg("บันทึกไฟล์ ${backup.fileName} ลงเครื่องแล้ว", "Saved ${backup.fileName} to device"),
                    ),
                )
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        res.exceptionOrNull()?.localizedMessage
                            ?: msg("บันทึกไฟล์ ZIP ลงเครื่องไม่สำเร็จ", "Failed to save ZIP file"),
                    ),
                )
            }
        }
    }

    fun restoreModsBackup(backup: SaveBackupInfo) {
        viewModelScope.launch {
            val res = container.saveBackupManager.restoreModsFromBackupZip(backup)
            if (res.isSuccess) {
                refreshSystemDiagnostics()
                val count = res.getOrThrow()
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg("กู้คืนมอดจาก ZIP แล้ว ($count ไฟล์)", "Restored mods from ZIP ($count files)"),
                    ),
                )
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        res.exceptionOrNull()?.localizedMessage
                            ?: msg("กู้คืนมอดจาก ZIP ล้มเหลว", "Failed to restore mods from ZIP"),
                    ),
                )
            }
        }
    }

    fun importModsBackupZipUri(uri: Uri) {
        viewModelScope.launch {
            val res = container.saveBackupManager.importAndRestoreModsBackupUri(uri)
            if (res.isSuccess) {
                refreshSystemDiagnostics()
                val count = res.getOrThrow()
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg(
                            "นำเข้าและกู้คืนมอดจากไฟล์ ZIP แล้ว ($count ไฟล์)",
                            "Imported & restored mods from ZIP ($count files)",
                        ),
                    ),
                )
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        res.exceptionOrNull()?.localizedMessage
                            ?: msg("นำเข้าไฟล์สำรองไม่สำเร็จ", "Failed to import backup ZIP"),
                    ),
                )
            }
        }
    }

    fun getBackupShareUri(backup: SaveBackupInfo): Uri = container.saveBackupManager.getShareableUri(backup)

    fun backupSavesFromUri(treeUri: Uri) {
        viewModelScope.launch {
            container.settingsPreferences.setSafSavesUri(treeUri.toString())
            _isCreatingBackup.value = true
            val res = container.saveBackupManager.createBackupFromTreeUri(treeUri)
            _isCreatingBackup.value = false
            if (res.isSuccess) {
                val backup = res.getOrThrow()
                _uiEvents.emit(UiEvent.PromptExportBackupZip(backup))
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        res.exceptionOrNull()?.localizedMessage
                            ?: msg("สำรองเซฟล้มเหลว", "Failed to backup save files"),
                    ),
                )
            }
        }
    }

    fun restoreSaveBackup(backup: SaveBackupInfo, targetTreeUri: Uri) {
        viewModelScope.launch {
            val res = container.saveBackupManager.restoreBackupToTreeUri(backup, targetTreeUri)
            if (res.isSuccess) {
                val count = res.getOrThrow()
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg("กู้คืนเซฟเกมแล้ว ($count ไฟล์)", "Restored save files ($count files)"),
                    ),
                )
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        res.exceptionOrNull()?.localizedMessage
                            ?: msg("กู้คืนเซฟล้มเหลว", "Failed to restore save files"),
                    ),
                )
            }
        }
    }

    fun deleteSaveBackup(backup: SaveBackupInfo) {
        viewModelScope.launch {
            container.saveBackupManager.deleteBackup(backup)
            _uiEvents.emit(UiEvent.ShowMessage(msg("ลบไฟล์สำรองแล้ว", "Backup deleted")))
        }
    }

    fun reloadLocalLogs() {
        viewModelScope.launch {
            val safLogUri = settings.value.safLogUri
            val count = container.logManager.reloadLocalLogs(safLogUri)
            if (count == 0) {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg(
                            "ไม่พบไฟล์ SMAPI-latest.txt ในเครื่อง กรุณากดปุ่มนำเข้าไฟล์ Log",
                            "SMAPI-latest.txt not found on device. Tap Import Log to pick a file.",
                        ),
                    ),
                )
            }
        }
    }

    fun importSmapiLog(uri: Uri) {
        viewModelScope.launch {
            val count = container.logManager.importSmapiLogFromUri(uri)
            if (count > 0) {
                container.settingsPreferences.setSafLogUri(uri.toString())
            }
            _uiEvents.emit(
                UiEvent.ShowMessage(
                    if (count > 0) {
                        msg("นำเข้าบันทึก $count รายการแล้ว", "Imported $count log entries")
                    } else {
                        msg("ไม่พบข้อมูลบันทึกที่อ่านได้", "No readable log entries found")
                    },
                ),
            )
        }
    }

    fun showUiMessage(text: String) {
        viewModelScope.launch { _uiEvents.emit(UiEvent.ShowMessage(text)) }
    }

    fun clearLogs() {
        viewModelScope.launch { container.settingsPreferences.setSafLogUri(null) }
        container.logManager.clearLogs()
    }

    fun exportLogsText(): String = container.logManager.buildExportText()

    fun refreshMultiplayerRooms() {
        container.multiplayerLobbyEngine.refreshRooms()
    }

    fun hostMultiplayerRoom(
        roomName: String,
        ownerName: String,
        maxPlayers: Int,
        visibility: RoomVisibility,
        password: String,
    ) {
        viewModelScope.launch {
            val gameVer = _gameDetection.value?.versionName?.takeIf { it.isNotBlank() } ?: "v1.6.15"
            val room = container.multiplayerLobbyEngine.startHostingRoom(
                HostedRoomConfig(
                    roomName = roomName,
                    ownerName = ownerName,
                    maxPlayers = maxPlayers,
                    visibility = visibility,
                    password = password,
                    gameVersion = gameVer,
                ),
            )
            if (room != null) {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg(
                            "เปิดห้อง ${room.roomName} แล้ว (รหัส: ${room.roomCode})",
                            "Room ${room.roomName} hosted (Code: ${room.roomCode})",
                        ),
                    ),
                )
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(
                        msg("ไม่สามารถเปิดพอร์ตเครือข่ายเพื่อสร้างห้องได้", "Could not open network socket to host room"),
                    ),
                )
            }
        }
    }

    fun stopHostingMultiplayerRoom() {
        container.multiplayerLobbyEngine.stopHostingRoom()
        viewModelScope.launch {
            _uiEvents.emit(UiEvent.ShowMessage(msg("ปิดห้องโฮสต์แล้ว", "Hosted room closed")))
        }
    }

    fun joinMultiplayerRoom(room: MultiplayerRoom, enteredPassword: String? = null) {
        viewModelScope.launch {
            when (val res = container.multiplayerLobbyEngine.joinRoom(room, enteredPassword)) {
                is JoinRoomResult.Success -> {
                    _pendingJoinPasswordRoom.value = null
                    _joinPasswordError.value = null
                    _uiEvents.emit(
                        UiEvent.ShowMessage(
                            msg(
                                "เชื่อมต่อห้อง ${res.room.roomName} (${res.room.hostAddress}:${res.room.port}) สำเร็จ กำลังเปิดเกม...",
                                "Connected to ${res.room.roomName} (${res.room.hostAddress}:${res.room.port}). Launching game...",
                            ),
                        ),
                    )
                    launchGame()
                }
                is JoinRoomResult.PasswordRequired -> {
                    _joinPasswordError.value = null
                    _pendingJoinPasswordRoom.value = res.room
                }
                is JoinRoomResult.InvalidPassword -> {
                    _joinPasswordError.value = msg("รหัสผ่านห้องไม่ถูกต้อง", "Incorrect room password")
                    _pendingJoinPasswordRoom.value = res.room
                }
                is JoinRoomResult.RoomFull -> {
                    _pendingJoinPasswordRoom.value = null
                    _uiEvents.emit(UiEvent.ShowMessage(msg("ห้องนี้เต็มแล้ว", "This room is full")))
                }
                is JoinRoomResult.Unreachable -> {
                    _pendingJoinPasswordRoom.value = null
                    _uiEvents.emit(UiEvent.ShowMessage(msg(res.reasonTh, res.reasonEn)))
                }
            }
        }
    }

    fun joinMultiplayerByCode(codeOrAddress: String, enteredPassword: String? = null) {
        viewModelScope.launch {
            val gameVer = _gameDetection.value?.versionName?.takeIf { it.isNotBlank() } ?: "v1.6.15"
            when (val res = container.multiplayerLobbyEngine.joinByRoomCode(codeOrAddress, enteredPassword, gameVer)) {
                is JoinRoomResult.Success -> {
                    _pendingJoinPasswordRoom.value = null
                    _joinPasswordError.value = null
                    _uiEvents.emit(
                        UiEvent.ShowMessage(
                            msg(
                                "เข้าร่วมห้อง ${res.room.roomName} สำเร็จ กำลังเปิดเกม...",
                                "Joined ${res.room.roomName}. Launching game...",
                            ),
                        ),
                    )
                    launchGame()
                }
                is JoinRoomResult.PasswordRequired -> {
                    _joinPasswordError.value = null
                    _pendingJoinPasswordRoom.value = res.room
                }
                is JoinRoomResult.InvalidPassword -> {
                    _joinPasswordError.value = msg("รหัสผ่านห้องไม่ถูกต้อง", "Incorrect room password")
                    _pendingJoinPasswordRoom.value = res.room
                }
                is JoinRoomResult.RoomFull -> {
                    _uiEvents.emit(UiEvent.ShowMessage(msg("ห้องนี้เต็มแล้ว", "This room is full")))
                }
                is JoinRoomResult.Unreachable -> {
                    _uiEvents.emit(UiEvent.ShowMessage(msg(res.reasonTh, res.reasonEn)))
                }
            }
        }
    }

    fun dismissJoinPasswordPrompt() {
        _pendingJoinPasswordRoom.value = null
        _joinPasswordError.value = null
    }

    fun toggleFavoriteRoom(room: MultiplayerRoom) {
        viewModelScope.launch {
            val isFav = room.roomId in favoriteRoomIds.value
            container.multiplayerLobbyEngine.toggleFavorite(room, isFav)
        }
    }

    fun clearCoopHistory() {
        viewModelScope.launch {
            container.multiplayerLobbyEngine.clearHistory()
            _uiEvents.emit(UiEvent.ShowMessage(msg("ล้างประวัติการเล่นแล้ว", "Co-op play history cleared")))
        }
    }

    override fun onCleared() {
        super.onCleared()
        container.multiplayerLobbyEngine.shutdown()
    }

    class Factory(private val container: KpziContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KpziViewModel(container) as T
        }
    }
}
