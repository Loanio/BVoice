package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TtsPayloadExtractorTest {
    @Test
    fun `extracts nonblank text from supported top level and nested payloads`() {
        assertEquals("第一句", TtsPayloadExtractor.extract("""{"text":"第一句"}""")?.text)
        assertEquals(
            "第二句",
            TtsPayloadExtractor.extract("""{"data":{"text":"第二句"},"requestId":"r-2"}""")?.text
        )
        assertEquals(
            "第三句",
            TtsPayloadExtractor.extract("""{"params":{"content":"第三句"}}""")?.text
        )
    }

    @Test
    fun `rejects blank malformed and unrelated websocket JSON`() {
        assertNull(TtsPayloadExtractor.extract("""{"text":"  "}"""))
        assertNull(TtsPayloadExtractor.extract("not-json"))
        assertNull(TtsPayloadExtractor.extract("""{"event":"ping","contentType":"audio"}"""))
        assertNull(TtsPayloadExtractor.extract("""{"url":"https://example.test","message":"hello"}"""))
    }

    @Test
    fun `transport fallback is gated to exact secure host and path`() {
        assertTrue(OkHttpTtsFallback.isTargetUrl("wss://openapi-slp.heytapmobi.com/tts/ws"))
        assertTrue(
            OkHttpTtsFallback.isTargetUrl(
                "wss://openapi-slp.heytapmobi.com/tts/ws?appKey=k&time=1&sign=s&requestId=r"
            )
        )
        assertFalse(OkHttpTtsFallback.isTargetUrl("ws://openapi-slp.heytapmobi.com/tts/ws"))
        assertFalse(OkHttpTtsFallback.isTargetUrl("wss://evil.example/tts/ws"))
        assertFalse(OkHttpTtsFallback.isTargetUrl("wss://openapi-slp.heytapmobi.com/tts/ws/extra"))
        assertFalse(OkHttpTtsFallback.isTargetUrl("not a url"))
    }

    @Test
    fun `diagnostics identify utterances without exposing their text`() {
        val original = "这是不能出现在日志里的完整播报文本"

        val diagnostic = HookDiagnostics.utterance(original)

        assertFalse(diagnostic.contains(original))
        assertFalse(diagnostic.contains("完整播报"))
        assertTrue(diagnostic.contains("chars=${original.length}"))
        assertTrue(diagnostic.matches(Regex("chars=\\d+,sha256=[0-9a-f]{12}")))
    }

    @Test
    fun `websocket diagnostics expose routing shape without payload values`() {
        val secretText = "不能泄露的播报正文"
        val diagnostic = HookDiagnostics.websocket(
            "wss://voice.example.test/v2/speech?token=secret-token",
            """{"event":"synthesis","data":{"text":"$secretText"}}"""
        )

        assertEquals(
            "scheme=wss,host=voice.example.test,path=/v2/speech,chars=49,keys=data|event,dataKeys=text",
            diagnostic
        )
        assertFalse(diagnostic.contains(secretText))
        assertFalse(diagnostic.contains("secret-token"))
    }

    @Test
    fun `stream diagnostics expose counts and digest without chunk values`() {
        val secret = "不可写入日志的流式正文"
        val diagnostic = HookDiagnostics.stream(listOf("第一段", secret))

        assertTrue(diagnostic.startsWith("chunks=2,chars=${"第一段".length + secret.length},sha256="))
        assertFalse(diagnostic.contains(secret))
        assertFalse(diagnostic.contains("第一段"))
        assertTrue(diagnostic.matches(Regex("chunks=2,chars=\\d+,sha256=[0-9a-f]{12}")))
    }
}
