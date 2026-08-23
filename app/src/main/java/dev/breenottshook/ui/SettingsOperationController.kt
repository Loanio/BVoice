package dev.breenottshook.ui

import dev.breenottshook.api.CatalogState
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.ConfigSnapshot
import dev.breenottshook.config.ConfigValidator
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
import dev.breenottshook.config.ValidationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

interface SettingsRepository {
    fun observe(): StateFlow<ConfigSnapshot>
    fun read(): ConfigSnapshot
    fun update(expectedVersion: Long, config: TtsConfig): UpdateResult
}

fun interface CatalogGateway {
    suspend fun refresh(baseUrl: String): CatalogState
}

fun interface ConnectionTester {
    suspend fun test(config: TtsConfig): Result<Unit>
}

interface PreviewController {
    suspend fun preview(text: String, config: TtsConfig, listener: PreviewListener): Result<Unit>
    suspend fun stop()
}

interface PreviewListener {
    fun onStarted()
    fun onCompleted()
    fun onError(error: Throwable)
    fun onCancelled(reason: String)
}

enum class SettingsOperation {
    IDLE,
    REFRESHING_CATALOG,
    TESTING_CONNECTION,
    QUICK_SETUP,
    PREVIEWING
}

enum class ServiceStatus {
    UNCHECKED,
    CHECKING,
    AVAILABLE,
    UNAVAILABLE
}

data class SettingsUiState(
    val persistedVersion: Long,
    val persisted: TtsConfig,
    val draft: TtsConfig,
    val validationIssues: Map<String, String> = emptyMap(),
    val characters: List<String> = emptyList(),
    val emotions: List<String> = emptyList(),
    val catalog: CharacterCatalog? = null,
    val operation: SettingsOperation = SettingsOperation.IDLE,
    val serviceStatus: ServiceStatus = ServiceStatus.UNCHECKED,
    val serviceStatusMessage: String? = null,
    val isBusy: Boolean = false,
    val isPreviewing: Boolean = false,
    val connectionSucceeded: Boolean? = null,
    val message: String? = null
) {
    val hasUnsavedChanges: Boolean
        get() = draft != persisted
}

