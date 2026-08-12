package dev.breenottshook.hook

class Breeno1299Profile : VersionProfile {
    override val id: String = "breeno-12.9.9"
    override val transport: TransportDescriptor = verifiedRealWebSocketTransport

    override val capabilities = HookCapabilities(
        transportFallback = true,
        businessTtsEntry = false,
        originalPlayer = false,
        stopInterception = false,
        settingsInjection = false,
        reason = "12.9.9 已验证传输签名；原播放器、停止入口与设置宿主仍保持安全降级"
    )

    override fun matches(packageVersion: String, classProbe: ClassProbe): Boolean =
        packageVersion == SUPPORTED_VERSION && classProbe.exists(transport.className)

    companion object {
        const val SUPPORTED_VERSION = "12.9.9"
    }
}
