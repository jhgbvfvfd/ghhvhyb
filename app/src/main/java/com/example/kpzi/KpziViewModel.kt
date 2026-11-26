package com.example.kpzi

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.kpzi.core.network.NexusApiClient
import com.example.kpzi.data.local.PlaySessionEntity
import com.example.kpzi.data.local.UserSettings
import com.example.kpzi.data.local.toCacheEntity
import com.example.kpzi.data.local.toSummary
import com.example.kpzi.domain.model.ApiQuotaState
import com.example.kpzi.domain.model.CompatibilityLevel
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
import com.example.kpzi.engine.loader.ConflictDetectionReport
import com.example.kpzi.engine.loader.LaunchOutcome
import com.example.kpzi.engine.loader.PreLaunchReport
import com.example.kpzi.notifications.PlaytimeTrackingService
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
            gameLoaderStateLabel = "กำลังตรวจสอบ...",
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

    init {
        initializeApp()
        observeSearchDebounce()
        observeCachedCatalog()
    }

    private fun initializeApp() {
        viewModelScope.launch {
            val currentSettings = container.settingsPreferences.settingsFlow.first()
            _modDetailState.update { it.copy(translateToThai = currentSettings.autoTranslateDescriptions) }
            container.logManager.reloadLocalLogs(currentSettings.safLogUri)
            currentSettings.activePlaytimeStartMs?.let { activeStart ->
                if (container.notificationHelper.canPostNotifications()) {
                    PlaytimeTrackingService.start(container.appContext, activeStart)
                }
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
                val err = result.exceptionOrNull()?.localizedMessage ?: "โหลดรายการมอดไม่สำเร็จ"
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
                            supportedGameVersions = listOf("กำลังตรวจสอบจาก Nexus Mods..."),
                            requiredSmapiVersion = null,
                            changelogs = emptyList(),
                            licenseName = "ตรวจสอบตามหน้าต้นฉบับ Nexus Mods",
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
                        errorMessage = "กรุณาเชื่อมบัญชี Nexus Mods API Key ในหน้าตั้งค่าเพื่อดึงรายการไฟล์และ Changelog ฉบับเต็ม",
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
                        errorMessage = fetched.exceptionOrNull()?.localizedMessage ?: "โหลดรายละเอียดไม่สำเร็จ",
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
                            ?: "ไม่สามารถรับลิงก์ดาวน์โหลดตรงได้ กรุณาดาวน์โหลดผ่านหน้าเว็บ Nexus Mods",
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
            _uiEvents.emit(UiEvent.ShowMessage("เพิ่ม ${detail.summary.name} เข้าคิวดาวน์โหลดแล้ว"))
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
                _uiEvents.emit(UiEvent.ShowMessage("กรุณาเชื่อม Nexus API Key ก่อนใช้งาน NXM Deep Link"))
                return@launch
            }
            val detail = runCatching { container.nexusApiClient.fetchModDetail(modId, apiKey) }.getOrNull()
            val targetFile = detail?.files?.firstOrNull { it.fileId == fileId }
            val links = runCatching {
                container.nexusApiClient.requestDownloadLinks(modId, fileId, apiKey, key, expires)
            }.getOrNull().orEmpty()

            val downloadUrl = links.firstOrNull()
            if (downloadUrl == null) {
                _uiEvents.emit(UiEvent.ShowMessage("ลิงก์ NXM หมดอายุหรือไม่ถูกต้อง"))
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
            _uiEvents.emit(UiEvent.ShowMessage("รับลิงก์ NXM และเริ่มดาวน์โหลดแล้ว"))
        }
    }

    fun connectNexusApiKey(apiKeyInput: String) {
        viewModelScope.launch {
            val trimmed = apiKeyInput.trim()
            if (trimmed.isEmpty()) {
                _accountError.value = "กรุณากรอก Personal API Key"
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
                _uiEvents.emit(UiEvent.ShowMessage("เชื่อมต่อบัญชี ${profile.username} สำเร็จ"))
                loadHomeFeeds(forceRefresh = true)
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "ยืนยัน API Key ไม่สำเร็จ"
                _accountError.value = msg
                container.logManager.error("NexusAuth", msg)
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
            if (res.isSuccess) {
                _pendingInstall.value = res.getOrThrow()
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "ไม่สามารถอ่านไฟล์ ZIP ได้"
                container.logManager.error("ModInstaller", msg)
                _uiEvents.emit(UiEvent.ShowMessage(msg))
            }
        }
    }

    fun importFolderUri(uri: Uri) {
        viewModelScope.launch {
            val res = container.modInstallationEngine.stageFolderTreeUri(uri)
            if (res.isSuccess) {
                _pendingInstall.value = res.getOrThrow()
            } else {
                val msg = res.exceptionOrNull()?.localizedMessage ?: "ไม่สามารถอ่านโฟลเดอร์มอดได้"
                container.logManager.error("ModInstaller", msg)
                _uiEvents.emit(UiEvent.ShowMessage(msg))
            }
        }
    }

    fun installFromCompletedDownload(task: DownloadTask) {
        val path = task.localFilePath ?: return
        viewModelScope.launch {
            val res = container.modInstallationEngine.stageLocalZipFile(File(path))
            if (res.isSuccess) {
                _pendingInstall.value = res.getOrThrow()
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "เปิดไฟล์ติดตั้งไม่สำเร็จ"),
                )
            }
        }
    }

    fun confirmPendingInstall() {
        val pending = _pendingInstall.value ?: return
        _pendingInstall.value = null
        viewModelScope.launch {
            when (val result = container.modInstallationEngine.commitPendingInstall(pending, _smapiStatus.value.smapiVersion)) {
                is ModInstallResult.Success -> {
                    _installedSuccessMod.value = result.mod
                    refreshSystemDiagnostics()
                }
                is ModInstallResult.Failure -> {
                    val rollbackSuffix = if (result.rolledBack) " (คืนค่าเวอร์ชันเดิมแล้ว)" else ""
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
                _uiEvents.emit(UiEvent.ShowMessage("ถอนการติดตั้งมอดแล้ว"))
                refreshSystemDiagnostics()
            }
        }
    }

    fun launchGame() {
        val detection = _gameDetection.value
        if (detection == null) {
            viewModelScope.launch { _uiEvents.emit(UiEvent.ShowMessage("กำลังตรวจสอบสถานะเกม...")) }
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
                    _uiEvents.emit(UiEvent.ShowMessage("กำลังเปิดเกมผ่าน $via และแสดงแถบจับเวลาเล่นเกม"))
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
                _uiEvents.emit(UiEvent.ShowMessage("เริ่มจับเวลาเล่นเกมบนแถบแจ้งเตือนแล้ว"))
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
            _uiEvents.emit(UiEvent.ShowMessage("บันทึกเวลาเล่นเกมแล้ว"))
        }
    }

    fun resetPlayStatistics() {
        viewModelScope.launch {
            PlaytimeTrackingService.stop(container.appContext)
            container.notificationHelper.cancelPlaytimeNotification()
            container.settingsPreferences.resetPlayStats()
            container.playStatsRepository.clearHistory()
            _uiEvents.emit(UiEvent.ShowMessage("รีเซ็ตสถิติการเล่นแล้ว"))
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

    fun saveSafModsFolder(uri: Uri) {
        viewModelScope.launch {
            container.settingsPreferences.setSafModsUri(uri.toString())
            refreshSystemDiagnostics()
            _uiEvents.emit(UiEvent.ShowMessage("บันทึกสิทธิ์โฟลเดอร์เกมแล้ว"))
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
                    _uiEvents.emit(UiEvent.ShowMessage("สร้างไฟล์สำรอง ${backup.fileName} แล้ว"))
                }
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "สร้างไฟล์ ZIP สำรองล้มเหลว"),
                )
            }
        }
    }

    fun exportBackupZipToUri(backup: SaveBackupInfo, destUri: Uri) {
        viewModelScope.launch {
            val res = container.saveBackupManager.exportBackupToUri(backup, destUri)
            if (res.isSuccess) {
                _uiEvents.emit(UiEvent.ShowMessage("บันทึกไฟล์ ${backup.fileName} ลงเครื่องแล้ว"))
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "บันทึกไฟล์ ZIP ลงเครื่องไม่สำเร็จ"),
                )
            }
        }
    }

    fun restoreModsBackup(backup: SaveBackupInfo) {
        viewModelScope.launch {
            val res = container.saveBackupManager.restoreModsFromBackupZip(backup)
            if (res.isSuccess) {
                refreshSystemDiagnostics()
                _uiEvents.emit(UiEvent.ShowMessage("กู้คืนมอดจาก ZIP แล้ว (${res.getOrThrow()} ไฟล์)"))
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "กู้คืนมอดจาก ZIP ล้มเหลว"),
                )
            }
        }
    }

    fun importModsBackupZipUri(uri: Uri) {
        viewModelScope.launch {
            val res = container.saveBackupManager.importAndRestoreModsBackupUri(uri)
            if (res.isSuccess) {
                refreshSystemDiagnostics()
                _uiEvents.emit(UiEvent.ShowMessage("นำเข้าและกู้คืนมอดจากไฟล์ ZIP แล้ว (${res.getOrThrow()} ไฟล์)"))
            } else {
                _uiEvents.emit(
                    UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "นำเข้าไฟล์สำรองไม่สำเร็จ"),
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
                _uiEvents.emit(UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "สำรองเซฟล้มเหลว"))
            }
        }
    }

    fun restoreSaveBackup(backup: SaveBackupInfo, targetTreeUri: Uri) {
        viewModelScope.launch {
            val res = container.saveBackupManager.restoreBackupToTreeUri(backup, targetTreeUri)
            if (res.isSuccess) {
                _uiEvents.emit(UiEvent.ShowMessage("กู้คืนเซฟเกมแล้ว (${res.getOrThrow()} ไฟล์)"))
            } else {
                _uiEvents.emit(UiEvent.ShowMessage(res.exceptionOrNull()?.localizedMessage ?: "กู้คืนเซฟล้มเหลว"))
            }
        }
    }

    fun deleteSaveBackup(backup: SaveBackupInfo) {
        viewModelScope.launch {
            container.saveBackupManager.deleteBackup(backup)
            _uiEvents.emit(UiEvent.ShowMessage("ลบไฟล์สำรองแล้ว"))
        }
    }

    fun reloadLocalLogs() {
        viewModelScope.launch {
            val safLogUri = settings.value.safLogUri
            val count = container.logManager.reloadLocalLogs(safLogUri)
            if (count == 0) {
                _uiEvents.emit(UiEvent.ShowMessage("ไม่พบไฟล์ SMAPI-latest.txt ในเครื่อง กรุณากดปุ่มนำเข้าไฟล์ Log"))
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
                UiEvent.ShowMessage(if (count > 0) "นำเข้าบันทึก $count รายการแล้ว" else "ไม่พบข้อมูลบันทึกที่อ่านได้"),
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

    class Factory(private val container: KpziContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return KpziViewModel(container) as T
        }
    }
}