class SettingsOperationController(
    private val repository: SettingsRepository,
    private val catalogGateway: CatalogGateway,
    private val connectionTester: ConnectionTester,
    private val previewController: PreviewController,
    operationScope: CoroutineScope? = null
) : AutoCloseable {
    private companion object {
        const val AUTO_SAVE_DEBOUNCE_MILLIS = 200L
    }

    private var previewGeneration = 0L
    private var autoSaveJob: Job? = null
    private var addressCheckJob: Job? = null
    private val controllerJob = SupervisorJob(operationScope?.coroutineContext?.get(Job))
    private val scope = CoroutineScope(
        (operationScope?.coroutineContext ?: Dispatchers.Main.immediate) + controllerJob
    )
    private val initial = repository.read()
    private val mutableState = MutableStateFlow(initial.toUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        scope.launch {
            repository.observe().collectLatest { snapshot ->
                val current = mutableState.value
                if (!current.hasUnsavedChanges && snapshot.version != current.persistedVersion) {
                    mutableState.value = snapshot.toUiState(
                        catalog = current.catalog,
                        characters = current.characters,
                        message = current.message
                    )
                }
            }
        }
    }

    fun edit(transform: (TtsConfig) -> TtsConfig) {
        val current = mutableState.value
        if (current.operation != SettingsOperation.IDLE) {
            return
        }
        val nextDraft = transform(current.draft)
        mutableState.value = current.copy(
            draft = nextDraft,
            validationIssues = emptyMap(),
            connectionSucceeded = null,
            message = null,
            emotions = current.catalog?.characters?.get(nextDraft.character).orEmpty()
        )
        scheduleAutoSave()
        if (nextDraft.baseUrl != current.draft.baseUrl) {
            scheduleAddressCheck()
        }
    }

    fun loadInitialCatalog() {
        if (mutableState.value.catalog != null) {
            return
        }
        refreshCatalog()
    }

    fun initialize() {
        testConnectionAndRefresh()
    }

    fun testConnectionAndRefresh() {
        addressCheckJob?.cancel()
        scope.launch { checkServiceAndRefresh() }
    }

    fun updateCoreSetting(transform: (TtsConfig) -> TtsConfig) {
        val previous = mutableState.value
        if (previous.operation != SettingsOperation.IDLE || previous.isBusy) {
            return
        }
        val nextDraft = transform(previous.draft)
        val persistedCandidate = previous.persisted.copyCoreSettingsFrom(nextDraft)
        mutableState.value = previous.copy(
            draft = nextDraft,
            validationIssues = emptyMap(),
            connectionSucceeded = null,
            message = null,
            emotions = previous.catalog?.characters?.get(nextDraft.character).orEmpty()
        )
        scope.launch {
            when (val validation = ConfigValidator.validate(persistedCandidate)) {
                is ValidationResult.Invalid -> {
                    mutableState.value = previous.copy(
                        validationIssues = validation.issues.associate { it.field to it.message },
                        message = "核心设置保存失败"
                    )
                }
                is ValidationResult.Valid -> {
                    mutableState.value = mutableState.value.copy(isBusy = true)
                    when (val result = repository.update(previous.persistedVersion, validation.value)) {
                        is UpdateResult.Success -> {
                            val syncedDraft = mutableState.value.draft.copyCoreSettingsFrom(
                                result.snapshot.value
                            )
                            applySnapshot(
                                snapshot = result.snapshot,
                                message = "核心设置已保存",
                                draft = syncedDraft
                            )
                        }
                        is UpdateResult.VersionConflict -> {
                            mutableState.value = previous.copy(
                                isBusy = false,
                                message = "核心设置保存失败：检测到配置冲突"
                            )
                        }
                        is UpdateResult.Invalid -> {
                            mutableState.value = previous.copy(
                                isBusy = false,
                                validationIssues = result.issues.associate { it.field to it.message },
                                message = "核心设置保存失败"
                            )
                        }
                        UpdateResult.PersistenceFailure -> {
                            mutableState.value = previous.copy(
                                isBusy = false,
                                message = "核心设置保存失败"
                            )
                        }
                    }
                }
            }
        }
    }

    fun save() {
        val current = mutableState.value
        when (val validation = ConfigValidator.validate(current.draft)) {
            is ValidationResult.Invalid -> {
                mutableState.value = current.copy(
                    validationIssues = validation.issues.associate { it.field to it.message },
                    message = "配置校验失败"
                )
            }
            is ValidationResult.Valid -> scope.launch {
                mutableState.value = mutableState.value.copy(isBusy = true, message = null)
                when (val result = repository.update(current.persistedVersion, validation.value)) {
                    is UpdateResult.Success -> applySnapshot(result.snapshot, "配置已保存")
                    is UpdateResult.VersionConflict -> {
                        applySnapshot(repository.read(), "检测到配置冲突，已加载另一界面的最新值")
                    }
                    is UpdateResult.Invalid -> {
                        mutableState.value = mutableState.value.copy(
                            isBusy = false,
                            validationIssues = result.issues.associate { it.field to it.message },
                            message = "配置校验失败"
                        )
                    }
                    UpdateResult.PersistenceFailure -> {
                        mutableState.value = mutableState.value.copy(
                            isBusy = false,
                            message = "配置保存失败"
                        )
                    }
                }
            }
        }
    }

    fun refreshCatalog() {
        val requestedUrl = mutableState.value.draft.baseUrl
        scope.launch {
            if (!beginOperation(SettingsOperation.REFRESHING_CATALOG) { current ->
                    current.copy(isBusy = true, message = null)
                }
            ) return@launch
            when (val result = catalogGateway.refresh(requestedUrl)) {
                is CatalogState.Fresh -> applyCatalog(result.catalog, null)
                is CatalogState.Stale -> applyCatalog(result.catalog, "刷新失败，正在使用缓存：${result.reason}")
                is CatalogState.Failed -> {
                    mutableState.value = mutableState.value.copy(
                        operation = SettingsOperation.IDLE,
                        isBusy = false,
                        message = "音色列表加载失败：${result.reason}"
                    )
                }
            }
        }
    }

    fun testConnection() {
        val draft = mutableState.value.draft
        scope.launch {
            if (!beginOperation(SettingsOperation.TESTING_CONNECTION) { current ->
                    current.copy(
                        isBusy = true,
                        connectionSucceeded = null,
                        serviceStatus = ServiceStatus.CHECKING,
                        serviceStatusMessage = null
                    )
                }
            ) return@launch
            val result = runCatching { connectionTester.test(draft) }
                .getOrElse { Result.failure(it) }
            mutableState.value = mutableState.value.copy(
                operation = SettingsOperation.IDLE,
                isBusy = false,
                connectionSucceeded = result.isSuccess,
                serviceStatus = if (result.isSuccess) {
                    ServiceStatus.AVAILABLE
                } else {
                    ServiceStatus.UNAVAILABLE
                },
                serviceStatusMessage = result.fold(
                    onSuccess = { "连接成功" },
                    onFailure = { "连接失败：${it.message ?: it::class.java.simpleName}" }
                ),
                message = result.fold(
                    onSuccess = { "连接成功" },
                    onFailure = { "连接失败：${it.message ?: it::class.java.simpleName}" }
                )
            )
        }
    }

    /** Saves the current configuration and runs the normal first-use check in one action. */
    fun quickSetup() {
        autoSaveJob?.cancel()
        scope.launch {
            if (!beginOperation(SettingsOperation.QUICK_SETUP) { current ->
                    current.copy(
                        isBusy = true,
                        connectionSucceeded = null,
                        serviceStatus = ServiceStatus.CHECKING,
                        serviceStatusMessage = "正在保存并检查服务…",
                        message = "正在保存并检查服务…"
                    )
                }
            ) return@launch

            val config = mutableState.value.draft
            when (val validation = ConfigValidator.validate(config)) {
                is ValidationResult.Invalid -> {
                    mutableState.value = mutableState.value.copy(
                        operation = SettingsOperation.IDLE,
                        isBusy = false,
                        validationIssues = validation.issues.associate { it.field to it.message },
                        message = "请先修正配置后再检查"
                    )
                    return@launch
                }
                is ValidationResult.Valid -> persistQuickSetupConfig(validation.value) ?: return@launch
            }

            val connectionResult = runCatching { connectionTester.test(mutableState.value.draft) }
                .getOrElse { Result.failure(it) }
            if (connectionResult.isFailure) {
                val reason = connectionResult.exceptionOrNull()?.message
                    ?: connectionResult.exceptionOrNull()?.javaClass?.simpleName
                    ?: "未知错误"
                mutableState.value = mutableState.value.copy(
                    operation = SettingsOperation.IDLE,
                    isBusy = false,
                    connectionSucceeded = false,
                    serviceStatus = ServiceStatus.UNAVAILABLE,
                    serviceStatusMessage = "连接失败：$reason",
                    message = "连接失败：$reason"
                )
                return@launch
            }

            mutableState.value = mutableState.value.copy(
                connectionSucceeded = true,
                serviceStatus = ServiceStatus.AVAILABLE,
                serviceStatusMessage = "连接成功，正在刷新音色…",
                message = "连接成功，正在刷新音色…"
            )
            when (val catalogResult = catalogGateway.refresh(mutableState.value.draft.baseUrl)) {
                is CatalogState.Fresh -> applyCatalog(
                    catalogResult.catalog,
                    "音色已刷新，正在试听…",
                    SettingsOperation.QUICK_SETUP,
                    true
                )
                is CatalogState.Stale -> applyCatalog(
                    catalogResult.catalog,
                    "音色刷新失败，正在使用缓存试听…",
                    SettingsOperation.QUICK_SETUP,
                    true
                )
                is CatalogState.Failed -> {
                    mutableState.value = mutableState.value.copy(
                        message = "音色列表加载失败，仍将尝试试听：${catalogResult.reason}"
                    )
                }
            }
            startQuickPreview()
        }
    }

    private fun scheduleAddressCheck() {
        addressCheckJob?.cancel()
        addressCheckJob = scope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MILLIS)
            checkServiceAndRefresh()
        }
    }

    private suspend fun checkServiceAndRefresh() {
        val current = mutableState.value
        if (current.operation != SettingsOperation.IDLE || current.draft.baseUrl.isBlank()) return
        if (!beginOperation(SettingsOperation.QUICK_SETUP) { state ->
                state.copy(
                    isBusy = true,
                    connectionSucceeded = null,
                    serviceStatus = ServiceStatus.CHECKING,
                    serviceStatusMessage = "正在检查服务并刷新音色…",
                    message = "正在检查服务并刷新音色…"
                )
            }
        ) return

        val connection = runCatching { connectionTester.test(current.draft) }
            .getOrElse { Result.failure(it) }
        if (connection.isFailure) {
            val reason = connection.exceptionOrNull()?.message
                ?: connection.exceptionOrNull()?.javaClass?.simpleName
                ?: "未知错误"
            mutableState.value = mutableState.value.copy(
                operation = SettingsOperation.IDLE,
                isBusy = false,
                connectionSucceeded = false,
                serviceStatus = ServiceStatus.UNAVAILABLE,
                serviceStatusMessage = "连接失败：$reason",
                message = "连接失败：$reason，可修改地址后重试"
            )
            return
        }

        mutableState.value = mutableState.value.copy(
            connectionSucceeded = true,
            serviceStatus = ServiceStatus.AVAILABLE,
            serviceStatusMessage = "连接成功，正在刷新音色…"
        )
        when (val result = catalogGateway.refresh(current.draft.baseUrl)) {
            is CatalogState.Fresh -> applyCatalog(result.catalog, "音色列表已自动刷新")
            is CatalogState.Stale -> applyCatalog(result.catalog, "刷新失败，已使用缓存音色")
            is CatalogState.Failed -> mutableState.value = mutableState.value.copy(
                operation = SettingsOperation.IDLE,
                isBusy = false,
                message = "连接成功，但音色列表加载失败，可点击一键检查并试听重试"
            )
        }
    }

    fun preview(configOverride: TtsConfig? = null) {
        val draft = configOverride ?: mutableState.value.draft
        val generation = ++previewGeneration
        scope.launch {
            if (!beginOperation(SettingsOperation.PREVIEWING) { current ->
                    current.copy(isBusy = true, message = null)
                }
            ) return@launch
            val result = previewController.preview(
                draft.testText,
                draft,
                object : PreviewListener {
                    override fun onStarted() = updatePreview(generation, isPreviewing = true)

                    override fun onCompleted() = updatePreview(generation, isPreviewing = false)

                    override fun onError(error: Throwable) = updatePreview(
                        generation,
                        isPreviewing = false,
                        message = "试听失败：${error.message ?: error::class.java.simpleName}"
                    )

                    override fun onCancelled(reason: String) =
                        updatePreview(generation, isPreviewing = false)
                }
            )
            mutableState.value = mutableState.value.copy(
                isBusy = false,
                message = result.exceptionOrNull()?.let {
                    "试听失败：${it.message ?: it::class.java.simpleName}"
                }
            )
        }
    }

    fun stopPreview() {
        previewGeneration++
        scope.launch {
            previewController.stop()
            mutableState.value = mutableState.value.copy(
                operation = SettingsOperation.IDLE,
                isPreviewing = false,
                isBusy = false
            )
        }
    }

    private fun updatePreview(generation: Long, isPreviewing: Boolean, message: String? = null) {
        if (generation != previewGeneration) return
        mutableState.value = mutableState.value.copy(
            operation = if (isPreviewing) {
                SettingsOperation.PREVIEWING
            } else {
                SettingsOperation.IDLE
            },
            isPreviewing = isPreviewing,
            isBusy = false,
            message = message ?: mutableState.value.message
        )
    }

    fun resetDefaults() {
        edit { TtsConfig() }
    }

    private fun scheduleAutoSave() {
        autoSaveJob?.cancel()
        autoSaveJob = scope.launch {
            delay(AUTO_SAVE_DEBOUNCE_MILLIS)
            saveAutomatically()
        }
    }

    private fun saveAutomatically() {
        val current = mutableState.value
        when (val validation = ConfigValidator.validate(current.draft)) {
            is ValidationResult.Invalid -> {
                mutableState.value = current.copy(
                    validationIssues = validation.issues.associate { it.field to it.message },
                    message = "自动保存待输入修正"
                )
            }
            is ValidationResult.Valid -> {
                mutableState.value = current.copy(isBusy = true, message = "正在自动保存")
                when (val result = repository.update(current.persistedVersion, validation.value)) {
                    is UpdateResult.Success -> {
                        val latestDraft = mutableState.value.draft
                        applySnapshot(
                            result.snapshot,
                            "已自动保存，已同步到小布（版本 ${result.snapshot.version}）",
                            draft = latestDraft
                        )
                        if (latestDraft != validation.value) scheduleAutoSave()
                    }
                    is UpdateResult.VersionConflict -> {
                        applySnapshot(repository.read(), "自动保存冲突，已加载另一界面的最新值")
                    }
                    is UpdateResult.Invalid -> {
                        mutableState.value = current.copy(
                            isBusy = false,
                            validationIssues = result.issues.associate { it.field to it.message },
                            message = "自动保存待输入修正"
                        )
                    }
                    UpdateResult.PersistenceFailure -> {
                        mutableState.value = current.copy(
                            isBusy = false,
                            message = "自动保存失败，请稍后重试"
                        )
                    }
                }
            }
        }
    }

    private fun applyCatalog(
        catalog: CharacterCatalog,
        message: String?,
        operation: SettingsOperation = SettingsOperation.IDLE,
        isBusy: Boolean = false
    ) {
        val current = mutableState.value
        val characters = catalog.characters.keys.sorted()
        val selectedCharacter = current.draft.character
            .takeIf { it in catalog.characters }
            ?: characters.firstOrNull()
            ?: current.draft.character
        val emotions = catalog.characters[selectedCharacter].orEmpty()
        val selectedEmotion = current.draft.emotion
            .takeIf { it in emotions }
            ?: emotions.firstOrNull()
            ?: current.draft.emotion
        mutableState.value = current.copy(
            draft = current.draft.copy(
                character = selectedCharacter,
                emotion = selectedEmotion
            ),
            operation = operation,
            catalog = catalog,
            characters = characters,
            emotions = emotions,
            isBusy = isBusy,
            message = message
        )
    }

    private fun persistQuickSetupConfig(config: TtsConfig): TtsConfig? {
        val current = mutableState.value
        if (config == current.persisted) return config
        return when (val result = repository.update(current.persistedVersion, config)) {
            is UpdateResult.Success -> {
                mutableState.value = mutableState.value.copy(
                    persistedVersion = result.snapshot.version,
                    persisted = result.snapshot.value,
                    draft = result.snapshot.value
                )
                result.snapshot.value
            }
            is UpdateResult.VersionConflict -> {
                applySnapshot(repository.read(), "配置冲突，已加载另一界面的最新值")
                null
            }
            is UpdateResult.Invalid -> {
                mutableState.value = mutableState.value.copy(
                    operation = SettingsOperation.IDLE,
                    isBusy = false,
                    validationIssues = result.issues.associate { it.field to it.message },
                    message = "请先修正配置后再检查"
                )
                null
            }
            UpdateResult.PersistenceFailure -> {
                mutableState.value = mutableState.value.copy(
                    operation = SettingsOperation.IDLE,
                    isBusy = false,
                    message = "自动保存失败，请稍后重试"
                )
                null
            }
        }
    }

    private suspend fun startQuickPreview() {
        val draft = mutableState.value.draft
        val generation = ++previewGeneration
        mutableState.value = mutableState.value.copy(
            operation = SettingsOperation.PREVIEWING,
            isBusy = true,
            message = "正在试听…"
        )
        val result = previewController.preview(
            draft.testText,
            draft,
            object : PreviewListener {
                override fun onStarted() = updatePreview(generation, isPreviewing = true)

                override fun onCompleted() = updatePreview(generation, isPreviewing = false)

                override fun onError(error: Throwable) = updatePreview(
                    generation,
                    isPreviewing = false,
                    message = "试听失败：${error.message ?: error::class.java.simpleName}"
                )

                override fun onCancelled(reason: String) = updatePreview(generation, isPreviewing = false)
            }
        )
        if (result.isFailure) {
            updatePreview(
                generation,
                isPreviewing = false,
                message = "试听失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
            )
        }
    }

    private fun applySnapshot(snapshot: ConfigSnapshot, message: String, draft: TtsConfig = snapshot.value) {
        val current = mutableState.value
        val emotions = current.catalog?.characters?.get(draft.character).orEmpty()
        mutableState.value = snapshot.toUiState(
            catalog = current.catalog,
            characters = current.characters,
            emotions = emotions,
            draft = draft,
            message = message
        ).copy(
            serviceStatus = current.serviceStatus,
            serviceStatusMessage = current.serviceStatusMessage,
            connectionSucceeded = current.connectionSucceeded
        )
    }

    private inline fun beginOperation(
        operation: SettingsOperation,
        update: (SettingsUiState) -> SettingsUiState
    ): Boolean {
        val current = mutableState.value
        if (current.operation != SettingsOperation.IDLE) {
            return false
        }
        mutableState.value = update(current).copy(operation = operation)
        return true
    }

    override fun close() {
        controllerJob.cancel()
    }

    private fun ConfigSnapshot.toUiState(
        catalog: CharacterCatalog? = null,
        characters: List<String> = emptyList(),
        emotions: List<String> = emptyList(),
        draft: TtsConfig = value,
        message: String? = null
    ) = SettingsUiState(
        persistedVersion = version,
        persisted = value,
        draft = draft,
        catalog = catalog,
        characters = characters,
        emotions = emotions,
        message = message
    )
}


private fun TtsConfig.copyCoreSettingsFrom(source: TtsConfig): TtsConfig = copy(
    enabled = source.enabled,
    character = source.character,
    emotion = source.emotion,
    useManualVoice = source.useManualVoice,
    fallbackToOriginal = source.fallbackToOriginal
)
