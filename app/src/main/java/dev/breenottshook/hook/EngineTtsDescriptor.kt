package dev.breenottshook.hook

data class EngineTtsDescriptor(
    val className: String,
    val speak: MethodDescriptor,
    val streamStart: MethodDescriptor,
    val streamChunk: MethodDescriptor,
    val streamEnd: MethodDescriptor,
    val streamCancel: MethodDescriptor? = null,
    val shutup: MethodDescriptor? = null,
    val streamPause: MethodDescriptor? = null,
    val streamResume: MethodDescriptor? = null
)

sealed interface TtsRoute {
    data class WebSocket(val descriptor: TransportDescriptor) : TtsRoute
    data class Engine(val descriptor: EngineTtsDescriptor) : TtsRoute
}
