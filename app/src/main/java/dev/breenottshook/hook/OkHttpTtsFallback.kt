package dev.breenottshook.hook

import dev.breenottshook.config.TtsConfig
import java.net.URI

object OkHttpTtsFallback {
    const val TARGET_ENDPOINT = "wss://openapi-slp.heytapmobi.com/tts/ws"

    fun isTargetUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("wss", ignoreCase = true) &&
            uri.host.equals("openapi-slp.heytapmobi.com", ignoreCase = true) &&
            (uri.port == -1 || uri.port == 443) &&
            uri.path == "/tts/ws" &&
            uri.fragment == null
    }.getOrDefault(false)
}

sealed interface TransportDecision {
    data object PassThrough : TransportDecision
    data class Intercept(
        val request: ExtractedTtsRequest,
        val config: TtsConfig
    ) : TransportDecision
}

object TransportFallbackPolicy {
    fun decide(url: String, payload: String, config: TtsConfig): TransportDecision {
        if (!config.enabled || !OkHttpTtsFallback.isTargetUrl(url)) {
            return TransportDecision.PassThrough
        }
        val request = TtsPayloadExtractor.extract(payload)
            ?: return TransportDecision.PassThrough
        return TransportDecision.Intercept(request, config)
    }
}
