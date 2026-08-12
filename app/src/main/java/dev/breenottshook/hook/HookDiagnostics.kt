package dev.breenottshook.hook

import java.nio.charset.StandardCharsets
import java.net.URI
import java.security.MessageDigest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

object HookDiagnostics {
    private val json = Json { ignoreUnknownKeys = true }

    fun utterance(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
            .take(12)
        return "chars=${text.length},sha256=$digest"
    }

    fun websocket(url: String, payload: String): String {
        val uri = runCatching { URI(url) }.getOrNull()
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
        val keys = root?.keys?.sorted()?.joinToString("|").orEmpty()
        val dataKeys = root?.get("data")?.runCatching { jsonObject.keys.sorted() }
            ?.getOrNull()?.joinToString("|").orEmpty()
        return buildString {
            append("scheme=${uri?.scheme.orEmpty()},host=${uri?.host.orEmpty()},path=${uri?.path.orEmpty()}")
            append(",chars=${payload.length},keys=$keys")
            if (dataKeys.isNotEmpty()) append(",dataKeys=$dataKeys")
        }
    }

    fun stream(chunks: List<String>): String {
        val source = chunks.joinToString(separator = "")
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(source.toByteArray(StandardCharsets.UTF_8))
            .joinToString(separator = "") { "%02x".format(it) }
            .take(12)
        return "chunks=${chunks.size},chars=${source.length},sha256=$digest"
    }
}
