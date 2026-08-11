package dev.breenottshook.hook

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

data class ExtractedTtsRequest(
    val text: String,
    val requestId: String? = null
)

object TtsPayloadExtractor {
    private val json = Json { ignoreUnknownKeys = true }

    fun extract(payload: String): ExtractedTtsRequest? {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull()
            ?: return null
        val text = root.string("text")
            ?: root.objectValue("data")?.string("text")
            ?: root.objectValue("params")?.string("text")
            ?: root.objectValue("params")?.string("content")
            ?: return null
        if (text.isBlank()) return null
        return ExtractedTtsRequest(
            text = text,
            requestId = root.string("requestId")
                ?: root.objectValue("data")?.string("requestId")
        )
    }

    private fun JsonObject.string(key: String): String? =
        get(key)?.runCatching { jsonPrimitive.contentOrNull }?.getOrNull()

    private fun JsonObject.objectValue(key: String): JsonObject? =
        get(key)?.runCatching { jsonObject }?.getOrNull()
}
