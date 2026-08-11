package dev.breenottshook.config

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ConfigCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(config: TtsConfig): String = json.encodeToString(config)

    fun decode(encoded: String): TtsConfig = json.decodeFromString(encoded)
}
