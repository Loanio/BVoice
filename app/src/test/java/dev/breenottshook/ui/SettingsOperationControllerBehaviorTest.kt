package dev.breenottshook.ui

import dev.breenottshook.api.CatalogState
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.ConfigSnapshot
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsOperationControllerBehaviorTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `editing advanced setting automatically persists without a success toast message`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(3, TtsConfig(character = "原音色")))
        val viewModel = viewModel(repository = repository)

        viewModel.edit { it.copy(character = "新音色") }
        advanceUntilIdle()

        assertEquals(4, repository.snapshot.version)
        assertEquals("新音色", viewModel.state.value.draft.character)
        assertEquals("新音色", repository.snapshot.value.character)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `temporarily invalid input remains silent while the user is typing`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig()))
        val viewModel = viewModel(repository = repository)

        viewModel.edit { it.copy(baseUrl = "h") }
        advanceUntilIdle()

        assertEquals(0, repository.updateCalls)
        assertTrue(viewModel.state.value.validationIssues.containsKey("baseUrl"))
        assertNull(viewModel.state.value.message)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun `validation errors block repository write`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig()))
        val viewModel = viewModel(repository = repository)

        viewModel.edit { it.copy(baseUrl = "file:///tmp/voice.wav", speed = 0.0) }
        viewModel.save()
        advanceUntilIdle()

        assertEquals(0, repository.updateCalls)
        assertEquals(setOf("baseUrl", "speed"), viewModel.state.value.validationIssues.keys)
        assertTrue(viewModel.state.value.hasUnsavedChanges)
    }

    @Test
    fun `version conflict reloads latest shared config and reports conflict`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(2, TtsConfig(character = "旧值")))
        val viewModel = viewModel(repository = repository)
        viewModel.edit { it.copy(character = "本地草稿") }
        repository.conflictWith = ConfigSnapshot(5, TtsConfig(character = "另一界面的值"))

        viewModel.save()
        advanceUntilIdle()

        assertEquals(5, viewModel.state.value.persistedVersion)
        assertEquals("另一界面的值", viewModel.state.value.draft.character)
        assertTrue(viewModel.state.value.message?.contains("冲突") == true)
    }

    @Test
    fun `catalog refresh preserves manual values and resets invalid selected emotion`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(
            ConfigSnapshot(
                1,
                TtsConfig(
                    character = "花火",
                    emotion = "开心",
                    useManualVoice = true,
                    manualCharacter = "自定义角色",
                    manualEmotion = "自定义情感"
                )
            )
        )
        val catalog = FakeCatalogGateway(
            CatalogState.Fresh(CharacterCatalog(mapOf("花火" to listOf("平静", "生气"))))
        )
        val viewModel = viewModel(repository = repository, catalog = catalog)

        viewModel.refreshCatalog()
        advanceUntilIdle()

        val draft = viewModel.state.value.draft
        assertEquals("自定义角色", draft.manualCharacter)
        assertEquals("自定义情感", draft.manualEmotion)
        assertEquals("平静", draft.emotion)
        assertEquals(listOf("花火"), viewModel.state.value.characters)
        assertEquals(listOf("平静", "生气"), viewModel.state.value.emotions)
    }

    @Test
    fun `catalog refresh keeps selected emotion when still valid`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(
            ConfigSnapshot(1, TtsConfig(character = "花火", emotion = "开心"))
        )
        val catalog = FakeCatalogGateway(
            CatalogState.Fresh(CharacterCatalog(mapOf("花火" to listOf("平静", "开心"))))
        )
        val viewModel = viewModel(repository = repository, catalog = catalog)

        viewModel.refreshCatalog()
        advanceUntilIdle()

        assertEquals("开心", viewModel.state.value.draft.emotion)
    }

    @Test
    fun `initial catalog loading refreshes once and populates choices`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(1, TtsConfig(character = "花火")))
        val catalog = RecordingCatalogGateway(
            CatalogState.Fresh(
                CharacterCatalog(
                    mapOf(
                        "花火" to listOf("平静", "开心"),
                        "青山" to listOf("default")
                    )
                )
            )
        )
        val viewModel = viewModel(repository = repository, catalog = catalog)

        viewModel.loadInitialCatalog()
        advanceUntilIdle()
        viewModel.loadInitialCatalog()
        advanceUntilIdle()

        assertEquals(1, catalog.calls)
        assertEquals(listOf("花火", "青山"), viewModel.state.value.characters)
        assertEquals(listOf("平静", "开心"), viewModel.state.value.emotions)
    }

    @Test
    fun `core setting persists immediately and clears unsaved state`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(3, TtsConfig(enabled = false)))
        val viewModel = viewModel(repository = repository)

        viewModel.updateCoreSetting { it.copy(enabled = true) }
        advanceUntilIdle()

        assertTrue(repository.snapshot.value.enabled)
        assertEquals(4, repository.snapshot.version)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
        assertTrue(viewModel.state.value.draft.enabled)
    }

    @Test
    fun `core setting update is ignored while connection test is suspended`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig(enabled = false)))
        val connection = SuspendedConnectionTester()
        val viewModel = viewModel(repository = repository, connection = connection)

        viewModel.testConnection()
        advanceUntilIdle()
        viewModel.updateCoreSetting { it.copy(enabled = true) }
        advanceUntilIdle()

        assertEquals(0, repository.updateCalls)
        assertFalse(viewModel.state.value.draft.enabled)
        assertFalse(repository.snapshot.value.enabled)

        connection.complete(Result.success(Unit))
        advanceUntilIdle()
    }

    @Test
    fun `connection and preview use the current automatic-save values`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig()))
        val connection = RecordingConnectionTester()
        val preview = RecordingPreviewController()
        val viewModel = viewModel(repository, connection = connection, preview = preview)
        viewModel.edit {
            it.copy(
                baseUrl = "https://tts.example.test/",
                character = "预览音色",
                testText = "固定测试文本"
            )
        }

        viewModel.testConnection()
        advanceUntilIdle()
        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)

        viewModel.preview()
        advanceUntilIdle()

        assertEquals("https://tts.example.test/", connection.lastConfig?.baseUrl)
        assertEquals("预览音色", preview.lastConfig?.character)
        assertEquals("固定测试文本", preview.lastText)
        assertTrue(viewModel.state.value.connectionSucceeded == true)

        viewModel.stopPreview()
        advanceUntilIdle()
        assertEquals(1, preview.stopCalls)
    }

    @Test
    fun `successful connection test leaves available service status and idle operation`() = runTest(dispatcher) {
        val viewModel = viewModel(
            repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig())),
            connection = RecordingConnectionTester()
        )

        viewModel.testConnection()
        advanceUntilIdle()

        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
        assertEquals(ServiceStatus.AVAILABLE, viewModel.state.value.serviceStatus)
        assertEquals("连接成功", viewModel.state.value.serviceStatusMessage)
    }

    @Test
    fun `quick setup saves then checks service refreshes catalog and starts preview`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(3, TtsConfig(character = "旧音色")))
        val events = mutableListOf<String>()
        val catalog = object : CatalogGateway {
            override suspend fun refresh(baseUrl: String): CatalogState {
                events += "catalog"
                return CatalogState.Fresh(CharacterCatalog(mapOf("花火" to listOf("开心"))))
            }
        }
        val connection = object : ConnectionTester {
            override suspend fun test(config: TtsConfig): Result<Unit> {
                events += "connection"
                return Result.success(Unit)
            }
        }
        val preview = object : PreviewController {
            override suspend fun preview(
                text: String,
                config: TtsConfig,
                listener: PreviewListener
            ): Result<Unit> {
                events += "preview"
                listener.onStarted()
                return Result.success(Unit)
            }

            override suspend fun stop() = Unit
        }
        val viewModel = viewModel(repository, catalog, connection, preview)

        viewModel.edit { it.copy(baseUrl = "https://tts.example.test/", character = "花火") }
        viewModel.quickSetup()
        advanceUntilIdle()

        assertEquals(4, repository.snapshot.version)
        assertEquals(listOf("connection", "catalog", "preview"), events)
        assertEquals(ServiceStatus.AVAILABLE, viewModel.state.value.serviceStatus)
        assertTrue(viewModel.state.value.isPreviewing)
    }

    @Test
    fun `editing base url does not check service until the field explicitly loses focus`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig()))
        val events = mutableListOf<String>()
        val connection = RecordingConnectionTester().also { tester ->
            tester.onTest = { events += "connection" }
        }
        val catalog = RecordingCatalogGateway().also { gateway ->
            gateway.onRefresh = { events += "catalog" }
        }
        val preview = RecordingPreviewController()
        val viewModel = viewModel(repository, catalog, connection, preview)

        viewModel.edit { it.copy(baseUrl = "https://tts.example.test/") }
        advanceUntilIdle()

        assertEquals(emptyList<String>(), events)
        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
        assertFalse(viewModel.state.value.isBusy)
        assertFalse(viewModel.state.value.isPreviewing)

        viewModel.testConnectionAndRefresh()
        advanceUntilIdle()

        assertEquals(listOf("connection", "catalog"), events)
        assertEquals(ServiceStatus.AVAILABLE, viewModel.state.value.serviceStatus)
    }

    @Test
    fun `initialization checks service and refreshes catalog without starting preview`() = runTest(dispatcher) {
        val events = mutableListOf<String>()
        val viewModel = viewModel(
            repository = FakeSettingsRepository(
                ConfigSnapshot(0, TtsConfig(baseUrl = "https://tts.example.test/"))
            ),
            catalog = RecordingCatalogGateway().also { it.onRefresh = { events += "catalog" } },
            connection = RecordingConnectionTester().also { it.onTest = { events += "connection" } }
        )

        viewModel.initialize()
        advanceUntilIdle()

        assertEquals(listOf("connection", "catalog"), events)
        assertFalse(viewModel.state.value.isPreviewing)
    }

    @Test
    fun `refresh is ignored while connection test is suspended`() = runTest(dispatcher) {
        val connection = SuspendedConnectionTester()
        val catalog = RecordingCatalogGateway()
        val viewModel = viewModel(
            repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig())),
            catalog = catalog,
            connection = connection
        )

        viewModel.testConnection()
        advanceUntilIdle()
        assertEquals(SettingsOperation.TESTING_CONNECTION, viewModel.state.value.operation)
        assertEquals(ServiceStatus.CHECKING, viewModel.state.value.serviceStatus)

        viewModel.refreshCatalog()
        advanceUntilIdle()

        assertEquals(0, catalog.calls)

        connection.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
        assertEquals(ServiceStatus.AVAILABLE, viewModel.state.value.serviceStatus)
    }

    @Test
    fun `refresh is ignored after preview starts until preview completes`() = runTest(dispatcher) {
        val preview = SuspendedPreviewController()
        val catalog = RecordingCatalogGateway()
        val viewModel = viewModel(
            repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig())),
            catalog = catalog,
            preview = preview
        )

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()

        assertEquals(SettingsOperation.PREVIEWING, viewModel.state.value.operation)
        assertTrue(viewModel.state.value.isPreviewing)

        viewModel.refreshCatalog()
        advanceUntilIdle()

        assertEquals(0, catalog.calls)

        preview.listener?.onCompleted()
        preview.complete(Result.success(Unit))
        advanceUntilIdle()

        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
    }

    @Test
    fun `preview terminal callbacks and stop reset operation from previewing to idle`() = runTest(dispatcher) {
        val preview = RecordingPreviewController()
        val viewModel = viewModel(
            repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig())),
            preview = preview
        )

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        assertTrue(viewModel.state.value.isPreviewing)
        assertEquals(SettingsOperation.PREVIEWING, viewModel.state.value.operation)

        preview.listener?.onCompleted()
        assertFalse(viewModel.state.value.isPreviewing)
        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        assertEquals(SettingsOperation.PREVIEWING, viewModel.state.value.operation)
        preview.listener?.onError(IllegalStateException("decoder failed"))
        assertFalse(viewModel.state.value.isPreviewing)
        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
        assertTrue(viewModel.state.value.message.orEmpty().contains("decoder failed"))

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        assertEquals(SettingsOperation.PREVIEWING, viewModel.state.value.operation)
        preview.listener?.onCancelled("interrupted")
        assertFalse(viewModel.state.value.isPreviewing)
        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        assertEquals(SettingsOperation.PREVIEWING, viewModel.state.value.operation)

        viewModel.stopPreview()
        advanceUntilIdle()

        assertEquals(SettingsOperation.IDLE, viewModel.state.value.operation)
        assertFalse(viewModel.state.value.isPreviewing)
    }

    private fun viewModel(
        repository: FakeSettingsRepository,
        catalog: CatalogGateway = FakeCatalogGateway(
            CatalogState.Fresh(CharacterCatalog(emptyMap()))
        ),
        connection: ConnectionTester = RecordingConnectionTester(),
        preview: PreviewController = RecordingPreviewController()
    ) = SettingsOperationController(repository, catalog, connection, preview)

    private class FakeSettingsRepository(initial: ConfigSnapshot) : SettingsRepository {
        private val flow = MutableStateFlow(initial)
        var snapshot: ConfigSnapshot = initial
            private set
        var updateCalls = 0
        var conflictWith: ConfigSnapshot? = null

        override fun observe(): StateFlow<ConfigSnapshot> = flow

        override fun read(): ConfigSnapshot = snapshot

        override fun update(expectedVersion: Long, config: TtsConfig): UpdateResult {
            updateCalls++
            conflictWith?.let {
                snapshot = it
                flow.value = it
                return UpdateResult.VersionConflict(it.version)
            }
            snapshot = ConfigSnapshot(expectedVersion + 1, config)
            flow.value = snapshot
            return UpdateResult.Success(snapshot)
        }
    }

    private class FakeCatalogGateway(private val result: CatalogState) : CatalogGateway {
        override suspend fun refresh(baseUrl: String): CatalogState = result
    }

    private class RecordingCatalogGateway(
        private val result: CatalogState = CatalogState.Fresh(CharacterCatalog(emptyMap()))
    ) : CatalogGateway {
        var calls = 0
        var onRefresh: (() -> Unit)? = null

        override suspend fun refresh(baseUrl: String): CatalogState {
            calls++
            onRefresh?.invoke()
            return result
        }
    }

    private class RecordingConnectionTester : ConnectionTester {
        var lastConfig: TtsConfig? = null
        var onTest: (() -> Unit)? = null
        override suspend fun test(config: TtsConfig): Result<Unit> {
            lastConfig = config
            onTest?.invoke()
            return Result.success(Unit)
        }
    }

    private class SuspendedConnectionTester : ConnectionTester {
        var lastConfig: TtsConfig? = null
        private val completion = CompletableDeferred<Result<Unit>>()

        override suspend fun test(config: TtsConfig): Result<Unit> {
            lastConfig = config
            return completion.await()
        }

        fun complete(result: Result<Unit>) {
            completion.complete(result)
        }
    }

    private class RecordingPreviewController : PreviewController {
        var lastText: String? = null
        var lastConfig: TtsConfig? = null
        var stopCalls = 0
        var listener: PreviewListener? = null

        override suspend fun preview(
            text: String,
            config: TtsConfig,
            listener: PreviewListener
        ): Result<Unit> {
            lastText = text
            lastConfig = config
            this.listener = listener
            return Result.success(Unit)
        }

        override suspend fun stop() {
            stopCalls++
        }
    }

    private class SuspendedPreviewController : PreviewController {
        var listener: PreviewListener? = null
        private val completion = CompletableDeferred<Result<Unit>>()

        override suspend fun preview(
            text: String,
            config: TtsConfig,
            listener: PreviewListener
        ): Result<Unit> {
            this.listener = listener
            return completion.await()
        }

        override suspend fun stop() = Unit

        fun complete(result: Result<Unit>) {
            completion.complete(result)
        }
    }
}
