package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportDescriptorTest {
    @Test
    fun `11_8_3 retains the observed websocket contract`() {
        val expected = TransportDescriptor(
            className = "okhttp3.internal.ws.RealWebSocket",
            send = MethodDescriptor("send", listOf("java.lang.String"), "boolean"),
            cancel = MethodDescriptor("cancel", emptyList(), "void"),
            close = MethodDescriptor("close", listOf("int", "java.lang.String"), "boolean")
        )

        val route = Breeno1183Profile().ttsRoute as TtsRoute.WebSocket

        assertEquals(expected, route.descriptor)
    }

    @Test
    fun `12_9_9 exposes the observed engine entry descriptors`() {
        val route = Breeno1299Profile().ttsRoute as TtsRoute.Engine

        assertEquals("com.heytap.speechassist.core.engine.TTSEngineImpl", route.descriptor.className)
        assertEquals(
            MethodDescriptor(
                "D0",
                listOf(
                    "java.lang.String",
                    "km.w",
                    "android.os.Bundle",
                    "com.heytap.speechassist.sdk.TTSEngine\$SlpTtsCallBack"
                ),
                "void"
            ),
            route.descriptor.speak
        )
        assertEquals(
            MethodDescriptor(
                "G",
                listOf("com.heytap.speechassist.sdk.tts.StreamTtsListener", "android.os.Bundle"),
                "void"
            ),
            route.descriptor.streamStart
        )
        assertEquals(
            MethodDescriptor("O0", listOf("java.lang.String"), "void"),
            route.descriptor.streamChunk
        )
        assertEquals(MethodDescriptor("J0", emptyList(), "void"), route.descriptor.streamEnd)
    }
}
