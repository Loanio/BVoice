package dev.breenottshook.ui

import dev.breenottshook.api.CatalogState
import dev.breenottshook.api.CharacterCatalog
import dev.breenottshook.config.ConfigSnapshot
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsOperationControllerTest {
    @Test
    fun `one controller blocks every conflicting operation while connection is running`() = runTest {
        val repository = RecordingRepository(ConfigSnapshot(0, TtsConfig(enabled = false)))
        val catalog = RecordingCatalogGateway()
        val connection = SuspendedConnectionTester()
        val controller = SettingsOperationController(
            repository = repository,
            catalogGateway = catalog,
            connectionTester = connection,
            previewController = NoOpPreviewController,
            operationScope = this
        )

        controller.testConnection()
        advanceUntilIdle()
        controller.refreshCatalog()
        controller.updateCoreSetting { it.copy(enabled = true) }
        advanceUntilIdle()

        assertEquals(SettingsOperation.TESTING_CONNECTION, controller.state.value.operation)
        assertEquals(0, catalog.calls)
        assertEquals(0, repository.updateCalls)
        assertFalse(controller.state.value.draft.enabled)

        connection.complete(Result.success(Unit))
        advanceUntilIdle()
        controller.close()
    }

    private class RecordingRepository(initial: ConfigSnapshot) : SettingsRepository {
        private val snapshots = MutableStateFlow(initial)
        var updateCalls = 0

        override fun observe(): StateFlow<ConfigSnapshot> = snapshots
        override fun read(): ConfigSnapshot = snapshots.value

        override fun update(expectedVersion: Long, config: TtsConfig): UpdateResult {
            updateCalls++
            val next = ConfigSnapshot(expectedVersion + 1, config)
            snapshots.value = next
            return UpdateResult.Success(next)
        }
    }

    private class RecordingCatalogGateway : CatalogGateway {
        var calls = 0

        override suspend fun refresh(baseUrl: String): CatalogState {
            calls++
            return CatalogState.Fresh(CharacterCatalog(emptyMap()))
        }
    }

    private class SuspendedConnectionTester : ConnectionTester {
        private val result = CompletableDeferred<Result<Unit>>()

        override suspend fun test(config: TtsConfig): Result<Unit> = result.await()

        fun complete(value: Result<Unit>) {
            result.complete(value)
        }
    }

    private object NoOpPreviewController : PreviewController {
        override suspend fun preview(
            text: String,
            config: TtsConfig,
            listener: PreviewListener
        ): Result<Unit> = Result.success(Unit)

        override suspend fun stop() = Unit
    }
}
