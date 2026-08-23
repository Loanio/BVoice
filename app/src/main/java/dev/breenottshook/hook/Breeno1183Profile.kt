package dev.breenottshook.hook

class Breeno1183Profile : VersionProfile {
    override val id: String = "breeno-11.8.3"
    override val ttsRoute: TtsRoute = TtsRoute.WebSocket(verifiedRealWebSocketTransport)

    override val capabilities = HookCapabilities(
        transportFallback = true,
        businessTtsEntry = false,
        originalPlayer = false,
        stopInterception = false,
        settingsInjection = false,
        reason = "11.8.3 原 TTS 业务入口、播放器与设置宿主描述符尚未经 APK/JADX 验证"
    )

    override fun matches(packageVersion: String, classProbe: ClassProbe): Boolean =
        classProbe.exists(
            (ttsRoute as TtsRoute.WebSocket).descriptor.className
        )

    companion object {
        const val SUPPORTED_VERSION = "11.8.3"
        const val REAL_WEB_SOCKET_CLASS = "okhttp3.internal.ws.RealWebSocket"
    }
}
