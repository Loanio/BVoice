package dev.breenottshook.ui

import android.content.Context
import dev.breenottshook.api.CharacterCache
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.config.ConfigRepository
import dev.breenottshook.config.ConfigSnapshot
import dev.breenottshook.config.TtsConfig
import dev.breenottshook.config.UpdateResult
import dev.breenottshook.playback.AudioTrackSink
import dev.breenottshook.session.GptSovitsEngine
import dev.breenottshook.session.OriginalCall
import dev.breenottshook.session.TtsCallbacks
import dev.breenottshook.session.TtsInvocation
import dev.breenottshook.session.TtsSessionCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

class ContentProviderSettingsRepository(
    private val delegate: ConfigRepository
) : SettingsRepository {
    override fun observe(): StateFlow<ConfigSnapshot> = delegate.observe()
    override fun read(): ConfigSnapshot = delegate.read()
    override fun update(expectedVersion: Long, config: TtsConfig): UpdateResult =
        delegate.update(expectedVersion, config)
}

class ApiCatalogGateway(
    private val cache: CharacterCache
) : CatalogGateway {
    override suspend fun refresh(baseUrl: String) = cache.getOrFetch(baseUrl, forceRefresh = true)
}

class ApiConnectionTester(
    private val client: GptSovitsClient
) : ConnectionTester {
    override suspend fun test(config: TtsConfig): Result<Unit> = runCatching {
        client.fetchCharacters(config.baseUrl)
        Unit
    }
}

class SessionPreviewController(
    context: Context,
    scope: CoroutineScope,
    client: GptSovitsClient
) : PreviewController {
    @Volatile
    private var activeConfig = TtsConfig()

    private val coordinator = TtsSessionCoordinator(
        scope = scope,
        configProvider = { activeConfig },
        synthesisEngine = GptSovitsEngine(client),
        sinkProvider = { AudioTrackSink(context.applicationContext) }
    )

    override suspend fun preview(
        text: String,
        config: TtsConfig,
        listener: PreviewListener
    ): Result<Unit> = runCatching {
        require(text.isNotBlank()) { "试听文本不能为空" }
        activeConfig = config
        coordinator.submit(
            TtsInvocation(
                text = text,
                originalCall = OriginalCall { },
                callbacks = object : TtsCallbacks {
                    override fun onStarted() = listener.onStarted()
                    override fun onCompleted() = listener.onCompleted()
                    override fun onError(error: Throwable) = listener.onError(error)
                    override fun onCancelled(reason: String) = listener.onCancelled(reason)
                }
            )
        )
        Unit
    }

    override suspend fun stop() {
        coordinator.cancelActive("preview stopped")
    }
}
