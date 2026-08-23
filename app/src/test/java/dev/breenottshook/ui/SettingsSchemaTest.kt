package dev.breenottshook.ui

import dev.breenottshook.config.LogLevel
import dev.breenottshook.config.SynthesisAudioFormat
import dev.breenottshook.config.TextLanguage
import dev.breenottshook.config.TtsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSchemaTest {
    @Test
    fun `schema exposes every persisted configuration field exactly once`() {
        val expectedKeys = setOf(
            "enabled",
            "baseUrl",
            "character",
            "emotion",
            "useManualVoice",
            "manualCharacter",
            "manualEmotion",
            "textLanguage",
            "audioFormat",
            "topK",
            "topP",
            "temperature",
            "batchSize",
            "speed",
            "saveTemp",
            "stream",
            "connectTimeoutMs",
            "readTimeoutMs",
            "fallbackToOriginal",
            "strictMode",
            "forceModulePlayer",
            "logLevel",
            "testText"
        )

        assertEquals(expectedKeys, SettingsSchema.fields.map { it.key }.toSet())
        assertEquals(expectedKeys.size, SettingsSchema.fields.size)
        assertTrue(SettingsSchema.fields.all { it.hostEditorSupported })
    }

    @Test
    fun `schema edits typed values without losing unrelated configuration`() {
        val original = TtsConfig(character = "保留角色", fallbackToOriginal = true)

        val edited = listOf(
            "enabled" to "true",
            "textLanguage" to TextLanguage.JAPANESE.name,
            "audioFormat" to SynthesisAudioFormat.MP3.name,
            "topK" to "12",
            "topP" to "0.75",
            "connectTimeoutMs" to "9000",
            "fallbackToOriginal" to "false",
            "logLevel" to LogLevel.DEBUG.name
        ).fold(original) { config, (key, rawValue) ->
            val result = SettingsSchema.edit(config, key, rawValue)
            assertTrue("$key should be accepted", result is SchemaEditResult.Success)
            (result as SchemaEditResult.Success).config
        }

        assertEquals("保留角色", edited.character)
        assertTrue(edited.enabled)
        assertEquals(TextLanguage.JAPANESE, edited.textLanguage)
        assertEquals(SynthesisAudioFormat.MP3, edited.audioFormat)
        assertEquals(12, edited.topK)
        assertEquals(0.75, edited.topP, 0.0)
        assertEquals(9_000L, edited.connectTimeoutMs)
        assertTrue(!edited.fallbackToOriginal)
        assertEquals(LogLevel.DEBUG, edited.logLevel)
    }

    @Test
    fun `schema rejects malformed typed values and unknown keys`() {
        assertEquals(
            SchemaEditResult.Invalid("topK", "请输入整数"),
            SettingsSchema.edit(TtsConfig(), "topK", "twelve")
        )
        assertEquals(
            SchemaEditResult.Invalid("missing", "未知配置项"),
            SettingsSchema.edit(TtsConfig(), "missing", "value")
        )
    }

    @Test
    fun `enabling manual voice seeds empty manual fields from current selection`() {
        val result = SettingsSchema.edit(
            TtsConfig(character = "花火", emotion = "开心"),
            "useManualVoice",
            "true"
        )

        assertTrue(result is SchemaEditResult.Success)
        val config = (result as SchemaEditResult.Success).config
        assertEquals("花火", config.manualCharacter)
        assertEquals("开心", config.manualEmotion)
    }
}
