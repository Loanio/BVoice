package dev.breenottshook.hook

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportDescriptorTest {
    @Test
    fun `both verified profiles expose the observed websocket contract`() {
        val expected = TransportDescriptor(
            className = "okhttp3.internal.ws.RealWebSocket",
            send = MethodDescriptor("send", listOf("java.lang.String"), "boolean"),
            cancel = MethodDescriptor("cancel", emptyList(), "void"),
            close = MethodDescriptor("close", listOf("int", "java.lang.String"), "boolean")
        )

        assertEquals(expected, Breeno1183Profile().transport)
        assertEquals(expected, Breeno1299Profile().transport)
    }
}
