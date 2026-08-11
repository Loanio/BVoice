package dev.breenottshook.config

import kotlinx.serialization.Serializable

@Serializable
data class TtsConfig(
    val enabled: Boolean = false,
    val baseUrl: String = DEFAULT_BASE_URL,
    val character: String = "",
    val emotion: String = "default",
    val useManualVoice: Boolean = false,
    val manualCharacter: String = "",
    val manualEmotion: String = "",
    val textLanguage: TextLanguage = TextLanguage.MULTILINGUAL,
    val audioFormat: SynthesisAudioFormat = SynthesisAudioFormat.WAV,
    val topK: Int = 5,
    val topP: Double = 1.0,
    val temperature: Double = 1.0,
    val batchSize: Int = 1,
    val speed: Double = 1.0,
    val saveTemp: Boolean = false,
    val stream: Boolean = true,
    val connectTimeoutMs: Long = 5_000,
    val readTimeoutMs: Long = 60_000,
    val fallbackToOriginal: Boolean = true,
    val strictMode: Boolean = false,
    val forceModulePlayer: Boolean = false,
    val logLevel: LogLevel = LogLevel.INFO,
    val testText: String = "你好，这是连接测试。"
) {
    val effectiveCharacter: String
        get() = manualCharacter.takeIf { useManualVoice && it.isNotBlank() } ?: character

    val effectiveEmotion: String
        get() = manualEmotion.takeIf { useManualVoice && it.isNotBlank() } ?: emotion

    companion object {
        const val DEFAULT_BASE_URL = "http://47.111.184.220:5000/"
    }
}

@Serializable
enum class TextLanguage(val apiValue: String) {
    CHINESE("中文"),
    ENGLISH("英文"),
    JAPANESE("日文"),
    CHINESE_ENGLISH("中英混合"),
    JAPANESE_ENGLISH("日英混合"),
    MULTILINGUAL("多语种混合")
}

@Serializable
enum class SynthesisAudioFormat(val apiValue: String) {
    WAV("wav"),
    PCM("pcm"),
    MP3("mp3"),
    OGG("ogg")
}

@Serializable
enum class LogLevel {
    ERROR,
    INFO,
    DEBUG
}

data class ConfigSnapshot(
    val version: Long,
    val value: TtsConfig
)
