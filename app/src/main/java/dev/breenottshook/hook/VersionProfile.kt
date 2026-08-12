package dev.breenottshook.hook

fun interface ClassProbe {
    fun exists(className: String): Boolean
}

data class HookCapabilities(
    val transportFallback: Boolean = false,
    val businessTtsEntry: Boolean = false,
    val originalPlayer: Boolean = false,
    val stopInterception: Boolean = false,
    val settingsInjection: Boolean = false,
    val reason: String = ""
)

interface VersionProfile {
    val id: String
    val capabilities: HookCapabilities
    val ttsRoute: TtsRoute
    fun matches(packageVersion: String, classProbe: ClassProbe): Boolean
}
