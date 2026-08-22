package dev.breenottshook.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.breenottshook.api.CatalogState
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.ConfigSnapshot
import dev.breenottshook.config.ConfigValidator
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
import dev.breenottshook.config.ValidationResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

class SettingsViewModel(
    private val repository: SettingsRepository,
    private val catalogGateway: CatalogGateway,
    private val connectionTester: ConnectionTester,
    private val previewController: PreviewController
) : ViewModel() {
    private var previewGeneration = 0L
    private val initial = repository.read()
    private val mutableState = MutableStateFlow(initial.toUiState())
    val state: StateFlow<SettingsUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
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
        val nextDraft = transform(current.draft)
        mutableState.value = current.copy(
            draft = nextDraft,
            validationIssues = emptyMap(),
            connectionSucceeded = null,
            message = null,
            emotions = current.catalog?.characters?.get(nextDraft.character).orEmpty()
        )
    }

    fun loadInitialCatalog() {
        if (mutableState.value.catalog != null) {
            return
        }
        refreshCatalog()
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
        viewModelScope.launch {
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
            is ValidationResult.Valid -> viewModelScope.launch {
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
        viewModelScope.launch {
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
        viewModelScope.launch {
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

    fun preview() {
        val draft = mutableState.value.draft
        val generation = ++previewGeneration
        viewModelScope.launch {
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
        viewModelScope.launch {
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
        val current = mutableState.value
        mutableState.value = current.copy(
            draft = TtsConfig(),
            validationIssues = emptyMap(),
            connectionSucceeded = null,
            message = "已恢复默认草稿，保存后生效"
        )
    }

    private fun applyCatalog(catalog: CharacterCatalog, message: String?) {
        val current = mutableState.value
        val characters = catalog.characters.keys.sorted()
        val selectedCharacter = current.draft.character
            .takeIf { it in catalog.characters }
            ?: characters.firstOrNull().orEmpty()
        val emotions = catalog.characters[selectedCharacter].orEmpty()
        val selectedEmotion = current.draft.emotion
            .takeIf { it in emotions }
            ?: emotions.firstOrNull().orEmpty()
        mutableState.value = current.copy(
            draft = current.draft.copy(
                character = selectedCharacter,
                emotion = selectedEmotion
            ),
            operation = SettingsOperation.IDLE,
            catalog = catalog,
            characters = characters,
            emotions = emotions,
            isBusy = false,
            message = message
        )
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
