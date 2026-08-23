package dev.breenottshook.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfigValidatorTest {

    @Test
    fun `normalizes base URL with one trailing slash`() {
        val result = ConfigValidator.validate(
            TtsConfig(baseUrl = "http://tts.example.test:5000")
        )

        assertEquals(
            "http://tts.example.test:5000/",
            (result as ValidationResult.Valid).value.baseUrl
        )
    }

    @Test
    fun `starts with an empty service address`() {
        assertEquals("", TtsConfig().baseUrl)
        assertTrue(ConfigValidator.validate(TtsConfig()) is ValidationResult.Valid)
    }

    @Test
    fun `rejects non HTTP base URL`() {
        assertInvalid("baseUrl", TtsConfig(baseUrl = "file:///data/local/tmp/tts"))
    }

    @Test
    fun `rejects zero speed`() {
        assertInvalid("speed", TtsConfig(speed = 0.0))
    }

    @Test
    fun `rejects batch size below one`() {
        assertInvalid("batchSize", TtsConfig(batchSize = 0))
    }

    @Test
    fun `rejects top p outside inclusive probability range`() {
        assertInvalid("topP", TtsConfig(topP = 1.01))
        assertInvalid("topP", TtsConfig(topP = -0.01))
    }

    @Test
    fun `rejects nonpositive temperature`() {
        assertInvalid("temperature", TtsConfig(temperature = 0.0))
    }

    @Test
    fun `rejects timeout outside one second to two minutes`() {
        assertInvalid("connectTimeoutMs", TtsConfig(connectTimeoutMs = 999))
        assertInvalid("readTimeoutMs", TtsConfig(readTimeoutMs = 120_001))
    }

    @Test
    fun `accepts timeout boundaries`() {
        assertTrue(
            ConfigValidator.validate(
                TtsConfig(connectTimeoutMs = 1_000, readTimeoutMs = 120_000)
            ) is ValidationResult.Valid
        )
    }

    @Test
    fun `codec round trip preserves every user setting`() {
        val expected = TtsConfig(
            enabled = true,
            baseUrl = "https://tts.example.test:8443/api/",
            character = "花火",
            emotion = "平静",
            useManualVoice = true,
            manualCharacter = "自定义角色",
            manualEmotion = "自定义情感",
            textLanguage = TextLanguage.CHINESE,
            audioFormat = SynthesisAudioFormat.WAV,
            topK = 8,
            topP = 0.75,
            temperature = 0.9,
            batchSize = 2,
            speed = 1.15,
            saveTemp = true,
            stream = false,
            connectTimeoutMs = 7_000,
            readTimeoutMs = 90_000,
            fallbackToOriginal = false,
            strictMode = true,
            forceModulePlayer = true,
            logLevel = LogLevel.DEBUG,
            testText = "配置往返测试"
        )

        assertEquals(expected, ConfigCodec.decode(ConfigCodec.encode(expected)))
    }

    private fun assertInvalid(field: String, config: TtsConfig) {
        val result = ConfigValidator.validate(config)
        assertTrue("Expected invalid result for $field but got $result", result is ValidationResult.Invalid)
        assertTrue((result as ValidationResult.Invalid).issues.any { it.field == field })
    }
}
