package dev.breenottshook.config

data class PersistedConfig(
    val version: Long,
    val payload: String
)

interface ConfigPersistence {
    fun read(): PersistedConfig?
    fun write(value: PersistedConfig): Boolean
}

interface ConfigStore {
    fun read(): ConfigSnapshot
    fun update(expectedVersion: Long?, config: TtsConfig): UpdateResult
}

sealed interface UpdateResult {
    data class Success(val snapshot: ConfigSnapshot) : UpdateResult
    data class VersionConflict(val currentVersion: Long) : UpdateResult
    data class Invalid(val issues: List<ConfigIssue>) : UpdateResult
    data object PersistenceFailure : UpdateResult
}

class AtomicConfigStore(
    private val persistence: ConfigPersistence
) : ConfigStore {
    private val lock = Any()

    override fun read(): ConfigSnapshot = synchronized(lock) {
        persistence.read()?.let { persisted ->
            runCatching {
                ConfigSnapshot(persisted.version, ConfigCodec.decode(persisted.payload))
            }.getOrNull()
        } ?: ConfigSnapshot(0, TtsConfig())
    }

    override fun update(expectedVersion: Long?, config: TtsConfig): UpdateResult = synchronized(lock) {
        val current = read()
        if (expectedVersion != null && expectedVersion != current.version) {
            return@synchronized UpdateResult.VersionConflict(current.version)
        }
        when (val validation = ConfigValidator.validate(config)) {
            is ValidationResult.Invalid -> UpdateResult.Invalid(validation.issues)
            is ValidationResult.Valid -> {
                val next = ConfigSnapshot(current.version + 1, validation.value)
                val saved = persistence.write(
                    PersistedConfig(next.version, ConfigCodec.encode(next.value))
                )
                if (saved) UpdateResult.Success(next) else UpdateResult.PersistenceFailure
            }
        }
    }
}
