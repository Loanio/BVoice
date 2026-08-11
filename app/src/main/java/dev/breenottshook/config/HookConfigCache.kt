package dev.breenottshook.config

fun interface ConfigSnapshotSource {
    fun read(): ConfigSnapshot
}

class HookConfigCache(
    private val source: ConfigSnapshotSource,
    initial: ConfigSnapshot = ConfigSnapshot(0, TtsConfig())
) {
    @Volatile
    private var snapshot: ConfigSnapshot = initial

    fun current(): ConfigSnapshot = snapshot

    fun refresh(): Result<ConfigSnapshot> = runCatching {
        source.read().also { snapshot = it }
    }
}
