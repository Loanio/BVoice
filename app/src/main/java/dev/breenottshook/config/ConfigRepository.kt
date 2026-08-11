package dev.breenottshook.config

import android.content.ContentResolver
import android.database.ContentObserver
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ConfigRepository(
    private val resolver: ContentResolver
) : ConfigSnapshotSource {
    private val state = MutableStateFlow(read())
    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            runCatching { read() }.onSuccess { state.value = it }
        }
    }

    init {
        resolver.registerContentObserver(ConfigContract.URI, false, observer)
    }

    fun observe(): StateFlow<ConfigSnapshot> = state.asStateFlow()

    override fun read(): ConfigSnapshot {
        val bundle = resolver.call(
            ConfigContract.URI,
            ConfigContract.METHOD_GET_CONFIG,
            null,
            null
        ) ?: error("Config provider returned no data")
        return bundle.toSnapshot()
    }

    fun update(expectedVersion: Long?, config: TtsConfig): UpdateResult {
        val extras = Bundle().apply {
            putString(ConfigContract.KEY_PAYLOAD, ConfigCodec.encode(config))
            expectedVersion?.let { putLong(ConfigContract.KEY_EXPECTED_VERSION, it) }
        }
        val result = resolver.call(
            ConfigContract.URI,
            ConfigContract.METHOD_UPDATE_CONFIG,
            null,
            extras
        ) ?: return UpdateResult.PersistenceFailure
        if (result.getBoolean(ConfigContract.KEY_RESULT)) {
            return UpdateResult.Success(result.toSnapshot().also { state.value = it })
        }
        return if (result.getString(ConfigContract.KEY_ERROR) == "version_conflict") {
            UpdateResult.VersionConflict(result.getLong(ConfigContract.KEY_VERSION))
        } else {
            UpdateResult.PersistenceFailure
        }
    }

    private fun Bundle.toSnapshot() = ConfigSnapshot(
        version = getLong(ConfigContract.KEY_VERSION),
        value = ConfigCodec.decode(getString(ConfigContract.KEY_PAYLOAD) ?: error("Missing config payload"))
    )
}
