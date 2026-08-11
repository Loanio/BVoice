package dev.breenottshook.hook

import dev.breenottshook.config.TtsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportFallbackPolicyTest {
    @Test
    fun `intercepts only enabled target TTS payload`() {
        val result = TransportFallbackPolicy.decide(
            url = OkHttpTtsFallback.TARGET_ENDPOINT,
            payload = """{"text":"需要替换的播报"}""",
            config = TtsConfig(enabled = true, character = "花火")
        )

        assertTrue(result is TransportDecision.Intercept)
        result as TransportDecision.Intercept
        assertEquals("需要替换的播报", result.request.text)
        assertEquals("花火", result.config.character)
    }

    @Test
    fun `disabled module and unrelated traffic pass through`() {
        assertEquals(
            TransportDecision.PassThrough,
            TransportFallbackPolicy.decide(
                OkHttpTtsFallback.TARGET_ENDPOINT,
                """{"text":"原 TTS"}""",
                TtsConfig(enabled = false)
            )
        )
        assertEquals(
            TransportDecision.PassThrough,
            TransportFallbackPolicy.decide(
                "wss://chat.example/ws",
                """{"text":"聊天消息"}""",
                TtsConfig(enabled = true)
            )
        )
        assertEquals(
            TransportDecision.PassThrough,
            TransportFallbackPolicy.decide(
                OkHttpTtsFallback.TARGET_ENDPOINT,
                """{"event":"ping"}""",
                TtsConfig(enabled = true)
            )
        )
    }
}
