package dev.breenottshook.hook

class Breeno1299Profile : VersionProfile {
    override val id: String = "breeno-12.9.9"
    override val ttsRoute: TtsRoute = TtsRoute.Engine(
        EngineTtsDescriptor(
            className = "com.heytap.speechassist.core.engine.TTSEngineImpl",
            speak = MethodDescriptor(
                "m39754C0",
                listOf(
                    "java.lang.String",
                    "km.w",
                    "android.os.Bundle",
                    "com.heytap.speechassist.sdk.TTSEngine\${'$'}SlpTtsCallBack"
                ),
                "void"
            ),
            streamStart = MethodDescriptor(
                "m39779P0",
                listOf("com.heytap.speechassist.sdk.tts.StreamTtsListener", "android.os.Bundle"),
                "void"
            ),
            streamChunk = MethodDescriptor("m39777O0", listOf("java.lang.String"), "void"),
            streamEnd = MethodDescriptor("m39768J0", emptyList(), "void")
        )
    )

    override val capabilities = HookCapabilities(
        transportFallback = false,
        businessTtsEntry = true,
        originalPlayer = false,
        stopInterception = false,
        settingsInjection = false,
        reason = "12.9.9 已验证 TTSEngineImpl 业务入口；播放器与停止入口仍保持安全降级"
    )

    override fun matches(packageVersion: String, classProbe: ClassProbe): Boolean =
        packageVersion == SUPPORTED_VERSION && classProbe.exists(
            (ttsRoute as TtsRoute.Engine).descriptor.className
        )

    companion object {
        const val SUPPORTED_VERSION = "12.9.9"
    }
}
