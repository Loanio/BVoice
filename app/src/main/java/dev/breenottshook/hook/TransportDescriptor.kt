package dev.breenottshook.hook

data class MethodDescriptor(
    val name: String,
    val parameterTypeNames: List<String>,
    val returnTypeName: String
)

data class TransportDescriptor(
    val className: String,
    val send: MethodDescriptor,
    val cancel: MethodDescriptor,
    val close: MethodDescriptor
)

internal val verifiedRealWebSocketTransport = TransportDescriptor(
    className = "okhttp3.internal.ws.RealWebSocket",
    send = MethodDescriptor("send", listOf("java.lang.String"), "boolean"),
    cancel = MethodDescriptor("cancel", emptyList(), "void"),
    close = MethodDescriptor("close", listOf("int", "java.lang.String"), "boolean")
)
