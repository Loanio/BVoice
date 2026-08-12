package dev.breenottshook.ui

import dev.breenottshook.api.CatalogState
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.ConfigSnapshot
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
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
    fun `editing draft does not persist until save then shared version increments`() = runTest(dispatcher) {
        val repository = FakeSettingsRepository(ConfigSnapshot(3, TtsConfig(character = "原音色")))
        val viewModel = viewModel(repository = repository)

        viewModel.edit { it.copy(character = "新音色") }

        assertEquals("原音色", repository.snapshot.value.character)
        assertEquals("新音色", viewModel.state.value.draft.character)
        assertTrue(viewModel.state.value.hasUnsavedChanges)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(4, repository.snapshot.version)
        assertEquals("新音色", repository.snapshot.value.character)
        assertFalse(viewModel.state.value.hasUnsavedChanges)
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
    fun `connection and preview use current unsaved draft values`() = runTest(dispatcher) {
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
    fun `preview completion error and cancellation reset preview state`() = runTest(dispatcher) {
        val preview = RecordingPreviewController()
        val viewModel = viewModel(
            repository = FakeSettingsRepository(ConfigSnapshot(0, TtsConfig())),
            preview = preview
        )

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        assertTrue(viewModel.state.value.isPreviewing)

        preview.listener?.onCompleted()
        assertFalse(viewModel.state.value.isPreviewing)

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        preview.listener?.onError(IllegalStateException("decoder failed"))
        assertFalse(viewModel.state.value.isPreviewing)
        assertTrue(viewModel.state.value.message.orEmpty().contains("decoder failed"))

        viewModel.preview()
        advanceUntilIdle()
        preview.listener?.onStarted()
        preview.listener?.onCancelled("interrupted")
        assertFalse(viewModel.state.value.isPreviewing)
    }

    private fun viewModel(
        repository: FakeSettingsRepository,
        catalog: CatalogGateway = FakeCatalogGateway(
            CatalogState.Fresh(CharacterCatalog(emptyMap()))
        ),
        connection: ConnectionTester = RecordingConnectionTester(),
        preview: PreviewController = RecordingPreviewController()
    ) = SettingsViewModel(repository, catalog, connection, preview)

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

    private class RecordingConnectionTester : ConnectionTester {
        var lastConfig: TtsConfig? = null
        override suspend fun test(config: TtsConfig): Result<Unit> {
            lastConfig = config
            return Result.success(Unit)
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
}
