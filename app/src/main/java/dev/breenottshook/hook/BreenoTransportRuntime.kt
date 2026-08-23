package dev.breenottshook.hook

import android.content.Context
import android.os.Bundle
import android.util.Log
import dev.breenottshook.api.AndroidApiDiagnostics
import dev.breenottshook.api.GptSovitsClient
import dev.breenottshook.config.ConfigContract
import dev.breenottshook.config.LogLevel
import dev.breenottshook.config.HookConfigCache
import dev.breenottshook.playback.AudioTrackSink
import dev.breenottshook.session.GptSovitsEngine
import dev.breenottshook.session.OriginalCall
import dev.breenottshook.session.TtsCallbacks
import dev.breenottshook.session.TtsInvocation
import dev.breenottshook.session.TtsSessionCoordinator
import java.lang.reflect.Method
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class BreenoTransportRuntime(
    private val context: Context,
    private val configCache: HookConfigCache,
    private val scope: CoroutineScope,
    private val status: HookStatusPublisher
) {
    private data class BypassKey(val socketIdentity: Int, val payload: String)

    private val bypass = ConcurrentHashMap.newKeySet<BypassKey>()
    private val coordinator = TtsSessionCoordinator(
        scope = scope,
        configProvider = { configCache.current().value },
        synthesisEngine = GptSovitsEngine(GptSovitsClient(OkHttpClient(), diagnostics = AndroidApiDiagnostics)),
        sinkProvider = { AudioTrackSink(context.applicationContext) }
    )

    fun onSend(socket: Any, originalMethod: Method, payload: String): Boolean? {
        val bypassKey = BypassKey(System.identityHashCode(socket), payload)
        if (bypass.remove(bypassKey)) return null

        configCache.refresh()
        val config = configCache.current().value
        val url = SocketRequestUrl.resolve(socket) ?: return null
        if (config.logLevel == LogLevel.DEBUG) {
            Log.d("BreenoTTSHook", "websocket: ${HookDiagnostics.websocket(url, payload)}")
        }
        val decision = TransportFallbackPolicy.decide(url, payload, config)
        if (decision !is TransportDecision.Intercept) return null

        status.publish("intercepted", HookDiagnostics.utterance(decision.request.text))
        scope.launch {
            coordinator.submit(
                TtsInvocation(
                    text = decision.request.text,
                    originalCall = OriginalCall {
                        bypass += bypassKey
                        runCatching { originalMethod.invoke(socket, payload) }
                            .onFailure {
                                bypass.remove(bypassKey)
                                status.publish("fallback_failed", it::class.java.simpleName)
                            }
                    },
                    callbacks = object : TtsCallbacks {
                        override fun onStarted() {
                            status.publish("playing", HookDiagnostics.utterance(decision.request.text))
                        }

                        override fun onCompleted() {
                            status.publish("completed", HookDiagnostics.utterance(decision.request.text))
                        }

                        override fun onError(error: Throwable) {
                            status.publish("failed", error::class.java.simpleName)
                        }

                        override fun onCancelled(reason: String) {
                            status.publish("cancelled", reason.take(120))
                        }
                    }
                )
            )
        }
        return false
    }

    fun cancelActive(reason: String) {
        scope.launch { coordinator.cancelActive(reason) }
    }

    fun shouldPermitCleartext(host: String): Boolean {
        configCache.refresh()
        val config = configCache.current().value
        if (!config.enabled) return false
        return runCatching {
            val uri = URI(config.baseUrl)
            uri.scheme.equals("http", ignoreCase = true) &&
                uri.host.equals(host, ignoreCase = true)
        }.getOrDefault(false)
    }
}

object SocketRequestUrl {
    fun resolve(socket: Any): String? = runCatching {
        val requestFields = socket.javaClass.declaredFields.filter {
            it.type.name == "okhttp3.Request"
        }
        if (requestFields.size != 1) return null
        val request = requestFields.single().apply { isAccessible = true }.get(socket)
            ?: return null
        val urlMethod = request.javaClass.methods.singleOrNull {
            it.name == "url" && it.parameterCount == 0
        } ?: return null
        urlMethod.invoke(request)?.toString()
    }.getOrNull()
}

class HookStatusPublisher(
    private val context: Context
) {
    fun publish(state: String, detail: String) {
        val redactedDetail = detail.replace(Regex("[\\r\\n]"), " ").take(300)
        val payload = "state=$state;detail=$redactedDetail;time=${System.currentTimeMillis()}"
        runCatching {
            context.contentResolver.call(
                ConfigContract.URI,
                ConfigContract.METHOD_PUT_HOOK_STATUS,
                null,
                Bundle().apply { putString(ConfigContract.KEY_HOOK_STATUS, payload) }
            )
        }.onSuccess {
            Log.i(
                "BreenoTTSHook",
                "status published: $payload persisted=${it?.getBoolean(ConfigContract.KEY_RESULT)}"
            )
        }.onFailure {
            Log.e("BreenoTTSHook", "status publish failed: $payload", it)
        }
    }
}
